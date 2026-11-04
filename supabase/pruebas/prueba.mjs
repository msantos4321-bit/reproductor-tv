// Pruebas de la nube de CineCasa en un Postgres local (PGlite) con los archivos 01…11.
import { PGlite } from '@electric-sql/pglite';
import { pgcrypto } from '@electric-sql/pglite/contrib/pgcrypto';
import fs from 'node:fs';

const DIR = new URL('..', import.meta.url).pathname.replace(/\/$/, '');
const db = new PGlite({ extensions: { pgcrypto } });
let fails = 0, oks = 0;
const ok = (cond, msg, extra) => { if (cond) { oks++; console.log('  ✓', msg); } else { fails++; console.log('  ✗', msg, extra !== undefined ? JSON.stringify(extra) : ''); } };
const q1 = async (sql, params = []) => (await db.query(sql, params)).rows[0];
const v = async (sql, params = []) => { const r = await q1(sql, params); return r ? Object.values(r)[0] : undefined; };
// Llama una función como lo haría la app (rol anon, como en Supabase)
const anon = async (fn, args = {}) => {
  const keys = Object.keys(args);
  const sql = `select ${fn}(${keys.map((k, i) => `${k} => $${i + 1}`).join(', ')}) as r`;
  await db.exec('set role anon');
  try { return (await db.query(sql, keys.map(k => args[k]))).rows[0].r; }
  finally { await db.exec('reset role'); }
};
const anonErr = async (fn, args = {}) => { try { await anon(fn, args); return null; } catch (e) { return e.message; } };

// ---- entorno parecido a Supabase ----
await db.exec(`
  create schema extensions; create extension pgcrypto schema extensions;
  create role anon nologin; create role authenticated nologin; create role service_role nologin;
  grant usage on schema public, extensions to anon, authenticated;
  alter default privileges in schema public grant execute on functions to anon, authenticated;
  alter default privileges in schema public grant all on tables to anon, authenticated;
`);
// el hogar dueño debe existir antes de 02
const files = fs.readdirSync(DIR).filter(f => /^\d\d_.*\.sql$/.test(f)).sort();
for (const f of files) {
  let sql = fs.readFileSync(`${DIR}/${f}`, 'utf8');
  if (f.startsWith('02')) await db.exec(`insert into households(id, secret_hash) values ('a90caa55-6cf6-49fd-a3e0-4a57f148e14a', encode(extensions.digest('secreto-del-dueno-123456789', 'sha256'), 'hex'))`);
  try { await db.exec(sql); } catch (e) { console.log('ERROR al aplicar', f, e.message); process.exit(1); }
}
console.log('Aplicados:', files.join(', '));
// volver a correr el 11 no debe romper nada
await db.exec(fs.readFileSync(`${DIR}/11_auditoria.sql`, 'utf8'));
console.log('11 se puede volver a correr ✓');

const H = 'a90caa55-6cf6-49fd-a3e0-4a57f148e14a', HS = 'secreto-del-dueno-123456789';
const FP = n => 'huella-del-aparato-' + n.toString().padStart(4, '0');

console.log('\n1) Panel y servidores');
const link = await anon('cc_mgr_link', { p_h: H, p_s: HS });
const mgr = (await anon('cc_login', { p_link: link, p_label: 'prueba' })).token;
await anon('cc_srv_save', { p_t: mgr, p_id: null, p_name: 'Servidor de Mauricio', p_url: 'http://mio.example:8080', p_default: true });
const rsId = await anon('cc_rs_save', { p_t: mgr, p_id: null, p_name: 'Ivan', p_phone: '6641112233', p_email: '', p_notes: '' });
const rsLink = await anon('cc_link_create', { p_t: mgr, p_reseller: rsId });
const rs = (await anon('cc_login', { p_link: rsLink, p_label: 'cel ivan' })).token;
await anon('cc_srv_save', { p_t: rs, p_id: null, p_name: 'Servidor de Ivan', p_url: 'ivan.example:80/player_api.php', p_default: true });
ok(true, 'gerente y distribuidor con su servidor');

