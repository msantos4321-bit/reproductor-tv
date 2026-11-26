#!/usr/bin/env python3
"""Servidor de prueba para la Roku: nube (rpc), función deportes y un proveedor Xtream ficticio."""
import json, time, base64, io, re, sys, hashlib
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlparse, parse_qs
from PIL import Image, ImageDraw, ImageFont

B = "http://127.0.0.1:8099"
NOW = int(time.time())
LOG = open("/home/claude/rokusim/mock.log", "a")


def iso(t):
    return time.strftime("%Y-%m-%dT%H:%MZ", time.gmtime(t))


def T(ab, n, full, id, col, sc="", rec="", win=False):
    return {"id": id, "n": n, "full": full, "ab": ab, "logo": f"{B}/logo/{ab}.png", "col": col, "sc": sc, "rec": rec, "win": win}


def EV(id, lg, sp, st, start, a, h, det="", tv=None, odds=None, sit=None, note="", name=""):
    return {"id": id, "lg": lg, "sp": sp, "name": name, "start": iso(start), "st": st, "det": det, "h": h, "a": a,
            "tv": tv or [], "odds": odds, "note": note, "sit": sit}


CHI = lambda sc="", win=False: T("CHI", "Bears", "Chicago Bears", "3", "0b1c3a", sc, "8-2", win)
DET = lambda sc="", win=False: T("DET", "Lions", "Detroit Lions", "8", "0076b6", sc, "8-2", win)
PHI = lambda: T("PHI", "Eagles", "Philadelphia Eagles", "21", "06424d", "", "3-7")
DAL = lambda: T("DAL", "Cowboys", "Dallas Cowboys", "6", "002a5c", "", "5-6")
GB = lambda sc="", win=False: T("GB", "Packers", "Green Bay Packers", "9", "204e32", sc, "6-5", win)
LAR = lambda sc="", win=False: T("LAR", "Rams", "Los Angeles Rams", "14", "003594", sc, "7-4", win)

EVENTS = [
    EV("401873081", "nfl", "football", "in", NOW - 3600, CHI("14"), DET("17"), "3er cuarto 4:30", ["CBS"],
       sit={"outs": None, "b1": False, "b2": False, "b3": False, "balls": None, "strikes": None, "pos": "3",
            "down": "3rd & 7 at DET 34", "rz": False, "last": "C.Williams pass short right to D.Moore for 9 yards."}),
    EV("401873080", "nfl", "football", "pre", NOW + 2 * 3600 + 900, PHI(), DAL(), "", ["Fox"],
       odds={"fav": "h", "mlh": "-455", "mla": "+354", "mld": "", "spread": "DAL -8.5", "ou": 49.5, "by": "Draft Kings"}),
    EV("401873079", "nfl", "football", "post", NOW - 20 * 3600, GB("13"), LAR("26", True), "Final", ["Netflix"]),
    EV("401810001", "nba", "basketball", "in", NOW - 1800, T("LAL", "Lakers", "Los Angeles Lakers", "13", "552583", "88"),
       T("BOS", "Celtics", "Boston Celtics", "2", "007a33", "91"), "4º cuarto 2:11", ["ESPN"]),
    EV("401810002", "nba", "basketball", "pre", NOW + 26 * 3600, T("GSW", "Warriors", "Golden State Warriors", "9", "1d428a"),
       T("MIA", "Heat", "Miami Heat", "14", "98002e"), "", ["TNT"],
       odds={"fav": "a", "mlh": "+140", "mla": "-165", "mld": "", "spread": "GSW -3.5", "ou": 228.5, "by": "Draft Kings"}),
    EV("760001", "ligamx", "soccer", "post", NOW - 8 * 3600, T("AME", "América", "Club América", "227", "fff200", "2", "", True),
       T("TOL", "Toluca", "Toluca", "223", "d50032", "1"), "Final", ["TUDN"], note="Liguilla · Cuartos de final"),
    EV("760002", "epl", "soccer", "pre", NOW + 5 * 3600, T("ARS", "Arsenal", "Arsenal", "359", "ef0107"),
       T("MCI", "Man City", "Manchester City", "382", "6cabdd"), "", ["Peacock"],
       odds={"fav": "h", "mlh": "+115", "mla": "+230", "mld": "+250", "spread": "", "ou": 2.5, "by": "Draft Kings"}),
    EV("2244001", "lmp", "baseball", "pre", NOW + 4 * 3600, T("MXL", "Águilas", "Águilas de Mexicali", "135220", ""),
       T("HER", "Naranjeros", "Naranjeros de Hermosillo", "135221", ""), "", ["Sky Sports"]),
    EV("401700001", "nhl", "hockey", "in", NOW - 2400, T("TOR", "Maple Leafs", "Toronto Maple Leafs", "21", "00205b", "2"),
       T("MTL", "Canadiens", "Montreal Canadiens", "10", "af1e2d", "2"), "2º periodo 8:40", ["ESPN+"]),
    EV("600050001", "ufc", "mma", "pre", NOW + 50 * 3600, T("PER", "Pereira", "Alex Pereira", "1", ""),
       T("ANK", "Ankalaev", "Magomed Ankalaev", "2", ""), "", ["ESPN+"], name="UFC 323: Pereira vs Ankalaev"),
]

