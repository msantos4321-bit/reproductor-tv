-- CineCasa: Centro de control (gerente y distribuidores).
-- 1 crédito = 1 aparato por 1 año. Pruebas gratis por días, sin gastar créditos.
-- El panel web entra con una sesión (token) que nace de:
--   * gerente: un enlace de un solo uso que pide la app del dueño (cc_mgr_link)
--   * distribuidor: un enlace que genera el gerente (cc_rs_link)
-- Se puede volver a correr sin romper nada.

create table if not exists resellers (
  id uuid primary key default gen_random_uuid(),
  name text not null,
  phone text not null default '',
  email text not null default '',
  notes text not null default '',
  credits numeric(10,2) not null default 0,
  paused boolean not null default false,
  created_at timestamptz not null default now()
);

create table if not exists ledger (
  id bigserial primary key,
  reseller_id uuid references resellers(id) on delete cascade,
  delta numeric(10,2) not null,
  reason text not null,
  code text,
  amount_mxn numeric(10,2),
  created_at timestamptz not null default now()
);
create index if not exists ledger_rs on ledger (reseller_id, created_at desc);

create table if not exists panel_sessions (
  token_hash text primary key,
  role text not null check (role in ('mgr', 'rs')),
  reseller_id uuid references resellers(id) on delete cascade,
  label text not null default '',
  created_at timestamptz not null default now(),
  last_seen timestamptz not null default now()
);

create table if not exists panel_links (
  token_hash text primary key,
  role text not null check (role in ('mgr', 'rs')),
  reseller_id uuid references resellers(id) on delete cascade,
  expires_at timestamptz not null
);

create table if not exists biz_settings (
  id int primary key default 1 check (id = 1),
  data jsonb not null
);

insert into biz_settings(id, data) values (1, '{
  "trial_days": 21,
  "trial_devices": 2,
  "trial_month_cap": 20,
  "start_credits": 3,
  "retail": {"d1": 149, "d2": 249, "d3": 299, "extra": 79},
  "levels": [
    {"name": "Inicial", "min": 0,   "price": 50},
    {"name": "Plata",   "min": 100, "price": 45},
    {"name": "Oro",     "min": 400, "price": 40}
  ],
  "manager_whatsapp": "",
  "download_url": "https://github.com/msantos4321-bit/reproductor-tv/releases/latest/download/CineCasa.apk"
}'::jsonb) on conflict (id) do nothing;

alter table licenses add column if not exists reseller_id uuid references resellers(id) on delete set null;
alter table licenses add column if not exists kind text not null default 'paid';
alter table licenses add column if not exists expires_at timestamptz;
alter table licenses add column if not exists customer text not null default '';
alter table licenses add column if not exists phone text not null default '';
alter table licenses add column if not exists paid_at timestamptz;
create index if not exists licenses_rs on licenses (reseller_id, created_at desc);

alter table resellers enable row level security;
alter table ledger enable row level security;
alter table panel_sessions enable row level security;
alter table panel_links enable row level security;
alter table biz_settings enable row level security;
revoke all on resellers, ledger, panel_sessions, panel_links, biz_settings from anon, authenticated;

-- ---------- utilidades internas ----------
create or replace function cc__hash(t text) returns text language sql immutable as $$
  select encode(extensions.digest(coalesce(t, ''), 'sha256'), 'hex')
$$;

create or replace function cc__token() returns text language sql volatile as $$
  select encode(extensions.gen_random_bytes(24), 'hex')
$$;

-- Devuelve la sesión válida o lanza error. Distribuidor en pausa sigue pudiendo ver, no crear.
create or replace function cc__sess(p_t text) returns panel_sessions
language plpgsql security definer set search_path = public as $$
declare s panel_sessions;
begin
  select * into s from panel_sessions where token_hash = cc__hash(p_t);
  if not found then raise exception 'sesion invalida'; end if;
  update panel_sessions set last_seen = now() where token_hash = s.token_hash;
  return s;
