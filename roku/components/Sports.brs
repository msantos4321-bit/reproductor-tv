' =====================================================================
' DEPORTES (1.4): marcadores al momento, Mis equipos, en qué canal pasa cada
' partido, detalle estilo ESPN (estadísticas, jugadas, momios), fichas de
' equipo, tablas de posiciones, quiniela de la casa y avisos de tus equipos
' mientras ves la tele. Datos: función «deportes» de la nube (ESPN y TheSportsDB).
' =====================================================================

sub spInit()
    m.spFn = "https://wnwecaapchcfhcljsvli.supabase.co/functions/v1/deportes"
    ids = ["pgSports", "spKicker", "spLgA", "spNA", "spSA", "spLgH", "spNH", "spSH", "spTitle", "spMeta1", "spMeta2", "spHint", "spFilters", "spRows", "spMsg", "pgInfo", "ipKicker", "ipMatch", "ipLgA", "ipNA", "ipRA", "ipSA", "ipMid", "ipSH", "ipNH", "ipRH", "ipLgH", "ipPlain", "ipLogo", "ipTitle", "ipSub", "ipInfo", "ipTabs", "ipList", "ipFoot", "spBanner", "bnA", "bnH", "bnTitle", "bnBody", "spMini", "mnA", "mnH", "mnTxt", "mnSub", "spTimer", "bnTimer", "miniTimer"]
    for each id in ids
        m[id] = m.top.findNode(id)
    end for
    m.sp = { events: [], byKey: {}, live: false, loaded: false, loading: false, error: "", at: 0 }
    m.spLg = spLeagues()
    m.spLgBy = {}
    for each l in m.spLg
        m.spLgBy[l.k] = l
    end for
    m.spFilter = "all"
    m.spFilterKeys = []
    m.spFilterSig = ""
    m.spSig = ""
    m.spItems = []
    m.spCache = {}
    m.spReq = {}
    m.spTickN = 0
    m.teams = {}
    m.nospoil = {}
    m.revealed = {}
    m.picks = {}
    m.kids = false
    m.ipStack = []
    m.ipCur = invalid
    m.ipRowsData = []
    m.ipTabKeys = []
    m.ipTabsSig = ""
    m.chan = invalid
    m.chanReady = false
    m.chanBase = ""
    m.chanQueue = []
    m.chanRows = invalid
    m.evChans = {}
    m.alertLast = {}
    m.alertWarned = {}
    m.miniKey = ""
    m.miniState = ""
    m.miniPending = ""
    m.trMemo = {}
    m.trAsked = {}
    m.trQueue = []
    m.tr = invalid
    m.spFilters.observeField("itemSelected", "spOnFilter")
    m.spRows.observeField("rowItemFocused", "spOnRowFocus")
    m.spRows.observeField("rowItemSelected", "spOnRowSelect")
    m.ipTabs.observeField("itemFocused", "ipOnTab")
    m.ipList.observeField("itemSelected", "ipOnSelect")
    m.spTimer.observeField("fire", "spTick")
    m.bnTimer.observeField("fire", "spBannerHide")
    m.miniTimer.observeField("fire", "spMiniHide")
    m.spTimer.control = "start"
end sub

' ---------------- ajustes ----------------
function spPref(k as string) as boolean
    return rget(k) <> "0"
end function

function spShowOdds() as boolean
    return spPref("showOdds") and not m.kids
end function

function spJoin(arr as object, sep as string) as string
    out = ""
    for each p in arr
        if p <> invalid and p <> "" then
            if out <> "" then out = out + sep
            out = out + p
        end if
    end for
    return out
end function

function spLgName(lg as string) as string
    l = m.spLgBy[lg]
    if l = invalid then return UCase(lg)
    return l.name
end function

function spLgShort(lg as string) as string
    l = m.spLgBy[lg]
    if l = invalid then return UCase(lg)
    return l.short
end function

' ---------------- tablero de marcadores ----------------
sub spLoadBoard()
    if m.sp.loading then return
    m.sp.loading = true
    req("sp:board", m.spFn + "?q=board", { parse: true })
end sub

sub spTick()
    onSports = m.home.visible and (m.pgSports.visible or m.pgInfo.visible)
    m.spTickN = m.spTickN + 1
    need = onSports
    if not need and m.video.visible and (m.miniKey <> "" or m.miniPending <> "") then need = true
    if not need and m.teams.Count() > 0 and spPref("sportAlerts") and (m.home.visible or m.video.visible) then
        ' avisos de tus equipos: cada minuto (cada 30 s si hay partidos en vivo)
        need = m.sp.live or (m.spTickN mod 2 = 0)
    end if
    if need then spLoadBoard()
    if m.pgInfo.visible and m.ipCur <> invalid and m.ipCur.kind = "game" then
        e = m.sp.byKey[m.ipCur.key]
        if e <> invalid and e.st = "in" then
            ce = spGet("game:" + m.ipCur.lg + ":" + m.ipCur.id, 20)
        end if
    end if
end sub

sub spOnResponse(id as string, code as integer, data as dynamic)
    if id = "sp:board" then
        spOnBoard(data)
        return
    end if
    k = Mid(id, 4)
    m.spReq.Delete(k)
    if type(data) = "roAssociativeArray" and data.error = invalid then
        m.spCache[k] = { at: nowSec(), v: data }
    else if m.spCache[k] = invalid or m.spCache[k].v = invalid then
        m.spCache[k] = { at: nowSec(), v: invalid }
    end if
    if m.pgInfo.visible and m.ipCur <> invalid then ipRender(true)
end sub

' Lo guardado si sigue fresco; si no, lo pide (una vez) y regresa lo que haya.
' Regresa invalid mientras carga por primera vez; { v: invalid } si falló.
function spGet(k as string, maxAge as integer) as dynamic
    c = m.spCache[k]
    fresh = (c <> invalid and nowSec() - c.at < maxAge)
    if not fresh and not m.spReq.DoesExist(k) then
        m.spReq[k] = true
        parts = k.Split(":")
        u = m.spFn + "?q=" + parts[0]
        if parts.Count() > 1 then u = u + "&lg=" + parts[1]
        if parts.Count() > 2 then u = u + "&id=" + parts[2]
        req("sp:" + k, u, { parse: true })
    end if
    return c
end function

sub spOnBoard(data as dynamic)
    m.sp.loading = false
    if type(data) <> "roAssociativeArray" or type(data.events) <> "roArray" then
        if not m.sp.loaded then m.sp.error = "No se pudieron cargar los marcadores. Revisa que la tele tenga internet; se vuelve a intentar sola."
        if m.pgSports.visible then spRender(true)
        return
    end if
    evs = []
    by = {}
    for each e in data.events
        if type(e) = "roAssociativeArray" and e.h <> invalid and e.a <> invalid then
            e.t = spIso(e.start)
            e.key = spS(e.lg) + ":" + spS(e.id)
            e.st = spS(e.st)
            e.lg = spS(e.lg)
            e.sp = spS(e.sp)
            e.hw = spWords(e.h)
            e.aw = spWords(e.a)
            evs.Push(e)
            by[e.key] = e
        end if
    end for
    m.sp.events = evs
    m.sp.byKey = by
    m.sp.live = (data.live = true)
    m.sp.loaded = true
    m.sp.error = ""
    m.sp.at = nowSec()
    spSettle()
    spCheckAlerts()
    if m.miniPending <> "" and m.video.visible then
        p = m.miniPending
        m.miniPending = ""
        spMiniFor(p)
    else
        spMiniUpdate()
    end if
    if m.pgSports.visible then
        spChanCmd({ op: "rows", events: evs })
        spRender(true)
    end if
    if m.pgInfo.visible and m.ipCur <> invalid then
        k = m.ipCur.kind
        if k = "ev" or k = "quin" then ipRender(true)
    end if
end sub

function spHas(e as object, key as string) as boolean
    return key = e.lg + ":" + spS(e.h.id) or key = e.lg + ":" + spS(e.a.id)
end function

function spIsMine(e as object) as boolean
    return m.teams.DoesExist(e.lg + ":" + spS(e.h.id)) or m.teams.DoesExist(e.lg + ":" + spS(e.a.id))
end function

' ¿Hay que ocultar el marcador? («sin marcadores» para alguno de los dos equipos)
function spHidden(e as object) as boolean
    if e.st = "pre" or m.revealed.DoesExist(e.key) then return false
    return m.nospoil.DoesExist(e.lg + ":" + spS(e.h.id)) or m.nospoil.DoesExist(e.lg + ":" + spS(e.a.id))
end function

function spStatus(e as object) as string
    d = spS(e.det)
    if e.st = "in" then
        if d = "" then return "En juego"
        return d
    end if
    if e.st = "post" then
        if d = "" then return "Final"
        return d
    end if
    if d <> "" then return d
    return spWhen(e.t)
end function

function spEvTitle(e as object) as string
    if spS(e.name) <> "" then return spS(e.name)
    return spS(e.a.n) + " vs " + spS(e.h.n)
end function

function spScoreLine(e as object) as string
    return spInitials(e.a) + " " + spS(e.a.sc) + " – " + spS(e.h.sc) + " " + spInitials(e.h)
end function

function spOddsLine(e as object) as string
    if not spShowOdds() then return ""
    o = e.odds
    if type(o) <> "roAssociativeArray" then return ""
    parts = []
    mlh = spS(o.mlh) : mla = spS(o.mla) : mld = spS(o.mld)
    if e.sp = "soccer" and mlh <> "" and mla <> "" then
        s = spInitials(e.h) + " " + spPm(mlh)
        if mld <> "" then s = s + " · Empate " + spPm(mld)
        parts.Push(s + " · " + spInitials(e.a) + " " + spPm(mla))
    else
        fav = spS(o.fav)
        if fav = "h" and mlh <> "" then
            parts.Push("Favorito: " + spInitials(e.h) + " " + spPm(mlh))
        else if fav = "a" and mla <> "" then
            parts.Push("Favorito: " + spInitials(e.a) + " " + spPm(mla))
        else if spS(o.spread) <> "" then
            parts.Push(spS(o.spread))
        end if
    end if
    if spN(o.ou) > 0 then parts.Push("Total " + spS(o.ou))
    return spJoin(parts, " · ")
end function

' Situación del partido en vivo (outs y corredores, oportunidad y yardas)
function spSitText(e as object) as string
    s = e.sit
    if type(s) <> "roAssociativeArray" then return ""
    if e.sp = "baseball" then
        parts = []
        if s.outs <> invalid then
            o = Int(spN(s.outs))
            if o = 1 then parts.Push("1 out") else parts.Push(Str(o).Trim() + " outs")
        end if
        b = []
        if s.b1 = true then b.Push("1ª")
        if s.b2 = true then b.Push("2ª")
        if s.b3 = true then b.Push("3ª")
        if b.Count() > 0 then parts.Push("corredores en " + spJoin(b, ", ")) else parts.Push("bases vacías")
        if s.balls <> invalid and s.strikes <> invalid then parts.Push(spS(s.balls) + "-" + spS(s.strikes))
        return spJoin(parts, " · ")
    end if
    if e.sp = "football" then
        t = spS(s.down)
        t = t.Replace("1st", "1ª").Replace("2nd", "2ª").Replace("3rd", "3ª").Replace("4th", "4ª").Replace(" and ", " y ").Replace(" & ", " y ").Replace(" at ", " en ")
        possTeam = spS(s["pos"])
        who = ""
        if possTeam <> "" then
            if possTeam = spS(e.h.id) then
                who = spInitials(e.h)
            else if possTeam = spS(e.a.id) then
                who = spInitials(e.a)
            end if
        end if
        parts = []
        if who <> "" then parts.Push("Balón: " + who)
        if t <> "" then parts.Push(t)
        if s.rz = true then parts.Push("zona roja")
        return spJoin(parts, " · ")
    end if
    return ""
end function

' ---------------- canales del proveedor (hilo aparte) ----------------
sub spEnsureChan()
    if m.server = "" or m.user = "" then return
    b = api("")
    if m.chan <> invalid and m.chanBase = b then return
    m.chanBase = b
    m.chanReady = false
    m.chanRows = invalid
    m.evChans = {}
    m.chanQueue = []
    m.chan = CreateObject("roSGNode", "ChanTask")
    m.chan.base = b
    m.chan.observeField("status", "spOnChanStatus")
    m.chan.observeField("out", "spOnChanOut")
    m.chan.control = "RUN"
end sub

