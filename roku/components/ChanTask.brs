' Canales de deportes del proveedor: baja una vez la lista de canales en vivo y, sin trabar
' la pantalla, contesta en qué canal pasa cada partido (por nombre y por la guía).
'   cmd = { op: "rows", events }      -> canales de pago por evento y categorías de deportes
'   cmd = { op: "chans", ev }         -> canales que probablemente pasan ese partido
'   cmd = { op: "scan", ev }          -> lo busca en la guía de los canales de deportes
sub init()
    m.top.functionName = "chanMain"
end sub

function chGet(url as string) as dynamic
    x = CreateObject("roUrlTransfer")
    x.SetUrl(url)
    x.SetCertificatesFile("common:/certs/ca-bundle.crt")
    x.InitClientCertificates()
    x.EnableEncodings(true)
    x.AddHeader("User-Agent", "IPTVSmartersPro")
    t = x.GetToString()
    if t = "" then return invalid
    return ParseJson(t)
end function

sub chanMain()
    port = CreateObject("roMessagePort")
    m.top.observeField("cmd", port)
    m.top.status = "loading"
    m.epgHits = {}
    loadCatalog()
    m.top.status = "ready"
    while true
        msg = wait(0, port)
        if type(msg) = "roSGNodeEvent" then
            c = msg.getData()
            if c <> invalid then handleCmd(c)
        end if
    end while
end sub

sub loadCatalog()
    b = m.top.base
    sportRe = spSportRe()
    ppvRe = spPpvRe()
    adultRe = spAdultRe()
    m.cats = []
    m.catBy = {}
    cats = chGet(b + "&action=get_live_categories")
    if type(cats) = "roArray" then
        for each c in cats
            id = spS(c.category_id)
            nm = spS(c.category_name)
            sport = spIsSportCat(nm, sportRe, adultRe)
            o = { id: id, name: nm, sport: sport, ppv: sport and ppvRe.IsMatch(spNorm(nm)) }
            m.cats.Push(o)
            m.catBy[id] = o
        end for
    end if
    m.ch = []
    m.chBy = {}
    streams = chGet(b + "&action=get_live_streams")
    if type(streams) = "roArray" then
        for each s in streams
            nm = spS(s.name)
            if nm <> "" then
                cat = spS(s.category_id)
                co = m.catBy[cat]
                o = { id: spS(s.stream_id), t: nm, cat: cat, img: spS(s.stream_icon), epg: spS(s.epg_channel_id), n: spNorm(nm), sport: co <> invalid and co.sport }
                m.ch.Push(o)
                m.chBy[o.id] = o
            end if
        end for
    end if
end sub

function outItem(c as object) as object
    return { t: c.t, xid: c.id, img: c.img, cat: c.cat }
end function

sub handleCmd(c as object)
    op = spS(c.op)
    if op = "rows" then
        doRows(c.events)
    else if op = "chans" then
        e = c.ev
        m.top.out = { op: "chans", key: spS(e.key), list: channelsFor(e) }
    else if op = "scan" then
        e = c.ev
        scanEpg(e)
        m.top.out = { op: "scan", key: spS(e.key), list: channelsFor(e) }
    end if
end sub

' Filas de canales para la pantalla de Deportes (con el partido que pasa en cada canal)
sub doRows(events as dynamic)
    ppv = []
    rows = []
    rowBy = {}
    for each c in m.cats
        if c.sport and not c.ppv then
            r = { id: c.id, name: c.name, chans: [] }
            rows.Push(r)
            rowBy[c.id] = r
        end if
    end for
    for each ch in m.ch
        co = m.catBy[ch.cat]
        if co <> invalid and co.sport then
            it = invalid
            if co.ppv then
                if ppv.Count() < 60 then it = outItem(ch) : ppv.Push(it)
            else
                r = rowBy[ch.cat]
                if r <> invalid and r.chans.Count() < 50 then it = outItem(ch) : r.chans.Push(it)
            end if
            if it <> invalid and events <> invalid then
                e = spEventForName(ch.n, events)
                if e <> invalid then it.ev = spS(e.key)
            end if
        end if
    end for
    out = []
    for each r in rows
        if r.chans.Count() > 0 then out.Push(r)
    end for
    m.top.out = { op: "rows", ppv: ppv, cats: out, total: m.ch.Count() }
end sub

function tvAliases() as object
    return {
        "espn": ["espn"], "espn2": ["espn 2", "espn2"], "abc": ["abc"], "cbs": ["cbs"], "nbc": ["nbc"], "fox": ["fox"],
        "fs1": ["fs1", "fox sports 1"], "fs2": ["fs2", "fox sports 2"], "tnt": ["tnt"], "tbs": ["tbs"],
        "nfl net": ["nfl network", "nfl net"], "nba tv": ["nba tv"], "mlb net": ["mlb network", "mlb net"],
        "nhl net": ["nhl network"], "peacock": ["peacock"], "prime video": ["prime video", "amazon prime"],
        "netflix": ["netflix"], "espn+": ["espn plus", "espn"], "tudn": ["tudn"], "univision": ["univision"],
        "telemundo": ["telemundo"], "unimas": ["unimas"]
    }