GAME_LIVE = {
    "lg": "nfl", "id": "401873081", "st": "in", "det": "3er cuarto 4:30", "start": iso(NOW - 3600), "tv": ["CBS"],
    "venue": "Ford Field", "city": "Detroit, MI", "att": 65000, "temp": None, "rain": None,
    "teams": [
        {"id": "3", "ab": "CHI", "n": "Bears", "full": "Chicago Bears", "logo": f"{B}/logo/CHI.png", "col": "0b1c3a", "home": False, "ls": ["7", "0", "7"], "sc": "14", "rec": "8-2", "win": False},
        {"id": "8", "ab": "DET", "n": "Lions", "full": "Detroit Lions", "logo": f"{B}/logo/DET.png", "col": "0076b6", "home": True, "ls": ["3", "7", "7"], "sc": "17", "rec": "8-2", "win": False},
    ],
    "wp": [50, 48, 55, 60, 52, 45, 40, 47, 58, 63, 66, 61, 57, 62, 64],
    "pred": None,
    "stats": [
        {"l": "Yardas totales", "a": "244", "h": "301", "an": 244, "hn": 301, "low": False},
        {"l": "Yardas por aire", "a": "180", "h": "150", "an": 180, "hn": 150, "low": False},
        {"l": "Primeros intentos", "a": "14", "h": "17", "an": 14, "hn": 17, "low": False},
        {"l": "Tercera oportunidad", "a": "4-9", "h": "6-10", "an": 4, "hn": 6, "low": False},
        {"l": "Pérdidas de balón", "a": "2", "h": "0", "an": 2, "hn": 0, "low": True},
        {"l": "Tiempo de posesión", "a": "19:02", "h": "21:28", "an": 1142, "hn": 1288, "low": False},
    ],
    "players": [{"team": "3", "groups": [{"g": "Pases", "cols": ["C/I", "YDS", "TD", "INT", "RAT"], "rows": [
        {"n": "C. Williams", "pos": "QB", "num": "18", "st": True, "v": ["17/26", "198", "1", "1", "88.4"]}]}]}],
    "leaders": [{"team": "8", "items": [{"cat": "Pases", "n": "Jared Goff", "v": "20/27, 230 YDS, 2 TD", "head": "", "pos": "QB"}]},
                {"team": "3", "items": [{"cat": "Carreras", "n": "D. Swift", "v": "12 CAR, 66 YDS", "head": "", "pos": "RB"}]}],
    "plays": [
        {"t": "C.Williams pass short right to D.Moore to DET 34 for 9 yards (B.Branch).", "clk": "4:30", "per": 3, "team": "3", "sc": False, "score": "14-17"},
        {"t": "J.Goff pass deep middle to A.St. Brown for 41 yards, TOUCHDOWN.", "clk": "6:12", "per": 3, "team": "8", "sc": True, "score": "14-17"},
        {"t": "D.Swift up the middle to CHI 40 for 3 yards.", "clk": "7:01", "per": 3, "team": "3", "sc": False, "score": "14-10"},
    ],
    "keyPlays": [
        {"t": "Jake Bates 48 Yd Field Goal", "clk": "9:55", "per": 1, "team": "8", "sc": True, "score": "0-3", "kind": ""},
        {"t": "Caleb Williams 12 Yd pass to DJ Moore (Cairo Santos Kick)", "clk": "2:10", "per": 1, "team": "3", "sc": True, "score": "7-3", "kind": ""},
        {"t": "Amon-Ra St. Brown 41 Yd pass from Jared Goff (Jake Bates Kick)", "clk": "6:12", "per": 3, "team": "8", "sc": True, "score": "14-17", "kind": ""},
    ],
    "drive": "8 plays, 52 yards, 3:41",
    "odds": {"by": "Draft Kings", "details": "DET -3.5", "favH": True, "favA": False, "favOpenH": True, "favOpenA": False,
             "mlH": {"open": "-180", "now": "-210"}, "mlA": {"open": "+150", "now": "+175"}, "mlD": "",
             "spH": {"open": "-3", "now": "-3.5", "oddsNow": "-110"}, "spA": {"open": "+3", "now": "+3.5", "oddsNow": "-110"},
             "ou": {"open": "47.5", "now": "48.5", "over": "-110", "under": "-110"}},
    "ats": [{"team": "8", "recs": [{"t": "overall", "s": "7-3-0"}, {"t": "home", "s": "4-1-0"}]}],
    "last5": [], "h2h": [], "lineups": [],
    "inj": [{"team": "8", "list": [{"n": "Aidan Hutchinson", "pos": "DE", "st": "Fuera", "d": "Leg"}, {"n": "Sam LaPorta", "pos": "TE", "st": "En duda", "d": "Back"}]}],
}