end $$;

create or replace function cc__mgr(p_t text) returns void
language plpgsql security definer set search_path = public as $$
begin
  if (cc__sess(p_t)).role <> 'mgr' then raise exception 'solo el gerente'; end if;
end $$;

create or replace function cc__rules() returns jsonb language sql stable security definer set search_path = public as $$
  select data from biz_settings where id = 1
$$;

-- Aparatos-año pagados en los últimos 365 días (para el nivel)
create or replace function cc__sold(p_rs uuid) returns numeric language sql stable security definer set search_path = public as $$
  select coalesce(-sum(delta), 0) from ledger
  where reseller_id = p_rs and delta < 0 and reason in ('venta', 'conversion', 'renovacion', 'aparato_extra')
    and created_at > now() - interval '365 days'
$$;

create or replace function cc__level(p_rs uuid) returns jsonb language plpgsql stable security definer set search_path = public as $$
declare sold numeric := cc__sold(p_rs); lv jsonb; best jsonb;
begin
  for lv in select * from jsonb_array_elements(cc__rules()->'levels') loop
    if sold >= (lv->>'min')::numeric then best := lv; end if;
  end loop;
  return coalesce(best, '{"name":"Inicial","min":0,"price":50}'::jsonb) || jsonb_build_object('sold', sold);
end $$;

create or replace function cc__newcode() returns text language plpgsql volatile security definer set search_path = public as $$
declare alphabet text := 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789'; v text; i int;
begin
  loop
    v := 'CC-';
    for i in 1..8 loop
      v := v || substr(alphabet, 1 + floor(random() * length(alphabet))::int, 1);
      if i = 4 then v := v || '-'; end if;
    end loop;
    exit when not exists (select 1 from licenses where code = v);
  end loop;
  return v;
end $$;

-- Cobra créditos a un distribuidor (no hace nada si es el gerente)
create or replace function cc__charge(p_rs uuid, p_n numeric, p_reason text, p_code text) returns void
language plpgsql security definer set search_path = public as $$
declare have numeric;
begin
  if p_n <= 0 then return; end if;
  if p_rs is null then
    -- Venta directa del gerente: no gasta créditos, pero queda registrada para las cuentas
    insert into ledger(reseller_id, delta, reason, code) values (null, -p_n, p_reason, p_code);
    return;
  end if;
  select credits into have from resellers where id = p_rs for update;
  if have < p_n then raise exception 'creditos insuficientes: tienes %, se necesitan %', have, p_n; end if;
  update resellers set credits = credits - p_n where id = p_rs;
  insert into ledger(reseller_id, delta, reason, code) values (p_rs, -p_n, p_reason, p_code);
end $$;

-- Una licencia que pertenece a quien pregunta (gerente: todas)
create or replace function cc__own(p_t text, p_code text) returns licenses
language plpgsql security definer set search_path = public as $$
declare s panel_sessions := cc__sess(p_t); l licenses;
begin
  select * into l from licenses where code = upper(trim(p_code));
  if not found then raise exception 'codigo no encontrado'; end if;
  if s.role = 'rs' and l.reseller_id is distinct from s.reseller_id then raise exception 'ese codigo no es tuyo'; end if;
  return l;
end $$;

create or replace function cc__status(l licenses) returns text language sql stable as $$
  select case
    when l.revoked then 'desactivado'
    when l.expires_at is not null and l.expires_at < now() then 'vencido'
    when l.kind = 'trial' then 'prueba'
    else 'activo' end
$$;