sub spChanCmd(c as object)
    if m.chan = invalid then return
    m.reqN = m.reqN + 1
    c.n = m.reqN
    if m.chanReady then
        m.chan.cmd = c
        return
    end if
    if c.op = "rows" then
        q = []
        for each x in m.chanQueue
            if x.op <> "rows" then q.Push(x)
        end for
        m.chanQueue = q
    end if
    m.chanQueue.Push(c)
end sub

sub spOnChanStatus()
    if m.chan = invalid or m.chan.status <> "ready" then return
    m.chanReady = true
    q = m.chanQueue
    m.chanQueue = []
    for each c in q
        m.chan.cmd = c
    end for
end sub

sub spOnChanOut()
    o = m.chan.out
    if o = invalid then return
    if o.op = "rows" then
        m.chanRows = o
        if m.pgSports.visible then spRender(true)
    else if o.op = "chans" or o.op = "scan" then
        m.evChans[o.key] = { list: o.list, scanned: (o.op = "scan"), scanning: false }
        if m.pgInfo.visible and m.ipCur <> invalid and m.ipCur.kind = "ev" and m.ipCur.key = o.key then ipRender(true)
    end if
end sub

' ---------------- pantalla de Deportes ----------------
sub spOpen(focus as boolean)
    m.section = 7
    m.pgHome.visible = false
    m.pgBrowse.visible = false
    m.pgSearch.visible = false
    m.pgDetail.visible = false
    m.pgInfo.visible = false
    m.pgSports.visible = true
    spEnsureChan()
    if m.sp.loaded then spChanCmd({ op: "rows", events: m.sp.events })
    spLoadBoard()
    spRender(false)
    if focus then
        if m.spRows.content <> invalid and m.spRows.content.getChildCount() > 0 then m.spRows.setFocus(true) else m.spFilters.setFocus(true)
    end if
end sub

function spMineFirst(arr as object) as object
    a = []
    b = []
    for each e in arr
        if spIsMine(e) then a.Push(e) else b.Push(e)
    end for
    a.Append(b)
    return a
end function

function spTake(arr as object, n as integer) as object
    if arr.Count() <= n then return arr
    out = []
    for i = 0 to n - 1
        out.Push(arr[i])
    end for
    return out
end function

function spTeamList() as object
    arr = []
    for each k in m.teams
        arr.Push(m.teams[k])
    end for
    arr.SortBy("n")
    return arr
end function

sub spRender(keep as boolean)
    evs = m.sp.events
    present = {}
    anyLive = false
    for each e in evs
        present[e.lg] = true
        if e.st = "in" then anyLive = true
    end for
    ppv = []
    cats = []
    if m.chanRows <> invalid then
        if type(m.chanRows.ppv) = "roArray" then ppv = m.chanRows.ppv
        if type(m.chanRows.cats) = "roArray" then cats = m.chanRows.cats
    end if
    ' --- filtros ---
    fl = [{ k: "all", t: "Todo" }]
    if anyLive then fl.Push({ k: "live", t: "En vivo" })
    fl.Push({ k: "mine", t: "Mis equipos" })
    for each l in m.spLg
        if present[l.k] = true then fl.Push({ k: l.k, t: l.short })
    end for
    if ppv.Count() > 0 then fl.Push({ k: "ppv", t: "Pago por evento" })
    if cats.Count() > 0 then fl.Push({ k: "ch", t: "Canales" })
    fl.Push({ k: "@quin", t: "Quiniela" })
    fl.Push({ k: "@table", t: "Tablas" })
    fl.Push({ k: "@pick", t: "Elegir equipos" })
    found = false
    for each f in fl
        if f.k = m.spFilter then found = true
    end for
    if not found then m.spFilter = "all"
    sig = m.spFilter + "#"
    for each f in fl
        sig = sig + f.k + "|"
    end for
    if sig <> m.spFilterSig then
        m.spFilterSig = sig
        m.spFilterKeys = fl
        c = CreateObject("roSGNode", "ContentNode")
        idx = 0
        for i = 0 to fl.Count() - 1
            n = c.createChild("ContentNode")
            if fl[i].k = m.spFilter then
                n.title = "• " + fl[i].t
                idx = i
            else
                n.title = "   " + fl[i].t
            end if
        end for
        fi = m.spFilters.itemFocused
        m.spFilters.content = c
        if fi >= 0 and fi < fl.Count() and keep then m.spFilters.jumpToItem = fi else m.spFilters.jumpToItem = idx
    end if

    ' --- filas ---
    f = m.spFilter
    rows = []
    now = nowSec()
    today = spDayKey(now)
    if (f = "all" or f = "mine") and m.teams.Count() > 0 then
        items = []
        for each t in spTeamList()
            items.Push({ kind: "team", key: "t:" + t.lg + ":" + t.id, f: t })
        end for
        rows.Push({ id: "teams", title: "Mis equipos", items: items })
    end if
    if f <> "ppv" and f <> "ch" then
        sel = []
        for each e in evs
            ok = true
            if f = "mine" then
                ok = spIsMine(e)
            else if m.spLgBy.DoesExist(f) then
                ok = (e.lg = f)
            end if
            if ok then sel.Push(e)
        end for
        liveL = []
        todayL = []
        laterL = []
        doneL = []
        for each e in sel
            if e.st = "in" then
                liveL.Push(e)
            else if e.st = "pre" then
                if spDayKey(e.t) <= today then todayL.Push(e) else laterL.Push(e)
            else if e.st = "post" and e.t > now - 30 * 3600 then
                doneL.Push(e)
            end if
        end for
        spAddEvRow(rows, "live", "En vivo ahora", spMineFirst(liveL))
        if f <> "live" then
            spAddEvRow(rows, "today", "Hoy", spMineFirst(todayL))
            later = spTake(spMineFirst(laterL), 40)
            later.SortBy("t")
            spAddEvRow(rows, "later", "Próximos días", later)
            doneL.SortBy("t", "r")
            spAddEvRow(rows, "done", "Resultados", spTake(spMineFirst(doneL), 40))
        end if
    end if
    if (f = "all" or f = "ppv" or f = "live") and ppv.Count() > 0 then
        items = []
        for i = 0 to ppv.Count() - 1
            items.Push({ kind: "ch", key: "c:" + ppv[i].xid, ch: ppv[i], cat: "Pago por evento", list: ppv, idx: i })
        end for
        rows.Push({ id: "ppv", title: "Pago por evento", items: items })
    end if
    if f = "all" or f = "ch" or f = "live" then
        for each r in cats
            items = []
            for i = 0 to r.chans.Count() - 1
                items.Push({ kind: "ch", key: "c:" + r.chans[i].xid, ch: r.chans[i], cat: r.name, list: r.chans, idx: i })
            end for
            if items.Count() > 0 then rows.Push({ id: "cat:" + r.id, title: r.name, items: items })
        end for
    end if

    ' --- mensaje ---
    msg = ""
    if not m.sp.loaded then
        msg = "Cargando marcadores…"
        if m.sp.error <> "" then msg = m.sp.error
    else if f = "mine" and m.teams.Count() = 0 then
        msg = "Aún no sigues equipos. Elige «Elegir equipos» a la izquierda y aquí verás cuándo juegan, en qué canal, sus resultados, la tabla, los lesionados y las noticias."
    else if rows.Count() = 0 then
        if f = "ch" or f = "ppv" then
            msg = "Buscando tus canales de deportes…"
        else
            msg = "No hay partidos aquí por ahora."
        end if
    end if
    m.spMsg.text = msg
    m.spMsg.visible = (msg <> "")

    ' --- contenido ---
    rsig = ""
    for each r in rows
        rsig = rsig + r.id + ":"
        for each it in r.items
            rsig = rsig + it.key + ","
        end for
        rsig = rsig + ";"
    end for
    if keep and rsig = m.spSig and m.spRows.content <> invalid then
        ' mismas tarjetas: solo se actualizan marcadores (no se mueve el enfoque)
        root = m.spRows.content
        for ri = 0 to rows.Count() - 1
            rn = root.getChild(ri)
            for ci = 0 to rows[ri].items.Count() - 1
                n = rn.getChild(ci)
                if n <> invalid then
                    n.setFields(spCardFields(rows[ri].items[ci]))
                    n.rev = n.rev + 1
                end if
            end for
        end for
        m.spItems = spItemsOf(rows)
        spOnRowFocus()
        return
    end if
    focusKey = ""
    fo = m.spRows.rowItemFocused
    if fo <> invalid and fo.Count() = 2 and fo[0] < m.spItems.Count() then
        r0 = m.spItems[fo[0]]
        if fo[1] < r0.Count() then focusKey = r0[fo[1]].key
    end if
    hadFocus = m.spRows.hasFocus()
    m.spSig = rsig
    root = CreateObject("roSGNode", "ContentNode")
    jr = -1
    jc = 0
    for ri = 0 to rows.Count() - 1
        r = rows[ri]
        rn = root.createChild("ContentNode")
        rn.title = r.title
        for ci = 0 to r.items.Count() - 1
            it = r.items[ci]
            n = rn.createChild("ContentNode")
            fields = spCardFields(it)
            fields.rev = 0
            n.addFields(fields)
            if jr < 0 and it.key = focusKey then
                jr = ri
                jc = ci
            end if
        end for
    end for
    m.spItems = spItemsOf(rows)
    m.spRows.content = root
    if jr >= 0 and keep then m.spRows.jumpToRowItem = [jr, jc]
    if hadFocus and rows.Count() = 0 then m.spFilters.setFocus(true)
    if rows.Count() = 0 then spHeroFeatured() else spOnRowFocus()
end sub

function spItemsOf(rows as object) as object
    out = []
    for each r in rows
        out.Push(r.items)
    end for
    return out
end function

sub spAddEvRow(rows as object, id as string, title as string, evs as object)
    if evs.Count() = 0 then return
    items = []
    for each e in evs
        items.Push({ kind: "ev", key: e.key, e: e })
    end for
    rows.Push({ id: id, title: title, items: items })
end sub

function spCardFields(it as object) as object
    if it.kind = "ev" then return spEvFields(it.e)
    if it.kind = "team" then return spTeamFields(it.f)
    return spChFields(it.ch, it.cat)
end function

function spEvFields(e as object) as object
    hide = spHidden(e)
    live = (e.st = "in")
    top = spLgShort(e.lg) + " · " + spStatus(e)
    if live then top = "• " + top
    asc = ""
    hsc = ""
    if e.st <> "pre" and not hide then
        asc = spS(e.a.sc)
        hsc = spS(e.h.sc)
    end if
    foot = ""
    if e.st = "pre" then
        foot = spCountdown(e.t)
        ol = spOddsLine(e)
        if ol <> "" then
            foot = foot + " · " + ol
        else if type(e.tv) = "roArray" and e.tv.Count() > 0 then
            foot = foot + " · " + spS(e.tv[0])
        end if
    else if live then
        foot = spSitText(e)
        if foot = "" and type(e.tv) = "roArray" and e.tv.Count() > 0 then foot = spS(e.tv[0])
    else
        foot = spS(e.note)
    end if
    if hide then foot = "Marcador oculto"
    return {
        k: "ev", top: top, live: live, mine: spIsMine(e), foot: foot, img: "",
        an: spS(e.a.n), hn: spS(e.h.n), asc: asc, hsc: hsc,
        alg: spS(e.a.logo), hlg: spS(e.h.logo), aab: spInitials(e.a), hab: spInitials(e.h),
        acol: spS(e.a.col), hcol: spS(e.h.col),
        aw: (e.a.win = true and not hide), hw: (e.h.win = true and not hide)
    }
end function

' Partido de un equipo: el que va en vivo, si no el próximo, si no el último
function spTeamEvent(key as string) as dynamic
    nxt = invalid
    last = invalid
    for each e in m.sp.events
        if spHas(e, key) then
            if e.st = "in" then return e
            if e.st = "pre" and nxt = invalid then nxt = e
            if e.st = "post" then last = e
        end if
    end for
    if nxt <> invalid then return nxt
    return last
end function