GAME_PRE = {
    "lg": "nfl", "id": "401873080", "st": "pre", "det": "", "start": iso(NOW + 2 * 3600 + 900), "tv": ["Fox"],
    "venue": "AT&T Stadium", "city": "Arlington, TX", "att": 0, "temp": 68, "rain": 10,
    "teams": [
        {"id": "21", "ab": "PHI", "n": "Eagles", "full": "Philadelphia Eagles", "logo": f"{B}/logo/PHI.png", "col": "06424d", "home": False, "ls": [], "sc": "", "rec": "3-7"},
        {"id": "6", "ab": "DAL", "n": "Cowboys", "full": "Dallas Cowboys", "logo": f"{B}/logo/DAL.png", "col": "002a5c", "home": True, "ls": [], "sc": "", "rec": "5-6"},
    ],
    "wp": [], "pred": {"h": 71.2, "a": 28.8},
    "stats": [{"l": "Puntos por partido", "a": "18.4", "h": "24.1", "an": 18.4, "hn": 24.1, "low": False},
              {"l": "Puntos permitidos por partido", "a": "25.0", "h": "21.3", "an": 25.0, "hn": 21.3, "low": True}],
    "players": [], "leaders": [], "plays": [], "keyPlays": [], "drive": "",
    "odds": {"by": "Draft Kings", "details": "DAL -8.5", "favH": True, "favA": False, "favOpenH": False, "favOpenA": True,
             "mlH": {"open": "+110", "now": "-455"}, "mlA": {"open": "-130", "now": "+354"}, "mlD": "",
             "spH": {"open": "+1.5", "now": "-8.5", "oddsNow": "-110"}, "spA": {"open": "-1.5", "now": "+8.5", "oddsNow": "-110"},
             "ou": {"open": "51.5", "now": "49.5", "over": "-112", "under": "-108"}},
    "ats": [],
    "last5": [{"team": "21", "ev": [{"r": "P", "s": "17-24", "o": "NYG", "logo": "", "at": "vs", "d": ""}, {"r": "G", "s": "27-20", "o": "WSH", "logo": "", "at": "en", "d": ""}]},
              {"team": "6", "ev": [{"r": "G", "s": "31-10", "o": "NYG", "logo": "", "at": "vs", "d": ""}]}],
    "h2h": [{"d": "2026-09-08T00:20Z", "h": "PHI", "a": "DAL", "hs": "24", "as": "20", "w": "h"}],
    "lineups": [], "inj": [{"team": "21", "list": [{"n": "A.J. Brown", "pos": "WR", "st": "En duda", "d": "Hamstring"}]}],
}