create or replace function cc__codejson(l licenses) returns jsonb language sql stable security definer set search_path = public as $$
  select jsonb_build_object(
    'code', l.code, 'note', l.note, 'customer', l.customer, 'phone', l.phone, 'kind', l.kind,
    'max', l.max_devices, 'revoked', l.revoked, 'created', l.created_at, 'expires', l.expires_at,
    'status', cc__status(l), 'reseller_id', l.reseller_id,
    'reseller', (select name from resellers r where r.id = l.reseller_id),
    'devices', coalesce((select jsonb_agg(jsonb_build_object('name', a.device_name, 'seen', a.last_seen, 'activated', a.activated_at) order by a.activated_at)
                         from activations a where a.code = l.code), '[]'::jsonb))
$$;

-- ---------- entrar al panel ----------
-- La app del dueño pide un enlace de un solo uso (10 minutos)
create or replace function cc_mgr_link(p_h uuid, p_s text) returns text
language plpgsql security definer set search_path = public as $$
declare t text := cc__token();
begin
  perform cc_owner_check(p_h, p_s);
  delete from panel_links where expires_at < now();
  insert into panel_links(token_hash, role, expires_at) values (cc__hash(t), 'mgr', now() + interval '10 minutes');
  return t;
end $$;

-- El gerente crea un enlace para abrir el panel en otro aparato suyo (10 min) o para un distribuidor (7 días)
create or replace function cc_link_create(p_t text, p_reseller uuid) returns text
language plpgsql security definer set search_path = public as $$
declare t text := cc__token();
begin
  perform cc__mgr(p_t);
  if p_reseller is null then
    insert into panel_links(token_hash, role, expires_at) values (cc__hash(t), 'mgr', now() + interval '10 minutes');
  else
    insert into panel_links(token_hash, role, reseller_id, expires_at) values (cc__hash(t), 'rs', p_reseller, now() + interval '7 days');
  end if;
  return t;
end $$;

-- Cambia un enlace por una sesión
create or replace function cc_login(p_link text, p_label text) returns jsonb
language plpgsql security definer set search_path = public as $$
declare k panel_links; t text := cc__token();
begin
  select * into k from panel_links where token_hash = cc__hash(p_link) and expires_at > now();
  if not found then raise exception 'enlace vencido o ya usado'; end if;
  delete from panel_links where token_hash = k.token_hash;
  insert into panel_sessions(token_hash, role, reseller_id, label) values (cc__hash(t), k.role, k.reseller_id, left(coalesce(p_label, ''), 80));
  return jsonb_build_object('token', t, 'role', k.role);
end $$;

create or replace function cc_logout(p_t text) returns void
language plpgsql security definer set search_path = public as $$
begin
  delete from panel_sessions where token_hash = cc__hash(p_t);
end $$;

create or replace function cc_me(p_t text) returns jsonb
language plpgsql security definer set search_path = public as $$
declare s panel_sessions := cc__sess(p_t); r resellers;
begin
  if s.role = 'mgr' then
    return jsonb_build_object('role', 'mgr', 'name', 'Gerente general', 'rules', cc__rules());
  end if;
  select * into r from resellers where id = s.reseller_id;
  return jsonb_build_object('role', 'rs', 'id', r.id, 'name', r.name, 'credits', r.credits, 'paused', r.paused,
    'level', cc__level(r.id), 'rules', cc__rules());
end $$;