console.log('\n2) Prueba automática sin distribuidor: sin servidor');
const t1 = await anon('cc_trial_auto', { p_device: 'aparato-0001', p_name: 'Tele', p_fp: FP(1), p_ref: '' });
ok(t1.result === 'ok', 'prueba creada y activada', t1);
let p = await anon('cc_lic_ping', { p_code: t1.code, p_device: 'aparato-0001', p_playing: false, p_model: 'X', p_version: '1.0.50' });
ok(p.status === 'ok' && p.server === null && Array.isArray(p.servers) && p.servers.length === 0, 'ping: sin servidor y sin lista', p);
p = await anon('cc_lic_server', { p_code: t1.code, p_device: 'aparato-0001' });
ok(p.server === null && p.servers.length === 0, 'pantalla de entrar: sin servidor', p);

console.log('\n3) Código que crea un distribuidor: con su servidor');
const c2 = await anon('cc_code_create', { p_t: rs, p_kind: 'trial', p_devices: 1, p_customer: 'Cliente de Ivan', p_phone: '', p_note: '' });
await anon('cc_lic_activate2', { p_code: c2.code, p_device: 'aparato-0002', p_name: 'Tele', p_fp: FP(2) });
p = await anon('cc_lic_ping', { p_code: c2.code, p_device: 'aparato-0002', p_playing: true, p_model: 'X', p_version: '1' });
ok(p.server && p.server.name === 'Servidor de Ivan' && p.server.url === 'http://ivan.example:80', 'servidor de Ivan', p);

console.log('\n4) Prueba automática recomendada por un cliente de Ivan: servidor de Ivan');
const t3 = await anon('cc_trial_auto', { p_device: 'aparato-0003', p_name: 'Cel', p_fp: FP(3), p_ref: c2.code.toLowerCase() });
p = await anon('cc_lic_ping', { p_code: t3.code, p_device: 'aparato-0003', p_playing: false, p_model: '', p_version: '' });
ok(p.server && p.server.name === 'Servidor de Ivan', 'servidor del distribuidor que lo trajo', p);

console.log('\n5) Venta directa del gerente: su servidor');
const c4 = await anon('cc_code_create', { p_t: mgr, p_kind: 'paid', p_devices: 2, p_customer: 'Directo', p_phone: '', p_note: '' });
await anon('cc_lic_activate2', { p_code: c4.code, p_device: 'aparato-0004', p_name: 'Tele', p_fp: FP(4) });
p = await anon('cc_lic_ping', { p_code: c4.code, p_device: 'aparato-0004', p_playing: false, p_model: '', p_version: '' });
ok(p.server && p.server.name === 'Servidor de Mauricio', 'servidor del gerente', p);
ok(c4.server && c4.server.name === 'Servidor de Mauricio', 'el panel lo muestra igual');

console.log('\n6) La prueba sin distribuidor pone después a quién la recomendó: pasa al servidor de Ivan');
ok(await anon('cc_lic_set_ref', { p_code: t1.code, p_device: 'aparato-0001', p_ref: c2.code }) === 'ok', 'recomendado guardado');
p = await anon('cc_lic_ping', { p_code: t1.code, p_device: 'aparato-0001', p_playing: false, p_model: '', p_version: '' });
ok(p.server && p.server.name === 'Servidor de Ivan', 'ahora con servidor de Ivan', p);

console.log('\n7) El gerente le asigna su servidor a una prueba automática');
const t5 = await anon('cc_trial_auto', { p_device: 'aparato-0005', p_name: 'Tele', p_fp: FP(5), p_ref: '' });
let j = (await anon('cc_code_list', { p_t: mgr, p_q: t5.code }))[0];
ok(j.server === null, 'en el panel: sin servidor (el cliente escribe la liga)', j.server);
const srvs = await anon('cc_srv_list', { p_t: mgr });
j = await anon('cc_code_server', { p_t: mgr, p_code: t5.code, p_server: srvs[0].id });
p = await anon('cc_lic_ping', { p_code: t5.code, p_device: 'aparato-0005', p_playing: false, p_model: '', p_version: '' });
ok(p.server && p.server.name === 'Servidor de Mauricio', 'con el servidor elegido', p);