GAME_SOCCER = {
    "lg": "ligamx", "id": "760001", "st": "post", "det": "Final", "start": iso(NOW - 8 * 3600), "tv": ["TUDN"],
    "venue": "Estadio Azteca", "city": "Ciudad de México", "att": 70000, "temp": None, "rain": None,
    "teams": [
        {"id": "223", "ab": "TOL", "n": "Toluca", "full": "Toluca", "logo": f"{B}/logo/TOL.png", "col": "d50032", "home": False, "ls": ["1", "0"], "sc": "1", "rec": ""},
        {"id": "227", "ab": "AME", "n": "América", "full": "Club América", "logo": f"{B}/logo/AME.png", "col": "fff200", "home": True, "ls": ["1", "1"], "sc": "2", "rec": "", "win": True},
    ],
    "wp": [], "pred": None,
    "stats": [{"l": "Posesión", "a": "45%", "h": "55%", "an": 45, "hn": 55, "low": False},
              {"l": "Tiros a gol", "a": "3", "h": "6", "an": 3, "hn": 6, "low": False},
              {"l": "Tarjetas amarillas", "a": "3", "h": "1", "an": 3, "hn": 1, "low": True}],
    "players": [], "leaders": [],
    "plays": [{"t": "¡Gol! América 2, Toluca 1. Henry Martín remata de cabeza.", "clk": "78'", "per": 2, "team": "227", "sc": True, "score": "1-2"}],
    "keyPlays": [{"t": "Gol de Henry Martín", "clk": "78'", "per": 2, "team": "227", "sc": True, "score": "1-2", "kind": ""},
                 {"t": "Tarjeta amarilla a Marcel Ruiz", "clk": "60'", "per": 2, "team": "223", "sc": True, "score": "", "kind": "amarilla"}],
    "drive": "", "odds": None, "ats": [], "last5": [], "h2h": [], "inj": [],
    "lineups": [{"team": "227", "formation": "4-2-3-1", "starters": [{"n": "Luis Malagón", "num": "1", "pos": "G", "ev": []}, {"n": "Henry Martín", "num": "21", "pos": "F", "ev": ["gol"]}]},
                {"team": "223", "formation": "4-3-3", "starters": [{"n": "Marcel Ruiz", "num": "8", "pos": "M", "ev": ["amarilla", "cambio"]}]}],
}

TEAM_CHI = {
    "lg": "nfl", "team": {"id": "3", "n": "Bears", "full": "Chicago Bears", "ab": "CHI", "logo": f"{B}/logo/CHI.png", "col": "0b1c3a",
                         "rec": "8-2", "home": "4-1", "away": "4-1", "standing": "1º en NFC North"},
    "past": [{"id": "401870001", "start": iso(NOW - 7 * 86400), "st": "post", "home": True, "opp": GB(), "us": "24", "them": "17", "res": "G", "tv": ["FOX"]},
             {"id": "401870002", "start": iso(NOW - 14 * 86400), "st": "post", "home": False, "opp": LAR(), "us": "20", "them": "27", "res": "P", "tv": ["CBS"]}],
    "next": [{"id": "401873081", "start": iso(NOW - 3600), "st": "in", "home": False, "opp": DET(), "us": "", "them": "", "res": "", "tv": ["CBS"]},
             {"id": "401880001", "start": iso(NOW + 7 * 86400), "st": "pre", "home": True, "opp": PHI(), "us": "", "them": "", "res": "", "tv": ["NBC"]}],
    "streak": "1G",
    "inj": [{"n": "Jaylon Johnson", "pos": "CB", "st": "Fuera", "d": "Groin", "ret": ""}],
    "leaders": [{"cat": "Pases", "n": "Caleb Williams", "v": "2,450 YDS"}],
    "news": [{"t": "Los Bears buscan su noveno triunfo en Detroit", "d": "Chicago llega con racha positiva al juego de Acción de Gracias.", "img": "", "at": iso(NOW - 86400)}],
}