-- ---------- resumen ----------
create or replace function cc_summary(p_t text) returns jsonb
language plpgsql security definer set search_path = public as $$
declare s panel_sessions := cc__sess(p_t); rs uuid;
begin
  rs := case when s.role = 'rs' then s.reseller_id else null end;
  return (
    with l as (select * from licenses where (rs is null or reseller_id = rs)),
         g as (select * from ledger where (rs is null or reseller_id = rs))
    select jsonb_build_object(
      'active_homes', (select count(*) from l where not revoked and (expires_at is null or expires_at > now()) and kind <> 'trial'),
      'active_devices', (select count(*) from activations a join l on l.code = a.code where not l.revoked and (l.expires_at is null or l.expires_at > now())),
      'trials', (select count(*) from l where kind = 'trial' and not revoked and expires_at > now()),
      'expiring', (select count(*) from l where kind <> 'trial' and not revoked and expires_at between now() and now() + interval '15 days'),
      'expired', (select count(*) from l where not revoked and expires_at < now()),
      'sold_month', (select coalesce(-sum(delta), 0) from g where delta < 0 and created_at > date_trunc('month', now())),
      'sold_year', (select coalesce(-sum(delta), 0) from g where delta < 0 and created_at > now() - interval '365 days'),
      'income_month', (select coalesce(sum(amount_mxn), 0) from g where amount_mxn is not null and created_at > date_trunc('month', now())),
      'trials_month', (select count(*) from l where kind = 'trial' and created_at > date_trunc('month', now())),
      'converted_month', (select count(*) from g where reason = 'conversion' and created_at > date_trunc('month', now())),
      'resellers', (case when rs is null then (select count(*) from resellers) else null end),
      'credits_out', (case when rs is null then (select coalesce(sum(credits), 0) from resellers) else (select credits from resellers where id = rs) end)
    )
  );
end $$;

-- ---------- reglas ----------
create or replace function cc_rules_set(p_t text, p_data jsonb) returns jsonb
language plpgsql security definer set search_path = public as $$
begin
  perform cc__mgr(p_t);
  update biz_settings set data = data || p_data where id = 1;
  return cc__rules();
end $$;

-- ---------- distribuidores ----------
create or replace function cc_rs_list(p_t text) returns jsonb
language plpgsql security definer set search_path = public as $$
begin
  perform cc__mgr(p_t);
  return coalesce((select jsonb_agg(jsonb_build_object(
      'id', r.id, 'name', r.name, 'phone', r.phone, 'email', r.email, 'notes', r.notes, 'credits', r.credits,
      'paused', r.paused, 'created', r.created_at, 'level', cc__level(r.id),
      'homes', (select count(*) from licenses l where l.reseller_id = r.id and l.kind <> 'trial' and not l.revoked and (l.expires_at is null or l.expires_at > now())),
      'trials', (select count(*) from licenses l where l.reseller_id = r.id and l.kind = 'trial' and not l.revoked and l.expires_at > now()),
      'expiring', (select count(*) from licenses l where l.reseller_id = r.id and l.kind <> 'trial' and not l.revoked and l.expires_at between now() and now() + interval '15 days'),
      'last_seen', (select max(last_seen) from panel_sessions ps where ps.reseller_id = r.id)
    ) order by r.created_at desc) from resellers r), '[]'::jsonb);
end $$;

create or replace function cc_rs_save(p_t text, p_id uuid, p_name text, p_phone text, p_email text, p_notes text) returns uuid
language plpgsql security definer set search_path = public as $$
declare v uuid; gift numeric;
begin
  perform cc__mgr(p_t);
  if coalesce(trim(p_name), '') = '' then raise exception 'falta el nombre'; end if;
  if p_id is null then
    insert into resellers(name, phone, email, notes) values (trim(p_name), coalesce(p_phone, ''), coalesce(p_email, ''), coalesce(p_notes, '')) returning id into v;
    gift := coalesce((cc__rules()->>'start_credits')::numeric, 0);
    if gift > 0 then
      update resellers set credits = credits + gift where id = v;
      insert into ledger(reseller_id, delta, reason) values (v, gift, 'regalo_inicio');
    end if;
  else
    update resellers set name = trim(p_name), phone = coalesce(p_phone, ''), email = coalesce(p_email, ''), notes = coalesce(p_notes, '') where id = p_id returning id into v;
  end if;
  return v;
end $$;

create or replace function cc_rs_pause(p_t text, p_id uuid, p_paused boolean) returns void
language plpgsql security definer set search_path = public as $$
begin
  perform cc__mgr(p_t);
  update resellers set paused = p_paused where id = p_id;
  if p_paused then delete from panel_sessions where reseller_id = p_id; end if;
end $$;