console.log('\n8) Ayuda para pagar (vencido, sin vendedor, aparato quitado)');
await db.exec(`update licenses set expires_at = now() - interval '1 day' where code = '${t5.code}'`);
ok(await anon('cc_lic_check', { p_code: t5.code, p_device: 'aparato-0005' }) === 'expired', 'la prueba ya venció');
let h = await anon('cc_lic_help', { p_code: t5.code, p_device: 'aparato-0005' });
ok(h.help_phone === '526862595403' && h.status === 'vencido' && h.seller === null && Number(h.price) === 169, 'vencida sin vendedor: WhatsApp del gerente y precio', h);
h = await anon('cc_lic_help', { p_code: c2.code, p_device: 'aparato-0002' });
ok(h.seller === 'Ivan' && h.help_phone === '6641112233', 'cliente de Ivan: WhatsApp de Ivan', h);
h = await anon('cc_lic_help', { p_code: c2.code, p_device: 'otro-aparato-999' });
ok(h.seller === null && h.help_phone === '526862595403' && h.code === undefined, 'aparato ajeno: solo contacto general, sin datos del código', h);
h = await anon('cc_sales_contact');
ok(h.help_phone === '526862595403' && Number(h.price) === 169, 'contacto general', h);
// quitar un aparato y pedir ayuda desde él
await anon('cc_lic_activate2', { p_code: c4.code, p_device: 'aparato-0006', p_name: 'Cel', p_fp: FP(6) });
ok(await anon('cc_lic_device_remove', { p_code: c4.code, p_device: 'aparato-0004', p_key: await v(`select cc__dkey('aparato-0006')`) }) === 'ok', 'aparato quitado');
ok(await anon('cc_lic_check', { p_code: c4.code, p_device: 'aparato-0006' }) === 'blocked', 'el quitado ve «blocked»');
h = await anon('cc_lic_help', { p_code: c4.code, p_device: 'aparato-0006' });
ok(h.code === c4.code && Number(h.price) === 279, 'el aparato quitado sí ve a quién escribir (2 aparatos = 279)', h);

console.log('\n9) Tele con QR: con llave');
const tok = 'llave-secreta-de-la-tele-0123456789';
const pc = await anon('cc_pair_new2', { p_token: tok });
ok(/^\d{6}$/.test(pc), 'código de 6 números', pc);
ok(await anon('cc_pair_set', { p_code: pc, p_server: 'http://x.example', p_user: 'juan', p_pass: 'p@ss' }) === 'ok', 'el celular manda los datos');
let g = await anon('cc_pair_get', { p_code: pc });
ok(g.status === 'waiting' && !g.pass, 'la función vieja ya no los entrega', g);
g = await anon('cc_pair_get2', { p_code: pc, p_token: 'otra-llave-cualquiera-123' });
ok(g.status === 'expired' && !g.pass, 'con otra llave no salen', g);
g = await anon('cc_pair_get2', { p_code: pc, p_token: tok });
ok(g.status === 'ok' && g.user === 'juan' && g.pass === 'p@ss', 'la tele con su llave los recibe', g);
g = await anon('cc_pair_get2', { p_code: pc, p_token: tok });
ok(g.status === 'expired', 'y se borran al recogerlos', g);
const old = await anon('cc_pair_new');
await anon('cc_pair_set', { p_code: old, p_server: 's', p_user: 'u', p_pass: 'p' });
g = await anon('cc_pair_get', { p_code: old });
ok(g.status === 'ok', 'las versiones viejas siguen funcionando con su código', g);
ok((await anonErr('cc_pair_new2', { p_token: 'corta' })) !== null, 'llave corta rechazada');

