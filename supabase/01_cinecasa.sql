-- CineCasa: perfiles y sincronización entre aparatos.
-- Nadie lee ni escribe las tablas directamente: todo pasa por funciones que
-- exigen el secreto del hogar (que solo conocen los aparatos emparejados).

create extension if not exists pgcrypto;

create table if not exists households (
  id uuid primary key default gen_random_uuid(),
  secret_hash text not null,
  created_at timestamptz not null default now()
);

-- Almacén genérico: scope '_' = datos del hogar (perfiles, aparatos);
-- scope = id del perfil para lo de cada persona (mi lista, progreso, vistos, canales…)
create table if not exists kv (
  household_id uuid not null references households(id) on delete cascade,
  scope text not null,
  kind text not null,
  key text not null,
  data jsonb,
  deleted boolean not null default false,
  updated_at timestamptz not null default clock_timestamp(),
  primary key (household_id, scope, kind, key)
);
create index if not exists kv_changes on kv (household_id, scope, updated_at);

-- Emparejar aparatos con un código de 6 números (vive 15 minutos)
create table if not exists pairings (
  code text primary key,
  token text not null,
  payload jsonb,
  created_at timestamptz not null default now()
);

alter table households enable row level security;
alter table kv enable row level security;
alter table pairings enable row level security;
revoke all on households, kv, pairings from anon, authenticated;

create or replace function cc_check(p_h uuid, p_s text) returns void
language plpgsql security definer set search_path = public as $$
begin
  if not exists (select 1 from households where id = p_h and secret_hash = encode(extensions.digest(p_s, 'sha256'), 'hex')) then
    raise exception 'acceso denegado';
  end if;
end $$;

create or replace function cc_create_household(p_s text) returns uuid
language plpgsql security definer set search_path = public as $$
declare v uuid;
begin
  if length(coalesce(p_s, '')) < 24 then raise exception 'secreto corto'; end if;
  insert into households(secret_hash) values (encode(extensions.digest(p_s, 'sha256'), 'hex')) returning id into v;
  return v;
end $$;

-- items: [{scope, kind, key, data, deleted}]
create or replace function cc_put(p_h uuid, p_s text, p_items jsonb) returns timestamptz
language plpgsql security definer set search_path = public as $$
begin
  perform cc_check(p_h, p_s);
  insert into kv(household_id, scope, kind, key, data, deleted, updated_at)
  select p_h, i->>'scope', i->>'kind', i->>'key', i->'data', coalesce((i->>'deleted')::boolean, false), clock_timestamp()
  from jsonb_array_elements(p_items) i
  on conflict (household_id, scope, kind, key)
  do update set data = excluded.data, deleted = excluded.deleted, updated_at = clock_timestamp();
  return clock_timestamp();
end $$;

-- Cambios desde una fecha, solo de los scopes pedidos ('_' y el perfil activo)
create or replace function cc_get(p_h uuid, p_s text, p_since timestamptz, p_scopes text[]) returns jsonb
language plpgsql security definer set search_path = public as $$
begin
  perform cc_check(p_h, p_s);
  return coalesce((
    select jsonb_agg(jsonb_build_object('scope', scope, 'kind', kind, 'key', key, 'data', data,
                                        'deleted', deleted, 'updated_at', updated_at) order by updated_at)
    from kv
    where household_id = p_h and updated_at > p_since and scope = any(p_scopes)
  ), '[]'::jsonb);
end $$;

-- Borra todo lo de un perfil (al eliminarlo)
create or replace function cc_wipe_scope(p_h uuid, p_s text, p_scope text) returns void
language plpgsql security definer set search_path = public as $$
begin
  perform cc_check(p_h, p_s);
  if p_scope = '_' then raise exception 'no permitido'; end if;
  delete from kv where household_id = p_h and scope = p_scope;
end $$;

create or replace function cc_pair_request(p_code text, p_token text) returns void
language plpgsql security definer set search_path = public as $$
begin
  delete from pairings where created_at < now() - interval '15 minutes';
  insert into pairings(code, token) values (p_code, p_token);
end $$;

create or replace function cc_pair_approve(p_h uuid, p_s text, p_code text, p_payload jsonb) returns boolean
language plpgsql security definer set search_path = public as $$
begin
  perform cc_check(p_h, p_s);
  update pairings set payload = p_payload
  where code = p_code and payload is null and created_at > now() - interval '15 minutes';
  return found;
end $$;

create or replace function cc_pair_poll(p_code text, p_token text) returns jsonb
language plpgsql security definer set search_path = public as $$
declare v jsonb;
begin
  select payload into v from pairings where code = p_code and token = p_token;
  if v is not null then delete from pairings where code = p_code; end if;
  return v;
end $$;

revoke all on function cc_check(uuid, text) from public, anon, authenticated;
grant execute on function cc_create_household(text) to anon;
grant execute on function cc_put(uuid, text, jsonb) to anon;
grant execute on function cc_get(uuid, text, timestamptz, text[]) to anon;
grant execute on function cc_wipe_scope(uuid, text, text) to anon;
grant execute on function cc_pair_request(text, text) to anon;
grant execute on function cc_pair_approve(uuid, text, text, jsonb) to anon;
grant execute on function cc_pair_poll(text, text) to anon;