-- Abonar o quitar créditos (p. ej. cuando te paga). amount = lo que pagó, para tus cuentas.
create or replace function cc_rs_credit(p_t text, p_id uuid, p_delta numeric, p_reason text, p_amount numeric) returns numeric
language plpgsql security definer set search_path = public as $$
declare v numeric;
begin
  perform cc__mgr(p_t);
  update resellers set credits = credits + p_delta where id = p_id returning credits into v;
  if v is null then raise exception 'distribuidor no encontrado'; end if;
  if v < 0 then raise exception 'no puede quedar en negativo'; end if;
  insert into ledger(reseller_id, delta, reason, amount_mxn) values (p_id, p_delta, coalesce(nullif(p_reason, ''), 'compra'), p_amount);
  return v;
end $$;

create or replace function cc_ledger(p_t text, p_id uuid) returns jsonb
language plpgsql security definer set search_path = public as $$
declare s panel_sessions := cc__sess(p_t); rs uuid;
begin
  rs := case when s.role = 'rs' then s.reseller_id else p_id end;
  return coalesce((select jsonb_agg(jsonb_build_object('delta', delta, 'reason', reason, 'code', code, 'amount', amount_mxn, 'at', created_at,
      'reseller', (select name from resellers r where r.id = g.reseller_id)) order by created_at desc)
    from (select * from ledger where (rs is null or reseller_id = rs) order by created_at desc limit 300) g), '[]'::jsonb);
end $$;

-- ---------- códigos ----------
-- kind: 'trial' (prueba gratis) | 'paid' (anual, gasta créditos) | 'gift' (solo gerente, sin vencimiento)
create or replace function cc_code_create(p_t text, p_kind text, p_devices int, p_customer text, p_phone text, p_note text) returns jsonb
language plpgsql security definer set search_path = public as $$
declare s panel_sessions := cc__sess(p_t); r jsonb := cc__rules(); v text := cc__newcode(); rs uuid; n int; used int; l licenses;
begin
  rs := case when s.role = 'rs' then s.reseller_id else null end;
  if rs is not null and (select paused from resellers where id = rs) then raise exception 'tu cuenta de distribuidor esta en pausa'; end if;
  n := greatest(1, least(coalesce(p_devices, 1), 20));
  if p_kind = 'trial' then
    n := least(n, coalesce((r->>'trial_devices')::int, 2));
    if rs is not null then
      select count(*) into used from licenses where reseller_id = rs and kind = 'trial' and created_at > date_trunc('month', now());
      if used >= coalesce((r->>'trial_month_cap')::int, 20) then raise exception 'llegaste al tope de pruebas de este mes'; end if;
    end if;
    insert into licenses(code, note, max_devices, reseller_id, kind, expires_at, customer, phone)
    values (v, coalesce(p_note, ''), n, rs, 'trial', now() + make_interval(days => coalesce((r->>'trial_days')::int, 21)), coalesce(p_customer, ''), coalesce(p_phone, ''));
  elsif p_kind = 'paid' then
    perform cc__charge(rs, n, 'venta', v);
    insert into licenses(code, note, max_devices, reseller_id, kind, expires_at, customer, phone, paid_at)
    values (v, coalesce(p_note, ''), n, rs, 'paid', now() + interval '1 year', coalesce(p_customer, ''), coalesce(p_phone, ''), now());
  elsif p_kind = 'gift' and rs is null then
    insert into licenses(code, note, max_devices, kind, customer, phone)
    values (v, coalesce(p_note, ''), n, 'gift', coalesce(p_customer, ''), coalesce(p_phone, ''));
  else
    raise exception 'tipo de codigo no valido';
  end if;
  select * into l from licenses where code = v;
  return cc__codejson(l);
end $$;

