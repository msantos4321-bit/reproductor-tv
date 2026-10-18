-- Reportes de cierres inesperados de la app (solo se pueden insertar)
create table if not exists crash_reports (
  id bigserial primary key,
  device text not null default '',
  report text not null,
  created_at timestamptz not null default now()
);
alter table crash_reports enable row level security;
revoke all on crash_reports from anon, authenticated;
create or replace function cc_crash(p_device text, p_text text) returns void
language plpgsql security definer set search_path = public as $$
begin
  if (select count(*) from crash_reports where created_at > now() - interval '1 hour') > 200 then return; end if;
  insert into crash_reports(device, report) values (left(coalesce(p_device, ''), 80), left(coalesce(p_text, ''), 9000));
end $$;
grant execute on function cc_crash(text, text) to anon;
