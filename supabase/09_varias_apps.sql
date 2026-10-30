-- Un solo Centro de control para varias apps (CineCasa, Vista y las que vengan).
-- Cada código pertenece a UNA app: un código de Vista no activa CineCasa y al revés.
-- Créditos: 1 crédito = 1 aparato por 1 año, en cualquier app.
-- Se puede volver a correr sin romper nada.

alter table licenses add column if not exists app text not null default 'cinecasa';
create index if not exists licenses_app on licenses (app, created_at desc);

-- Una prueba gratis por aparato y por app
alter table trial_devices add column if not exists app text not null default 'cinecasa';
do $$ begin
  if exists (select 1 from pg_constraint where conname = 'trial_devices_pkey'
             and pg_get_constraintdef(oid) not like '%app%') then
    alter table trial_devices drop constraint trial_devices_pkey;
    alter table trial_devices add primary key (fp, app);
  end if;
end $$;

-- Apps que vende el Centro de control (nombre, prefijo del código, descarga y si da prueba sola)
update biz_settings set data = data || jsonb_build_object('apps', jsonb_build_object(
  'cinecasa', jsonb_build_object('name', 'CineCasa', 'prefix', 'CC', 'auto_trial', 'on',
                'download_url', 'https://msantos4321-bit.github.io/reproductor-tv/app/', 'downloader', '8835880'),
  'vista',    jsonb_build_object('name', 'Vista', 'prefix', 'VI', 'auto_trial', 'on', 'trial_days', 7, 'trial_devices', 3,
                'download_url', 'https://github.com/msantos4321-bit/vista/releases/latest/download/Vista.apk', 'downloader', '6214004')
)) where id = 1 and not (data ? 'apps');

create or replace function cc__app(p_app text) returns jsonb language sql stable security definer set search_path = public as $$
  select cc__rules()->'apps'->lower(coalesce(p_app, 'cinecasa'))
$$;

create or replace function cc__newcode_app(p_app text) returns text language plpgsql volatile security definer set search_path = public as $$
declare alphabet text := 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789'; v text; i int;
  pre text := coalesce(cc__app(p_app)->>'prefix', 'CC');
begin
  loop
    v := pre || '-';
    for i in 1..8 loop
      v := v || substr(alphabet, 1 + floor(random() * length(alphabet))::int, 1);
      if i = 4 then v := v || '-'; end if;
    end loop;
    exit when not exists (select 1 from licenses where code = v);
  end loop;
  return v;
end $$;

create or replace function cc__codejson(l licenses) returns jsonb language sql stable security definer set search_path = public as $$
  select jsonb_build_object(
    'code', l.code, 'app', l.app, 'app_name', coalesce(cc__app(l.app)->>'name', l.app),
    'note', l.note, 'customer', l.customer, 'phone', l.phone, 'kind', l.kind,
    'max', l.max_devices, 'revoked', l.revoked, 'created', l.created_at, 'expires', l.expires_at,
    'status', cc__status(l), 'reseller_id', l.reseller_id, 'ref_by', l.ref_by,
    'reseller', (select name from resellers r where r.id = l.reseller_id),
    'devices', coalesce((select jsonb_agg(jsonb_build_object('name', a.device_name, 'seen', a.last_seen, 'activated', a.activated_at) order by a.activated_at)
                         from activations a where a.code = l.code), '[]'::jsonb))
$$;

-- ---------- panel: crear código de cualquier app ----------
create or replace function cc_code_create_app(p_t text, p_app text, p_kind text, p_devices int, p_customer text, p_phone text, p_note text) returns jsonb
language plpgsql security definer set search_path = public as $$
declare a text := lower(coalesce(nullif(trim(p_app), ''), 'cinecasa')); j jsonb; v text; nv text;
begin
  if cc__app(a) is null then raise exception 'app no valida'; end if;
  j := cc_code_create(p_t, p_kind, p_devices, p_customer, p_phone, p_note);
  v := j->>'code';
  if a <> 'cinecasa' then
    nv := cc__newcode_app(a);
    update licenses set code = nv, app = a where code = v;
    update ledger set code = nv where code = v;
    v := nv;
    if p_kind = 'trial' then   -- la prueba de cada app tiene sus propios días y aparatos
      update licenses set
        expires_at = now() + make_interval(days => coalesce((cc__app(a)->>'trial_days')::int, (cc__rules()->>'trial_days')::int, 21)),
        max_devices = least(greatest(coalesce(p_devices, 1), 1), coalesce((cc__app(a)->>'trial_devices')::int, max_devices))
      where code = v;
    end if;
  end if;
  return cc__codejson((select l from licenses l where l.code = v));
end $$;

-- ---------- lo que ven las apps ----------
create or replace function cc_app_activate(p_app text, p_code text, p_device text, p_name text, p_fp text) returns text
language plpgsql security definer set search_path = public as $$
declare a text := lower(coalesce(p_app, '')); l licenses; n int;
begin
  select * into l from licenses where code = upper(trim(p_code));
  if not found or l.app <> a then return 'unknown'; end if;
  if l.revoked then return 'revoked'; end if;
  if l.expires_at is not null and l.expires_at < now() then return 'expired'; end if;
  if length(coalesce(p_device, '')) < 8 then return 'device'; end if;
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

