-- CineCasa (29 oct 2026)
-- 1) Servidores (ligas) por distribuidor: varios, uno por defecto, y cada código puede usar uno distinto.
--    El cliente solo escribe usuario y contraseña; si el distribuidor cambia la liga, la app la toma sola.
-- 2) Quitar UN aparato de un código (sin liberar todos). El aparato quitado no se puede volver a activar
--    con ese código hasta que se le dé «Permitir otra vez».
-- 3) «En uso ahora»: la app avisa cada pocos minutos si está abierta o reproduciendo (sin decir qué).
-- Se puede volver a correr sin romper nada.

create table if not exists servers (
  id uuid primary key default gen_random_uuid(),
  reseller_id uuid references resellers(id) on delete cascade,   -- null = del gerente
  name text not null,
  url text not null,
  is_default boolean not null default false,
  created_at timestamptz not null default now()
);
create index if not exists servers_rs on servers (reseller_id);

alter table licenses add column if not exists server_id uuid references servers(id) on delete set null;

alter table activations add column if not exists model text not null default '';
alter table activations add column if not exists version text not null default '';
alter table activations add column if not exists playing_at timestamptz;

create table if not exists device_blocks (
  code text not null references licenses(code) on delete cascade on update cascade,
  device_id text not null,
  name text not null default '',
  blocked_at timestamptz not null default now(),
  primary key (code, device_id)
);

alter table servers enable row level security;
alter table device_blocks enable row level security;
revoke all on servers, device_blocks from anon, authenticated;

-- ---------- utilidades ----------
create or replace function cc__dkey(p_device text) returns text language sql immutable as $$
  select left(md5(coalesce(p_device, '')), 10)
$$;

create or replace function cc__url(p text) returns text language plpgsql immutable as $$
declare t text := trim(coalesce(p, ''));
begin
  if t = '' then return ''; end if;
  if t !~* '^https?://' then t := 'http://' || t; end if;
  -- quita rutas que a veces se pegan con la liga
  t := regexp_replace(t, '/(player_api\.php|get\.php|xmltv\.php|panel_api\.php|c/).*$', '', 'i');
  return rtrim(t, '/');
end $$;

create or replace function cc__srvjson(s servers) returns jsonb language sql stable as $$
  select case when s.id is null then null else
    jsonb_build_object('id', s.id, 'name', s.name, 'url', s.url, 'default', s.is_default) end
$$;

-- Servidor que le toca a una licencia: el que tenga asignado, o el de por defecto de su vendedor (o del gerente)
create or replace function cc__server_for(l licenses) returns servers
language plpgsql stable security definer set search_path = public as $$
declare s servers;
begin
  if l.server_id is not null then
    select * into s from servers where id = l.server_id;
    if found then return s; end if;
  end if;
  select * into s from servers where reseller_id is not distinct from l.reseller_id
    order by is_default desc, created_at limit 1;
  return s;
end $$;

create or replace function cc__servers_of(p_rs uuid) returns jsonb
language sql stable security definer set search_path = public as $$
  select coalesce(jsonb_agg(cc__srvjson(s) order by s.is_default desc, s.created_at), '[]'::jsonb)
  from servers s where s.reseller_id is not distinct from p_rs
$$;

-- Estado de un aparato: viendo ahora / app abierta / nada
create or replace function cc__devstate(a activations) returns text language sql stable as $$
  select case when a.playing_at > now() - interval '4 minutes' then 'viendo'
              when a.last_seen > now() - interval '4 minutes' then 'abierta'
              else '' end
$$;

create or replace function cc__devjson(a activations) returns jsonb language sql stable as $$
  select jsonb_build_object('key', cc__dkey(a.device_id), 'name', a.device_name, 'model', a.model, 'version', a.version,
    'seen', a.last_seen, 'activated', a.activated_at, 'state', cc__devstate(a))
$$;