end function

' Canales que probablemente pasan el partido, del más probable al menos (máximo 12)
function channelsFor(e as object) as object
    hw = spWords(e.h)
    aw = spWords(e.a)
    key = spS(e.key)
    scored = {}
    for each ch in m.ch
        sh = spAny(hw, ch.n)
        sa = spAny(aw, ch.n)
        s = 0
        if sh and sa then
            s = 100
        else if (sh or sa) and ch.sport then
            s = 60
        end if
        if s > 0 then addScore(scored, ch.id, s)
    end for
    hits = m.epgHits[key]
    if hits <> invalid then
        for each id in hits
            addScore(scored, id, 90)
        end for
    end if
    ' Cadenas de TV (CBS, FOX, ESPN…): dentro de deportes valen más
    nets = []
    al = tvAliases()
    if type(e.tv) = "roArray" then
        for each tv in e.tv
            k = LCase(spS(tv)).Trim()
            found = invalid
            for each a in al
                if k = a or Left(k, Len(a)) = a then found = al[a] : exit for
            end for
            if found <> invalid then
                for each w in found
                    spAddOnce(nets, w)
                end for
            else
                w = spNorm(k)
                if Len(w) >= 3 then spAddOnce(nets, w)
            end if
        end for
    end if
    if nets.Count() > 0 then
        res = []
        for each w in nets
            res.Push(CreateObject("roRegex", "(^|[^a-z0-9])" + w + "([^a-z0-9+]|$)", ""))
        end for
        for each ch in m.ch
            if not scored.DoesExist(ch.id) then
                n = " " + ch.n + " "
                for each r in res
                    if r.IsMatch(n) then
                        if ch.sport then addScore(scored, ch.id, 40) else addScore(scored, ch.id, 25)
                        exit for
                    end if
                end for
            end if
        end for
    end if
    arr = []
    for each id in scored
        arr.Push({ id: id, s: scored[id] })
    end for
    arr.SortBy("s", "r")
    out = []
    for each a in arr
        c = m.chBy[a.id]
        if c <> invalid then out.Push(outItem(c))
        if out.Count() >= 12 then exit for
    end for
    return out
end function

sub addScore(scored as object, id as string, s as integer)
    if not scored.DoesExist(id) or scored[id] < s then scored[id] = s
end sub

' Busca el partido en la guía de los canales de deportes (hasta 80 canales, 6 a la vez)
sub scanEpg(e as object)
    key = spS(e.key)
    if m.epgHits[key] <> invalid then return
    hw = spWords(e.h)
    aw = spWords(e.a)
    st = spN(e.t)
    dur = spDuration(spS(e.sp), spS(e.lg))
    pool = []
    for each ch in m.ch
        if ch.sport and ch.epg <> "" then pool.Push(ch)
        if pool.Count() >= 80 then exit for
    end for
    hits = []
    port = CreateObject("roMessagePort")
    busy = {}
    i = 0
    b = m.top.base
    while i < pool.Count() or busy.Count() > 0
        while i < pool.Count() and busy.Count() < 6
            ch = pool[i]
            i = i + 1
            x = CreateObject("roUrlTransfer")
            x.SetMessagePort(port)
            x.SetUrl(b + "&action=get_short_epg&limit=12&stream_id=" + ch.id)
            x.SetCertificatesFile("common:/certs/ca-bundle.crt")
            x.InitClientCertificates()
            x.EnableEncodings(true)
            x.AddHeader("User-Agent", "IPTVSmartersPro")
            if x.AsyncGetToString() then busy[Str(x.GetIdentity()).Trim()] = { x: x, ch: ch }
        end while
        msg = wait(15000, port)
        if type(msg) = "roUrlEvent" then
            k = Str(msg.GetSourceIdentity()).Trim()
            job = busy[k]
            busy.Delete(k)
            if job <> invalid and msg.GetResponseCode() = 200 then
                if epgHit(msg.GetString(), hw, aw, st, dur) then hits.Push(job.ch.id)
            end if
        else if msg = invalid then
            exit while
        end if
    end while
    m.epgHits[key] = hits
end sub

function epgHit(body as string, hw as object, aw as object, st as integer, dur as integer) as boolean
    d = ParseJson(body)
    if d = invalid or type(d.epg_listings) <> "roArray" then return false
    for each p in d.epg_listings
        a = Int(spN(p.start_timestamp))
        z = Int(spN(p.stop_timestamp))
        if z > st - 1800 and a < st + dur then
            t = spNorm(b64(spS(p.title)) + " " + b64(spS(p.description)))
            if spAny(hw, t) and spAny(aw, t) then return true
        end if
    end for
    return false
end function

function b64(s as string) as string
    if s = "" then return ""
    ba = CreateObject("roByteArray")
    ba.FromBase64String(s)
    out = ba.ToAsciiString()
    if out = "" then return s
    return out
end function
