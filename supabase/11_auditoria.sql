-- CineCasa (4 nov 2026) — arreglos de la auditoría.
-- 1) La prueba automática que NO viene de un distribuidor ya no recibe el servidor del gerente:
--    el cliente escribe la liga de su propio proveedor.
-- 2) Al vencer (o si quitaron el aparato) la app puede mostrar a quién pagar y escribir: cc_lic_help.
--    Si no hay vendedor, sale el WhatsApp y la liga de pago del gerente (cc_sales_contact).
-- 3) Configurar la tele con QR: ahora con una llave que solo conoce la tele (cc_pair_new2 / cc_pair_get2).
-- 4) Sesiones del Centro de control: vencen tras 30 días sin usarse; «cerrar sesión en todos lados».
-- 5) Reportes de fallas: límites por aparato y sin usuario/contraseña en el texto.
-- Se puede volver a correr sin romper nada. Va DESPUÉS de 01…10.

-- ---------- 1) prueba automática sin servidor del gerente ----------
alter table licenses add column if not exists ask_server boolean not null default false;

-- Las pruebas automáticas que ya existen y no son de ningún distribuidor
update licenses set ask_server = true
where note = 'auto' and reseller_id is null and server_id is null and not ask_server;

-- ¿Este código debe pedir la liga al cliente? (prueba automática sin distribuidor ni servidor elegido)
create or replace function cc__ask_server(l licenses) returns boolean language sql stable as $$
  select coalesce(l.ask_server, false) and l.reseller_id is null and l.server_id is null
$$;

create or replace function cc__server_for(l licenses) returns servers
language plpgsql stable security definer set search_path = public as $$
declare s servers;
begin
  if l.server_id is not null then
    select * into s from servers where id = l.server_id;
    if found then return s; end if;
  end if;
  if cc__ask_server(l) then return null; end if;
  select * into s from servers where reseller_id is not distinct from l.reseller_id
    order by is_default desc, created_at limit 1;
  return s;
end $$;

-- Lista de servidores para elegir (vacía si el cliente escribe su propia liga)
create or replace function cc__servers_for(l licenses) returns jsonb
language sql stable security definer set search_path = public as $$
  select case when cc__ask_server(l) then '[]'::jsonb else cc__servers_of(l.reseller_id) end
$$;

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
  return jsonb_build_object('status', 'ok', 'server', cc__srvjson(cc__server_for(l)), 'servers', cc__servers_for(l));
end $$;

create or replace function cc_lic_server(p_code text, p_device text) returns jsonb
language plpgsql security definer set search_path = public as $$
declare l licenses;
begin
  select * into l from licenses where code = upper(trim(p_code));
  if not found or not exists (select 1 from activations where code = l.code and device_id = p_device) then return null; end if;
  return jsonb_build_object('server', cc__srvjson(cc__server_for(l)), 'servers', cc__servers_for(l));
end $$;

-- La prueba automática marca si debe pedir la liga (solo cuando no la trae un distribuidor)
create or replace function cc_trial_auto(p_device text, p_name text, p_fp text, p_ref text) returns jsonb
language plpgsql security definer set search_path = public as $$
declare r jsonb := cc__rules(); v text; rs uuid; refc text; rf licenses; res text;
  d int := coalesce((r->>'auto_trial_days')::int, 21); n int := coalesce((r->>'auto_trial_devices')::int, 5);
begin
  if coalesce(r->>'auto_trial', 'on') <> 'on' then return jsonb_build_object('error', 'off'); end if;
  if length(coalesce(p_fp, '')) < 16 or length(coalesce(p_device, '')) < 8 then return jsonb_build_object('error', 'device'); end if;
  if exists (select 1 from trial_devices where fp = p_fp and app = 'cinecasa') then return jsonb_build_object('error', 'trial_used'); end if;
  if (select count(*) from licenses where note = 'auto' and created_at > now() - interval '1 hour') > 120 then
    return jsonb_build_object('error', 'busy');
  end if;
  if coalesce(trim(p_ref), '') <> '' then
    select * into rf from licenses where code = upper(trim(p_ref)) and app = 'cinecasa';
    if rf.code is null then return jsonb_build_object('error', 'bad_ref'); end if;
    refc := rf.code; rs := rf.reseller_id;
  end if;
  v := cc__newcode();
  insert into licenses(code, note, max_devices, reseller_id, kind, expires_at, customer, ref_by, ask_server)
  values (v, 'auto', n, rs, 'trial', now() + make_interval(days => d),
          left(coalesce(nullif(trim(p_name), ''), 'Prueba desde la app'), 60), refc, rs is null);
  res := cc_lic_activate2(v, p_device, p_name, p_fp);
  return jsonb_build_object('code', v, 'result', res, 'days', d, 'devices', n);
