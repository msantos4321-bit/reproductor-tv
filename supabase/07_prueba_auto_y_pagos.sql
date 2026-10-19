-- 1) Prueba automática desde la app, 2) recomendados desde la app, 3) liga de pago por vendedor.
alter table licenses add column if not exists ref_paid boolean not null default false;
alter table resellers add column if not exists pay_link text not null default '';

update biz_settings set data = data || '{"auto_trial": "on", "auto_trial_days": 21, "auto_trial_devices": 5}'::jsonb
where id = 1 and not (data ? 'auto_trial');

-- Regala días a quien recomendó (una sola vez por cliente nuevo y solo cuando el nuevo ya pagó)
create or replace function cc__ref_bonus(p_code text) returns void
language plpgsql security definer set search_path = public as $$
declare l licenses; dias int := coalesce((cc__rules()->>'referral_days')::int, 30);
begin
  select * into l from licenses where code = p_code;
  if l.code is null or l.kind <> 'paid' or l.ref_by is null or l.ref_paid then return; end if;
  update licenses set ref_paid = true where code = l.code;
  update licenses set expires_at = greatest(coalesce(expires_at, now()), now()) + make_interval(days => dias), revoked = false
  where code = l.ref_by and kind = 'paid';
end $$;
revoke all on function cc__ref_bonus(text) from public, anon, authenticated;

-- La app pide su prueba gratis sola (sin pedir código a nadie)
create or replace function cc_trial_auto(p_device text, p_name text, p_fp text, p_ref text) returns jsonb
language plpgsql security definer set search_path = public as $$
declare r jsonb := cc__rules(); v text; rs uuid; refc text; rf licenses; res text;
  d int := coalesce((r->>'auto_trial_days')::int, 21); n int := coalesce((r->>'auto_trial_devices')::int, 5);
begin
  if coalesce(r->>'auto_trial', 'on') <> 'on' then return jsonb_build_object('error', 'off'); end if;
  if length(coalesce(p_fp, '')) < 16 or length(coalesce(p_device, '')) < 8 then return jsonb_build_object('error', 'device'); end if;
  if exists (select 1 from trial_devices where fp = p_fp) then return jsonb_build_object('error', 'trial_used'); end if;
  if (select count(*) from licenses where note = 'auto' and created_at > now() - interval '1 hour') > 120 then
    return jsonb_build_object('error', 'busy');
  end if;
  if coalesce(trim(p_ref), '') <> '' then
    select * into rf from licenses where code = upper(trim(p_ref));
    if rf.code is null then return jsonb_build_object('error', 'bad_ref'); end if;
    refc := rf.code; rs := rf.reseller_id;   -- el cliente nuevo queda con el distribuidor de quien lo recomendó
  end if;
  v := cc__newcode();
  insert into licenses(code, note, max_devices, reseller_id, kind, expires_at, customer, ref_by)
  values (v, 'auto', n, rs, 'trial', now() + make_interval(days => d), left(coalesce(nullif(trim(p_name), ''), 'Prueba desde la app'), 60), refc);
  res := cc_lic_activate2(v, p_device, p_name, p_fp);
  return jsonb_build_object('code', v, 'result', res, 'days', d, 'devices', n);
end $$;
grant execute on function cc_trial_auto(text, text, text, text) to anon;

-- Desde la app: "¿Quién te recomendó?" (si ya pagó, el regalo se da de inmediato; si es prueba, al pagar)
create or replace function cc_lic_set_ref(p_code text, p_device text, p_ref text) returns text
language plpgsql security definer set search_path = public as $$
declare l licenses; rf licenses;
begin
  select * into l from licenses where code = upper(trim(p_code));
  if l.code is null or not exists (select 1 from activations where code = l.code and device_id = p_device) then return 'unknown'; end if;
  if l.ref_by is not null then return 'already'; end if;
  select * into rf from licenses where code = upper(trim(p_ref));
  if rf.code is null then return 'bad_ref'; end if;
  if rf.code = l.code then return 'self'; end if;
  update licenses set ref_by = rf.code,
    reseller_id = case when l.kind = 'trial' and l.reseller_id is null then rf.reseller_id else l.reseller_id end
  where code = l.code;
  perform cc__ref_bonus(l.code);
  return 'ok';
end $$;
grant execute on function cc_lic_set_ref(text, text, text) to anon;