function spTeamFields(f as object) as object
    key = f.lg + ":" + f.id
    e = spTeamEvent(key)
    foot = "Sin partidos en estos días"
    live = false
    if e <> invalid then
        home = (spS(e.h.id) = f.id)
        opp = e.h
        pre = "en "
        if home then
            opp = e.a
            pre = "vs "
        end if
        if e.st = "in" then
            live = true
            if spHidden(e) then foot = "EN VIVO · marcador oculto" else foot = "EN VIVO · " + spScoreLine(e)
        else if e.st = "pre" then
            foot = spWhen(e.t) + " · " + pre + spS(opp.n)
        else
            us = e.a
            if home then us = e.h
            if spHidden(e) then
                foot = "Último: " + pre + spS(opp.n)
            else
                r = "E"
                if us.win = true then r = "G" else if opp.win = true then r = "P"
                foot = "Último: " + r + " " + spS(us.sc) + "-" + spS(opp.sc) + " " + pre + spS(opp.n)
            end if
        end if
    end if
    return {
        k: "team", top: spLgShort(f.lg), live: live, mine: false, foot: foot, img: "",
        an: "", hn: spS(f.n), asc: "", hsc: "", alg: "", aab: "", acol: "",
        hlg: spS(f.logo), hab: spInitials(f), hcol: spS(f.col), aw: false, hw: false
    }
end function

function spChFields(ch as object, catName as string) as object
    o = {
        k: "ch", top: catName, live: false, mine: false, foot: "OK: ver el canal", img: spS(ch.img),
        an: "", hn: spS(ch.t), asc: "", hsc: "", alg: "", aab: "", acol: "", hlg: "", hab: "", hcol: "", aw: false, hw: false
    }
    if ch.ev <> invalid then
        e = m.sp.byKey[ch.ev]
        if e <> invalid then
            o.alg = spS(e.a.logo) : o.aab = spInitials(e.a) : o.acol = spS(e.a.col)
            o.hlg = spS(e.h.logo) : o.hab = spInitials(e.h) : o.hcol = spS(e.h.col)
            o.mine = spIsMine(e)
            if e.st = "in" then
                o.live = true
                if spHidden(e) then o.foot = "EN VIVO · marcador oculto" else o.foot = "EN VIVO · " + spScoreLine(e)
            else if e.st = "pre" then
                o.foot = spEvTitle(e) + " · " + spWhen(e.t)
            else if not spHidden(e) then
                o.foot = "Final · " + spScoreLine(e)
            end if
        end if
    end if
    return o
end function

' ---------------- portada de la pantalla (lo que tienes enfocado) ----------------
sub spOnRowFocus()
    if not m.pgSports.visible then return
    fo = m.spRows.rowItemFocused
    if fo = invalid or fo.Count() <> 2 or fo[0] < 0 or fo[0] >= m.spItems.Count() then
        spHeroFeatured()
        return
    end if
    r = m.spItems[fo[0]]
    if fo[1] < 0 or fo[1] >= r.Count() then
        spHeroFeatured()
        return
    end if
    it = r[fo[1]]
    if it.kind = "ev" then
        spHeroEvent(it.e)
    else if it.kind = "team" then
        e = spTeamEvent(it.f.lg + ":" + it.f.id)
        if e <> invalid then
            spHeroEvent(e)
        else
            spHeroPlain(spLgName(it.f.lg), spS(it.f.full), "Sin partidos en estos días.", "OK: ficha del equipo con calendario, tabla, lesionados y noticias")
        end if
    else
        e = invalid
        if it.ch.ev <> invalid then e = m.sp.byKey[it.ch.ev]
        if e <> invalid then
            spHeroEvent(e)
            m.spHint.text = "OK: ver " + spS(it.ch.t)
        else
            spHeroPlain(it.cat, spS(it.ch.t), "", "OK: ver el canal")
        end if
    end if
end sub

' Sin nada enfocado: el partido en vivo de tus equipos, o el mejor del momento
sub spHeroFeatured()
    evs = m.sp.events
    best = invalid
    for each e in evs
        if e.st = "in" and spIsMine(e) then
            best = e
            exit for
        end if
    end for
    if best = invalid then
        now = nowSec()
        for each e in evs
            if e.st = "pre" and spIsMine(e) and e.t < now + 86400 then
                best = e
                exit for
            end if
        end for
    end if
    if best = invalid then
        bi = 99
        for each e in evs
            if e.st = "in" then
                i = 98
                for j = 0 to m.spLg.Count() - 1
                    if m.spLg[j].k = e.lg then i = j
                end for
                if i < bi then
                    bi = i
                    best = e
                end if
            end if
        end for
    end if
    if best = invalid then
        for each e in evs
            if e.st = "pre" then
                best = e
                exit for
            end if
        end for
    end if
    if best <> invalid then
        spHeroEvent(best)
    else if not m.sp.loaded then
        spHeroPlain("DEPORTES", "Marcadores en vivo", "Cargando…", "")
    else
        spHeroPlain("DEPORTES", "Marcadores en vivo", "No hay partidos en estos días.", "")
    end if
end sub

sub spHeroPlain(kicker as string, title as string, meta as string, hint as string)
    m.spKicker.text = UCase(kicker)
    m.spKicker.color = "0x3D9BFFFF"
    m.spTitle.visible = true
    m.spTitle.text = title
    for each n in [m.spLgA, m.spNA, m.spSA, m.spLgH, m.spNH, m.spSH]
        n.visible = false
    end for
    m.spMeta1.text = meta
    m.spMeta2.text = ""
    m.spHint.text = hint
end sub

sub spHeroEvent(e as object)
    hide = spHidden(e)
    m.spTitle.visible = false
    for each n in [m.spLgA, m.spNA, m.spSA, m.spLgH, m.spNH, m.spSH]
        n.visible = true
    end for
    k = spLgName(e.lg)
    if spS(e.note) <> "" then k = k + " · " + spS(e.note)
    st = spStatus(e)
    if e.st = "pre" then st = spWhen(e.t) + " · " + spCountdown(e.t)
    if e.st = "in" then
        m.spKicker.text = "• EN VIVO · " + UCase(k) + " · " + st
        m.spKicker.color = "0xFF453AFF"
    else
        m.spKicker.text = UCase(k) + " · " + st
        m.spKicker.color = "0x3D9BFFFF"
    end if
    spSetLogo(m.spLgA, e.a)
    spSetLogo(m.spLgH, e.h)
    m.spNA.text = spS(e.a.n)
    m.spNH.text = spS(e.h.n)
    if e.st = "pre" or hide then
        m.spSA.text = ""
        m.spSH.text = ""
    else
        m.spSA.text = spS(e.a.sc)
        m.spSH.text = spS(e.h.sc)
    end if
    loserA = (e.h.win = true and not hide)
    loserH = (e.a.win = true and not hide)
    if loserA then m.spSA.color = "0x8E97ADFF" else m.spSA.color = "0xFFFFFFFF"
    if loserH then m.spSH.color = "0x8E97ADFF" else m.spSH.color = "0xFFFFFFFF"
    l1 = []
    if type(e.tv) = "roArray" and e.tv.Count() > 0 then
        tv = []
        for each x in e.tv
            tv.Push(spS(x))
        end for
        l1.Push("Transmite: " + spJoin(tv, ", "))
    end if
    if e.st = "pre" then l1.Push(spOddsLine(e))
    m.spMeta1.text = spJoin(l1, "  ·  ")
    l2 = []
    if hide then
        l2.Push("Marcador oculto (activaste «sin marcadores» para este equipo).")
    else if e.st = "in" then
        l2.Push(spSitText(e))
        if type(e.sit) = "roAssociativeArray" then l2.Push(spTr(spS(e.sit.last)))
    end if
    recs = []
    if spS(e.a.rec) <> "" then recs.Push(spInitials(e.a) + " " + spS(e.a.rec))
    if spS(e.h.rec) <> "" then recs.Push(spInitials(e.h) + " " + spS(e.h.rec))
    if recs.Count() > 0 then l2.Push("Récords: " + spJoin(recs, " · "))
    m.spMeta2.text = spJoin(l2, chr(10))
    m.spHint.text = "OK: dónde verlo, detalle del partido y más"
    spTrFlush()
end sub

sub spSetLogo(n as object, t as object)
    n.uri = spS(t.logo)
    n.ab = spInitials(t)
    n.col = spS(t.col)
end sub

sub spOnRowSelect()
    fo = m.spRows.rowItemSelected
    if fo = invalid or fo.Count() <> 2 or fo[0] >= m.spItems.Count() then return
    r = m.spItems[fo[0]]
    if fo[1] >= r.Count() then return
    it = r[fo[1]]
    if it.kind = "ev" then
        ipOpen({ kind: "ev", key: it.key }, false)
    else if it.kind = "team" then
        ipOpen({ kind: "team", lg: it.f.lg, id: it.f.id, t: it.f, tab: "" }, false)
    else
        spPlayList(it.list, it.idx)
    end if
end sub

sub spPlayList(list as object, idx as integer)
    arr = []
    for each c in list
        arr.Push({ title: spS(c.t), xid: spS(c.xid) })
    end for
    if arr.Count() = 0 then return
    m.live = { list: arr, idx: idx, fmt: 0, tries: 0 }
    playLiveNow()
end sub

sub spOnFilter()
    i = m.spFilters.itemSelected
    if i < 0 or i >= m.spFilterKeys.Count() then return
    k = m.spFilterKeys[i].k
    if k = "@quin" then
        ipOpen({ kind: "quin" }, false)
        return
    end if
    if k = "@table" then
        lg = ""
        if m.spLgBy.DoesExist(m.spFilter) and m.spFilter <> "lmp" and m.spFilter <> "ufc" then lg = m.spFilter
        if lg = "" then
            for each l in m.spLg
                if lg = "" and l.k <> "lmp" and l.k <> "ufc" then
                    for each e in m.sp.events
                        if e.lg = l.k then
                            lg = l.k
                            exit for
                        end if
                    end for
                end if
            end for
        end if
        if lg = "" then lg = "nfl"
        ipOpen({ kind: "table", tab: lg }, false)
        return
    end if
    if k = "@pick" then
        lg = "nfl"
        if m.spLgBy.DoesExist(m.spFilter) then lg = m.spFilter
        ipOpen({ kind: "pick", tab: lg }, false)
        return
    end if
    m.spFilter = k
    spRender(false)
    if m.spRows.content <> invalid and m.spRows.content.getChildCount() > 0 then m.spRows.setFocus(true)
end sub

' ---------------- Mis equipos, sin marcadores y quiniela (se guardan con el perfil) ----------------
sub spPut(scope as string, kind as string, key as string, data as dynamic, deleted as boolean)
    if m.hh = invalid or m.hh = "" or scope = "" then return
    it = { scope: scope, kind: kind, key: key, deleted: deleted }
    if data <> invalid and not deleted then it.data = data
    rpc("put" + Str(m.reqN + 1).Trim(), "cc_put", { p_h: m.hh, p_s: m.hs, p_items: [it] })
end sub

sub spSaveLocal()
    if m.guest then return
    rset("sports_local", FormatJson({ teams: m.teams, nospoil: m.nospoil }))
end sub

sub spToggleTeam(lg as string, t as object)
    k = lg + ":" + spS(t.id)
    if m.teams.DoesExist(k) then
        m.teams.Delete(k)
        if m.scope <> "" then spPut(m.scope, "team", k, invalid, true) else spSaveLocal()
    else
        f = { lg: lg, id: spS(t.id), n: spS(t.n), full: spS(t.full), ab: spS(t.ab), logo: spS(t.logo), col: spS(t.col) }
        if f.full = "" then f.full = f.n
        m.teams[k] = f
        if m.scope <> "" then spPut(m.scope, "team", k, f, false) else spSaveLocal()
    end if
    m.spFilterSig = ""
end sub

sub spToggleNoSpoil(k as string)
    if m.nospoil.DoesExist(k) then
        m.nospoil.Delete(k)
        if m.scope <> "" then spPut(m.scope, "nospoil", k, invalid, true) else spSaveLocal()
    else
        m.nospoil[k] = true
        if m.scope <> "" then spPut(m.scope, "nospoil", k, { on: true }, false) else spSaveLocal()
    end if
end sub

sub spLoadLocal()
    m.teams = {}
    m.nospoil = {}
    t = rget("sports_local")
    if t = "" then return
    d = ParseJson(t)
    if type(d) <> "roAssociativeArray" then return
    if type(d.teams) = "roAssociativeArray" then m.teams = d.teams
    if type(d.nospoil) = "roAssociativeArray" then m.nospoil = d.nospoil
end sub