TABLE_NFL = {"lg": "nfl", "cols": ["G", "P", "%", "Dif"], "groups": [
    {"n": "NFC North", "rows": [
        {"id": "3", "n": "Bears", "ab": "CHI", "logo": f"{B}/logo/CHI.png", "seed": 1, "v": ["8", "2", ".800", "-"]},
        {"id": "8", "n": "Lions", "ab": "DET", "logo": f"{B}/logo/DET.png", "seed": 2, "v": ["8", "2", ".800", "-"]},
        {"id": "9", "n": "Packers", "ab": "GB", "logo": f"{B}/logo/GB.png", "seed": 7, "v": ["6", "5", ".545", "2.5"]}]},
    {"n": "NFC East", "rows": [
        {"id": "6", "n": "Cowboys", "ab": "DAL", "logo": f"{B}/logo/DAL.png", "seed": 4, "v": ["5", "6", ".455", "-"]},
        {"id": "21", "n": "Eagles", "ab": "PHI", "logo": f"{B}/logo/PHI.png", "seed": 0, "v": ["3", "7", ".300", "1.5"]}]}]}

TEAMS_NFL = {"lg": "nfl", "teams": [{k: v for k, v in t.items() if k in ("id", "n", "full", "ab", "logo", "col")} for t in [CHI(), DAL(), DET(), GB(), LAR(), PHI()]]}

CATS = [{"category_id": "1", "category_name": "Noticias"}, {"category_id": "2", "category_name": "Deportes MX"},
        {"category_id": "3", "category_name": "USA Sports"}, {"category_id": "4", "category_name": "NFL Sunday Ticket PPV"},
        {"category_id": "5", "category_name": "Infantiles"}]
CHANS = [("101", "Milenio TV", "1"), ("102", "TUDN HD", "2"), ("103", "ESPN HD", "2"), ("104", "Fox Sports 1", "3"),
         ("105", "CBS Sports HD", "3"), ("106", "FOX HD Dallas", "3"), ("107", "NFL Bears vs Lions", "4"),
         ("108", "NFL Event 2", "4"), ("109", "Discovery Kids", "5"), ("110", "Sky Sports Mexico", "2")]
STREAMS = [{"num": i + 1, "name": n, "stream_id": int(sid), "stream_icon": f"{B}/ch/{sid}.png", "epg_channel_id": f"ch{sid}" if cat in ("2", "3") else "",
            "category_id": cat} for i, (sid, n, cat) in enumerate(CHANS)]

PROFILES = [
    {"scope": "_", "kind": "profile", "key": "p1", "deleted": False, "data": {"name": "Mauricio", "kids": False}},
    {"scope": "_", "kind": "profile", "key": "p2", "deleted": False, "data": {"name": "Niños", "kids": True}},
    {"scope": "_", "kind": "pick", "key": "nfl:401873079|p1", "deleted": False, "data": {"ev": "nfl:401873079", "pr": "p1", "p": "h", "t": "Packers vs Rams", "s": (NOW - 20 * 3600) * 1000, "r": ""}},
    {"scope": "_", "kind": "pick", "key": "nfl:401873079|p2", "deleted": False, "data": {"ev": "nfl:401873079", "pr": "p2", "p": "a", "t": "Packers vs Rams", "s": (NOW - 20 * 3600) * 1000, "r": "h"}},
]
LIB = [{"scope": "p1", "kind": "team", "key": "nfl:3", "deleted": False, "data": {"lg": "nfl", "id": "3", "n": "Bears", "full": "Chicago Bears", "ab": "CHI", "logo": f"{B}/logo/CHI.png", "col": "0b1c3a"}}]
PUTS = []
STATE = {"board": 0}


