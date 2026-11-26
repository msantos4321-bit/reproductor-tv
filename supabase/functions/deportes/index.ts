// Deportes de CineCasa: marcadores, momios, calendario, tabla, lesionados y noticias.
// Toma los datos públicos de ESPN, los resume y los guarda un rato para que todas las teles
// lean de aquí (así no se le pega a ESPN desde cada aparato).
//
//   ?q=board                     marcadores de ayer a 3 días adelante de todas las ligas
//   ?q=team&lg=nfl&id=12         ficha del equipo (partidos, tabla, lesionados, noticias)
//   ?q=table&lg=nfl              tabla de posiciones
//   ?q=game&lg=nfl&id=401873081  detalle del partido (probabilidad, jugadas, lesionados)
//   ?q=teams&lg=nfl              lista de equipos (para elegir Mis equipos)
//   ?q=leagues                   ligas disponibles

type J = any;

const LEAGUES: Record<string, { sp: string; lg: string; name: string; short: string }> = {
  lmp: { sp: "baseball", lg: "mexican-winter-league", name: "Liga Mexicana del Pacífico", short: "LMP" },
  ligamx: { sp: "soccer", lg: "mex.1", name: "Liga MX", short: "Liga MX" },
  nfl: { sp: "football", lg: "nfl", name: "NFL", short: "NFL" },
  nba: { sp: "basketball", lg: "nba", name: "NBA", short: "NBA" },
  mlb: { sp: "baseball", lg: "mlb", name: "MLB", short: "MLB" },
  nhl: { sp: "hockey", lg: "nhl", name: "NHL", short: "NHL" },
  ucl: { sp: "soccer", lg: "uefa.champions", name: "Champions League", short: "Champions" },
  epl: { sp: "soccer", lg: "eng.1", name: "Premier League", short: "Premier" },
  laliga: { sp: "soccer", lg: "esp.1", name: "LaLiga", short: "LaLiga" },
  mls: { sp: "soccer", lg: "usa.1", name: "MLS", short: "MLS" },
  ncaaf: { sp: "football", lg: "college-football", name: "Futbol americano colegial", short: "NCAA" },
  ufc: { sp: "mma", lg: "ufc", name: "UFC", short: "UFC" },
};

const SITE = "https://site.api.espn.com/apis/site/v2/sports";
const CORS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "apikey, content-type, authorization",
};

// ---------------- caché (memoria + tabla sports_cache) ----------------
const mem = new Map<string, { at: number; ttl: number; v: J }>();
const SB = Deno.env.get("SUPABASE_URL") ?? "";
const SR = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";

async function dbGet(k: string): Promise<{ at: number; v: J } | null> {
  if (!SB || !SR) return null;
  try {
    const r = await fetch(`${SB}/rest/v1/sports_cache?k=eq.${encodeURIComponent(k)}&select=v,at`, {
      headers: { apikey: SR, Authorization: `Bearer ${SR}` },
    });
    const a = await r.json();
    if (!Array.isArray(a) || !a.length) return null;
    return { at: Date.parse(a[0].at), v: a[0].v };
  } catch { return null; }
}

async function dbPut(k: string, v: J) {
  if (!SB || !SR) return;
  try {
    await fetch(`${SB}/rest/v1/sports_cache`, {
      method: "POST",
      headers: {
        apikey: SR, Authorization: `Bearer ${SR}`, "Content-Type": "application/json",
        Prefer: "resolution=merge-duplicates,return=minimal",
      },
      body: JSON.stringify({ k, v, at: new Date().toISOString() }),
    });
  } catch { /* sin caché no pasa nada */ }
}

/** Devuelve lo guardado si sigue fresco; si no, lo vuelve a armar. Si ESPN falla, regresa lo viejo. */
async function cached(k: string, ttl: (v: J) => number, make: () => Promise<J>): Promise<J> {
  const now = Date.now();
  const m = mem.get(k);
  if (m && now - m.at < m.ttl) return m.v;
  const d = await dbGet(k);
  if (d && now - d.at < ttl(d.v)) { mem.set(k, { at: d.at, ttl: ttl(d.v), v: d.v }); return d.v; }
  try {
    const v = await make();
    mem.set(k, { at: now, ttl: ttl(v), v });
    await dbPut(k, v);
    return v;
  } catch (e) {
    const old = m?.v ?? d?.v;
    if (old) return old;
    throw e;
  }
}

async function getJson(url: string): Promise<J> {
  const ctl = new AbortController();
  const t = setTimeout(() => ctl.abort(), 9000);
  try {
    const r = await fetch(url, { signal: ctl.signal, headers: { "User-Agent": "Mozilla/5.0 CineCasa" } });
    if (!r.ok) throw new Error(`HTTP ${r.status}`);
    return await r.json();
  } finally { clearTimeout(t); }
}

// ---------------- traducciones cortas ----------------
const ordM = (n: number) => (n === 1 ? "1er" : n === 2 ? "2º" : n === 3 ? "3er" : `${n}º`);
const ordF = (n: number) => `${n}ª`;