create or replace function cc_code_list(p_t text, p_q text) returns jsonb
language plpgsql security definer set search_path = public as $$
declare s panel_sessions := cc__sess(p_t); rs uuid; q text := '%' || lower(coalesce(trim(p_q), '')) || '%';
begin
  rs := case when s.role = 'rs' then s.reseller_id else null end;
  return coalesce((select jsonb_agg(cc__codejson(l) order by l.created_at desc) from (
      select * from licenses l
      where (rs is null or l.reseller_id = rs)
        and (q = '%%' or lower(l.code || ' ' || l.customer || ' ' || l.note || ' ' || l.phone || ' ' ||
             coalesce((select name from resellers r where r.id = l.reseller_id), '')) like q)
      order by l.created_at desc limit 500) l), '[]'::jsonb);
end $$;

-- Prueba → anual (gasta créditos por aparato). p_devices = aparatos del plan pagado.
create or replace function cc_code_convert(p_t text, p_code text, p_devices int) returns jsonb
language plpgsql security definer set search_path = public as $$
declare l licenses := cc__own(p_t, p_code); n int := greatest(1, least(coalesce(p_devices, l.max_devices), 20));
begin
  if l.kind <> 'trial' then raise exception 'ese codigo ya es anual'; end if;
  perform cc__charge(l.reseller_id, n, 'conversion', l.code);
  update licenses set kind = 'paid', max_devices = n, expires_at = now() + interval '1 year', paid_at = now(), revoked = false where code = l.code;
  select * into l from licenses where code = l.code;
  return cc__codejson(l);
end $$;

-- Renovar un año más (gasta 1 crédito por aparato)
create or replace function cc_code_renew(p_t text, p_code text) returns jsonb
language plpgsql security definer set search_path = public as $$
declare l licenses := cc__own(p_t, p_code);
begin
  if l.kind = 'trial' then raise exception 'primero conviertela a anual'; end if;
  if l.kind = 'gift' then raise exception 'los codigos de regalo no vencen'; end if;
  perform cc__charge(l.reseller_id, l.max_devices, 'renovacion', l.code);
  update licenses set expires_at = greatest(coalesce(expires_at, now()), now()) + interval '1 year', revoked = false where code = l.code;
  select * into l from licenses where code = l.code;
  return cc__codejson(l);
end $$;

-- Agregar aparatos: proporcional al tiempo que queda (medio crédito si quedan menos de 6 meses)
create or replace function cc_code_add(p_t text, p_code text, p_n int) returns jsonb
language plpgsql security definer set search_path = public as $$
declare l licenses := cc__own(p_t, p_code); n int := greatest(1, least(coalesce(p_n, 1), 10)); cost numeric;
begin
  if l.max_devices + n > 20 then raise exception 'maximo 20 aparatos por codigo'; end if;
  if l.kind = 'paid' then
    cost := n * (case when l.expires_at < now() + interval '6 months' then 0.5 else 1 end);
    perform cc__charge(l.reseller_id, cost, 'aparato_extra', l.code);
  elsif l.kind = 'trial' then
    if l.max_devices + n > coalesce((cc__rules()->>'trial_devices')::int, 2) then raise exception 'la prueba permite pocos aparatos; conviertela a anual'; end if;
  end if;
  update licenses set max_devices = max_devices + n where code = l.code;
  select * into l from licenses where code = l.code;
  return cc__codejson(l);
end $$;

create or replace function cc_code_set(p_t text, p_code text, p_revoked boolean, p_reset boolean, p_customer text, p_phone text, p_note text) returns jsonb
language plpgsql security definer set search_path = public as $$
declare l licenses := cc__own(p_t, p_code);
begin
  update licenses set
    revoked = coalesce(p_revoked, revoked),
    customer = coalesce(p_customer, customer), phone = coalesce(p_phone, phone), note = coalesce(p_note, note)
  where code = l.code;
  if coalesce(p_reset, false) then delete from activations where code = l.code; end if;
  select * into l from licenses where code = l.code;
  return cc__codejson(l);
end $$;

