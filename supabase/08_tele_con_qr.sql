-- Configurar la tele desde el celular: la tele muestra un QR, el celular manda servidor/usuario/contraseña.
-- Los datos solo se guardan unos minutos y se borran en cuanto la tele los recoge.
create table if not exists tv_pair (
  code text primary key,
  created_at timestamptz not null default now(),
  server text, usr text, pwd text
);
alter table tv_pair enable row level security;
revoke all on tv_pair from anon, authenticated;

create or replace function cc_pair_new() returns text
language plpgsql security definer set search_path = public as $$
declare c text; i int := 0;
begin
  delete from tv_pair where created_at < now() - interval '15 minutes';
  loop
    c := lpad((floor(random() * 1000000))::int::text, 6, '0');
    begin
      insert into tv_pair(code) values (c);
      return c;
    exception when unique_violation then
      i := i + 1; if i > 20 then raise exception 'ocupado'; end if;
    end;
  end loop;
end $$;

create or replace function cc_pair_set(p_code text, p_server text, p_user text, p_pass text) returns text
language plpgsql security definer set search_path = public as $$
begin
  update tv_pair set server = left(trim(p_server), 300), usr = left(trim(p_user), 120), pwd = left(p_pass, 120)
  where code = trim(p_code) and created_at > now() - interval '15 minutes' and server is null;
  if not found then return 'expired'; end if;
  return 'ok';
end $$;

create or replace function cc_pair_get(p_code text) returns jsonb
language plpgsql security definer set search_path = public as $$
declare r tv_pair;
begin
  select * into r from tv_pair where code = trim(p_code);
  if r.code is null or r.created_at < now() - interval '15 minutes' then return jsonb_build_object('status', 'expired'); end if;
  if r.server is null then return jsonb_build_object('status', 'waiting'); end if;
  delete from tv_pair where code = r.code;
  return jsonb_build_object('status', 'ok', 'server', r.server, 'user', r.usr, 'pass', r.pwd);
end $$;

grant execute on function cc_pair_new() to anon;
grant execute on function cc_pair_set(text, text, text, text) to anon;
grant execute on function cc_pair_get(text) to anon;