-- Lo que el panel ve de cada código (incluye app, aparatos uno por uno, quitados y servidor)
create or replace function cc__codejson(l licenses) returns jsonb language sql stable security definer set search_path = public as $$
  select jsonb_build_object(
    'code', l.code, 'app', l.app, 'app_name', coalesce(cc__app(l.app)->>'name', l.app),
    'note', l.note, 'customer', l.customer, 'phone', l.phone, 'kind', l.kind,
    'max', l.max_devices, 'revoked', l.revoked, 'created', l.created_at, 'expires', l.expires_at,
    'status', cc__status(l), 'reseller_id', l.reseller_id, 'ref_by', l.ref_by,
    'reseller', (select name from resellers r where r.id = l.reseller_id),
    'server_id', l.server_id,
    'server', cc__srvjson(cc__server_for(l)),
    'devices', coalesce((select jsonb_agg(cc__devjson(a) order by a.activated_at) from activations a where a.code = l.code), '[]'::jsonb),
    'blocked', coalesce((select jsonb_agg(jsonb_build_object('key', cc__dkey(b.device_id), 'name', b.name, 'at', b.blocked_at) order by b.blocked_at desc)
                         from device_blocks b where b.code = l.code), '[]'::jsonb))
$$;

-- Búsqueda de códigos también por nombre de aparato
create or replace function cc_code_list(p_t text, p_q text) returns jsonb
language plpgsql security definer set search_path = public as $$
declare s panel_sessions := cc__sess(p_t); rs uuid; q text := '%' || lower(coalesce(trim(p_q), '')) || '%';
begin
  rs := case when s.role = 'rs' then s.reseller_id else null end;
  return coalesce((select jsonb_agg(cc__codejson(l) order by l.created_at desc) from (
      select * from licenses l
      where (rs is null or l.reseller_id = rs)
        and (q = '%%' or lower(l.code || ' ' || l.customer || ' ' || l.note || ' ' || l.phone || ' ' ||
             coalesce((select name from resellers r where r.id = l.reseller_id), '') || ' ' ||
             coalesce((select string_agg(a.device_name, ' ') from activations a where a.code = l.code), '')) like q)
      order by l.created_at desc limit 500) l), '[]'::jsonb);
end $$;

-- ---------- panel: servidores ----------
create or replace function cc__mygroup(p_t text) returns uuid
language plpgsql security definer set search_path = public as $$
declare s panel_sessions := cc__sess(p_t);
begin
  return case when s.role = 'rs' then s.reseller_id else null end;
end $$;

create or replace function cc_srv_list(p_t text) returns jsonb
language plpgsql security definer set search_path = public as $$
declare g uuid := cc__mygroup(p_t);
begin
  return coalesce((select jsonb_agg(cc__srvjson(s) || jsonb_build_object(
      'codes', (select count(*) from licenses l where l.server_id = s.id))
      order by s.is_default desc, s.created_at)
    from servers s where s.reseller_id is not distinct from g), '[]'::jsonb);
end $$;

create or replace function cc_srv_save(p_t text, p_id uuid, p_name text, p_url text, p_default boolean) returns jsonb
language plpgsql security definer set search_path = public as $$
declare g uuid := cc__mygroup(p_t); u text := cc__url(p_url); v uuid; first boolean;
begin
  if coalesce(trim(p_name), '') = '' then raise exception 'falta el nombre'; end if;
  if u = '' or u !~* '^https?://[^/ ]+' then raise exception 'liga no valida'; end if;
  first := not exists (select 1 from servers where reseller_id is not distinct from g);
  if p_id is null then
    if (select count(*) from servers where reseller_id is not distinct from g) >= 20 then raise exception 'maximo 20 servidores'; end if;
    insert into servers(reseller_id, name, url, is_default) values (g, left(trim(p_name), 40), left(u, 300), first or coalesce(p_default, false)) returning id into v;
  else
    update servers set name = left(trim(p_name), 40), url = left(u, 300)
    where id = p_id and reseller_id is not distinct from g returning id into v;
    if v is null then raise exception 'servidor no encontrado'; end if;
  end if;
  if coalesce(p_default, false) or first then
    update servers set is_default = (id = v) where reseller_id is not distinct from g;
  end if;
  return cc_srv_list(p_t);
end $$;

create or replace function cc_srv_default(p_t text, p_id uuid) returns jsonb
language plpgsql security definer set search_path = public as $$
declare g uuid := cc__mygroup(p_t);
begin
  if not exists (select 1 from servers where id = p_id and reseller_id is not distinct from g) then raise exception 'servidor no encontrado'; end if;
  update servers set is_default = (id = p_id) where reseller_id is not distinct from g;
  return cc_srv_list(p_t);
