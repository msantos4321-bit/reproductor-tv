' Herramientas de Deportes que usan la pantalla y el hilo de canales.

function spLeagues() as object
    return [
        { k: "lmp", short: "LMP", name: "Liga Mexicana del Pacífico", sp: "baseball" },
        { k: "ligamx", short: "Liga MX", name: "Liga MX", sp: "soccer" },
        { k: "nfl", short: "NFL", name: "NFL", sp: "football" },
        { k: "nba", short: "NBA", name: "NBA", sp: "basketball" },
        { k: "mlb", short: "MLB", name: "MLB", sp: "baseball" },
        { k: "nhl", short: "NHL", name: "NHL", sp: "hockey" },
        { k: "ucl", short: "Champions", name: "Champions League", sp: "soccer" },
        { k: "epl", short: "Premier", name: "Premier League", sp: "soccer" },
        { k: "laliga", short: "LaLiga", name: "LaLiga", sp: "soccer" },
        { k: "mls", short: "MLS", name: "MLS", sp: "soccer" },
        { k: "ncaaf", short: "NCAA", name: "Futbol americano colegial", sp: "football" },
        { k: "ufc", short: "UFC", name: "UFC", sp: "mma" },
    ]
end function

' Convierte cualquier valor a texto (los números llegan como número del JSON)
function spS(v as dynamic) as string
    if v = invalid then return ""
    t = type(v)
    if t = "roString" or t = "String" then return v
    if t = "roInt" or t = "Integer" or t = "LongInteger" or t = "roLongInteger" then return Str(v).Trim()
    if t = "roFloat" or t = "Float" or t = "Double" or t = "roDouble" then
        if v = Int(v) then return Str(Int(v)).Trim()
        return Str(v).Trim()
    end if
    if t = "roBoolean" or t = "Boolean" then
        if v then return "1"
        return "0"
    end if
    return ""
end function

function spN(v as dynamic) as float
    t = type(v)
    if t = "roString" or t = "String" then return Val(v)
    if v = invalid or t = "roBoolean" or t = "Boolean" or t = "roAssociativeArray" or t = "roArray" then return 0
    return v
end function

' Minúsculas, sin acentos y solo letras y números separados por un espacio
function spNorm(s as string) as string
    if s = "" then return ""
    s = LCase(s)
    pairs = [["á", "a"], ["à", "a"], ["ä", "a"], ["â", "a"], ["ã", "a"], ["é", "e"], ["è", "e"], ["ë", "e"], ["ê", "e"], ["í", "i"], ["ì", "i"], ["ï", "i"], ["î", "i"], ["ó", "o"], ["ò", "o"], ["ö", "o"], ["ô", "o"], ["õ", "o"], ["ú", "u"], ["ù", "u"], ["ü", "u"], ["û", "u"], ["ñ", "n"], ["ç", "c"], ["Á", "a"], ["É", "e"], ["Í", "i"], ["Ó", "o"], ["Ú", "u"], ["Ñ", "n"], ["Ü", "u"]]
    for each p in pairs
        s = s.Replace(p[0], p[1])
    end for
    if m.spNormRe = invalid then m.spNormRe = CreateObject("roRegex", "[^a-z0-9]+", "")
    return m.spNormRe.ReplaceAll(s, " ").Trim()
end function

' Palabras para reconocer a un equipo en el nombre de un canal o en la guía
' («Chicago Bears» -> «bears»; «Águilas de Mexicali» -> «mexicali»)
function spWords(t as dynamic) as object
    out = []
    if t = invalid then return out
    n = spNorm(spS(t.n))
    f = spNorm(spS(t.full))
    if Len(n) >= 4 then out.Push(n)
    if Len(f) >= 4 and f <> n then out.Push(f)
    parts = f.Split(" de ")
    if parts.Count() > 1 then
        lastDe = parts[parts.Count() - 1]
        if Len(lastDe) >= 5 and lastDe <> f then spAddOnce(out, lastDe)
    end if
    ws = f.Split(" ")
    if ws.Count() > 0 then
        lastW = ws[ws.Count() - 1]
        if Len(lastW) >= 5 then spAddOnce(out, lastW)
    end if
    return out
end function

sub spAddOnce(arr as object, s as string)
    for each x in arr
        if x = s then return
    end for
    arr.Push(s)
end sub

function spAny(words as object, n as string) as boolean
    for each w in words
        if Instr(1, n, w) > 0 then return true
    end for
    return false
end function

' Categorías del proveedor que son de deportes (y no de adultos)
function spSportRe() as object
    return CreateObject("roRegex", "(deport|sport|ppv|pay ?per ?view|evento|event|nfl|nba|mlb|nhl|ufc|mma|box|futbol|football|soccer|liga|league|espn|tudn|dazn|bein|golf|tenis|tennis|f1|formula|motor|nascar|wwe|lucha|beisbol|baseball|basket|hockey|ncaa|afizzionados|claro sports|fox ?sports|sky sports|tsn|willow|cricket|rugby|olymp)", "i")
end function