function statusEs(sp: string, s: J): string {
  const t = s?.type ?? {};
  const name: string = t.name ?? "";
  const period: number = s?.period ?? 0;
  const clock: string = s?.displayClock ?? "";
  if (name.includes("POSTPONED")) return "Pospuesto";
  if (name.includes("CANCELED")) return "Cancelado";
  if (name.includes("DELAYED") || name.includes("RAIN")) return "Retrasado";
  if (name.includes("SUSPENDED")) return "Suspendido";
  if (t.state === "pre") return "";
  if (t.state === "post") {
    const reg = sp === "hockey" || sp === "soccer" ? 3 : sp === "baseball" ? 9 : 4;
    if (sp === "soccer") return (t.shortDetail ?? "").includes("Pen") ? "Final (penales)" : "Final";
    if (sp === "baseball") return period > 9 ? `Final (${period} entradas)` : "Final";
    return period > reg ? "Final (tiempo extra)" : "Final";
  }
  if (name.includes("HALFTIME")) return "Medio tiempo";
  switch (sp) {
    case "baseball": {
      const d: string = (t.shortDetail ?? "").toLowerCase();
      const half = d.startsWith("top") ? "Alta" : d.startsWith("bot") ? "Baja" : d.startsWith("mid") ? "Mitad" : d.startsWith("end") ? "Fin" : "";
      return `${half} ${ordF(period)}`.trim();
    }
    case "football":
    case "basketball":
      if (name.includes("END_PERIOD")) return `Fin del ${ordM(period)} cuarto`;
      return period > 4 ? `Tiempo extra ${clock}` : `${ordM(period)} cuarto ${clock}`;
    case "hockey":
      if (name.includes("END_PERIOD")) return `Fin del ${ordM(period)} periodo`;
      return period > 3 ? `Tiempo extra ${clock}` : `${ordM(period)} periodo ${clock}`;
    case "soccer":
      return clock ? clock.replace(/'?$/, "'") : "En juego";
    case "mma":
      return `Round ${period}`;
  }
  return t.shortDetail ?? "";
}

// ---------------- resúmenes ----------------
function teamOf(c: J) {
  const t = c?.team ?? c?.athlete ?? {};
  return {
    id: String(t.id ?? ""),
    n: t.shortDisplayName ?? t.name ?? t.displayName ?? "",
    full: t.displayName ?? t.fullName ?? "",
    ab: t.abbreviation ?? (t.shortName ?? "").slice(0, 4).toUpperCase(),
    logo: t.logo ?? t.logos?.[0]?.href ?? t.flag?.href ?? "",
    col: t.color ?? "",
    sc: scoreStr(c),
    rec: c?.records?.[0]?.summary ?? c?.record?.[0]?.displayValue ?? "",
    win: c?.winner === true,
  };
}

function oddsOf(comp: J) {
  const o = comp?.odds?.[0];
  if (!o) return null;
  const fav = o.homeTeamOdds?.favorite ? "h" : o.awayTeamOdds?.favorite ? "a" : "";
  const ml = (side: string) => o.moneyline?.[side]?.close?.odds ?? o.moneyline?.[side]?.open?.odds ?? (side === "home" ? o.homeTeamOdds?.moneyLine : o.awayTeamOdds?.moneyLine) ?? "";
  const out = {
    fav,
    mlh: String(ml("home") ?? ""),
    mla: String(ml("away") ?? ""),
    mld: String(o.drawOdds?.moneyLine ?? o.moneyline?.draw?.close?.odds ?? ""),
    spread: o.details ?? "",
    ou: o.overUnder ?? null,
    by: o.provider?.name ?? "",
  };
  if (!out.mlh && !out.mla && !out.spread) return null;
  return out;
}

function eventOf(key: string, sp: string, e: J) {
  let comp = e?.competitions?.[0] ?? {};
  // UFC: cada evento trae varias peleas; la estelar es la última
  if (sp === "mma" && Array.isArray(e?.competitions) && e.competitions.length) comp = e.competitions[e.competitions.length - 1];
  const cs: J[] = comp.competitors ?? [];
  const home = cs.find((c) => c.homeAway === "home") ?? cs[0];
  const away = cs.find((c) => c.homeAway === "away") ?? cs[1];
  const st = comp.status ?? e.status ?? {};
  const sit = comp.situation;
  const tv = (comp.broadcasts ?? []).flatMap((b: J) => b.names ?? []).concat(comp.broadcast ? [comp.broadcast] : []);
  return {
    id: String(e.id),
    lg: key,
    sp,
    name: sp === "mma" ? (e.name ?? "") : "",
    start: e.date ?? comp.date,
    st: st.type?.state ?? "pre",
    det: statusEs(sp, st),
    h: teamOf(home),
    a: teamOf(away),
    tv: [...new Set(tv)].slice(0, 3),
    odds: oddsOf(comp),
    note: comp.notes?.[0]?.headline ?? "",
    sit: sit ? {
      outs: sit.outs ?? null, b1: !!sit.onFirst, b2: !!sit.onSecond, b3: !!sit.onThird,
      balls: sit.balls ?? null, strikes: sit.strikes ?? null,
      pos: sit.possession ? String(sit.possession) : "",
      down: sit.shortDownDistanceText ?? sit.downDistanceText ?? "",
      rz: !!sit.isRedZone,
      last: sit.lastPlay?.text ?? "",
    } : null,
  };
}

/** Fecha AAAAMMDD en la zona del Este de EU (así cuenta los días ESPN). */
function etDay(offsetDays: number): string {
  const d = new Date(Date.now() + offsetDays * 86400_000);
  const p = new Intl.DateTimeFormat("en-CA", { timeZone: "America/New_York", year: "numeric", month: "2-digit", day: "2-digit" }).format(d);
  return p.replace(/-/g, "");
}
function utcDay(offsetDays: number): string {
  return new Date(Date.now() + offsetDays * 86400_000).toISOString().slice(0, 10);
}

// ---------------- Liga Mexicana del Pacífico (TheSportsDB; ESPN casi no la trae) ----------------
const TSDB = "https://www.thesportsdb.com/api/v1/json/123";
const LMP_ID = "5109";
const LMP_AB: Record<string, string> = {
  mexicali: "MXL", guasave: "GSV", "los mochis": "MOC", nayarit: "NAY", navojoa: "NAV", hermosillo: "HER",
  "culiacán": "CUL", "mazatlán": "MAZ", "obregón": "OBR", jalisco: "JAL", monterrey: "MTY", monclova: "MCV",
};
function lmpTeam(id: string, name: string, badge: string, score: J, win: boolean) {
  const nick = (name ?? "").split(" de ")[0] || name;
  const city = ((name ?? "").split(" de ").slice(1).join(" de ") || name).toLowerCase();
  return {
    id: String(id ?? ""), n: nick, full: name ?? "", ab: LMP_AB[city] ?? city.normalize("NFD").replace(/[^a-z]/g, "").slice(0, 3).toUpperCase(),
    logo: badge ? `${badge}/small` : "", col: "", sc: score == null ? "" : String(score), rec: "", win,
  };
}
function lmpState(s: string): { st: string; det: string } {
  const k = (s ?? "").toUpperCase();
  if (!k || k === "NS" || k === "NOT STARTED" || k === "TBD") return { st: "pre", det: "" };
  if (k === "PST" || k.includes("POSTP")) return { st: "post", det: "Pospuesto" };
  if (k === "CANC" || k.includes("CANCEL")) return { st: "post", det: "Cancelado" };
  if (k === "FT" || k.startsWith("AOT") || k.includes("FINISH") || k === "AET") return { st: "post", det: "Final" };
  const inn = k.match(/(\d+)/);
  return { st: "in", det: inn ? `${inn[1]}ª entrada` : "En juego" };
}
function lmpEvent(e: J) {
  const hs = e.intHomeScore, as = e.intAwayScore;
  const s = lmpState(e.strStatus);
  const hw = s.st === "post" && hs != null && as != null && Number(hs) > Number(as);
  const aw = s.st === "post" && hs != null && as != null && Number(as) > Number(hs);
  const ts = e.strTimestamp ? (e.strTimestamp.endsWith("Z") ? e.strTimestamp : e.strTimestamp + "Z") : `${e.dateEvent}T${e.strTime ?? "00:00:00"}Z`;
  return {
    id: String(e.idEvent), lg: "lmp", sp: "baseball", name: "", start: ts, st: s.st, det: s.det,
    h: lmpTeam(e.idHomeTeam, e.strHomeTeam, e.strHomeTeamBadge, hs, hw),
    a: lmpTeam(e.idAwayTeam, e.strAwayTeam, e.strAwayTeamBadge, as, aw),
    tv: e.strTVStation ? [e.strTVStation] : [], odds: null, note: "", sit: null,
  };
}
async function lmpDay(d: string, off: number): Promise<J[]> {
  return cached(`lmpday:${d}`, (v) => (off < 0 ? 3 * 3600_000 : off === 0 || (v ?? []).some((x: J) => x.st === "in") ? 120_000 : 30 * 60_000), async () => {
    const j = await getJson(`${TSDB}/eventsday.php?d=${d}&l=${LMP_ID}`);
    return (Array.isArray(j.events) ? j.events : []).map(lmpEvent);
  });
}

/** Un día de una liga de ESPN, guardado según qué tan rápido cambia. */
async function espnDay(key: string, d: string, off: number): Promise<J[]> {
  const L = LEAGUES[key];
  return cached(`day:${key}:${d}`, (v) => {
    if ((v ?? []).some((x: J) => x.st === "in")) return 25_000;
    if (off < 0) return 3 * 3600_000;
    if (off === 0) return 120_000;
    return 30 * 60_000;
  }, async () => {
    const j = await getJson(`${SITE}/${L.sp}/${L.lg}/scoreboard?dates=${d}`);
    return (j.events ?? []).map((e: J) => eventOf(key, L.sp, e));
  });
}

async function board(): Promise<J> {
  const offs = [-1, 0, 1, 2, 3];
  const jobs: Promise<J[]>[] = [];
  for (const key of Object.keys(LEAGUES)) {
    if (key === "lmp") continue;
    for (const o of offs) jobs.push(espnDay(key, etDay(o), o));
  }
  for (const o of [-1, 0, 1, 2, 3, 4]) jobs.push(lmpDay(utcDay(o), o - 1));
  const results = await Promise.allSettled(jobs);
  const seen = new Set<string>();
  const events = results.flatMap((r) => (r.status === "fulfilled" ? r.value : []))
    .filter((e: J) => { const k = `${e.lg}:${e.id}`; if (seen.has(k)) return false; seen.add(k); return true; })
    .filter((e: J) => Date.parse(e.start) > Date.now() - 36 * 3600_000);
  events.sort((x: J, y: J) => Date.parse(x.start) - Date.parse(y.start));
  return { at: Date.now(), live: events.some((e: J) => e.st === "in"), events };
}

async function lmpTeams(): Promise<J> {
  const [a, b] = await Promise.allSettled([
    getJson(`${TSDB}/search_all_teams.php?l=Mexican_Pacific_League`),
    getJson(`${TSDB}/eventsseason.php?id=${LMP_ID}&s=${lmpSeason()}`),
  ]);
  const m = new Map<string, J>();
  for (const t of a.status === "fulfilled" ? a.value.teams ?? [] : []) m.set(String(t.idTeam), lmpTeam(t.idTeam, t.strTeam, t.strBadge, null, false));
  for (const e of b.status === "fulfilled" ? b.value.events ?? [] : []) {
    if (!m.has(String(e.idHomeTeam))) m.set(String(e.idHomeTeam), lmpTeam(e.idHomeTeam, e.strHomeTeam, e.strHomeTeamBadge, null, false));
    if (!m.has(String(e.idAwayTeam))) m.set(String(e.idAwayTeam), lmpTeam(e.idAwayTeam, e.strAwayTeam, e.strAwayTeamBadge, null, false));
  }
  return { lg: "lmp", teams: [...m.values()].map(({ sc, rec, win, ...t }) => t).sort((x, y) => x.full.localeCompare(y.full)) };
}
function lmpSeason() {
  const d = new Date();
  const y = d.getUTCFullYear();
  return d.getUTCMonth() >= 6 ? `${y}-${y + 1}` : `${y - 1}-${y}`;
}

async function lmpTeamPage(id: string): Promise<J> {
  const [info, last, next] = await Promise.allSettled([
    getJson(`${TSDB}/lookupteam.php?id=${id}`),
    getJson(`${TSDB}/eventslast.php?id=${id}`),
    getJson(`${TSDB}/eventsnext.php?id=${id}`),
  ]);
  const t = info.status === "fulfilled" ? info.value.teams?.[0] ?? {} : {};
  const g = (e: J) => {
    const ev = lmpEvent(e);
    const home = String(e.idHomeTeam) === id;
    const us = home ? ev.h : ev.a, them = home ? ev.a : ev.h;
    const a = Number(us.sc), b = Number(them.sc);
    return {
      id: ev.id, start: ev.start, st: ev.st, home, opp: them, us: us.sc, them: them.sc,
      res: ev.st === "post" && !isNaN(a) && !isNaN(b) && us.sc !== "" ? (a > b ? "G" : a < b ? "P" : "E") : "", tv: ev.tv,
    };
  };
  const past = (last.status === "fulfilled" ? last.value.results ?? [] : []).map(g).sort((x: J, y: J) => Date.parse(x.start) - Date.parse(y.start));
  const nxt = (next.status === "fulfilled" ? next.value.events ?? [] : []).map(g);
  const s0 = past.length ? past[past.length - 1].res : "";
  let n = 0;
  for (let i = past.length - 1; i >= 0 && past[i].res === s0; i--) n++;
  const me = lmpTeam(id, t.strTeam ?? "", t.strBadge ?? "", null, false);
  return {
    lg: "lmp",
    team: { id, n: me.n, full: me.full, ab: me.ab, logo: me.logo, col: "", rec: "", home: "", away: "", standing: "" },
    past, next: nxt, streak: s0 ? `${n}${s0}` : "", inj: [], leaders: [], news: [],
  };
}

function resultOf(us: J, them: J): string {
  const a = Number(us?.score?.value ?? us?.score ?? NaN), b = Number(them?.score?.value ?? them?.score ?? NaN);
  if (isNaN(a) || isNaN(b)) return "";
  if (us?.winner === true || a > b) return "G";
  if (them?.winner === true || a < b) return "P";
  return "E";
}

function scoreStr(c: J) {
  const s = c?.score;
  if (s == null) return "";
  if (typeof s === "object") return s.displayValue ?? String(s.value ?? "");
  return String(s);
}

async function team(key: string, id: string): Promise<J> {
  if (key === "lmp") return lmpTeamPage(id);
  const L = LEAGUES[key];
  if (!L) throw new Error("liga");
  const base = `${SITE}/${L.sp}/${L.lg}`;
  const [info, sched, news] = await Promise.allSettled([
    getJson(`${base}/teams/${id}`),
    getJson(`${base}/teams/${id}/schedule`),
    getJson(`${base}/news?team=${id}&limit=8&lang=es&region=mx`),
  ]);
  const t = info.status === "fulfilled" ? info.value.team ?? {} : {};
  const evs: J[] = sched.status === "fulfilled" ? sched.value.events ?? [] : [];
  const games = evs.map((e) => {
    const comp = e.competitions?.[0] ?? {};
    const cs: J[] = comp.competitors ?? [];
    const us = cs.find((c) => String(c.id ?? c.team?.id) === id);
    const them = cs.find((c) => c !== us);
    const st = comp.status?.type ?? {};
    return {
      id: String(e.id),
      start: e.date,
      st: st.state ?? (st.completed ? "post" : "pre"),
      home: us?.homeAway === "home",
      opp: teamOf(them),
      us: scoreStr(us),
      them: scoreStr(them),
      res: st.completed ? resultOf(us, them) : "",
      tv: (comp.broadcasts ?? []).map((b: J) => b.media?.shortName ?? b.names?.[0]).filter(Boolean).slice(0, 2),
    };
  });
  games.sort((x, y) => Date.parse(x.start) - Date.parse(y.start));
  const past = games.filter((g) => g.st === "post").slice(-8);
  const next = games.filter((g) => g.st !== "post").slice(0, 6);
  // Racha: «3G» = tres ganados seguidos
  const s0 = past.length ? past[past.length - 1].res : "";
  let n = 0;
  for (let i = past.length - 1; i >= 0 && past[i].res === s0; i--) n++;
  const streak = s0 ? `${n}${s0}` : "";

  // Lesionados: vienen en el detalle del próximo partido (o del último)
  let inj: J[] = [];
  let leaders: J[] = [];
  const g = next[0] ?? past[past.length - 1];
  if (g) {
    try {
      const s = await getJson(`${base}/summary?event=${g.id}`);
      const mine = (s.injuries ?? []).find((x: J) => String(x.team?.id) === id);
      inj = (mine?.injuries ?? []).slice(0, 15).map((x: J) => ({
        n: x.athlete?.displayName ?? "",
        pos: x.athlete?.position?.abbreviation ?? "",
        st: injEs(x.status ?? x.type?.description ?? ""),
        d: x.details?.type ?? x.type?.abbreviation ?? "",
        ret: x.details?.returnDate ?? "",
      }));
      const lead = (s.leaders ?? []).find((x: J) => String(x.team?.id) === id);
      leaders = (lead?.leaders ?? []).slice(0, 4).map((c: J) => ({
        cat: c.displayName ?? c.name ?? "",
        n: c.leaders?.[0]?.athlete?.displayName ?? "",
        v: c.leaders?.[0]?.displayValue ?? "",
      })).filter((x: J) => x.n);
    } catch { /* sin detalle */ }
  }
  const arts: J[] = news.status === "fulfilled" ? news.value.articles ?? [] : [];
  const recItems: J[] = t.record?.items ?? [];
  const rec = (type: string) => recItems.find((r) => r.type === type)?.summary ?? "";
  return {
    lg: key,
    team: {
      id, n: t.shortDisplayName ?? t.name ?? "", full: t.displayName ?? "", ab: t.abbreviation ?? "",
      logo: t.logos?.[0]?.href ?? "", col: t.color ?? "",
      rec: rec("total"), home: rec("home"), away: rec("road") || rec("away"),
      standing: standingEs(t.standingSummary ?? ""),
    },
    past, next, streak, inj, leaders,
    news: arts.slice(0, 8).map((a) => ({
      t: a.headline ?? "", d: a.description ?? "", img: a.images?.[0]?.url ?? "", at: a.published ?? "",
    })),
  };
}

/** «1st in AFC West» -> «1º en AFC West» */
function standingEs(s: string): string {
  return s.replace(/^(\d+)(st|nd|rd|th)\s+in\s+/i, "$1º en ").replace(/^T-(\d+)(st|nd|rd|th)\s+in\s+/i, "Empatado en $1º en ");
}

function injEs(s: string): string {
  const k = s.toLowerCase();
  if (k.includes("out")) return "Fuera";
  if (k.includes("doubt")) return "Dudoso";
  if (k.includes("question")) return "En duda";
  if (k.includes("probable")) return "Probable";
  if (k.includes("injured reserve") || k === "ir") return "Lista de lesionados";
  if (k.includes("day-to-day")) return "Día a día";
  if (k.includes("suspen")) return "Suspendido";
  return s;
}

async function table(key: string): Promise<J> {
  if (key === "lmp") return { lg: key, cols: [], groups: [] };
  const L = LEAGUES[key];
  if (!L) throw new Error("liga");
  const j = await getJson(`https://site.api.espn.com/apis/v2/sports/${L.sp}/${L.lg}/standings`);
  const groups: J[] = [];
  const walk = (g: J) => {
    if (g.standings?.entries?.length) groups.push(g);
    (g.children ?? []).forEach(walk);
  };
  walk(j);
  const want = L.sp === "soccer" ? ["gamesPlayed", "points", "pointDifferential"] :
    L.sp === "hockey" ? ["gamesPlayed", "points", "wins", "losses"] :
    ["wins", "losses", "winPercent", "gamesBehind"];
  return {
    lg: key,
    cols: L.sp === "soccer" ? ["PJ", "Pts", "Dif"] : L.sp === "hockey" ? ["PJ", "Pts", "G", "P"] : ["G", "P", "%", "Dif"],
    groups: groups.map((g) => ({
      n: g.name ?? "",
      rows: (g.standings.entries as J[])
        .map((e) => {
          const stats: J[] = e.stats ?? [];
          const sv = (n: string) => stats.find((s) => s.name === n);
          return {
            id: String(e.team?.id ?? ""), n: e.team?.shortDisplayName ?? e.team?.displayName ?? "", ab: e.team?.abbreviation ?? "",
            logo: e.team?.logos?.[0]?.href ?? "",
            seed: Number(sv("playoffSeed")?.value ?? sv("rank")?.value ?? 0),
            v: want.map((n) => sv(n)?.displayValue ?? ""),
          };
        })
        .sort((a, b) => (a.seed || 99) - (b.seed || 99)),
    })),
  };
}

// ---------------- Detalle del partido (estilo Gamecast) ----------------
type StatDef = { k: string; l: string; low?: boolean; pct?: boolean };
const STATS: Record<string, StatDef[]> = {
  football: [
    { k: "totalYards", l: "Yardas totales" }, { k: "netPassingYards", l: "Yardas por aire" }, { k: "rushingYards", l: "Yardas por tierra" },
    { k: "firstDowns", l: "Primeros intentos" }, { k: "thirdDownEff", l: "Tercera oportunidad" }, { k: "redZoneAttempts", l: "Zona roja (anotó-intentos)" },
    { k: "yardsPerPlay", l: "Yardas por jugada" }, { k: "turnovers", l: "Pérdidas de balón", low: true }, { k: "sacksYardsLost", l: "Capturas recibidas", low: true },
    { k: "totalPenaltiesYards", l: "Castigos (yardas)", low: true }, { k: "possessionTime", l: "Tiempo de posesión" },
    { k: "totalPointsPerGame", l: "Puntos por partido" }, { k: "totalPointsPerGameAllowed", l: "Puntos permitidos por partido", low: true },
    { k: "yardsPerGame", l: "Yardas por partido" }, { k: "yardsPerGameAllowed", l: "Yardas permitidas por partido", low: true },
    { k: "passingYardsPerGame", l: "Yardas por aire por partido" }, { k: "rushingYardsPerGame", l: "Yardas por tierra por partido" },
  ],
  basketball: [
    { k: "fieldGoalPct", l: "% de tiros de campo", pct: true }, { k: "threePointFieldGoalPct", l: "% de triples", pct: true }, { k: "freeThrowPct", l: "% de tiros libres", pct: true },
    { k: "totalRebounds", l: "Rebotes" }, { k: "offensiveRebounds", l: "Rebotes ofensivos" }, { k: "assists", l: "Asistencias" }, { k: "steals", l: "Robos" }, { k: "blocks", l: "Bloqueos" },
    { k: "totalTurnovers", l: "Pérdidas", low: true }, { k: "pointsInPaint", l: "Puntos en la pintura" }, { k: "fastBreakPoints", l: "Puntos de contraataque" },
    { k: "turnoverPoints", l: "Puntos tras pérdida" }, { k: "largestLead", l: "Mayor ventaja" }, { k: "fouls", l: "Faltas", low: true },
    { k: "avgPoints", l: "Puntos por partido" }, { k: "avgRebounds", l: "Rebotes por partido" }, { k: "avgAssists", l: "Asistencias por partido" },
  ],
  hockey: [
    { k: "shotsTotal", l: "Tiros" }, { k: "faceoffPercent", l: "% de faceoffs", pct: true }, { k: "powerPlayGoals", l: "Goles en power play" },
    { k: "powerPlayOpportunities", l: "Oportunidades de power play" }, { k: "hits", l: "Golpes" }, { k: "blockedShots", l: "Tiros bloqueados" },
    { k: "takeaways", l: "Recuperaciones" }, { k: "giveaways", l: "Pérdidas", low: true }, { k: "penaltyMinutes", l: "Minutos de castigo", low: true },
  ],
  soccer: [
    { k: "possessionPct", l: "Posesión", pct: true }, { k: "totalShots", l: "Tiros" }, { k: "shotsOnTarget", l: "Tiros a gol" }, { k: "wonCorners", l: "Tiros de esquina" },
    { k: "saves", l: "Atajadas" }, { k: "passPct", l: "Precisión de pases", pct: true }, { k: "accuratePasses", l: "Pases completos" },
    { k: "foulsCommitted", l: "Faltas", low: true }, { k: "offsides", l: "Fueras de lugar", low: true }, { k: "yellowCards", l: "Tarjetas amarillas", low: true }, { k: "redCards", l: "Tarjetas rojas", low: true },
  ],
  baseball: [
    { k: "runs", l: "Carreras" }, { k: "hits", l: "Hits" }, { k: "homeRuns", l: "Jonrones" }, { k: "RBIs", l: "Carreras impulsadas" }, { k: "walks", l: "Bases por bolas" },
    { k: "strikeouts", l: "Ponches" }, { k: "errors", l: "Errores", low: true }, { k: "leftOnBase", l: "Dejados en base", low: true }, { k: "avg", l: "Promedio de bateo" }, { k: "ERA", l: "Efectividad (ERA)", low: true },
  ],
};
const GROUP_ES: Record<string, string> = {
  passing: "Pases", rushing: "Carreras", receiving: "Recepciones", defensive: "Defensa", forwards: "Delanteros", defenses: "Defensas",
  goalies: "Porteros", batting: "Bateo", pitching: "Pitcheo", "": "Jugadores",
};
const COLS: Record<string, string[]> = {
  // basquetbol
  "": ["points", "rebounds", "assists", "fieldGoalsMade-fieldGoalsAttempted", "threePointFieldGoalsMade-threePointFieldGoalsAttempted", "minutes"],
  passing: ["completions/passingAttempts", "passingYards", "passingTouchdowns", "interceptions", "QBRating"],
  rushing: ["rushingAttempts", "rushingYards", "yardsPerRushAttempt", "rushingTouchdowns", "longRushing"],
  receiving: ["receptions", "receivingYards", "receivingTouchdowns", "longReception", "receivingTargets"],
  forwards: ["goals", "assists", "shotsTotal", "plusMinus", "timeOnIce"],
  defenses: ["goals", "assists", "blockedShots", "plusMinus", "timeOnIce"],
  goalies: ["saves", "shotsAgainst", "goalsAgainst", "savePct", "timeOnIce"],
};
const COL_ES: Record<string, string> = {
  points: "PTS", rebounds: "REB", assists: "AST", "fieldGoalsMade-fieldGoalsAttempted": "TC", "threePointFieldGoalsMade-threePointFieldGoalsAttempted": "3P", minutes: "MIN",
  "completions/passingAttempts": "C/I", passingYards: "YDS", passingTouchdowns: "TD", interceptions: "INT", QBRating: "RAT",
  rushingAttempts: "ACA", rushingYards: "YDS", yardsPerRushAttempt: "PROM", rushingTouchdowns: "TD", longRushing: "LARGA",
  receptions: "REC", receivingYards: "YDS", receivingTouchdowns: "TD", longReception: "LARGA", receivingTargets: "OBJ",
  goals: "G", shotsTotal: "TIR", plusMinus: "+/-", timeOnIce: "TIEMPO", blockedShots: "BLQ", saves: "ATAJ", shotsAgainst: "TIR", goalsAgainst: "GC", savePct: "%",
};

function num(s: string): number {
  if (!s) return NaN;
  if (/^\d+:\d+$/.test(s)) { const [m, x] = s.split(":").map(Number); return m * 60 + x; }
  const a = s.match(/^(\d+)[-/](\d+)/);
  if (a) return Number(a[1]);
  return Number(String(s).replace("%", ""));
}

function teamStats(sp: string, box: J): J[] {
  const teams: J[] = box?.teams ?? [];
  const flat = (t: J): Record<string, J> => {
    const m: Record<string, J> = {};
    for (const s of t?.statistics ?? []) {
      if (Array.isArray(s.stats)) for (const x of s.stats) m[x.name] = x; // béisbol: grupos
      else m[s.name] = s;
    }
    return m;
  };
  const home = teams.find((t) => t.homeAway === "home") ?? teams[1];
  const away = teams.find((t) => t.homeAway === "away") ?? teams[0];
  const h = flat(home), a = flat(away);
  const defs = STATS[sp] ?? [];
  const out: J[] = [];
  for (const d of defs) {
    const hs = h[d.k], as = a[d.k];
    if (!hs && !as) continue;
    let hv = hs?.displayValue ?? "", av = as?.displayValue ?? "";
    if (d.pct && /^0?\.\d+$/.test(hv)) hv = String(Math.round(Number(hv) * 100));
    if (d.pct && /^0?\.\d+$/.test(av)) av = String(Math.round(Number(av) * 100));
    out.push({ l: d.l, h: d.pct && hv ? hv + "%" : hv, a: d.pct && av ? av + "%" : av, hn: num(hv), an: num(av), low: !!d.low });
  }
  if (!out.length) {
    // Liga sin lista propia: lo que mande ESPN, con su etiqueta
    for (const k of Object.keys(h).slice(0, 12)) out.push({ l: h[k].label ?? k, h: h[k].displayValue ?? "", a: a[k]?.displayValue ?? "", hn: num(h[k].displayValue), an: num(a[k]?.displayValue), low: false });
  }
  return out;
}

function players(sp: string, box: J): J[] {
  const out: J[] = [];
  for (const t of box?.players ?? []) {
    const groups: J[] = [];
    for (const g of t.statistics ?? []) {
      const name = g.name ?? "";
      const want = COLS[name] ?? (sp === "basketball" ? COLS[""] : null);
      if (!want || !(g.athletes ?? []).length) continue;
      const keys: string[] = g.keys ?? [];
      const idx = want.map((k) => keys.indexOf(k)).filter((i) => i >= 0);
      let rows = (g.athletes as J[]).filter((x) => (x.stats ?? []).length).map((x) => ({
        n: x.athlete?.shortName ?? x.athlete?.displayName ?? "", pos: x.athlete?.position?.abbreviation ?? "", num: x.athlete?.jersey ?? "",
        st: !!x.starter, v: idx.map((i) => x.stats[i] ?? ""),
      }));
      if (sp === "basketball") rows.sort((p, q) => Number(q.v[0]) - Number(p.v[0]));
      if (sp === "hockey" && name !== "goalies") rows.sort((p, q) => (Number(q.v[0]) + Number(q.v[1])) - (Number(p.v[0]) + Number(p.v[1])));
      rows = rows.slice(0, sp === "basketball" ? 8 : 4);
      if (rows.length) groups.push({ g: GROUP_ES[name] ?? name, cols: idx.map((i) => COL_ES[keys[i]] ?? keys[i]), rows });
    }
    if (groups.length) out.push({ team: String(t.team?.id ?? ""), groups: groups.slice(0, 4) });
  }
  return out;
}

function lineups(s: J): J[] {
  return (s.rosters ?? []).map((r: J) => ({
    team: String(r.team?.id ?? ""), formation: r.formation ?? "",
    starters: (r.roster ?? []).filter((p: J) => p.starter).map((p: J) => ({
      n: p.athlete?.displayName ?? "", num: p.jersey ?? "", pos: p.position?.abbreviation ?? "",
      ev: (p.plays ?? []).map((x: J) => (x.didScore ? "gol" : x.yellowCard ? "amarilla" : x.redCard ? "roja" : x.substitution ? "cambio" : "")).filter(Boolean),
    })),
  })).filter((r: J) => r.starters.length);
}

function oddsDetail(s: J, comp: J): J {
  const p = s.pickcenter?.[0] ?? s.odds?.[0];
  if (!p) return null;
  const side = (o: J, which: string) => ({
    open: String(o?.[which]?.open?.odds ?? ""), now: String(o?.[which]?.close?.odds ?? o?.[which]?.current?.odds ?? ""),
  });
  const line = (o: J, which: string) => ({
    open: String(o?.[which]?.open?.line ?? ""), now: String(o?.[which]?.close?.line ?? ""),
    oddsNow: String(o?.[which]?.close?.odds ?? ""),
  });
  const ml = p.moneyline ?? {};
  const ps = p.pointSpread ?? {};
  const tot = p.total ?? {};
  return {
    by: p.provider?.name ?? "",
    details: p.details ?? "",
    favH: !!p.homeTeamOdds?.favorite, favA: !!p.awayTeamOdds?.favorite,
    favOpenH: !!p.homeTeamOdds?.favoriteAtOpen, favOpenA: !!p.awayTeamOdds?.favoriteAtOpen,
    mlH: { ...side(ml, "home"), now: String(ml?.home?.close?.odds ?? p.homeTeamOdds?.moneyLine ?? "") },
    mlA: { ...side(ml, "away"), now: String(ml?.away?.close?.odds ?? p.awayTeamOdds?.moneyLine ?? "") },
    mlD: String(ml?.draw?.close?.odds ?? p.drawOdds?.moneyLine ?? ""),
    spH: line(ps, "home"), spA: line(ps, "away"),
    ou: { open: String(tot?.over?.open?.line ?? "").replace(/^o/, ""), now: String(p.overUnder ?? ""), over: String(p.overOdds ?? tot?.over?.close?.odds ?? ""), under: String(p.underOdds ?? tot?.under?.close?.odds ?? "") },
  };
}

function last5(s: J): J[] {
  return (s.lastFiveGames ?? []).map((t: J) => ({
    team: String(t.team?.id ?? ""),
    ev: (t.events ?? []).map((e: J) => ({
      r: ({ W: "G", L: "P", T: "E", D: "E" } as Record<string, string>)[e.gameResult] ?? e.gameResult ?? "",
      s: e.score ?? "", o: e.opponent?.abbreviation ?? "", logo: e.opponent?.logo ?? "", at: e.atVs === "@" || e.atVs === "en" ? "en" : "vs", d: e.gameDate ?? "",
    })),
  }));
}

function h2h(s: J): J[] {
  const ss = s.seasonseries?.[0];
  if (!ss) return [];
  return (ss.events ?? []).filter((e: J) => e.statusType?.completed !== false).slice(-6).map((e: J) => {
    const cs: J[] = e.competitors ?? [];
    const h = cs.find((c) => c.homeAway === "home") ?? cs[0], a = cs.find((c) => c.homeAway === "away") ?? cs[1];
    return { d: e.date ?? "", h: h?.team?.abbreviation ?? "", a: a?.team?.abbreviation ?? "", hs: String(h?.score ?? ""), as: String(a?.score ?? ""), w: h?.winner ? "h" : a?.winner ? "a" : "" };
  }).filter((x: J) => x.hs !== "" && x.hs !== "undefined");
}

function playsOf(sp: string, s: J): J[] {
  let raw: J[] = [];
  // Futbol americano: cada jugada toma el equipo de su serie (ESPN no lo pone en la jugada)
  const withTeam = (d: J) => (d?.plays ?? []).map((p: J) => (p.team ? p : { ...p, team: d.team }));
  if (sp === "football") raw = [...(s.drives?.previous ?? []).flatMap(withTeam), ...withTeam(s.drives?.current)];
  else if (sp === "soccer") raw = s.commentary ?? s.keyEvents ?? [];
  else raw = s.plays ?? [];
  return raw.slice(-45).reverse().map((p: J) => ({
    t: p.text ?? p.play?.text ?? p.shortText ?? "",
    clk: p.clock?.displayValue ?? p.time?.displayValue ?? "",
    per: p.period?.number ?? 0,
    team: String(p.team?.id ?? p.play?.team?.id ?? ""),
    sc: !!(p.scoringPlay ?? p.play?.scoringPlay),
    score: p.awayScore != null && p.homeScore != null ? `${p.awayScore}-${p.homeScore}` : "",
  })).filter((p: J) => p.t && !/^(END GAME|End of Game|Game End)$/i.test(p.t.trim()));
}

function keyPlays(sp: string, s: J): J[] {
  let raw: J[] = [];
  if (sp === "football") raw = s.scoringPlays ?? [];
  else if (sp === "soccer") raw = (s.keyEvents ?? []).filter((e: J) => e.scoringPlay || /card|tarjeta|goal|gol|penal/i.test(e.type?.type ?? e.type?.text ?? ""));
  else raw = (s.plays ?? []).filter((p: J) => p.scoringPlay);
  return raw.slice(-14).map((p: J) => ({
    t: p.text ?? p.shortText ?? "", clk: p.clock?.displayValue ?? "", per: p.period?.number ?? 0, team: String(p.team?.id ?? ""), sc: true,
    score: p.awayScore != null && p.homeScore != null ? `${p.awayScore}-${p.homeScore}` : "",
    kind: /yellow|amarilla/i.test(p.type?.text ?? "") ? "amarilla" : /red|roja/i.test(p.type?.text ?? "") ? "roja" : "",
  }));
}

function leadersOf(s: J): J[] {
  return (s.leaders ?? []).map((t: J) => ({
    team: String(t.team?.id ?? ""),
    items: (t.leaders ?? []).slice(0, 4).map((c: J) => ({
      cat: leaderCat(c.name ?? "", c.displayName ?? ""), n: c.leaders?.[0]?.athlete?.displayName ?? "",
      v: c.leaders?.[0]?.displayValue ?? "", head: c.leaders?.[0]?.athlete?.headshot?.href ?? "",
      pos: c.leaders?.[0]?.athlete?.position?.abbreviation ?? "",
    })).filter((x: J) => x.n),
  })).filter((t: J) => t.items.length);
}

function leaderCat(n: string, d: string): string {
  const m: Record<string, string> = {
    passingYards: "Pases", rushingYards: "Carreras", receivingYards: "Recepciones", sacks: "Capturas", totalTackles: "Tacleadas",
    points: "Puntos", rebounds: "Rebotes", assists: "Asistencias", goals: "Goles", saves: "Atajadas", savePct: "Porcentaje de atajadas",
    pointsPerGame: "Puntos por partido", reboundsPerGame: "Rebotes por partido", assistsPerGame: "Asistencias por partido",
  };
  return m[n] ?? d;
}

async function game(key: string, id: string): Promise<J> {
  if (key === "lmp") return { lg: key, id, st: "", det: "", teams: [], wp: [], plays: [], keyPlays: [], stats: [], players: [], leaders: [], inj: [], last5: [], h2h: [], lineups: [] };
  const L = LEAGUES[key];
  if (!L) throw new Error("liga");
  const lang = L.sp === "soccer" ? "&lang=es&region=mx" : "";
  const s = await getJson(`${SITE}/${L.sp}/${L.lg}/summary?event=${id}${lang}`);
  const comp = s.header?.competitions?.[0] ?? {};
  const wp: J[] = s.winprobability ?? [];
  const step = Math.max(1, Math.ceil(wp.length / 120));
  const teams = (comp.competitors ?? []).map((c: J) => ({
    id: String(c.id ?? c.team?.id), ab: c.team?.abbreviation ?? "", n: c.team?.shortDisplayName ?? c.team?.name ?? "", full: c.team?.displayName ?? "",
    logo: c.team?.logos?.[0]?.href ?? c.team?.logo ?? "", col: c.team?.color ?? "", home: c.homeAway === "home",
    ls: (c.linescores ?? []).map((x: J) => x.displayValue ?? String(x.value ?? "")),
    sc: c.score ?? "", rec: c.record?.[0]?.summary ?? c.record?.[0]?.displayValue ?? "", win: !!c.winner,
  }));
  const gi = s.gameInfo ?? {};
  const pred = s.predictor ? {
    h: Number(s.predictor.homeTeam?.gameProjection ?? NaN), a: Number(s.predictor.awayTeam?.gameProjection ?? NaN),
  } : null;
  return {
    lg: key, id, st: comp.status?.type?.state ?? "", det: statusEs(L.sp, comp.status), start: comp.date ?? "",
    tv: (comp.broadcasts ?? []).map((b: J) => b.media?.shortName ?? b.type?.shortName).filter(Boolean).slice(0, 3),
    venue: gi.venue?.fullName ?? "", city: [gi.venue?.address?.city, gi.venue?.address?.state ?? gi.venue?.address?.country].filter(Boolean).join(", "),
    att: gi.attendance ?? 0, temp: gi.weather?.temperature ?? null, rain: gi.weather?.precipitation ?? null,
    teams,
    wp: wp.filter((_, i) => i % step === 0 || i === wp.length - 1).map((p) => Math.round((p.homeWinPercentage ?? 0.5) * 1000) / 10),
    pred: pred && !isNaN(pred.h) ? pred : null,
    stats: teamStats(L.sp, s.boxscore),
    players: players(L.sp, s.boxscore),
    leaders: leadersOf(s),
    plays: playsOf(L.sp, s),
    keyPlays: keyPlays(L.sp, s),
    drive: s.drives?.current?.description ?? "",
    odds: oddsDetail(s, comp),
    ats: (s.againstTheSpread ?? []).map((t: J) => ({ team: String(t.team?.id ?? ""), recs: (t.records ?? []).map((r: J) => ({ t: r.type ?? "", s: r.summary ?? "" })) })).filter((t: J) => t.recs.length),
    last5: last5(s),
    h2h: h2h(s),
    lineups: lineups(s),
    inj: (s.injuries ?? []).map((t: J) => ({
      team: String(t.team?.id ?? ""),
      list: (t.injuries ?? []).slice(0, 12).map((x: J) => ({ n: x.athlete?.displayName ?? "", pos: x.athlete?.position?.abbreviation ?? "", st: injEs(x.status ?? ""), d: x.details?.type ?? "" })),
    })).filter((t: J) => t.list.length),
  };
}

async function teams(key: string): Promise<J> {
  if (key === "lmp") return lmpTeams();
  const L = LEAGUES[key];
  if (!L) throw new Error("liga");
  const j = await getJson(`${SITE}/${L.sp}/${L.lg}/teams?limit=400`);
  const list: J[] = j.sports?.[0]?.leagues?.[0]?.teams ?? [];
  return {
    lg: key,
    teams: list.map((x) => x.team).filter(Boolean).map((t: J) => ({
      id: String(t.id), n: t.shortDisplayName ?? t.name ?? "", full: t.displayName ?? "", ab: t.abbreviation ?? "",
      logo: t.logos?.[0]?.href ?? "", col: t.color ?? "",
    })).sort((a: J, b: J) => a.full.localeCompare(b.full)),
  };
}

function reply(body: J, status = 200, maxAge = 30) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...CORS, "Content-Type": "application/json; charset=utf-8", "Cache-Control": `public, max-age=${maxAge}` },
  });
}