' Cambios que vienen de la nube (del perfil o de la quiniela de la casa)
sub spApplyRemote(kind as string, key as string, data as dynamic, deleted as boolean)
    if kind = "team" then
        if deleted or type(data) <> "roAssociativeArray" then
            m.teams.Delete(key)
        else
            m.teams[key] = { lg: spS(data.lg), id: spS(data.id), n: spS(data.n), full: spS(data.full), ab: spS(data.ab), logo: spS(data.logo), col: spS(data.col) }
        end if
    else if kind = "nospoil" then
        if deleted then m.nospoil.Delete(key) else m.nospoil[key] = true
    else if kind = "pick" then
        if deleted or type(data) <> "roAssociativeArray" then
            m.picks.Delete(key)
        else
            cur = m.picks[key]
            ' el resultado nunca se borra con una copia vieja
            if cur <> invalid and spS(cur.r) <> "" and spS(data.r) = "" then data.r = cur.r
            m.picks[key] = data
        end if
    end if
end sub

function spMsToSec(v as dynamic) as integer
    t = type(v)
    if t = "LongInteger" or t = "roLongInteger" then return Int(v / 1000&)
    if t = "Integer" or t = "roInt" then return Int(v / 1000)
    if t = "Double" or t = "roDouble" or t = "Float" or t = "roFloat" then return Int(v / 1000)
    if t = "String" or t = "roString" then return Int(Val(Left(v, Len(v) - 3)))
    return 0
end function

sub spPick(e as object, p as string)
    if m.scope = "" or m.guest or e.st <> "pre" then return
    key = e.key + "|" + m.scope
    ms& = e.t
    ms& = ms& * 1000&
    data = { ev: e.key, pr: m.scope, p: p, t: spEvTitle(e), s: ms&, r: "" }
    m.picks[key] = data
    spPut("_", "pick", key, data, false)
end sub

' Cuando termina un partido se anota el resultado en las elecciones
sub spSettle()
    for each k in m.picks
        p = m.picks[k]
        if p <> invalid and spS(p.r) = "" then
            e = m.sp.byKey[spS(p.ev)]
            if e <> invalid and e.st = "post" then
                r = ""
                if e.h.win = true then
                    r = "h"
                else if e.a.win = true then
                    r = "a"
                else if spS(e.h.sc) <> "" and spS(e.h.sc) = spS(e.a.sc) then
                    r = "d"
                end if
                if r <> "" then
                    p.r = r
                    spPut("_", "pick", k, p, false)
                end if
            end if
        end if
    end for
end sub

' ---------------- avisos de tus equipos y marcador en la esquina ----------------
sub spCheckAlerts()
    if not spPref("sportAlerts") or m.teams.Count() = 0 then return
    now = nowSec()
    first = (m.alertLast.Count() = 0)
    show = (m.home.visible or m.video.visible)
    for each e in m.sp.events
        if spIsMine(e) then
            stt = e.st + "|" + spS(e.a.sc) + "|" + spS(e.h.sc)
            prev = m.alertLast[e.key]
            m.alertLast[e.key] = stt
            title = spEvTitle(e)
            if e.st = "pre" and e.t - now >= 0 and e.t - now <= 16 * 60 and not m.alertWarned.DoesExist(e.key) then
                m.alertWarned[e.key] = true
                if show then spBanner("Tu partido empieza " + spCountdown(e.t), title + " · búscalo en Deportes", e)
            else if not first and prev <> invalid and prev <> stt and show then
                hide = spHidden(e)
                score = spS(e.a.n) + " " + spS(e.a.sc) + " – " + spS(e.h.sc) + " " + spS(e.h.n)
                if e.st = "post" and Left(prev, 4) <> "post" then
                    if hide then spBanner("Terminó", title + " · marcador oculto", e) else spBanner("Terminó", score, e)
                else if e.st = "in" and Left(prev, 3) = "pre" then
                    spBanner("Ya empezó", title, e)
                else if e.st = "in" and not hide then
                    w = "Anotación"
                    if e.sp = "baseball" then
                        w = "Carrera"
                    else if e.sp = "soccer" then
                        w = "Gol"
                    end if
                    spBanner(w, score + " · " + spS(e.det), e)
                end if
            end if
        end if
    end for
end sub

sub spBanner(title as string, body as string, e as object)
    m.bnTitle.text = title
    m.bnBody.text = body
    spSetLogo(m.bnA, e.a)
    spSetLogo(m.bnH, e.h)
    m.spBanner.visible = true
    m.bnTimer.control = "stop"
    m.bnTimer.control = "start"
end sub

sub spBannerHide()
    m.spBanner.visible = false
end sub

' Al ver un canal que pasa un partido: marcador chico arriba a la derecha
sub spMiniFor(chName as string)
    m.miniKey = ""
    m.miniState = ""
    m.spMini.visible = false
    if not spPref("miniScore") then return
    if not m.sp.loaded then
        m.miniPending = chName
        spLoadBoard()
        return
    end if
    e = spEventForName(spNorm(chName), m.sp.events)
    if e = invalid or e.st = "post" then return
    m.miniKey = e.key
    spMiniUpdate()
end sub

sub spMiniUpdate()
    if m.miniKey = "" or not m.video.visible then return
    e = m.sp.byKey[m.miniKey]
    if e = invalid then return
    stt = e.st + "|" + spS(e.a.sc) + "|" + spS(e.h.sc)
    spSetLogo(m.mnA, e.a)
    spSetLogo(m.mnH, e.h)
    if e.st = "pre" then
        m.mnTxt.text = spInitials(e.a) + " vs " + spInitials(e.h)
        m.mnSub.text = "Empieza " + spCountdown(e.t)
    else if spHidden(e) then
        m.mnTxt.text = spInitials(e.a) + " vs " + spInitials(e.h)
        m.mnSub.text = "Marcador oculto"
    else
        m.mnTxt.text = spScoreLine(e)
        m.mnSub.text = spStatus(e)
    end if
    if stt <> m.miniState then
        m.miniState = stt
        spMiniShow()
    end if
end sub

sub spMiniShow()
    if m.miniKey = "" then return
    m.spMini.visible = true
    m.miniTimer.control = "stop"
    m.miniTimer.control = "start"
end sub

sub spMiniHide()
    m.spMini.visible = false
end sub

sub spMiniStop()
    m.miniKey = ""
    m.miniPending = ""
    m.miniState = ""
    m.spMini.visible = false
end sub

' =====================================================================
' PANTALLAS DE INFORMACIÓN: partido, detalle, equipo, tablas, elegir equipos, quiniela
' =====================================================================
sub ipOpen(st as object, push as boolean)
    if push and m.ipCur <> invalid then
        m.ipCur.focus = m.ipList.itemFocused
        m.ipStack.Push(m.ipCur)
    end if
    if not push then
        m.ipStack = []
        m.ipFromFilters = m.spFilters.hasFocus()
    end if
    m.ipCur = st
    m.mode = "info"
    m.side.visible = false
    m.pgSports.visible = false
    m.pgInfo.visible = true
    m.ipTabsSig = ""
    ipRender(false)
    m.ipOpenedAt = nowSec()
    m.ipAutoIdx = ipFirstAct()
    m.ipList.jumpToItem = m.ipAutoIdx
    m.ipList.setFocus(true)
end sub

' Primer renglón que hace algo (para que el cursor no empiece en un título)
function ipFirstAct() as integer
    for i = 0 to m.ipRowsData.Count() - 1
        a = m.ipRowsData[i].act
        if a <> invalid and a <> "" then return i
    end for
    for i = 0 to m.ipRowsData.Count() - 1
        if m.ipRowsData[i].rt <> "h" then return i
    end for
    return 0
end function

sub ipBack()
    if m.ipStack.Count() > 0 then
        m.ipCur = m.ipStack.Pop()
        m.ipTabsSig = ""
        ipRender(false)
        f = m.ipCur.focus
        if f <> invalid and f >= 0 then m.ipList.jumpToItem = f
        m.ipList.setFocus(true)
        return
    end if
    ipClose()
end sub

sub ipClose()
    m.pgInfo.visible = false
    m.ipCur = invalid
    m.side.visible = true
    m.mode = "home"
    m.pgSports.visible = true
    spRender(true)
    if m.ipFromFilters = true or m.spRows.content = invalid or m.spRows.content.getChildCount() = 0 then
        m.spFilters.setFocus(true)
    else
        m.spRows.setFocus(true)
    end if
end sub

sub ipAdd(r as object)
    m.ipRowsData.Push(r)
end sub

sub ipRender(keep as boolean)
    st = m.ipCur
    if st = invalid then return
    m.ipRowsData = []
    m.ipFoot.text = ""
    m.ipInfo.text = ""
    k = st.kind
    if k = "ev" then
        ipEvent(st)
    else if k = "game" then
        ipGame(st)
    else if k = "team" then
        ipTeam(st)
    else if k = "table" then
        ipTable(st)
    else if k = "pick" then
        ipPick(st)
    else if k = "quin" then
        ipQuin(st)
    end if
    ipShowRows(keep)
end sub

function ipNodeFields(r as object) as object
    f = {
        rt: "t", t1: "", t2: "", t3: "", pre: "", lg1: "", ab1: "", col1: "", c3: "",
        hl: false, warn: false, dim: false, bold: false, wrap: false, lastBold: false,
        av: "", hv: "", af: 0.0, hf: 0.0, better: "", vals: [], cw: 0
    }
    for each k in f
        v = r[k]
        if v <> invalid then f[k] = v
    end for
    if type(f.af) <> "Float" and type(f.af) <> "roFloat" then f.af = 0.0 + spN(f.af)
    if type(f.hf) <> "Float" and type(f.hf) <> "roFloat" then f.hf = 0.0 + spN(f.hf)
    return f
end function

sub ipShowRows(keep as boolean)
    rows = m.ipRowsData
    old = m.ipList.content
    if keep and old <> invalid and old.getChildCount() = rows.Count() then
        for i = 0 to rows.Count() - 1
            n = old.getChild(i)
            n.setFields(ipNodeFields(rows[i]))
            n.rev = n.rev + 1
        end for
        return
    end if
    fi = m.ipList.itemFocused
    c = CreateObject("roSGNode", "ContentNode")
    for each r in rows
        n = c.createChild("ContentNode")
        f = ipNodeFields(r)
        f.rev = 0
        n.addFields(f)
    end for
    m.ipList.content = c
    if keep and m.ipOpenedAt <> invalid and nowSec() - m.ipOpenedAt <= 4 and fi = m.ipAutoIdx then
        m.ipAutoIdx = ipFirstAct()
        m.ipList.jumpToItem = m.ipAutoIdx
    else if keep and fi > 0 and rows.Count() > 0 then
        if fi >= rows.Count() then fi = rows.Count() - 1
        m.ipList.jumpToItem = fi
    end if
end sub

sub ipSetTabs(tabs as object, cur as string)
    m.ipTabKeys = tabs
    show = (tabs.Count() > 1)
    m.ipTabs.visible = show
    if show then
        m.ipList.translation = [420, 310]
        m.ipList.itemSize = [1420, 68]
    else
        m.ipList.translation = [80, 310]
        m.ipList.itemSize = [1760, 68]
    end if
    sig = ""
    idx = 0
    for i = 0 to tabs.Count() - 1
        sig = sig + tabs[i].k + "|"
        if tabs[i].k = cur then idx = i
    end for
    if sig = m.ipTabsSig then return
    m.ipTabsSig = sig
    c = CreateObject("roSGNode", "ContentNode")
    for each t in tabs
        n = c.createChild("ContentNode")
        n.title = t.t
    end for
    m.ipTabs.content = c
    m.ipTabs.jumpToItem = idx
end sub

sub ipOnTab()
    if m.ipCur = invalid or not m.ipTabs.visible then return
    i = m.ipTabs.itemFocused
    if i < 0 or i >= m.ipTabKeys.Count() then return
    k = m.ipTabKeys[i].k
    if k = m.ipCur.tab then return
    m.ipCur.tab = k
    ipRender(false)
    m.ipList.jumpToItem = ipFirstAct()
end sub

sub ipOnSelect()
    i = m.ipList.itemSelected
    if i < 0 or i >= m.ipRowsData.Count() then return
    r = m.ipRowsData[i]
    a = r.act
    if a = invalid or a = "" then return
    st = m.ipCur
    if a = "play" then
        spPlayList(r.list, r.idx)
    else if a = "scan" then
        e = m.sp.byKey[st.key]
        ce = m.evChans[st.key]
        if e <> invalid and ce <> invalid then
            ce.scanning = true
            spChanCmd({ op: "scan", ev: e })
            ipRender(true)
        end if
    else if a = "chanpage" then
        ipClose()
        m.spFilter = "ch"
        spRender(false)
        m.spRows.setFocus(true)
    else if a = "pick" then
        e = m.sp.byKey[st.key]
        if e <> invalid then spPick(e, r.p)
        ipRender(true)
    else if a = "game" then
        ipOpen({ kind: "game", key: r.key, lg: r.lg, id: r.id, tab: "" }, true)
    else if a = "team" then
        ipOpen({ kind: "team", lg: r.lg, id: spS(r.t.id), t: r.t, tab: "" }, true)
    else if a = "ev" then
        ipOpen({ kind: "ev", key: r.key }, true)
    else if a = "follow" or a = "pickTeam" then
        spToggleTeam(r.lg, r.t)
        ipRender(true)
    else if a = "nospoil" then
        spToggleNoSpoil(r.key)
        ipRender(true)
    else if a = "reveal" then
        m.revealed[r.key] = true
        ipRender(true)
    end if
