-- CineCasa: códigos de activación (licencias).
-- El hogar dueño (Mauricio) genera y revoca códigos. Sus aparatos y los que empareje quedan activados siempre.

create table if not exists owners (
  household_id uuid primary key references households(id) on delete cascade
);

create table if not exists licenses (
  code text primary key,
  note text not null default '',
  max_devices int not null default 1,
  revoked boolean not null default false,
  created_at timestamptz not null default now()
);

create table if not exists activations (
  code text not null references licenses(code) on delete cascade,
  device_id text not null,
  device_name text not null default '',
  activated_at timestamptz not null default now(),
  last_seen timestamptz not null default now(),
  primary key (code, device_id)
);

alter table owners enable row level security;
alter table licenses enable row level security;
alter table activations enable row level security;
revoke all on owners, licenses, activations from anon, authenticated;

create or replace function cc_is_owner(p_h uuid, p_s text) returns boolean
language plpgsql security definer set search_path = public as $$
begin
  perform cc_check(p_h, p_s);
  return exists (select 1 from owners where household_id = p_h);
end $$;

create or replace function cc_owner_check(p_h uuid, p_s text) returns void
language plpgsql security definer set search_path = public as $$
begin
  perform cc_check(p_h, p_s);
  if not exists (select 1 from owners where household_id = p_h) then raise exception 'solo el dueño'; end if;
end $$;

-- Código tipo CC-7F3K-92QD (sin letras que se confunden: 0/O, 1/I)
create or replace function cc_lic_create(p_h uuid, p_s text, p_note text, p_devices int) returns text
language plpgsql security definer set search_path = public as $$
declare alphabet text := 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789'; v text; i int;
begin
  perform cc_owner_check(p_h, p_s);
  loop
    v := 'CC-';
    for i in 1..8 loop
      v := v || substr(alphabet, 1 + floor(random() * length(alphabet))::int, 1);
      if i = 4 then v := v || '-'; end if;
    end loop;
    exit when not exists (select 1 from licenses where code = v);
  end loop;
  insert into licenses(code, note, max_devices) values (v, coalesce(p_note, ''), greatest(1, least(coalesce(p_devices, 1), 20)));
  return v;
end $$;

create or replace function cc_lic_list(p_h uuid, p_s text) returns jsonb
language plpgsql security definer set search_path = public as $$
begin
  perform cc_owner_check(p_h, p_s);
  return coalesce((
    select jsonb_agg(jsonb_build_object(
      'code', l.code, 'note', l.note, 'max', l.max_devices, 'revoked', l.revoked, 'created', l.created_at,
      'devices', coalesce((select jsonb_agg(jsonb_build_object('name', a.device_name, 'activated', a.activated_at, 'seen', a.last_seen, 'id', a.device_id))
                           from activations a where a.code = l.code), '[]'::jsonb)
    ) order by l.created_at desc)
    from licenses l
  ), '[]'::jsonb);
end $$;

create or replace function cc_lic_set(p_h uuid, p_s text, p_code text, p_revoked boolean, p_reset boolean) returns void
language plpgsql security definer set search_path = public as $$
begin
  perform cc_owner_check(p_h, p_s);
  update licenses set revoked = p_revoked where code = upper(p_code);
  if p_reset then delete from activations where code = upper(p_code); end if;
end $$;

-- Activar un aparato con un código
create or replace function cc_lic_activate(p_code text, p_device text, p_name text) returns text
language plpgsql security definer set search_path = public as $$
declare l licenses; n int;
begin
  select * into l from licenses where code = upper(trim(p_code));
  if not found then return 'unknown'; end if;
  if l.revoked then return 'revoked'; end if;
  if exists (select 1 from activations where code = l.code and device_id = p_device) then
    update activations set last_seen = now(), device_name = coalesce(nullif(p_name, ''), device_name) where code = l.code and device_id = p_device;
    return 'ok';
  end if;
  select count(*) into n from activations where code = l.code;
  if n >= l.max_devices then return 'full'; end if;
  insert into activations(code, device_id, device_name) values (l.code, p_device, coalesce(p_name, ''));
  return 'ok';
end $$;

-- Revisión al abrir la app
create or replace function cc_lic_check(p_code text, p_device text) returns text
language plpgsql security definer set search_path = public as $$
declare l licenses;
begin
  select * into l from licenses where code = upper(trim(p_code));
  if not found then return 'unknown'; end if;
  if l.revoked then return 'revoked'; end if;
  if not exists (select 1 from activations where code = l.code and device_id = p_device) then return 'removed'; end if;
  update activations set last_seen = now() where code = l.code and device_id = p_device;
  return 'ok';
end $$;

revoke all on function cc_owner_check(uuid, text) from public, anon, authenticated;
grant execute on function cc_is_owner(uuid, text) to anon;
grant execute on function cc_lic_create(uuid, text, text, int) to anon;
grant execute on function cc_lic_list(uuid, text) to anon;
grant execute on function cc_lic_set(uuid, text, text, boolean, boolean) to anon;
grant execute on function cc_lic_activate(text, text, text) to anon;
grant execute on function cc_lic_check(text, text) to anon;

insert into owners(household_id) values ('a90caa55-6cf6-49fd-a3e0-4a57f148e14a') on conflict do nothing;