end $$;

create or replace function cc_srv_delete(p_t text, p_id uuid) returns jsonb
language plpgsql security definer set search_path = public as $$
declare g uuid := cc__mygroup(p_t); was boolean;
begin
  delete from servers where id = p_id and reseller_id is not distinct from g returning is_default into was;
  if was is null then raise exception 'servidor no encontrado'; end if;
  -- los códigos que lo usaban pasan solos al de por defecto
  if was then
    update servers set is_default = true where id = (select id from servers where reseller_id is not distinct from g order by created_at limit 1);
  end if;
  return cc_srv_list(p_t);
end $$;

-- Elegir servidor de un código (null = el de por defecto)
create or replace function cc_code_server(p_t text, p_code text, p_server uuid) returns jsonb
language plpgsql security definer set search_path = public as $$
declare l licenses := cc__own(p_t, p_code);
begin
  if p_server is not null and not exists (select 1 from servers where id = p_server and reseller_id is not distinct from l.reseller_id) then
    raise exception 'ese servidor no es de este distribuidor';
  end if;
  update licenses set server_id = p_server where code = l.code;
  select * into l from licenses where code = l.code;
  return cc__codejson(l);
end $$;

-- ---------- panel: aparatos uno por uno ----------
create or replace function cc_code_device_remove(p_t text, p_code text, p_key text) returns jsonb
language plpgsql security definer set search_path = public as $$
declare l licenses := cc__own(p_t, p_code); a activations;
begin
  select * into a from activations where code = l.code and cc__dkey(device_id) = p_key;
  if a.code is null then raise exception 'aparato no encontrado'; end if;
  insert into device_blocks(code, device_id, name) values (l.code, a.device_id, a.device_name)
    on conflict (code, device_id) do update set blocked_at = now(), name = excluded.name;
  delete from activations where code = l.code and device_id = a.device_id;
  select * into l from licenses where code = l.code;
  return cc__codejson(l);
end $$;

create or replace function cc_code_device_allow(p_t text, p_code text, p_key text) returns jsonb
language plpgsql security definer set search_path = public as $$
declare l licenses := cc__own(p_t, p_code);
begin
  delete from device_blocks where code = l.code and cc__dkey(device_id) = p_key;
  select * into l from licenses where code = l.code;
  return cc__codejson(l);
end $$;

create or replace function cc_code_device_rename(p_t text, p_code text, p_key text, p_name text) returns jsonb
language plpgsql security definer set search_path = public as $$
declare l licenses := cc__own(p_t, p_code);
begin
  update activations set device_name = left(trim(coalesce(p_name, '')), 40) where code = l.code and cc__dkey(device_id) = p_key;
  select * into l from licenses where code = l.code;
  return cc__codejson(l);
end $$;

-- ---------- lo que ven las apps ----------
create or replace function cc_lic_activate(p_code text, p_device text, p_name text) returns text
language plpgsql security definer set search_path = public as $$
declare l licenses; n int;
begin
  select * into l from licenses where code = upper(trim(p_code));
  if not found or l.app <> 'cinecasa' then return 'unknown'; end if;
  if l.revoked then return 'revoked'; end if;
  if l.expires_at is not null and l.expires_at < now() then return 'expired'; end if;
  if exists (select 1 from device_blocks where code = l.code and device_id = p_device) then return 'blocked'; end if;
  if exists (select 1 from activations where code = l.code and device_id = p_device) then
    update activations set last_seen = now(), device_name = coalesce(nullif(p_name, ''), device_name) where code = l.code and device_id = p_device;
    return 'ok';
  end if;
  select count(*) into n from activations where code = l.code;
  if n >= l.max_devices then return 'full'; end if;
  insert into activations(code, device_id, device_name) values (l.code, p_device, left(coalesce(p_name, ''), 80));
  return 'ok';
end $$;