end sub

' ---------- encabezados ----------
sub ipHeadPlain(kicker as string, title as string, subTxt as string, logoTeam as dynamic)
    m.ipMatch.visible = false
    m.ipPlain.visible = true
    m.ipKicker.text = UCase(kicker)
    m.ipKicker.color = "0x3D9BFFFF"
    m.ipTitle.text = title
    m.ipSub.text = subTxt
    if logoTeam <> invalid then
        m.ipLogo.visible = true
        spSetLogo(m.ipLogo, logoTeam)
        m.ipKicker.translation = [240, 50]
        m.ipTitle.translation = [240, 92]
        m.ipSub.translation = [240, 168]
    else
        m.ipLogo.visible = false
        m.ipKicker.translation = [80, 50]
        m.ipTitle.translation = [80, 92]
        m.ipSub.translation = [80, 168]
    end if
end sub

sub ipHeadMatch(e as object, hide as boolean)
    m.ipMatch.visible = true
    m.ipPlain.visible = false
    m.ipKicker.translation = [80, 40]
    k = spLgName(e.lg)
    if spS(e.note) <> "" then k = k + " · " + spS(e.note)
    if e.st = "in" then
        m.ipKicker.text = "• EN VIVO · " + UCase(k)
        m.ipKicker.color = "0xFF453AFF"
    else
        m.ipKicker.text = UCase(k)
        m.ipKicker.color = "0x3D9BFFFF"
    end if
    spSetLogo(m.ipLgA, e.a)
    spSetLogo(m.ipLgH, e.h)
    m.ipNA.text = spS(e.a.full)
    if m.ipNA.text = "" then m.ipNA.text = spS(e.a.n)
    m.ipNH.text = spS(e.h.full)
    if m.ipNH.text = "" then m.ipNH.text = spS(e.h.n)
    m.ipRA.text = spS(e.a.rec)
    m.ipRH.text = spS(e.h.rec)
    if e.st = "pre" or hide then
        m.ipSA.text = ""
        m.ipSH.text = ""
        if hide then m.ipMid.text = "Marcador oculto" else m.ipMid.text = spWhen(e.t) + chr(10) + spCountdown(e.t)
        m.ipMid.color = "0xB9C2D6FF"
    else
        m.ipSA.text = spS(e.a.sc)
        m.ipSH.text = spS(e.h.sc)
        m.ipMid.text = spStatus(e)
        if e.st = "in" then m.ipMid.color = "0xFF453AFF" else m.ipMid.color = "0xB9C2D6FF"
    end if
    if e.h.win = true and not hide then m.ipSA.color = "0x8E97ADFF" else m.ipSA.color = "0xFFFFFFFF"
    if e.a.win = true and not hide then m.ipSH.color = "0x8E97ADFF" else m.ipSH.color = "0xFFFFFFFF"
    parts = []
    if type(e.tv) = "roArray" and e.tv.Count() > 0 then
        tv = []
        for each x in e.tv
            tv.Push(spS(x))
        end for
        parts.Push("Transmite: " + spJoin(tv, ", "))
    end if
    if e.st = "pre" then parts.Push(spOddsLine(e))
    if e.st = "in" and not hide then parts.Push(spSitText(e))
    m.ipInfo.text = spJoin(parts, "   ·   ")
end sub

' ---------- ventana de un partido: dónde verlo, quiniela y más ----------
sub ipEvent(st as object)
    e = m.sp.byKey[st.key]
    ipSetTabs([], "")
    if e = invalid then
        ipHeadPlain("Deportes", "Partido", "Este partido ya no está en el tablero.", invalid)
        ipAdd({ rt: "t", t1: "Regresa y elige otro partido." })
        return
    end if
    hide = spHidden(e)
    ipHeadMatch(e, hide)
    m.ipFoot.text = "Los canales se buscan por nombre y en la guía de tu proveedor. Marcadores: ESPN y TheSportsDB."
    if hide then ipAdd({ rt: "btn", t1: "Mostrar marcador", act: "reveal", key: e.key })
    ipAdd({ rt: "h", t1: "Dónde verlo" })
    if m.chan = invalid then spEnsureChan()
    ce = m.evChans[e.key]
    if ce = invalid then
        ce = { list: invalid, scanned: false, scanning: false }
        m.evChans[e.key] = ce
        spChanCmd({ op: "chans", ev: e })
    end if
    if ce.list = invalid then
        ipAdd({ rt: "t", t1: "Buscando en qué canal pasa…", dim: true })
    else
        if ce.list.Count() = 0 then
            if ce.scanned then
                txt = "No lo encontré en la guía de tus canales de deportes. Búscalo en «Ver todos los canales de deportes»."
            else
                txt = "No encontré el canal por su nombre."
                if type(e.tv) = "roArray" and e.tv.Count() > 0 then
                    tv = []
                    for each x in e.tv
                        tv.Push(spS(x))
                    end for
                    txt = txt + " Lo transmite: " + spJoin(tv, ", ") + "."
                end if
            end if
            ipAdd({ rt: "t", t1: txt, wrap: true })
        end if
        for i = 0 to ce.list.Count() - 1
            ipAdd({ rt: "btn", t1: "Ver en " + spS(ce.list[i].t), act: "play", list: ce.list, idx: i })
        end for
        if not ce.scanned then
            if ce.scanning then
                ipAdd({ rt: "t", t1: "Buscando en la guía de tus canales… (tarda unos segundos)", dim: true })
            else
                ipAdd({ rt: "btn", t1: "Buscar en la guía de tus canales de deportes", act: "scan" })
            end if
        end if
        ipAdd({ rt: "btn", t1: "Ver todos los canales de deportes", act: "chanpage" })
    end if
    if e.st = "pre" and m.scope <> "" and not m.guest then
        ipAdd({ rt: "h", t1: "Quiniela de la casa: ¿quién gana?", t3: "sin dinero, solo por el gusto" })
        my = m.picks[e.key + "|" + m.scope]
        p = ""
        if my <> invalid then p = spS(my.p)
        ipAdd({ rt: "btn", t1: ipPickLabel("Gana " + spS(e.a.n), p = "a"), act: "pick", p: "a" })
        if e.sp = "soccer" then ipAdd({ rt: "btn", t1: ipPickLabel("Empate", p = "d"), act: "pick", p: "d" })
        ipAdd({ rt: "btn", t1: ipPickLabel("Gana " + spS(e.h.n), p = "h"), act: "pick", p: "h" })
    end if
    ipAdd({ rt: "h", t1: "Más" })
    if e.lg <> "lmp" and e.lg <> "ufc" then
        lbl = "Detalle del partido: estadísticas, jugadas y momios"
        if e.st = "pre" then lbl = "Previa del partido: pronóstico, momios, últimos juegos y lesionados"
        ipAdd({ rt: "btn", t1: lbl, act: "game", key: e.key, lg: e.lg, id: spS(e.id) })
    end if
    if spS(e.name) = "" or e.sp <> "mma" then
        for each t in [e.a, e.h]
            ipAdd({ rt: "btn", t1: "Ficha de " + spS(t.n) + ": calendario, tabla y noticias", act: "team", lg: e.lg, t: t })
        end for
        for each t in [e.a, e.h]
            if m.teams.DoesExist(e.lg + ":" + spS(t.id)) then
                ipAdd({ rt: "btn", t1: "Siguiendo a " + spS(t.n) + " (toca para dejar de seguir)", act: "follow", lg: e.lg, t: t })
            else
                ipAdd({ rt: "btn", t1: "Seguir a " + spS(t.n), act: "follow", lg: e.lg, t: t })
            end if
        end for
    end if
end sub

function ipPickLabel(t as string, isOn as boolean) as string
    if isOn then return t + "   (tu elección)"
    return t
end function

' ---------- detalle del partido (estilo ESPN) ----------
function spEvFromGame(st as object, g as object) as object
    h = invalid
    a = invalid
    if type(g.teams) = "roArray" then
        for each t in g.teams
            if t.home = true then h = t else a = t
        end for
    end if
    if h = invalid then h = { id: "", n: "Local", full: "", ab: "", logo: "", col: "", sc: "", rec: "" }
    if a = invalid then a = { id: "", n: "Visitante", full: "", ab: "", logo: "", col: "", sc: "", rec: "" }
    l = m.spLgBy[st.lg]
    sp = ""
    if l <> invalid then sp = l.sp
    return { key: st.key, id: st.id, lg: st.lg, sp: sp, name: "", st: spS(g.st), det: spS(g.det), t: spIso(g.start), h: h, a: a, tv: g.tv, note: "", odds: invalid, sit: invalid }
end function

sub ipGame(st as object)
    e = m.sp.byKey[st.key]
    age = 300
    if e <> invalid and e.st = "in" then age = 20
    ce = spGet("game:" + st.lg + ":" + st.id, age)
    g = invalid
    if ce <> invalid then g = ce.v
    if e = invalid and g <> invalid then e = spEvFromGame(st, g)
    if e = invalid then
        ipHeadPlain(spLgName(st.lg), "Detalle del partido", "", invalid)
        ipSetTabs([], "")
        if ce = invalid then ipAdd({ rt: "t", t1: "Cargando el partido…", dim: true }) else ipAdd({ rt: "t", t1: "No se pudo cargar el partido. Intenta de nuevo en un momento." })
        return
    end if
    hide = spHidden(e)
    ipHeadMatch(e, hide)
    if g = invalid then
        ipSetTabs([], "")
        if ce = invalid then ipAdd({ rt: "t", t1: "Cargando estadísticas, jugadas y momios…", dim: true }) else ipAdd({ rt: "t", t1: "No se pudo cargar el detalle. Intenta de nuevo en un momento." })
        return
    end if
    odds = invalid
    if spShowOdds() and type(g.odds) = "roAssociativeArray" then odds = g.odds
    if hide then
        ipSetTabs([], "")
        ipAdd({ rt: "t", t1: "Activaste «sin marcadores» para este equipo. Elige «Mostrar marcador» para ver estadísticas y jugadas.", wrap: true })
        ipAdd({ rt: "btn", t1: "Mostrar marcador", act: "reveal", key: e.key })
        return
    end if
    tabs = []
    if e.st = "pre" then tabs.Push({ k: "pre", t: "Previa" }) else tabs.Push({ k: "sum", t: "Resumen" })
    if e.st <> "pre" and ipCount(g.stats) > 0 then tabs.Push({ k: "stats", t: "Estadísticas" })
    if ipCount(g.lineups) > 0 then tabs.Push({ k: "lineup", t: "Alineaciones" })
    if ipCount(g.players) > 0 then tabs.Push({ k: "players", t: "Jugadores" })
    if ipCount(g.plays) > 0 then tabs.Push({ k: "plays", t: "Jugadas" })
    if odds <> invalid then tabs.Push({ k: "bets", t: "Apuestas" })
    if ipCount(g.inj) > 0 then tabs.Push({ k: "inj", t: "Lesionados" })
    cur = spS(st.tab)
    ok = false
    for each t in tabs
        if t.k = cur then ok = true
    end for
    if not ok then cur = tabs[0].k
    st.tab = cur
    ipSetTabs(tabs, cur)
    m.gBy = {}
    m.gA = e.a
    m.gH = e.h
    if type(g.teams) = "roArray" then
        for each t in g.teams
            m.gBy[spS(t.id)] = t
        end for
    end if
    foot = "Datos de ESPN."
    if odds <> invalid then
        by = spS(odds.by)
        if by = "" then by = "la casa de apuestas"
        foot = foot + " Momios de " + by + "; solo informativos."
    end if
    m.ipFoot.text = foot
    if cur = "sum" then
        gSummary(e, g)
    else if cur = "pre" then
        gPreview(e, g, odds)
    else if cur = "stats" then
        gStats(e, g.stats, "Estadísticas del partido")
    else if cur = "lineup" then
        gLineups(e, g)
    else if cur = "players" then
        gPlayers(e, g)
    else if cur = "plays" then
        gPlays(e, g)
    else if cur = "bets" then
        gBets(e, g, odds)
    else if cur = "inj" then
        gInjuries(e, g.inj)
    end if
    if m.ipRowsData.Count() = 0 then ipAdd({ rt: "t", t1: "Todavía no hay información de este partido.", dim: true })
    spTrFlush()