end $$;

-- ---------- 2) a quién pagar y escribir (también vencido o quitado) ----------
-- WhatsApp del gerente para quien no tiene vendedor (el mismo de las publicaciones), si aún no está puesto
update biz_settings set data = data || '{"manager_whatsapp": "526862595403"}'::jsonb
where id = 1 and coalesce(data->>'manager_whatsapp', '') = '';

create or replace function cc__price(n int) returns numeric language sql stable security definer set search_path = public as $$
  select case when n <= 1 then coalesce((r->'retail'->>'d1')::numeric, 169)
              when n = 2 then coalesce((r->'retail'->>'d2')::numeric, 279)
              else coalesce((r->'retail'->>'d3')::numeric, 349) + greatest(n - 3, 0) * coalesce((r->'retail'->>'extra')::numeric, 89) end
  from (select cc__rules() as r) x
$$;

-- Contacto general de ventas (para quien no tiene código o ya usó su prueba)
create or replace function cc_sales_contact() returns jsonb
language sql stable security definer set search_path = public as $$
  select jsonb_build_object('help_phone', coalesce(r->>'manager_whatsapp', ''), 'pay_link', coalesce(r->>'pay_link', ''),
    'price', cc__price(1), 'seller', null)
  from (select cc__rules() as r) x
$$;

-- Datos de contacto y pago de un código, aunque esté vencido, desactivado o este aparato se haya quitado
create or replace function cc_lic_help(p_code text, p_device text) returns jsonb
language plpgsql security definer set search_path = public as $$
declare l licenses; r resellers; ru jsonb := cc__rules(); n int;
begin
  select * into l from licenses where code = upper(trim(coalesce(p_code, '')));
  if not found or not (exists (select 1 from activations where code = l.code and device_id = p_device)
                    or exists (select 1 from device_blocks where code = l.code and device_id = p_device)) then
    return cc_sales_contact();
  end if;
  select * into r from resellers where id = l.reseller_id;
  n := case when l.kind = 'trial' then 1 else l.max_devices end;
  return jsonb_build_object('code', l.code, 'kind', l.kind, 'status', cc__status(l), 'expires', l.expires_at, 'max', l.max_devices,
    'seller', r.name,
    'help_phone', coalesce(nullif(r.phone, ''), ru->>'manager_whatsapp', ''),
    'pay_link', coalesce(nullif(r.pay_link, ''), ru->>'pay_link', ''),
    'price', cc__price(n));
end $$;

-- ---------- 3) tele con QR: con llave ----------
alter table tv_pair add column if not exists token_hash text;

create or replace function cc_pair_new2(p_token text) returns text
language plpgsql security definer set search_path = public as $$
declare c text; i int := 0;
begin
  if length(coalesce(p_token, '')) < 16 then raise exception 'llave corta'; end if;
  delete from tv_pair where created_at < now() - interval '15 minutes';
  loop
    c := lpad((floor(random() * 1000000))::int::text, 6, '0');
    begin
      insert into tv_pair(code, token_hash) values (c, encode(extensions.digest(p_token, 'sha256'), 'hex'));
      return c;
    exception when unique_violation then
      i := i + 1; if i > 20 then raise exception 'ocupado'; end if;
    end;
  end loop;
end $$;

create or replace function cc_pair_get2(p_code text, p_token text) returns jsonb
language plpgsql security definer set search_path = public as $$
declare r tv_pair;
begin
  select * into r from tv_pair where code = trim(p_code)
    and token_hash = encode(extensions.digest(coalesce(p_token, ''), 'sha256'), 'hex');
  if r.code is null or r.created_at < now() - interval '15 minutes' then return jsonb_build_object('status', 'expired'); end if;
  if r.server is null then return jsonb_build_object('status', 'waiting'); end if;
  delete from tv_pair where code = r.code;
  return jsonb_build_object('status', 'ok', 'server', r.server, 'user', r.usr, 'pass', r.pwd);
end $$;

-- La función vieja (versiones anteriores de la app) ya no entrega datos de códigos con llave
create or replace function cc_pair_get(p_code text) returns jsonb
language plpgsql security definer set search_path = public as $$
declare r tv_pair;
begin
  select * into r from tv_pair where code = trim(p_code);
  if r.code is null or r.created_at < now() - interval '15 minutes' then return jsonb_build_object('status', 'expired'); end if;
  if r.token_hash is not null or r.server is null then return jsonb_build_object('status', 'waiting'); end if;
  delete from tv_pair where code = r.code;
  return jsonb_build_object('status', 'ok', 'server', r.server, 'user', r.usr, 'pass', r.pwd);