create or replace function cc_lic_check(p_code text, p_device text) returns text
language plpgsql security definer set search_path = public as $$
declare l licenses;
begin
  select * into l from licenses where code = upper(trim(p_code));
  if not found or l.app <> 'cinecasa' then return 'unknown'; end if;
  if l.revoked then return 'revoked'; end if;
  if l.expires_at is not null and l.expires_at < now() then return 'expired'; end if;
  if exists (select 1 from device_blocks where code = l.code and device_id = p_device) then return 'blocked'; end if;
  if not exists (select 1 from activations where code = l.code and device_id = p_device) then return 'removed'; end if;
  update activations set last_seen = now() where code = l.code and device_id = p_device;
  return 'ok';
end $$;

-- Revisión periódica de la app: estado + servidor que le toca. p_playing = está reproduciendo (no dice qué).
create or replace function cc_lic_ping(p_code text, p_device text, p_playing boolean, p_model text, p_version text) returns jsonb
language plpgsql security definer set search_path = public as $$
declare st text := cc_lic_check(p_code, p_device); l licenses;
begin
  if st <> 'ok' then return jsonb_build_object('status', st); end if;
  select * into l from licenses where code = upper(trim(p_code));
  update activations set
    playing_at = case when coalesce(p_playing, false) then now() else playing_at end,
    model = left(coalesce(nullif(p_model, ''), model), 60), version = left(coalesce(nullif(p_version, ''), version), 20)
  where code = l.code and device_id = p_device;
  return jsonb_build_object('status', 'ok', 'server', cc__srvjson(cc__server_for(l)), 'servers', cc__servers_of(l.reseller_id));
end $$;

-- Servidor que le toca a un código, para la pantalla de entrar (aunque todavía no haya revisión)
create or replace function cc_lic_server(p_code text, p_device text) returns jsonb
language plpgsql security definer set search_path = public as $$
declare l licenses;
begin
  select * into l from licenses where code = upper(trim(p_code));
  if not found or not exists (select 1 from activations where code = l.code and device_id = p_device) then return null; end if;
  return jsonb_build_object('server', cc__srvjson(cc__server_for(l)), 'servers', cc__servers_of(l.reseller_id));
end $$;

-- El dueño (Mauricio) también entra con su servidor de por defecto
create or replace function cc_owner_server(p_h uuid, p_s text) returns jsonb
language plpgsql security definer set search_path = public as $$
declare s servers;
begin
  perform cc_owner_check(p_h, p_s);
  select * into s from servers where reseller_id is null order by is_default desc, created_at limit 1;
  return jsonb_build_object('server', cc__srvjson(s), 'servers', cc__servers_of(null));
end $$;

-- «Mis aparatos» en la app del cliente (solo si este aparato está activado con ese código)
create or replace function cc_lic_devices(p_code text, p_device text) returns jsonb
language plpgsql security definer set search_path = public as $$
declare l licenses;
begin
  select * into l from licenses where code = upper(trim(p_code));
  if not found or not exists (select 1 from activations where code = l.code and device_id = p_device) then return null; end if;
  return jsonb_build_object('max', l.max_devices, 'me', cc__dkey(p_device),
    'devices', coalesce((select jsonb_agg(cc__devjson(a) order by a.activated_at) from activations a where a.code = l.code), '[]'::jsonb),
    'blocked', coalesce((select jsonb_agg(jsonb_build_object('key', cc__dkey(b.device_id), 'name', b.name, 'at', b.blocked_at) order by b.blocked_at desc)
                         from device_blocks b where b.code = l.code), '[]'::jsonb));
end $$;

create or replace function cc_lic_device_remove(p_code text, p_device text, p_key text) returns text
language plpgsql security definer set search_path = public as $$
declare l licenses; a activations;
begin
  select * into l from licenses where code = upper(trim(p_code));
  if not found or not exists (select 1 from activations where code = l.code and device_id = p_device) then return 'unknown'; end if;
  if cc__dkey(p_device) = p_key then return 'self'; end if;
  select * into a from activations where code = l.code and cc__dkey(device_id) = p_key;
  if a.code is null then return 'gone'; end if;
  insert into device_blocks(code, device_id, name) values (l.code, a.device_id, a.device_name)
    on conflict (code, device_id) do update set blocked_at = now(), name = excluded.name;
  delete from activations where code = l.code and device_id = a.device_id;
  return 'ok';