end sub

function ipCount(a as dynamic) as integer
    if type(a) <> "roArray" then return 0
    return a.Count()
end function

function gTeam(id as dynamic) as object
    t = m.gBy[spS(id)]
    if t <> invalid then return t
    if spS(id) = spS(m.gH.id) then return m.gH
    if spS(id) = spS(m.gA.id) then return m.gA
    return { id: "", n: "", full: "", ab: "", logo: "", col: "" }
end function

function gTeamName(t as object) as string
    n = spS(t.full)
    if n = "" then n = spS(t.n)
    return n
end function

function gHead(e as object, i as integer) as string
    sp = e.sp
    if sp = "soccer" then
        if i = 0 then return "1T"
        if i = 1 then return "2T"
        return "TE"
    end if
    reg = 4
    if sp = "hockey" then reg = 3
    if sp = "baseball" then reg = 9
    if i >= reg then
        if sp = "baseball" then return Str(i + 1).Trim()
        if i = reg then return "TE"
        return "TE" + Str(i - reg + 1).Trim()
    end if
    return Str(i + 1).Trim()
end function

function gPer(e as object, per as integer) as string
    sp = e.sp
    if per <= 0 then return ""
    if sp = "football" or sp = "basketball" then
        if per <= 4 then return Str(per).Trim() + "C"
        return "TE"
    end if
    if sp = "hockey" then
        if per <= 3 then return Str(per).Trim() + "P"
        return "TE"
    end if
    if sp = "baseball" then return "Ent. " + Str(per).Trim()
    if sp = "mma" then return "R" + Str(per).Trim()
    return ""
end function

function gCw(n as integer, total as integer) as integer
    if n <= 0 then return 110
    cw = Int(total / n)
    if cw > 110 then cw = 110
    if cw < 54 then cw = 54
    return cw
end function

sub gSummary(e as object, g as object)
    a = invalid
    h = invalid
    if type(g.teams) = "roArray" then
        for each t in g.teams
            if t.home = true then h = t else a = t
        end for
    end if
    if a <> invalid and h <> invalid and (ipCount(a.ls) > 0 or ipCount(h.ls) > 0) then
        n = ipCount(a.ls)
        if ipCount(h.ls) > n then n = ipCount(h.ls)
        heads = []
        for i = 0 to n - 1
            heads.Push(gHead(e, i))
        end for
        heads.Push("T")
        cw = gCw(n + 1, 880)
        ipAdd({ rt: "h", t1: "Marcador por periodo" })
        ipAdd({ rt: "cols", bold: true, t1: "", vals: heads, cw: cw })
        for each t in [a, h]
            vals = []
            for i = 0 to n - 1
                v = ""
                if i < ipCount(t.ls) then v = spS(t.ls[i])
                vals.Push(v)
            end for
            vals.Push(spS(t.sc))
            ipAdd({ rt: "cols", lg1: spS(t.logo), ab1: spInitials(t), col1: spS(t.col), t1: spS(t.n), vals: vals, cw: cw, lastBold: true })
        end for
    end if
    gLeaders(e, g, "Líderes del partido")
    wp = g.wp
    if ipCount(wp) > 2 then
        last = spN(wp[wp.Count() - 1])
        ipAdd({ rt: "h", t1: "Probabilidad de ganar", t3: "arriba " + spInitials(e.h) + ", abajo " + spInitials(e.a) })
        vals = []
        for each v in wp
            vals.Push(spN(v))
        end for
        ipAdd({ rt: "chart", t1: spInitials(e.h) + " " + Str(Int(last + 0.5)).Trim() + "%", t2: spInitials(e.a) + " " + Str(Int(100 - last + 0.5)).Trim() + "%", vals: vals })
    end if
    if ipCount(g.keyPlays) > 0 then
        title = "Anotaciones"
        if e.sp = "soccer" then title = "Momentos clave"
        ipAdd({ rt: "h", t1: title })
        for each p in g.keyPlays
            ipAdd(gPlayRow(e, p))
        end for
    end if
    if spS(g.drive) <> "" then
        ipAdd({ rt: "h", t1: "Serie actual" })
        ipAdd({ rt: "t", t1: spTr(spS(g.drive)), wrap: true })
    end if
end sub

function gPlayRow(e as object, p as object) as object
    tm = gTeam(p.team)
    pre = spJoin([gPer(e, Int(spN(p.per))), spS(p.clk)], " ")
    r = { rt: "t", pre: pre, t1: spTr(spS(p.t)), wrap: true, hl: (p.sc = true), t3: spS(p.score) }
    if spS(p.team) <> "" then
        r.lg1 = spS(tm.logo)
        r.ab1 = spInitials(tm)
        r.col1 = spS(tm.col)
    end if
    kd = spS(p.kind)
    if kd = "amarilla" then
        r.t3 = "Amarilla"
        r.c3 = "orange"
    else if kd = "roja" then
        r.t3 = "Roja"
        r.c3 = "red"
    end if
    return r
end function

sub gLeaders(e as object, g as object, title as string)
    if ipCount(g.leaders) = 0 then return
    ipAdd({ rt: "h", t1: title })
    for each t in g.leaders
        tm = gTeam(t.team)
        if type(t.items) = "roArray" then
            for each it in t.items
                n = spS(it.n)
                if spS(it["pos"]) <> "" then n = n + " · " + spS(it["pos"])
                ipAdd({ rt: "t", lg1: spS(tm.logo), ab1: spInitials(tm), col1: spS(tm.col), t1: n, t2: spS(it.cat), t3: spS(it.v) })
            end for
        end if
    end for
end sub

sub gStats(e as object, stats as dynamic, title as string)
    if ipCount(stats) = 0 then return
    ipAdd({ rt: "h", t1: title })
    ipAdd({ rt: "t", lg1: spS(e.a.logo), ab1: spInitials(e.a), col1: spS(e.a.col), t1: spS(e.a.n), t3: spS(e.h.n), bold: true })
    for each s in stats
        an = spN(s.an)
        hn = spN(s.hn)
        tot = an + hn
        af = 0.0
        hf = 0.0
        if tot > 0 then
            af = an / tot
            hf = hn / tot
        end if
        better = ""
        if an <> hn then
            if s.low = true then
                if an < hn then better = "a" else better = "h"
            else
                if an > hn then better = "a" else better = "h"
            end if
        end if
        ipAdd({ rt: "stat", t1: spS(s.l), av: spS(s.a), hv: spS(s.h), af: af, hf: hf, better: better })
    end for
end sub

sub gPreview(e as object, g as object, odds as dynamic)
    pr = g.pred
    if type(pr) = "roAssociativeArray" then
        ipAdd({ rt: "h", t1: "Pronóstico", t3: "según ESPN" })
        r = { rt: "t", t1: spS(e.h.n) + " " + Str(Int(spN(pr.h) + 0.5)).Trim() + "%   ·   " + spS(e.a.n) + " " + Str(Int(spN(pr.a) + 0.5)).Trim() + "%", bold: true }
        if odds <> invalid then
            ih = spImplied(spS(odds.mlH.now))
            ia = spImplied(spS(odds.mlA.now))
            if ih >= 0 and ia >= 0 then r.t2 = "Lo que dicen los momios: " + spS(e.h.n) + " " + Str(ih).Trim() + "% · " + spS(e.a.n) + " " + Str(ia).Trim() + "%"
        end if
        ipAdd(r)
    end if
    if odds <> invalid then
        ipAdd({ rt: "h", t1: "Momios" })
        mlh = spS(odds.mlH.now)
        mla = spS(odds.mlA.now)
        if mlh <> "" or mla <> "" then
            t = "Ganador: " + spInitials(e.a) + " " + spPm(mla) + "  ·  " + spInitials(e.h) + " " + spPm(mlh)
            if spS(odds.mlD) <> "" then t = t + "  ·  Empate " + spPm(spS(odds.mlD))
            ipAdd({ rt: "t", t1: t })
        end if
        if spS(odds.spH.now) <> "" then ipAdd({ rt: "t", t1: "Hándicap: " + spInitials(e.a) + " " + spPm(spS(odds.spA.now)) + "  ·  " + spInitials(e.h) + " " + spPm(spS(odds.spH.now)) })
        if spS(odds.ou.now) <> "" then ipAdd({ rt: "t", t1: "Total (más/menos): " + spS(odds.ou.now) })
    end if
    if ipCount(g.last5) > 0 then
        ipAdd({ rt: "h", t1: "Últimos 5 partidos" })
        for each t in g.last5
            tm = gTeam(t.team)
            parts = []
            w = 0
            l = 0
            if type(t.ev) = "roArray" then
                for each x in t.ev
                    r = spS(x.r)
                    if r = "G" then w = w + 1
                    if r = "P" then l = l + 1
                    parts.Push(r + " " + spS(x.s) + " " + spS(x.at) + " " + spS(x.o))
                end for
            end if
            ipAdd({ rt: "t", lg1: spS(tm.logo), ab1: spInitials(tm), col1: spS(tm.col), t1: gTeamName(tm), t2: spJoin(parts, "  ·  "), t3: Str(w).Trim() + "G " + Str(l).Trim() + "P" })
        end for
    end if
    gStats(e, g.stats, "Comparativo de la temporada")
    if ipCount(g.h2h) > 0 then
        ipAdd({ rt: "h", t1: "Enfrentamientos recientes" })
        for each x in g.h2h
            hs = spS(x.hs)
            ascore = spS(x["as"])
            ipAdd({ rt: "t", t1: spS(x.a) + " " + ascore + "  –  " + hs + " " + spS(x.h), t2: spDate(spIso(x.d)), bold: true })
        end for
    end if
    gLeaders(e, g, "Líderes de la temporada")
    ipAdd({ rt: "h", t1: "Cuándo y dónde" })
    ipAdd({ rt: "t", t1: spWhen(e.t) + "  ·  " + spCountdown(e.t) })
    if spS(g.venue) <> "" then
        v = spS(g.venue)
        if spS(g.city) <> "" then v = v + ", " + spS(g.city)
        ipAdd({ rt: "t", t1: v })
    end if
    if ipCount(g.tv) > 0 then
        tv = []
        for each x in g.tv
            tv.Push(spS(x))
        end for
        ipAdd({ rt: "t", t1: "Transmite: " + spJoin(tv, ", ") })
    end if
    if g.temp <> invalid then
        tf = spN(g.temp)
        c = Int((tf - 32) * 5 / 9 + 0.5)
        t = "Clima: " + Str(c).Trim() + " °C"
        if g.rain <> invalid then t = t + " · " + spS(g.rain) + " % de lluvia"
        ipAdd({ rt: "t", t1: t })
    end if
    gInjuries(e, g.inj)
end sub

function spDate(t as integer) as string
    if t <= 0 then return ""
    d = spLocal(t)
    mm = ["ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic"]
    return Str(d.GetDayOfMonth()).Trim() + " " + mm[d.GetMonth() - 1] + " " + Str(d.GetYear()).Trim()
end function

sub gPlayers(e as object, g as object)
    for each p in g.players
        tm = gTeam(p.team)
        ipAdd({ rt: "h", t1: gTeamName(tm) })
        if type(p.groups) = "roArray" then
            for each grp in p.groups
                cols = []
                if type(grp.cols) = "roArray" then
                    for each c in grp.cols
                        cols.Push(spS(c))
                    end for
                end if
                cw = gCw(cols.Count(), 700)
                ipAdd({ rt: "cols", bold: true, t1: spS(grp.g), vals: cols, cw: cw })
                if type(grp.rows) = "roArray" then
                    for each r in grp.rows
                        vals = []
                        if type(r.v) = "roArray" then
                            for each v in r.v
                                vals.Push(spS(v))
                            end for
                        end if
                        subs = []
                        if spS(r.num) <> "" then subs.Push("#" + spS(r.num))
                        subs.Push(spS(r["pos"]))
                        if r.st = true then subs.Push("titular")
                        ipAdd({ rt: "cols", t1: spS(r.n), t2: spJoin(subs, " · "), vals: vals, cw: cw })
                    end for
                end if
            end for
        end if
    end for
end sub