console.log('\n10) Sesiones del Centro de control');
const me = await anon('cc_me', { p_t: rs });
ok(me.role === 'rs' && me.name === 'Ivan', 'sesión del distribuidor funciona');
await db.exec(`update panel_sessions set last_seen = now() - interval '31 days' where label = 'cel ivan'`);
let e = await anonErr('cc_me', { p_t: rs });
ok(e && e.includes('vencio'), 'tras 30 días sin usarse, vence', e);
ok((await anonErr('cc_me', { p_t: rs })).includes('vencio'), 'sigue vencida');
const l2 = await anon('cc_link_create', { p_t: mgr, p_reseller: rsId });
const rs2 = (await anon('cc_login', { p_link: l2, p_label: 'tablet ivan' })).token;
ok(await v(`select count(*) from panel_sessions where label = 'cel ivan'`) == 0, 'al entrar de nuevo se limpia la vencida');
const l3 = await anon('cc_link_create', { p_t: mgr, p_reseller: rsId });
const rs3 = (await anon('cc_login', { p_link: l3, p_label: 'cel2 ivan' })).token;
ok(await anon('cc_logout_all', { p_t: rs2 }) == 2, 'cerrar en todos lados (distribuidor): 2 sesiones');
ok((await anonErr('cc_me', { p_t: rs3 })) !== null, 'su otra sesión ya no entra');
ok((await anon('cc_me', { p_t: mgr })).role === 'mgr', 'la del gerente sigue');
const l4 = await anon('cc_link_create', { p_t: mgr, p_reseller: rsId });
const rs4 = (await anon('cc_login', { p_link: l4, p_label: 'x' })).token;
ok(await anon('cc_rs_logout', { p_t: mgr, p_id: rsId }) == 1, 'el gerente cierra las sesiones del distribuidor');
ok((await anonErr('cc_rs_logout', { p_t: rs4, p_id: rsId })) !== null, 'un distribuidor no puede usar cc_rs_logout');

console.log('\n11) Reportes de fallas sin datos de la cuenta');
await anon('cc_crash', { p_device: 'dev-x', p_text: 'PLAY ERROR http://h.example:80/live/juan/secreta/123.ts?username=juan&password=secreta' });
const rep = await v(`select report from crash_reports order by id desc limit 1`);
ok(!rep.includes('juan') && !rep.includes('secreta') && rep.includes('/live/***/***/'), 'sin usuario ni contraseña', rep);
for (let i = 0; i < 30; i++) await anon('cc_crash', { p_device: 'dev-y', p_text: 'cierre ' + i });
ok(await v(`select count(*) from crash_reports where device = 'dev-y'`) == 20, 'máximo 20 por aparato por hora');

console.log('\n12) Permisos');
for (const [fn, args] of [['cc__server_for', null], ['cc__price', { n: 1 }], ['cc__sess', { p_t: 'x' }], ['cc__servers_for', null], ['cc__ask_server', null]]) {
  let err;
  if (args) err = await anonErr(fn, args);
  else { try { await db.exec('set role anon'); await db.query(`select ${fn}(l) from licenses l limit 1`); err = null; } catch (x) { err = x.message; } finally { await db.exec('reset role'); } }
  ok(err && /permission denied/.test(err), `${fn}: prohibido para la app`, err);
}
for (const t of ['licenses', 'tv_pair', 'panel_sessions', 'servers']) {
  let err = null; try { await db.exec('set role anon'); await db.query(`select * from ${t} limit 1`); } catch (x) { err = x.message; } finally { await db.exec('reset role'); }
  ok(err && /permission denied/.test(err), `tabla ${t}: sin acceso directo`, err);
}

console.log('\n13) Lo de antes sigue igual');
const l9 = await anon('cc_link_create', { p_t: mgr, p_reseller: rsId });
const rs9 = (await anon('cc_login', { p_link: l9, p_label: 'y' })).token;
const c9 = await anon('cc_code_create', { p_t: rs9, p_kind: 'paid', p_devices: 1, p_customer: 'Paga', p_phone: '', p_note: '' });
ok(c9.kind === 'paid', 'el distribuidor vende un código (gasta crédito)');
ok(await anon('cc_lic_activate2', { p_code: c9.code, p_device: 'aparato-0010', p_name: 'Tele', p_fp: FP(10) }) === 'ok', 'activar');
ok(await anon('cc_lic_activate2', { p_code: c9.code, p_device: 'aparato-0011', p_name: 'Tele 2', p_fp: FP(11) }) === 'full', 'lleno');
ok((await anon('cc_lic_info', { p_code: c9.code, p_device: 'aparato-0010' })).seller === 'Ivan', 'cc_lic_info igual');
ok((await anon('cc_trial_auto', { p_device: 'aparato-0012', p_name: 'Tele', p_fp: FP(1), p_ref: '' })).error === 'trial_used', 'una prueba por aparato');
ok(await anon('cc_is_owner', { p_h: H, p_s: HS }) === true, 'dueño reconocido');

console.log(`\n${oks} bien, ${fails} mal`);
process.exit(fails ? 1 : 0);