-- Al pasar de prueba a anual, si alguien lo recomendó, se le regala su mes
create or replace function cc_code_convert(p_t text, p_code text, p_devices int) returns jsonb
language plpgsql security definer set search_path = public as $$
declare l licenses := cc__own(p_t, p_code); n int := greatest(1, least(coalesce(p_devices, l.max_devices), 20));
begin
  if l.kind <> 'trial' then raise exception 'ese codigo ya es anual'; end if;
  perform cc__charge(l.reseller_id, n, 'conversion', l.code);
  update licenses set kind = 'paid', max_devices = n, expires_at = now() + interval '1 year', paid_at = now(), revoked = false where code = l.code;
  perform cc__ref_bonus(l.code);
  select * into l from licenses where code = l.code;
  return cc__codejson(l);
end $$;

-- El panel también marca el regalo como dado
create or replace function cc_code_refer(p_t text, p_code text, p_ref text) returns jsonb
language plpgsql security definer set search_path = public as $$
declare l licenses := cc__own(p_t, p_code); r licenses;
begin
  if l.kind <> 'paid' then raise exception 'el cliente nuevo primero tiene que pagar'; end if;
  if l.ref_by is not null then raise exception 'ya se registró quién lo recomendó'; end if;
  select * into r from licenses where code = upper(trim(p_ref));
  if r.code is null then raise exception 'no existe ese código'; end if;
  if r.code = l.code then raise exception 'no puede recomendarse a sí mismo'; end if;
  if r.kind <> 'paid' then raise exception 'quien recomienda debe tener plan anual'; end if;
  update licenses set ref_by = r.code where code = l.code;
  perform cc__ref_bonus(l.code);
  select * into r from licenses where code = r.code;
  return cc__codejson(r);
end $$;

-- Datos para "Tu suscripción" en la app: a quién pagar y a quién escribir
create or replace function cc_lic_info(p_code text, p_device text) returns jsonb
language plpgsql security definer set search_path = public as $$
declare l licenses; r resellers; ru jsonb := cc__rules(); price numeric;
begin
  select * into l from licenses where code = upper(trim(p_code));
  if not found then return null; end if;
  if not exists (select 1 from activations where code = l.code and device_id = p_device) then return null; end if;
  select * into r from resellers where id = l.reseller_id;
  price := case when l.max_devices <= 1 then coalesce((ru->'retail'->>'d1')::numeric, 169)
                when l.max_devices = 2 then coalesce((ru->'retail'->>'d2')::numeric, 279)
                else coalesce((ru->'retail'->>'d3')::numeric, 349) + greatest(l.max_devices - 3, 0) * coalesce((ru->'retail'->>'extra')::numeric, 89) end;
  return jsonb_build_object('kind', l.kind, 'expires', l.expires_at, 'max', l.max_devices,
    'used', (select count(*) from activations a where a.code = l.code), 'status', cc__status(l),
    'seller', r.name, 'seller_phone', r.phone,
    'help_phone', coalesce(nullif(r.phone, ''), ru->>'manager_whatsapp', ''),
    'pay_link', coalesce(nullif(r.pay_link, ''), ru->>'pay_link', ''),
    'price', price, 'ref_by', l.ref_by, 'referral_days', coalesce((ru->>'referral_days')::int, 30),
    'download_url', coalesce(nullif(ru->>'download_url', ''), 'https://msantos4321-bit.github.io/reproductor-tv/app/'));
end $$;

-- El distribuidor pone su WhatsApp y su liga de pago (Mercado Pago, etc.) desde "Mi cuenta"
create or replace function cc_rs_self(p_t text, p_phone text, p_pay_link text) returns jsonb
language plpgsql security definer set search_path = public as $$
declare s panel_sessions := cc__sess(p_t);
begin
  if s.role <> 'rs' then raise exception 'solo distribuidores'; end if;
  update resellers set phone = left(coalesce(trim(p_phone), ''), 20), pay_link = left(coalesce(trim(p_pay_link), ''), 300) where id = s.reseller_id;
  return cc_me(p_t);
end $$;
grant execute on function cc_rs_self(text, text, text) to anon;

create or replace function cc_me(p_t text) returns jsonb
language plpgsql security definer set search_path = public as $$
declare s panel_sessions := cc__sess(p_t); r resellers;
begin
  if s.role = 'mgr' then
    return jsonb_build_object('role', 'mgr', 'name', 'Gerente general', 'rules', cc__rules());
  end if;
  select * into r from resellers where id = s.reseller_id;
  return jsonb_build_object('role', 'rs', 'id', r.id, 'name', r.name, 'phone', r.phone, 'pay_link', r.pay_link, 'credits', r.credits, 'paused', r.paused,
    'level', cc__level(r.id), 'rules', cc__rules());
end $$;