end $$;

create or replace function cc_lic_device_allow(p_code text, p_device text, p_key text) returns text
language plpgsql security definer set search_path = public as $$
declare l licenses;
begin
  select * into l from licenses where code = upper(trim(p_code));
  if not found or not exists (select 1 from activations where code = l.code and device_id = p_device) then return 'unknown'; end if;
  delete from device_blocks where code = l.code and cc__dkey(device_id) = p_key;
  return 'ok';
end $$;

create or replace function cc_lic_device_rename(p_code text, p_device text, p_key text, p_name text) returns text
language plpgsql security definer set search_path = public as $$
declare l licenses;
begin
  select * into l from licenses where code = upper(trim(p_code));
  if not found or not exists (select 1 from activations where code = l.code and device_id = p_device) then return 'unknown'; end if;
  update activations set device_name = left(trim(coalesce(p_name, '')), 40) where code = l.code and cc__dkey(device_id) = p_key;
  return 'ok';
end $$;

-- Vista y otras apps: también respetan los aparatos quitados
create or replace function cc_app_activate(p_app text, p_code text, p_device text, p_name text, p_fp text) returns text
language plpgsql security definer set search_path = public as $$
declare a text := lower(coalesce(p_app, '')); l licenses; n int;
begin
  select * into l from licenses where code = upper(trim(p_code));
  if not found or l.app <> a then return 'unknown'; end if;
  if l.revoked then return 'revoked'; end if;
  if l.expires_at is not null and l.expires_at < now() then return 'expired'; end if;
  if length(coalesce(p_device, '')) < 8 then return 'device'; end if;
  if exists (select 1 from device_blocks where code = l.code and device_id = p_device) then return 'removed'; end if;
  if exists (select 1 from activations where code = l.code and device_id = p_device) then
    update activations set last_seen = now(), device_name = coalesce(nullif(p_name, ''), device_name) where code = l.code and device_id = p_device;
    return 'ok';
  end if;
  if l.kind = 'trial' and length(coalesce(p_fp, '')) >= 16
     and exists (select 1 from trial_devices where fp = p_fp and app = a and code <> l.code) then
    return 'trial_used';
  end if;
  select count(*) into n from activations where code = l.code;
  if n >= l.max_devices then return 'full'; end if;
  insert into activations(code, device_id, device_name) values (l.code, p_device, left(coalesce(p_name, ''), 80));
  if l.kind = 'trial' and length(coalesce(p_fp, '')) >= 16 then
    insert into trial_devices(fp, code, app) values (p_fp, l.code, a) on conflict do nothing;
  end if;
  return 'ok';
end $$;

-- ---------- permisos ----------
revoke all on function cc__dkey(text), cc__url(text), cc__srvjson(servers), cc__server_for(licenses), cc__servers_of(uuid),
  cc__devstate(activations), cc__devjson(activations), cc__codejson(licenses), cc__mygroup(text) from public, anon, authenticated;

grant execute on function cc_code_list(text, text) to anon;
grant execute on function cc_srv_list(text) to anon;
grant execute on function cc_srv_save(text, uuid, text, text, boolean) to anon;
grant execute on function cc_srv_default(text, uuid) to anon;
grant execute on function cc_srv_delete(text, uuid) to anon;
grant execute on function cc_code_server(text, text, uuid) to anon;
grant execute on function cc_code_device_remove(text, text, text) to anon;
grant execute on function cc_code_device_allow(text, text, text) to anon;
grant execute on function cc_code_device_rename(text, text, text, text) to anon;
grant execute on function cc_lic_activate(text, text, text) to anon;
grant execute on function cc_lic_check(text, text) to anon;
grant execute on function cc_lic_ping(text, text, boolean, text, text) to anon;
grant execute on function cc_lic_server(text, text) to anon;
grant execute on function cc_owner_server(uuid, text) to anon;
grant execute on function cc_lic_devices(text, text) to anon;
grant execute on function cc_lic_device_remove(text, text, text) to anon;
grant execute on function cc_lic_device_allow(text, text, text) to anon;
grant execute on function cc_lic_device_rename(text, text, text, text) to anon;
grant execute on function cc_app_activate(text, text, text, text, text) to anon;