-- ---------- lo que ve la app ----------
create or replace function cc_lic_activate(p_code text, p_device text, p_name text) returns text
language plpgsql security definer set search_path = public as $$
declare l licenses; n int;
begin
  select * into l from licenses where code = upper(trim(p_code));
  if not found then return 'unknown'; end if;
  if l.revoked then return 'revoked'; end if;
  if l.expires_at is not null and l.expires_at < now() then return 'expired'; end if;
  if exists (select 1 from activations where code = l.code and device_id = p_device) then
    update activations set last_seen = now(), device_name = coalesce(nullif(p_name, ''), device_name) where code = l.code and device_id = p_device;
    return 'ok';
  end if;
  select count(*) into n from activations where code = l.code;
  if n >= l.max_devices then return 'full'; end if;
  insert into activations(code, device_id, device_name) values (l.code, p_device, coalesce(p_name, ''));
  return 'ok';
end $$;

create or replace function cc_lic_check(p_code text, p_device text) returns text
language plpgsql security definer set search_path = public as $$
declare l licenses;
begin
  select * into l from licenses where code = upper(trim(p_code));
  if not found then return 'unknown'; end if;
  if l.revoked then return 'revoked'; end if;
  if l.expires_at is not null and l.expires_at < now() then return 'expired'; end if;
  if not exists (select 1 from activations where code = l.code and device_id = p_device) then return 'removed'; end if;
  update activations set last_seen = now() where code = l.code and device_id = p_device;
  return 'ok';
end $$;

-- Datos para mostrar en Ajustes (tipo, vencimiento, aparatos, quién la vendió)
create or replace function cc_lic_info(p_code text, p_device text) returns jsonb
language plpgsql security definer set search_path = public as $$
declare l licenses;
begin
  select * into l from licenses where code = upper(trim(p_code));
  if not found then return null; end if;
  if not exists (select 1 from activations where code = l.code and device_id = p_device) then return null; end if;
  return jsonb_build_object('kind', l.kind, 'expires', l.expires_at, 'max', l.max_devices,
    'used', (select count(*) from activations a where a.code = l.code), 'status', cc__status(l),
    'seller', (select name from resellers r where r.id = l.reseller_id),
    'seller_phone', (select phone from resellers r where r.id = l.reseller_id));
end $$;

-- ---------- permisos ----------
revoke all on function cc__hash(text), cc__token(), cc__sess(text), cc__mgr(text), cc__rules(), cc__sold(uuid), cc__level(uuid),
  cc__newcode(), cc__charge(uuid, numeric, text, text), cc__own(text, text), cc__status(licenses), cc__codejson(licenses) from public, anon, authenticated;

grant execute on function cc_mgr_link(uuid, text) to anon;
grant execute on function cc_link_create(text, uuid) to anon;
grant execute on function cc_login(text, text) to anon;
grant execute on function cc_logout(text) to anon;
grant execute on function cc_me(text) to anon;
grant execute on function cc_summary(text) to anon;
grant execute on function cc_rules_set(text, jsonb) to anon;
grant execute on function cc_rs_list(text) to anon;
grant execute on function cc_rs_save(text, uuid, text, text, text, text) to anon;
grant execute on function cc_rs_pause(text, uuid, boolean) to anon;
grant execute on function cc_rs_credit(text, uuid, numeric, text, numeric) to anon;
grant execute on function cc_ledger(text, uuid) to anon;
grant execute on function cc_code_create(text, text, int, text, text, text) to anon;
grant execute on function cc_code_list(text, text) to anon;
grant execute on function cc_code_convert(text, text, int) to anon;
grant execute on function cc_code_renew(text, text) to anon;
grant execute on function cc_code_add(text, text, int) to anon;
grant execute on function cc_code_set(text, text, boolean, boolean, text, text, text) to anon;
grant execute on function cc_lic_activate(text, text, text) to anon;
grant execute on function cc_lic_check(text, text) to anon;
grant execute on function cc_lic_info(text, text) to anon;
