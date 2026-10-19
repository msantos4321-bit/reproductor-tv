-- Recomendados: si un cliente trae a otro que paga, se le regala tiempo (30 días por defecto).
alter table licenses add column if not exists ref_by text;

create or replace function cc_code_refer(p_t text, p_code text, p_ref text) returns jsonb
language plpgsql security definer set search_path = public as $$
declare l licenses := cc__own(p_t, p_code); r licenses; dias int;
begin
  if l.kind <> 'paid' then raise exception 'el cliente nuevo primero tiene que pagar'; end if;
  if l.ref_by is not null then raise exception 'ya se registró quién lo recomendó'; end if;
  select * into r from licenses where code = upper(trim(p_ref));
  if r.code is null then raise exception 'no existe ese código'; end if;
  if r.code = l.code then raise exception 'no puede recomendarse a sí mismo'; end if;
  if r.kind <> 'paid' then raise exception 'quien recomienda debe tener plan anual'; end if;
  dias := coalesce((cc__rules()->>'referral_days')::int, 30);
  update licenses set ref_by = r.code where code = l.code;
  update licenses set expires_at = greatest(coalesce(expires_at, now()), now()) + make_interval(days => dias), revoked = false where code = r.code;
  select * into r from licenses where code = r.code;
  return cc__codejson(r);
end $$;
grant execute on function cc_code_refer(text, text, text) to anon;

-- El distribuidor ve su propio WhatsApp en "Mi cuenta" y va en sus invitaciones
create or replace function cc_me(p_t text) returns jsonb
language plpgsql security definer set search_path = public as $$
declare s panel_sessions := cc__sess(p_t); r resellers;
begin
  if s.role = 'mgr' then
    return jsonb_build_object('role', 'mgr', 'name', 'Gerente general', 'rules', cc__rules());
  end if;
  select * into r from resellers where id = s.reseller_id;
  return jsonb_build_object('role', 'rs', 'id', r.id, 'name', r.name, 'phone', r.phone, 'credits', r.credits, 'paused', r.paused,
    'level', cc__level(r.id), 'rules', cc__rules());
end $$;

update biz_settings set data = data || '{"referral_days": 30}'::jsonb where id = 1 and not (data ? 'referral_days');