function spPpvRe() as object
    return CreateObject("roRegex", "(ppv|pay ?per ?view|evento|event|replay|nfl ?sunday|game ?pass|league ?pass|mlb ?tv|nba ?tv|nhl ?tv|espn ?\+|dazn|ufc ?fight|fight ?pass|peacock|flo ?sports)", "i")
end function

function spAdultRe() as object
    return CreateObject("roRegex", "(adult|xxx|\+18|18\+|porn|para adultos|adultos|hot|erotic|erotic)", "i")
end function

function spIsSportCat(name as string, sportRe as object, adultRe as object) as boolean
    f = spNorm(name)
    return sportRe.IsMatch(f) and not adultRe.IsMatch(name)
end function

' «2026-11-27T01:15Z» -> segundos
function spIso(v as dynamic) as integer
    s = spS(v)
    if s = "" then return 0
    if Len(s) = 17 and Right(s, 1) = "Z" then s = Left(s, 16) + ":00Z"
    d = CreateObject("roDateTime")
    d.FromISO8601String(s)
    return d.AsSeconds()
end function

function spNowSec() as integer
    return CreateObject("roDateTime").AsSeconds()
end function

function spLocal(t as integer) as object
    d = CreateObject("roDateTime")
    d.FromSeconds(t)
    d.ToLocalTime()
    return d
end function

function spDayKey(t as integer) as integer
    d = spLocal(t)
    return d.GetYear() * 10000 + d.GetMonth() * 100 + d.GetDayOfMonth()
end function

function spHHMM(t as integer) as string
    d = spLocal(t)
    hh = d.GetHours()
    mi = d.GetMinutes()
    return Right("0" + Str(hh).Trim(), 2) + ":" + Right("0" + Str(mi).Trim(), 2)
end function

' «Hoy 19:30», «Mañana 13:00», «jue 27 17:00»
function spWhen(t as integer) as string
    if t <= 0 then return ""
    now = spNowSec()
    k = spDayKey(t)
    dayTxt = ""
    if k = spDayKey(now) then
        dayTxt = "Hoy"
    else if k = spDayKey(now + 86400) then
        dayTxt = "Mañana"
    else if k = spDayKey(now - 86400) then
        dayTxt = "Ayer"
    else
        d = spLocal(t)
        names = ["dom", "lun", "mar", "mié", "jue", "vie", "sáb"]
        dayTxt = names[d.GetDayOfWeek()] + " " + Str(d.GetDayOfMonth()).Trim()
    end if
    return dayTxt + " " + spHHMM(t)
end function

' «en 3 h 20 min», «en 15 min»
function spCountdown(t as integer) as string
    mi = Int((t - spNowSec()) / 60)
    if mi <= 0 then return "ya empieza"
    if mi < 60 then return "en " + Str(mi).Trim() + " min"
    if mi < 24 * 60 then
        out = "en " + Str(Int(mi / 60)).Trim() + " h"
        rest = mi mod 60
        if rest >= 5 then out = out + " " + Str(rest).Trim() + " min"
        return out
    end if
    return spWhen(t)
end function

' Momio americano -> probabilidad que implica (en %)
function spImplied(odds as string) as integer
    s = odds.Replace("+", "").Replace("−", "-").Trim()
    if s = "" then return -1
    o = Int(Val(s))
    if o = 0 then return -1
    if o < 0 then return Int(-o * 100 / (-o + 100))
    return Int(100 * 100 / (o + 100))
end function

' Momio con signo bonito (−150, +130)
function spPm(s as string) as string
    if s = "" then return "—"
    t = s.Replace("−", "-")
    if Left(t, 1) <> "-" and Left(t, 1) <> "+" then
        v = Val(t)
        if v > 0 and Instr(1, t, ".") = 0 then t = "+" + t
    end if
    return t
end function

function spInitials(t as dynamic) as string
    if t = invalid then return ""
    ab = spS(t.ab)
    if ab = "" then ab = Left(spS(t.n), 3)
    return UCase(Left(ab, 4))
end function

' Duración aproximada del partido (para buscarlo en la guía)
function spDuration(sp as string, lg as string) as integer
    if sp = "football" then
        if lg = "ncaaf" then return 4 * 3600
        return 3 * 3600 + 45 * 60
    end if
    if sp = "baseball" then return 3 * 3600 + 30 * 60
    if sp = "basketball" then return 2 * 3600 + 45 * 60
    if sp = "soccer" then return 2 * 3600 + 15 * 60
    if sp = "mma" then return 5 * 3600
    return 3 * 3600
end function

' Partido que pasa en un canal (los dos equipos en el nombre del canal)
function spEventForName(n as string, events as object) as dynamic
    if Len(n) < 6 or events = invalid then return invalid
    now = spNowSec()
    for each e in events
        st = spS(e.st)
        if st <> "post" or spN(e.t) > now - 6 * 3600 then
            hw = e.hw
            if hw = invalid then hw = spWords(e.h)
            aw = e.aw
            if aw = invalid then aw = spWords(e.a)
            if spAny(hw, n) and spAny(aw, n) then return e
        end if
    end for
    return invalid
end function