sub gLineups(e as object, g as object)
    for each l in g.lineups
        tm = gTeam(l.team)
        title = gTeamName(tm)
        if spS(l.formation) <> "" then title = title + "  ·  " + spS(l.formation)
        ipAdd({ rt: "h", t1: title })
        if type(l.starters) = "roArray" then
            for each p in l.starters
                evs = []
                c3 = ""
                if type(p.ev) = "roArray" then
                    for each x in p.ev
                        k = spS(x)
                        if k = "gol" then
                            evs.Push("Gol")
                            c3 = "green"
                        else if k = "amarilla" then
                            evs.Push("Amarilla")
                            if c3 = "" then c3 = "orange"
                        else if k = "roja" then
                            evs.Push("Roja")
                            c3 = "red"
                        else if k = "cambio" then
                            evs.Push("Cambio")
                        end if
                    end for
                end if
                ipAdd({ rt: "t", pre: spS(p.num), t1: spS(p.n), t2: spS(p["pos"]), t3: spJoin(evs, " · "), c3: c3 })
            end for
        end if
    end for
end sub

sub gPlays(e as object, g as object)
    if spS(g.drive) <> "" then
        ipAdd({ rt: "h", t1: "Serie actual" })
        ipAdd({ rt: "t", t1: spTr(spS(g.drive)), wrap: true })
    end if
    ipAdd({ rt: "h", t1: "Jugada por jugada", t3: "la más reciente arriba" })
    for each p in g.plays
        ipAdd(gPlayRow(e, p))
    end for
end sub

sub gBets(e as object, g as object, o as object)
    ipAdd({ rt: "h", t1: "Ganador del partido (moneyline)" })
    sides = [[e.a, o.mlA, o.favA = true, o.favOpenA = true], [e.h, o.mlH, o.favH = true, o.favOpenH = true]]
    for each s in sides
        t = s[0]
        ml = s[1]
        now = ""
        op = ""
        if type(ml) = "roAssociativeArray" then
            now = spS(ml.now)
            op = spS(ml.open)
        end if
        ip = spImplied(now)
        t3 = spPm(now)
        if ip >= 0 then t3 = t3 + "   (" + Str(ip).Trim() + " %)"
        subs = []
        if s[2] then subs.Push("Favorito")
        if op <> "" then subs.Push("abrió " + spPm(op))
        ipAdd({ rt: "t", lg1: spS(t.logo), ab1: spInitials(t), col1: spS(t.col), t1: spS(t.n), t2: spJoin(subs, " · "), t3: t3 })
    end for
    if spS(o.mlD) <> "" then
        ip = spImplied(spS(o.mlD))
        t3 = spPm(spS(o.mlD))
        if ip >= 0 then t3 = t3 + "   (" + Str(ip).Trim() + " %)"
        ipAdd({ rt: "t", t1: "Empate", t3: t3 })
    end if
    ipAdd({ rt: "t", t1: "El porcentaje es la probabilidad que implica el momio.", dim: true })
    if type(o.spH) = "roAssociativeArray" and spS(o.spH.now) <> "" then
        ipAdd({ rt: "h", t1: "Hándicap (spread)" })
        for each s in [[e.a, o.spA], [e.h, o.spH]]
            t = s[0]
            ln = s[1]
            if type(ln) = "roAssociativeArray" then
                t3 = spPm(spS(ln.now))
                if spS(ln.oddsNow) <> "" then t3 = t3 + "   (" + spPm(spS(ln.oddsNow)) + ")"
                t2 = ""
                if spS(ln.open) <> "" then t2 = "abrió " + spPm(spS(ln.open))
                ipAdd({ rt: "t", lg1: spS(t.logo), ab1: spInitials(t), col1: spS(t.col), t1: spS(t.n), t2: t2, t3: t3 })
            end if
        end for
    end if
    ou = o.ou
    if type(ou) = "roAssociativeArray" and spS(ou.now) <> "" then
        title = "Total de puntos (más/menos)"
        if e.sp = "soccer" or e.sp = "hockey" then title = "Total de goles (más/menos)"
        if e.sp = "baseball" then title = "Total de carreras (más/menos)"
        ipAdd({ rt: "h", t1: title })
        t2 = ""
        c3 = ""
        opv = Val(spS(ou.open))
        nwv = Val(spS(ou.now))
        if spS(ou.open) <> "" and opv <> nwv then
            if nwv > opv then t2 = "Abrió en " + spS(ou.open) + ": subió" else t2 = "Abrió en " + spS(ou.open) + ": bajó"
        end if
        ipAdd({ rt: "t", t1: "Línea " + spS(ou.now), t2: t2, t3: "Más " + spPm(spS(ou.over)) + "  ·  Menos " + spPm(spS(ou.under)), bold: true })
    end if
    moves = []
    if (o.favOpenH = true and o.favA = true) or (o.favOpenA = true and o.favH = true) then
        was = e.a.n
        if o.favOpenH = true then was = e.h.n
        isNow = e.a.n
        if o.favH = true then isNow = e.h.n
        moves.Push("Cambió el favorito: abrió " + spS(was) + " y ahora es " + spS(isNow) + ".")
    end if
    for each s in sides
        ml = s[1]
        if type(ml) = "roAssociativeArray" then
            op = spS(ml.open)
            now = spS(ml.now)
            if op <> "" and now <> "" and Val(op) <> Val(now) then
                tag = "menos apostado"
                if spImplied(now) > spImplied(op) then tag = "más apostado"
                moves.Push(spS(s[0].n) + ": de " + spPm(op) + " a " + spPm(now) + " (" + tag + ").")
            end if
        end if
    end for
    ipAdd({ rt: "h", t1: "Movimiento de la línea" })
    if moves.Count() = 0 then moves.Push("Sin cambios importantes desde que abrió.")
    for each x in moves
        ipAdd({ rt: "t", t1: x, wrap: true })
    end for
    if ipCount(g.ats) > 0 then
        ipAdd({ rt: "h", t1: "Contra el hándicap (ATS)" })
        names = { overall: "Total", home: "Local", away: "Visitante", road: "Visitante", favorite: "De favorito", underdog: "De no favorito" }
        for each t in g.ats
            tm = gTeam(t.team)
            parts = []
            if type(t.recs) = "roArray" then
                for each r in t.recs
                    k = LCase(spS(r.t))
                    lbl = names[k]
                    if lbl = invalid then lbl = spS(r.t)
                    parts.Push(lbl + " " + spS(r.s))
                end for
            end if
            ipAdd({ rt: "t", lg1: spS(tm.logo), ab1: spInitials(tm), col1: spS(tm.col), t1: gTeamName(tm), t2: spJoin(parts, "  ·  ") })
        end for
    end if
end sub

sub gInjuries(e as object, inj as dynamic)
    if ipCount(inj) = 0 then return
    for each t in inj
        tm = gTeam(t.team)
        ipAdd({ rt: "h", t1: "Lesionados de " + gTeamName(tm) })
        if type(t.list) = "roArray" then
            for each x in t.list
                ipAdd(spInjRow(x))
            end for
        end if
    end for
end sub

function spInjRow(x as object) as object
    n = spS(x.n)
    if spS(x["pos"]) <> "" then n = n + " (" + spS(x["pos"]) + ")"
    st = spS(x.st)
    c3 = ""
    if st = "Fuera" or st = "Lista de lesionados" or st = "Suspendido" then
        c3 = "red"
    else if st = "En duda" or st = "Dudoso" or st = "Día a día" then
        c3 = "orange"
    end if
    t2 = spS(x.d)
    if spS(x.ret) <> "" then t2 = spJoin([t2, "regreso estimado " + spDate(spIso(x.ret))], " · ")
    return { rt: "t", t1: n, t2: t2, t3: st, c3: c3 }
end function

' ---------- ficha del equipo ----------
sub ipTeam(st as object)
    key = st.lg + ":" + st.id
    ce = spGet("team:" + key, 600)
    d = invalid
    if ce <> invalid then d = ce.v
    tm = st.t
    if d <> invalid and type(d.team) = "roAssociativeArray" then
        tm = d.team
        if spS(tm.logo) = "" then tm.logo = spS(st.t.logo)
    end if
    parts = []
    if d <> invalid and type(d.team) = "roAssociativeArray" then
        t = d.team
        if spS(t.rec) <> "" then parts.Push("Récord " + spS(t.rec))
        if spS(t.home) <> "" then parts.Push("Local " + spS(t.home))
        if spS(t.away) <> "" then parts.Push("Visitante " + spS(t.away))
        if spS(t.standing) <> "" then parts.Push(spS(t.standing))
        sk = spS(d.streak)
        if Len(sk) >= 2 then
            nn = Left(sk, Len(sk) - 1)
            kk = Right(sk, 1)
            word = "ganados"
            if kk = "P" then word = "perdidos"
            if kk = "E" then word = "empates"
            if nn = "1" then word = Left(word, Len(word) - 1)
            parts.Push("Racha: " + nn + " " + word)
        end if
    end if
    full = spS(tm.full)
    if full = "" then full = spS(tm.n)
    ipHeadPlain(spLgName(st.lg), full, spJoin(parts, "   ·   "), tm)
    tabs = [{ k: "games", t: "Partidos" }]
    if st.lg <> "lmp" and st.lg <> "ufc" then tabs.Push({ k: "table", t: "Tabla" })
    if d <> invalid then
        if ipCount(d.inj) > 0 then tabs.Push({ k: "inj", t: "Lesionados" })
        if ipCount(d.leaders) > 0 then tabs.Push({ k: "lead", t: "Líderes" })
        if ipCount(d.news) > 0 then tabs.Push({ k: "news", t: "Noticias" })
    end if
    cur = spS(st.tab)
    ok = false
    for each t in tabs
        if t.k = cur then ok = true
    end for
    if not ok then cur = "games"
    st.tab = cur
    ipSetTabs(tabs, cur)
    m.ipFoot.text = "Datos de ESPN y TheSportsDB."
    following = m.teams.DoesExist(key)
    if cur = "games" then
        tObj = { id: st.id, n: spS(tm.n), full: full, ab: spS(tm.ab), logo: spS(tm.logo), col: spS(tm.col) }
        if following then
            ipAdd({ rt: "btn", t1: "Siguiendo a " + spS(tm.n) + " (toca para dejar de seguir)", act: "follow", lg: st.lg, t: tObj })
        else
            ipAdd({ rt: "btn", t1: "Seguir a " + spS(tm.n), act: "follow", lg: st.lg, t: tObj })
        end if
        if m.nospoil.DoesExist(key) then
            ipAdd({ rt: "btn", t1: "Sin marcadores: ACTIVADO (no te enseña resultados de este equipo)", act: "nospoil", key: key })
        else
            ipAdd({ rt: "btn", t1: "Sin marcadores: desactivado (para verlo grabado sin saber quién ganó)", act: "nospoil", key: key })
        end if
        if d = invalid then
            if ce = invalid then ipAdd({ rt: "t", t1: "Cargando el calendario…", dim: true }) else ipAdd({ rt: "t", t1: "No se pudo cargar la ficha. Intenta de nuevo en un momento." })
            return
        end if
        nx = d["next"]
        if ipCount(nx) > 0 then
            ipAdd({ rt: "h", t1: "Próximos partidos" })
            for each gm in nx
                ipAdd(ipTeamGameRow(st.lg, gm, false, key))
            end for
        end if
        past = d.past
        if ipCount(past) > 0 then
            ipAdd({ rt: "h", t1: "Resultados" })
            for i = past.Count() - 1 to 0 step -1
                ipAdd(ipTeamGameRow(st.lg, past[i], true, key))
            end for
        end if
    else if cur = "table" then
        tce = spGet("table:" + st.lg, 1800)
        if tce = invalid then
            ipAdd({ rt: "t", t1: "Cargando la tabla…", dim: true })
        else if tce.v = invalid then
            ipAdd({ rt: "t", t1: "No se pudo cargar la tabla." })
        else
            shown = false
            if type(tce.v.groups) = "roArray" then
                for each grp in tce.v.groups
                    has = false
                    if type(grp.rows) = "roArray" then
                        for each r in grp.rows
                            if spS(r.id) = st.id then has = true
                        end for
                    end if
                    if has then
                        ipTableGroup(st.lg, grp, tce.v.cols, st.id)
                        shown = true
                    end if
                end for
            end if
            if not shown then ipAdd({ rt: "t", t1: "No encontré a este equipo en la tabla de posiciones." })
        end if
    else if cur = "inj" then
        for each x in d.inj
            ipAdd(spInjRow(x))
        end for
    else if cur = "lead" then
        for each x in d.leaders
            ipAdd({ rt: "t", t1: spS(x.n), t2: spS(x.cat), t3: spS(x.v) })
        end for
    else if cur = "news" then
        for each x in d.news
            ipAdd({ rt: "t", t1: spS(x.t), t2: spS(x.d) + "  ·  " + spDate(spIso(x.at)) })
        end for
    end if