const okId = (s: string | null) => !!s && /^[0-9]{1,12}$/.test(s);

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response(null, { headers: CORS });
  const u = new URL(req.url);
  const q = u.searchParams.get("q") ?? "board";
  const lg = u.searchParams.get("lg") ?? "";
  const id = u.searchParams.get("id");
  try {
    switch (q) {
      case "leagues":
        return reply({ leagues: Object.entries(LEAGUES).map(([k, L]) => ({ k, name: L.name, short: L.short, sp: L.sp })) }, 200, 3600);
      case "board": {
        const v = await cached("board", (b) => (b?.live ? 20_000 : 60_000), board);
        return reply(v, 200, v.live ? 20 : 60);
      }
      case "team":
        if (!LEAGUES[lg] || !okId(id)) return reply({ error: "datos" }, 400);
        return reply(await cached(`team:${lg}:${id}`, () => 15 * 60_000, () => team(lg, id!)), 200, 300);
      case "table":
        if (!LEAGUES[lg]) return reply({ error: "datos" }, 400);
        return reply(await cached(`table:${lg}`, () => 30 * 60_000, () => table(lg)), 200, 600);
      case "game": {
        if (!LEAGUES[lg] || !okId(id)) return reply({ error: "datos" }, 400);
        const v = await cached(`game3:${lg}:${id}`, (g) => (g?.st === "in" ? 20_000 : 10 * 60_000), () => game(lg, id!));
        return reply(v, 200, v.st === "in" ? 15 : 300);
      }
      case "teams":
        if (!LEAGUES[lg]) return reply({ error: "datos" }, 400);
        return reply(await cached(`teams:${lg}`, () => 24 * 3600_000, () => teams(lg)), 200, 3600);
    }
    return reply({ error: "q" }, 400);
  } catch (e) {
    return reply({ error: String((e as Error)?.message ?? e) }, 502, 5);
  }
});