def logo_png(text, seed):
    h = hashlib.md5(seed.encode()).digest()
    im = Image.new("RGBA", (200, 200), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.ellipse((10, 10, 190, 190), fill=(h[0], h[1] // 2 + 60, h[2], 255))
    try:
        f = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", 54)
    except Exception:
        f = ImageFont.load_default()
    w = d.textlength(text, font=f)
    d.text(((200 - w) / 2, 70), text, fill="white", font=f)
    b = io.BytesIO()
    im.save(b, "PNG")
    return b.getvalue()


def b64(s):
    return base64.b64encode(s.encode()).decode()


class H(BaseHTTPRequestHandler):
    def log_message(self, *a):
        pass

    def send(self, obj, code=200, ctype="application/json"):
        body = obj if isinstance(obj, bytes) else json.dumps(obj).encode()
        self.send_response(code)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        u = urlparse(self.path)
        q = {k: v[0] for k, v in parse_qs(u.query).items()}
        LOG.write("GET " + self.path + "\n"); LOG.flush()
        if u.path.startswith("/logo/") or u.path.startswith("/ch/"):
            name = u.path.split("/")[-1].replace(".png", "")
            return self.send(logo_png(name[:4], u.path), ctype="image/png")
        if u.path == "/functions/v1/deportes":
            qq = q.get("q")
            if qq == "board":
                STATE["board"] += 1
                evs = json.loads(json.dumps(EVENTS))
                # en la tercera carga los Bears anotan (para probar el aviso)
                if STATE["board"] >= 3:
                    evs[0]["a"]["sc"] = "21"
                    evs[0]["det"] = "3er cuarto 1:02"
                return self.send({"at": NOW * 1000, "live": True, "events": evs})
            if qq == "game":
                g = {"401873081": GAME_LIVE, "401873080": GAME_PRE, "760001": GAME_SOCCER}.get(q.get("id"))
                if g is None:
                    g = dict(GAME_LIVE, id=q.get("id"), st="post", det="Final")
                return self.send(g)
            if qq == "team":
                return self.send(TEAM_CHI)
            if qq == "table":
                return self.send(TABLE_NFL)
            if qq == "teams":
                return self.send(TEAMS_NFL)
            return self.send({"error": "q"}, 400)
        if u.path == "/translate":
            lines = q.get("q", "").split("\n")
            out = "\n".join("ES: " + l for l in lines)
            return self.send([[[out, q.get("q", ""), None, None]], None, "en"])
        if u.path == "/player_api.php":
            a = q.get("action", "")
            if a == "":
                return self.send({"user_info": {"auth": 1, "status": "Active"}, "server_info": {}})
            if a == "get_live_categories":
                return self.send(CATS)
            if a == "get_live_streams":
                c = q.get("category_id")
                return self.send([s for s in STREAMS if not c or s["category_id"] == c])
            if a == "get_vod_categories" or a == "get_series_categories":
                return self.send([{"category_id": "9", "category_name": "Estrenos"}])
            if a == "get_vod_streams":
                return self.send([{"name": f"Película {i}", "stream_id": 500 + i, "stream_icon": f"{B}/ch/P{i}.png", "added": str(NOW - i * 86400 * 2), "container_extension": "mp4", "year": "2026"} for i in range(1, 9)])
            if a == "get_series":
                return self.send([{"name": f"Serie {i}", "series_id": 700 + i, "cover": f"{B}/ch/S{i}.png", "last_modified": str(NOW - i * 86400 * 3), "genre": "Drama"} for i in range(1, 6)])
            if a == "get_short_epg":
                sid = q.get("stream_id")
                lst = []
                if sid == "104":
                    lst = [{"title": b64("NFL: Eagles at Cowboys"), "description": b64("Thanksgiving Day"), "start_timestamp": str(NOW + 2 * 3600), "stop_timestamp": str(NOW + 6 * 3600)}]
                else:
                    lst = [{"title": b64("Noticiero"), "description": b64(""), "start_timestamp": str(NOW), "stop_timestamp": str(NOW + 3600)}]
                return self.send({"epg_listings": lst})
            return self.send([])
        self.send({"error": "404"}, 404)

    def do_POST(self):
        u = urlparse(self.path)
        n = int(self.headers.get("Content-Length", 0))
        body = json.loads(self.rfile.read(n) or b"{}")
        fn = u.path.split("/")[-1]
        LOG.write("POST " + fn + " " + json.dumps(body)[:300] + "\n"); LOG.flush()
        if fn == "cc_lic_activate2":
            return self.send("ok")
        if fn == "cc_lic_ping":
            return self.send({"status": "ok"})
        if fn in ("cc_lic_server", "cc_owner_server"):
            return self.send({})
        if fn == "cc_is_owner":
            return self.send(False)
        if fn == "cc_get":
            sc = body.get("p_scopes", [])
            if "_" in sc:
                return self.send(PROFILES)
            return self.send(LIB if "p1" in sc else [])
        if fn == "cc_put":
            PUTS.extend(body.get("p_items", []))
            for it in body.get("p_items", []):
                LOG.write("PUT " + json.dumps(it) + "\n"); LOG.flush()
            return self.send(True)
        if fn in ("cc_pair_new2", "cc_pair_new"):
            return self.send("123456")
        return self.send(None)


if __name__ == "__main__":
    ThreadingHTTPServer(("127.0.0.1", 8099), H).serve_forever()