end $$;

-- ---------- 4) sesiones del Centro de control ----------
create or replace function cc__sess(p_t text) returns panel_sessions
language plpgsql security definer set search_path = public as $$
declare s panel_sessions;
begin
  select * into s from panel_sessions where token_hash = cc__hash(p_t);
  if not found then raise exception 'sesion invalida'; end if;
  if s.last_seen < now() - interval '30 days' then
    delete from panel_sessions where token_hash = s.token_hash;
    raise exception 'tu sesion vencio por no usarla en 30 dias; entra otra vez con tu enlace';
  end if;
  update panel_sessions set last_seen = now() where token_hash = s.token_hash;
  return s;
end $$;

-- Cierra la sesión en todos los aparatos (los tuyos; un distribuidor solo los suyos)
create or replace function cc_logout_all(p_t text) returns int
language plpgsql security definer set search_path = public as $$
declare s panel_sessions := cc__sess(p_t); n int;
begin
  if s.role = 'mgr' then delete from panel_sessions where role = 'mgr';
  else delete from panel_sessions where reseller_id = s.reseller_id; end if;
  get diagnostics n = row_count;
  return n;
end $$;

-- El gerente cierra todas las sesiones de un distribuidor (por ejemplo, si perdió su celular)
create or replace function cc_rs_logout(p_t text, p_id uuid) returns int
language plpgsql security definer set search_path = public as $$
declare n int;
begin
  perform cc__mgr(p_t);
  delete from panel_sessions where reseller_id = p_id;
  get diagnostics n = row_count;
  return n;
end $$;

-- Al entrar con un enlace también se limpian las sesiones viejas
create or replace function cc_login(p_link text, p_label text) returns jsonb
language plpgsql security definer set search_path = public as $$
declare k panel_links; t text := cc__token();
begin
  delete from panel_sessions where last_seen < now() - interval '30 days';
  select * into k from panel_links where token_hash = cc__hash(p_link) and expires_at > now();
  if not found then raise exception 'enlace vencido o ya usado'; end if;
  delete from panel_links where token_hash = k.token_hash;
  insert into panel_sessions(token_hash, role, reseller_id, label) values (cc__hash(t), k.role, k.reseller_id, left(coalesce(p_label, ''), 80));
  return jsonb_build_object('token', t, 'role', k.role);
end $$;

delete from panel_sessions where last_seen < now() - interval '30 days';

-- ---------- 5) reportes de fallas ----------
create or replace function cc_crash(p_device text, p_text text) returns void
language plpgsql security definer set search_path = public as $$
declare d text := left(coalesce(p_device, ''), 80); t text := left(coalesce(p_text, ''), 9000);
begin
  -- nunca guardar usuario ni contraseña del proveedor
  t := regexp_replace(t, '/(live|movie|series|timeshift)/[^/\s]+/[^/\s]+/', '/\1/***/***/', 'g');
  t := regexp_replace(t, '(username|password)=[^&\s"]*', '\1=***', 'gi');
  if (select count(*) from crash_reports where device = d and created_at > now() - interval '1 hour') >= 20 then return; end if;
  if t like 'PLAY%' and (select count(*) from crash_reports where report like 'PLAY%' and created_at > now() - interval '1 hour') >= 150 then return; end if;
  if (select count(*) from crash_reports where created_at > now() - interval '1 hour') >= 400 then return; end if;
  insert into crash_reports(device, report) values (d, t);
end $$;
create index if not exists crash_reports_dev on crash_reports (device, created_at desc);

-- ---------- permisos ----------
revoke all on function cc__ask_server(licenses), cc__server_for(licenses), cc__servers_for(licenses), cc__price(int), cc__sess(text)
  from public, anon, authenticated;
grant execute on function cc_lic_ping(text, text, boolean, text, text) to anon;
grant execute on function cc_lic_server(text, text) to anon;
grant execute on function cc_trial_auto(text, text, text, text) to anon;
grant execute on function cc_sales_contact() to anon;
grant execute on function cc_lic_help(text, text) to anon;
grant execute on function cc_pair_new2(text) to anon;
grant execute on function cc_pair_get2(text, text) to anon;
grant execute on function cc_pair_get(text) to anon;
grant execute on function cc_logout_all(text) to anon;
grant execute on function cc_rs_logout(text, uuid) to anon;
grant execute on function cc_crash(text, text) to anon;
