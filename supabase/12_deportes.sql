-- 12: Deportes. Caché de marcadores para la función «deportes».
-- Solo la usa la función con su llave de servicio; la app nunca la lee directo.
-- OJO: ya está aplicado. NO volver a correrlo solo.

create table if not exists public.sports_cache (
  k  text primary key,
  v  jsonb not null,
  at timestamptz not null default now()
);
alter table public.sports_cache enable row level security;
revoke all on public.sports_cache from anon, authenticated;

-- Limpieza diaria de lo viejo (fichas de equipos y partidos que ya nadie pide)
select cron.schedule('limpiar_sports_cache', '17 9 * * *',
  $$delete from public.sports_cache where at < now() - interval '3 days'$$)
where not exists (select 1 from cron.job where jobname = 'limpiar_sports_cache');
