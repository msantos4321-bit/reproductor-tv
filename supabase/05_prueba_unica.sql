-- Una sola prueba gratis por aparato, aunque se desinstale la app.
create table if not exists trial_devices (
  fp text primary key,
  code text not null,
  used_at timestamptz not null default now()
);
alter table trial_devices enable row level security;
revoke all on trial_devices from anon, authenticated;

create or replace function cc_lic_activate2(p_code text, p_device text, p_name text, p_fp text) returns text
language plpgsql security definer set search_path = public as $$
declare l licenses; r text;
begin
  select * into l from licenses where code = upper(trim(p_code));
  if found and l.kind = 'trial' and length(coalesce(p_fp, '')) >= 16
     and exists (select 1 from trial_devices where fp = p_fp and code <> l.code) then
    return 'trial_used';
  end if;
  r := cc_lic_activate(p_code, p_device, p_name);
  if r = 'ok' and found and l.kind = 'trial' and length(coalesce(p_fp, '')) >= 16 then
    insert into trial_devices(fp, code) values (p_fp, l.code) on conflict (fp) do nothing;
  end if;
  return r;
end $$;
grant execute on function cc_lic_activate2(text, text, text, text) to anon;

-- Precios revisados (18 oct 2026): más margen para el gerente sin dejar al distribuidor sin ganancia
update biz_settings set data = data || '{
  "retail": {"d1": 169, "d2": 279, "d3": 349, "extra": 89},
  "levels": [
    {"name": "Inicial", "min": 0,   "price": 60},
    {"name": "Plata",   "min": 100, "price": 55},
    {"name": "Oro",     "min": 400, "price": 50}
  ]
}'::jsonb where id = 1;