create or replace function cc_app_check(p_app text, p_code text, p_device text) returns text
language plpgsql security definer set search_path = public as $$
declare l licenses;
begin
  select * into l from licenses where code = upper(trim(p_code));
  if not found or l.app <> lower(coalesce(p_app, '')) then return 'unknown'; end if;
  if l.revoked then return 'revoked'; end if;
  if l.expires_at is not null and l.expires_at < now() then return 'expired'; end if;
  if not exists (select 1 from activations where code = l.code and device_id = p_device) then return 'removed'; end if;
  update activations set last_seen = now() where code = l.code and device_id = p_device;
  return 'ok';
end $$;

-- Datos para "Tu suscripción": estado, vencimiento, a quién pagar
create or replace function cc_app_info(p_app text, p_code text, p_device text) returns jsonb
language plpgsql security definer set search_path = public as $$
declare l licenses; j jsonb;
begin
  select * into l from licenses where code = upper(trim(p_code));
  if not found or l.app <> lower(coalesce(p_app, '')) then return null; end if;
  j := cc_lic_info(p_code, p_device);
  if j is null then return null; end if;
  return j || jsonb_build_object('app', l.app,
    'download_url', coalesce(cc__app(l.app)->>'download_url', j->>'download_url'));
end $$;

-- Prueba gratis automática de cualquier app
create or replace function cc_app_trial(p_app text, p_device text, p_name text, p_fp text) returns jsonb
language plpgsql security definer set search_path = public as $$
declare a text := lower(coalesce(p_app, '')); ap jsonb := cc__app(a); r jsonb := cc__rules(); v text; res text;
  d int; n int;
begin
  if ap is null then return jsonb_build_object('error', 'app'); end if;
  if coalesce(ap->>'auto_trial', 'off') <> 'on' or coalesce(r->>'auto_trial', 'on') <> 'on' then return jsonb_build_object('error', 'off'); end if;
  if length(coalesce(p_fp, '')) < 16 or length(coalesce(p_device, '')) < 8 then return jsonb_build_object('error', 'device'); end if;
  if exists (select 1 from trial_devices where fp = p_fp and app = a) then return jsonb_build_object('error', 'trial_used'); end if;
  if (select count(*) from licenses where note = 'auto' and created_at > now() - interval '1 hour') > 120 then
    return jsonb_build_object('error', 'busy');
  end if;
  d := coalesce((ap->>'trial_days')::int, (r->>'auto_trial_days')::int, 7);
  n := coalesce((ap->>'trial_devices')::int, (r->>'auto_trial_devices')::int, 2);
  v := cc__newcode_app(a);
  insert into licenses(code, app, note, max_devices, kind, expires_at, customer)
  values (v, a, 'auto', n, 'trial', now() + make_interval(days => d), left(coalesce(nullif(trim(p_name), ''), 'Prueba desde la app'), 60));
  res := cc_app_activate(a, v, p_device, p_name, p_fp);
  return jsonb_build_object('code', v, 'result', res, 'days', d, 'devices', n);
end $$;

-- ---------- CineCasa sigue igual, pero solo acepta códigos de CineCasa ----------
create or replace function cc_lic_activate(p_code text, p_device text, p_name text) returns text
language plpgsql security definer set search_path = public as $$
declare l licenses; n int;
begin
  select * into l from licenses where code = upper(trim(p_code));
  if not found or l.app <> 'cinecasa' then return 'unknown'; end if;
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
  if not found or l.app <> 'cinecasa' then return 'unknown'; end if;
  if l.revoked then return 'revoked'; end if;
  if l.expires_at is not null and l.expires_at < now() then return 'expired'; end if;
  if not exists (select 1 from activations where code = l.code and device_id = p_device) then return 'removed'; end if;
  update activations set last_seen = now() where code = l.code and device_id = p_device;
  return 'ok';
end $$;

create or replace function cc_lic_activate2(p_code text, p_device text, p_name text, p_fp text) returns text
language plpgsql security definer set search_path = public as $$
declare l licenses; r text;
begin
  select * into l from licenses where code = upper(trim(p_code));
  if l.code is not null and l.kind = 'trial' and length(coalesce(p_fp, '')) >= 16
     and exists (select 1 from trial_devices where fp = p_fp and app = 'cinecasa' and code <> l.code) then
    return 'trial_used';
  end if;
  r := cc_lic_activate(p_code, p_device, p_name);
  if r = 'ok' and l.code is not null and l.kind = 'trial' and length(coalesce(p_fp, '')) >= 16 then
    insert into trial_devices(fp, code, app) values (p_fp, l.code, 'cinecasa') on conflict do nothing;
  end if;
  return r;
end $$;

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
  insert into licenses(code, note, max_devices, reseller_id, kind, expires_at, customer, ref_by)
  values (v, 'auto', n, rs, 'trial', now() + make_interval(days => d), left(coalesce(nullif(trim(p_name), ''), 'Prueba desde la app'), 60), refc);
  res := cc_lic_activate2(v, p_device, p_name, p_fp);
  return jsonb_build_object('code', v, 'result', res, 'days', d, 'devices', n);
end $$;

-- ---------- permisos ----------
revoke all on function cc__app(text), cc__newcode_app(text) from public, anon, authenticated;
grant execute on function cc_code_create_app(text, text, text, int, text, text, text) to anon;
grant execute on function cc_app_activate(text, text, text, text, text) to anon;
grant execute on function cc_app_check(text, text, text) to anon;
grant execute on function cc_app_info(text, text, text) to anon;
grant execute on function cc_app_trial(text, text, text, text) to anon;