end sub

function ipTeamGameRow(lg as string, gm as object, past as boolean, teamKey as string) as object
    opp = gm.opp
    if type(opp) <> "roAssociativeArray" then opp = {}
    pre = "en "
    if gm.home = true then pre = "vs "
    t = spIso(gm.start)
    subs = [spWhen(t)]
    if ipCount(gm.tv) > 0 then
        tv = []
        for each x in gm.tv
            tv.Push(spS(x))
        end for
        subs.Push(spJoin(tv, ", "))
    end if
    r = { rt: "t", lg1: spS(opp.logo), ab1: spInitials(opp), col1: spS(opp.col), t1: pre + spS(opp.n), t2: spJoin(subs, "  ·  ") }
    if past then
        if m.nospoil.DoesExist(teamKey) then
            r.t3 = "—"
        else
            res = spS(gm.res)
            r.t3 = spJoin([res, spS(gm.us) + "-" + spS(gm.them)], " ")
            if res = "G" then r.c3 = "green"
            if res = "P" then r.c3 = "red"
        end if
    end if
    k = lg + ":" + spS(gm.id)
    if m.sp.byKey.DoesExist(k) then
        r.act = "ev"
        r.key = k
    else if lg <> "lmp" and lg <> "ufc" and spS(gm.id) <> "" then
        r.act = "game"
        r.key = k
        r.lg = lg
        r.id = spS(gm.id)
    end if
    return r
end function

' ---------- tablas de posiciones ----------
sub ipTableGroup(lg as string, grp as object, cols as dynamic, hlId as string)
    ipAdd({ rt: "h", t1: spS(grp.n) })
    hc = []
    if type(cols) = "roArray" then
        for each c in cols
            hc.Push(spS(c))
        end for
    end if
    cw = gCw(hc.Count(), 520)
    ipAdd({ rt: "cols", bold: true, pre: "#", t1: "Equipo", vals: hc, cw: cw })
    if type(grp.rows) <> "roArray" then return
    i = 0
    for each r in grp.rows
        i = i + 1
        vals = []
        if type(r.v) = "roArray" then
            for each v in r.v
                vals.Push(spS(v))
            end for
        end if
        seed = spS(r.seed)
        if seed = "" or seed = "0" then seed = Str(i).Trim()
        t = { id: spS(r.id), n: spS(r.n), full: spS(r.n), ab: spS(r.ab), logo: spS(r.logo), col: "" }
        mine = m.teams.DoesExist(lg + ":" + t.id) or t.id = hlId
        ipAdd({ rt: "cols", pre: seed, lg1: t.logo, ab1: spInitials(t), t1: t.n, vals: vals, cw: cw, hl: mine, act: "team", lg: lg, t: t })
    end for
end sub

function ipLeagueTabs(withAll as boolean) as object
    tabs = []
    for each l in m.spLg
        if withAll or (l.k <> "lmp" and l.k <> "ufc") then tabs.Push({ k: l.k, t: l.short })
    end for
    return tabs
end function

sub ipTable(st as object)
    lg = spS(st.tab)
    ipHeadPlain("Deportes", "Tablas de posiciones", spLgName(lg) + ". Elige una liga a la izquierda; OK en un equipo para ver su ficha.", invalid)
    ipSetTabs(ipLeagueTabs(false), lg)
    m.ipFoot.text = "Datos de ESPN."
    ce = spGet("table:" + lg, 1800)
    if ce = invalid then
        ipAdd({ rt: "t", t1: "Cargando la tabla…", dim: true })
        return
    end if
    if ce.v = invalid or ipCount(ce.v.groups) = 0 then
        ipAdd({ rt: "t", t1: "No hay tabla disponible para esta liga ahora." })
        return
    end if
    for each grp in ce.v.groups
        ipTableGroup(lg, grp, ce.v.cols, "")
    end for
end sub

' ---------- elegir mis equipos ----------
sub ipPick(st as object)
    lg = spS(st.tab)
    where = "Se guardan en tu perfil y en tus otros aparatos."
    if m.scope = "" then where = "Se guardan en esta tele."
    if m.guest then where = "Como invitado no se guardan."
    ipHeadPlain("Deportes", "Elegir mis equipos", "Toca un equipo para seguirlo: sale primero en Deportes y te avisamos cuando anota, aunque estés viendo otra cosa. " + where, invalid)
    ipSetTabs(ipLeagueTabs(true), lg)
    m.ipFoot.text = Str(m.teams.Count()).Trim() + " equipos seguidos."
    ce = spGet("teams:" + lg, 86400)
    if ce = invalid then
        ipAdd({ rt: "t", t1: "Cargando equipos…", dim: true })
        return
    end if
    if ce.v = invalid or ipCount(ce.v.teams) = 0 then
        ipAdd({ rt: "t", t1: "No se pudo cargar la lista de equipos de esta liga." })
        return
    end if
    for each t in ce.v.teams
        isOn = m.teams.DoesExist(lg + ":" + spS(t.id))
        r = { rt: "t", lg1: spS(t.logo), ab1: spInitials(t), col1: spS(t.col), t1: spS(t.full), act: "pickTeam", lg: lg, t: t }
        if isOn then
            r.t3 = "Siguiendo"
            r.c3 = "green"
            r.bold = true
        else
            r.t3 = "Seguir"
            r.c3 = ""
        end if
        ipAdd(r)
    end for
end sub

' ---------- quiniela de la casa ----------
sub ipQuin(st as object)
    ipHeadPlain("Deportes", "Quiniela de la casa", "Cada perfil elige quién gana. Sin dinero, solo por el gusto. La tabla cuenta los últimos 30 días.", invalid)
    ipSetTabs([], "")
    names = {}
    for each p in m.profiles
        names[p.id] = p.name
    end for
    since = nowSec() - 30 * 86400
    stand = {}
    for each k in m.picks
        p = m.picks[k]
        if p <> invalid and spS(p.r) <> "" and spMsToSec(p.s) > since then
            pr = spS(p.pr)
            if not stand.DoesExist(pr) then stand[pr] = { pr: pr, ok: 0, n: 0 }
            stand[pr].n = stand[pr].n + 1
            if spS(p.r) = spS(p.p) then stand[pr].ok = stand[pr].ok + 1
        end if
    end for
    arr = []
    for each k in stand
        arr.Push(stand[k])
    end for
    arr.SortBy("ok", "r")
    ipAdd({ rt: "h", t1: "Tabla de la casa" })
    if arr.Count() = 0 then
        ipAdd({ rt: "t", t1: "Todavía no hay resultados. Elige quién gana en los próximos partidos y aquí verás quién va ganando.", wrap: true })
    else
        rank = 0
        for each s in arr
            rank = rank + 1
            nm = names[s.pr]
            if nm = invalid then nm = "Perfil"
            ipAdd({ rt: "t", pre: Str(rank).Trim(), t1: nm, t3: spPlural(s.ok, "acierto", "aciertos") + " de " + Str(s.n).Trim(), hl: (s.pr = m.scope) })
        end for
    end if
    if m.scope = "" or m.guest then
        ipAdd({ rt: "h", t1: "Para jugar" })
        ipAdd({ rt: "t", t1: "Entra con un perfil para elegir quién gana (Mi cuenta → Unir a mi cuenta).", wrap: true })
        return
    end if
    ipAdd({ rt: "h", t1: "Elige quién gana", t3: "próximos partidos" })
    now = nowSec()
    up = []
    for each e in m.sp.events
        if e.st = "pre" and e.t < now + 2 * 86400 then up.Push(e)
    end for
    up = spTake(spMineFirst(up), 30)
    up.SortBy("t")
    if up.Count() = 0 then ipAdd({ rt: "t", t1: "No hay partidos por empezar en estos días.", dim: true })
    for each e in up
        my = m.picks[e.key + "|" + m.scope]
        t2 = spLgShort(e.lg) + " · " + spWhen(e.t)
        t3 = "Elegir"
        c3 = "orange"
        if my <> invalid then
            pk = spS(my.p)
            c3 = "green"
            if pk = "h" then
                t3 = "Elegiste " + spS(e.h.n)
            else if pk = "a" then
                t3 = "Elegiste " + spS(e.a.n)
            else
                t3 = "Elegiste empate"
            end if
        end if
        ipAdd({ rt: "t", lg1: spS(e.a.logo), ab1: spInitials(e.a), col1: spS(e.a.col), t1: spEvTitle(e), t2: t2, t3: t3, c3: c3, act: "ev", key: e.key })
    end for
    mine = []
    for each k in m.picks
        p = m.picks[k]
        if p <> invalid and spS(p.pr) = m.scope and spS(p.r) <> "" then
            p.sec = spMsToSec(p.s)
            mine.Push(p)
        end if
    end for
    if mine.Count() > 0 then
        mine.SortBy("sec", "r")
        ipAdd({ rt: "h", t1: "Tus resultados" })
        for each p in spTake(mine, 20)
            okp = (spS(p.r) = spS(p.p))
            t3 = "Fallaste"
            c3 = "red"
            if okp then
                t3 = "Acertaste"
                c3 = "green"
            end if
            ipAdd({ rt: "t", t1: spS(p.t), t2: spDate(p.sec), t3: t3, c3: c3 })
        end for
    end if
end sub

function spPlural(n as integer, one as string, many as string) as string
    if n = 1 then return "1 " + one
    return Str(n).Trim() + " " + many
end function

' ---------------- jugadas en español (ESPN las da en inglés, como en Android) ----------------
function spLooksEnglish(t as string) as boolean
    if m.trWordRe = invalid then m.trWordRe = CreateObject("roRegex", "[^a-z]+", "")
    words = m.trWordRe.Split(LCase(t))
    en = { "the": 1, "and": 1, "of": 1, "to": 1, "a": 1, "in": 1, "is": 1, "his": 1, "with": 1, "for": 1, "on": 1, "pass": 1, "yards": 1, "yard": 1, "yd": 1, "kick": 1, "run": 1, "rush": 1, "left": 1, "right": 1, "middle": 1, "short": 1, "deep": 1, "shot": 1, "makes": 1, "misses": 1, "foul": 1, "rebound": 1, "assists": 1, "goal": 1, "field": 1, "plays": 1, "sacked": 1, "timeout": 1, "warning": 1, "end": 1, "quarter": 1, "penalty": 1, "punts": 1, "incomplete": 1, "touchdown": 1, "jumper": 1, "layup": 1, "free": 1, "throw": 1, "turnover": 1, "defensive": 1, "offensive": 1, "shotgun": 1, "up": 1, "by": 1, "at": 1 }
    es = { "el": 1, "la": 1, "los": 1, "las": 1, "de": 1, "del": 1, "que": 1, "y": 1, "en": 1, "un": 1, "una": 1, "por": 1, "con": 1, "para": 1, "su": 1, "se": 1, "al": 1, "tras": 1 }
    a = 0
    b = 0
    for each w in words
        if w <> "" then
            if en.DoesExist(w) then b = b + 1
            if es.DoesExist(w) then a = a + 1
        end if
    end for
    return b > a
end function

' Regresa la traducción si ya está; si no, la pide (en grupo) y regresa el original por ahora
function spTr(t as string) as string
    k = t.Trim()
    if k = "" then return t
    v = m.trMemo[k]
    if v <> invalid then return v
    if not m.trAsked.DoesExist(k) and spLooksEnglish(k) then
        m.trAsked[k] = true
        m.trQueue.Push(k)
    end if
    return t
end function

sub spTrFlush()
    if m.trQueue.Count() = 0 then return
    if m.tr = invalid then
        m.tr = CreateObject("roSGNode", "TrTask")
        m.tr.observeField("res", "spOnTr")
        m.tr.control = "RUN"
    end if
    q = m.trQueue
    m.trQueue = []
    m.tr.req = q
end sub

sub spOnTr()
    r = m.tr.res
    if type(r) <> "roAssociativeArray" or r.Count() = 0 then return
    if m.trMemo.Count() > 3000 then m.trMemo = {}
    for each k in r
        m.trMemo[k] = r[k]
    end for
    if m.pgInfo.visible and m.ipCur <> invalid then ipRender(true)
    if m.pgSports.visible then spOnRowFocus()
end sub
