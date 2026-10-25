' Búsqueda al instante: baja una sola vez los nombres de películas, series y canales
' y filtra en este hilo cada vez que cambia "query" (sin trabar la pantalla).
sub init()
    m.top.functionName = "searchMain"
end sub

function fold(s as string) as string
    s = LCase(s)
    pairs = [["á","a"],["à","a"],["ä","a"],["â","a"],["é","e"],["è","e"],["ë","e"],["ê","e"],["í","i"],["ì","i"],["ï","i"],["î","i"],["ó","o"],["ò","o"],["ö","o"],["ô","o"],["ú","u"],["ù","u"],["ü","u"],["û","u"],["ñ","n"],["Á","a"],["É","e"],["Í","i"],["Ó","o"],["Ú","u"],["Ñ","n"],[":"," "],["-"," "],["."," "],["'",""]]
    for each p in pairs
        s = s.Replace(p[0], p[1])
    end for
    return s
end function

function getJson(url as string) as dynamic
    x = CreateObject("roUrlTransfer")
    x.SetUrl(url)
    x.SetCertificatesFile("common:/certs/ca-bundle.crt")
    x.InitClientCertificates()
    x.EnableEncodings(true)
    x.AddHeader("User-Agent", "CineCasaRoku/1.1")
    t = x.GetToString()
    if t = "" then return invalid
    return ParseJson(t)
end function

function str2(v as dynamic) as string
    if v = invalid then return ""
    t = type(v)
    if t = "roString" or t = "String" then return v
    if t = "roInt" or t = "Integer" or t = "roFloat" or t = "Float" or t = "Double" or t = "roDouble" or t = "LongInteger" or t = "roLongInteger" then return Str(v).Trim()
    return ""
end function

function index(d as dynamic, kind as string) as object
    out = []
    if type(d) <> "roArray" then return out
    for each it in d
        n = str2(it.name)
        if n <> "" then
            o = { k: kind, t: n, n: " " + fold(n) + " " }
            if kind = "series" then
                o.id = str2(it.series_id) : o.img = str2(it.cover)
            else
                o.id = str2(it.stream_id) : o.img = str2(it.stream_icon) : o.ext = str2(it.container_extension)
            end if
            out.Push(o)
        end if
    end for
    return out
end function

sub searchMain()
    port = CreateObject("roMessagePort")
    m.top.observeField("query", port)
    m.top.status = "loading"
    b = m.top.base
    m.idx = {}
    m.idx.movie = index(getJson(b + "&action=get_vod_streams"), "movie")
    m.idx.series = index(getJson(b + "&action=get_series"), "series")
    m.idx.live = index(getJson(b + "&action=get_live_streams"), "live")
    m.top.status = "ready"
    if m.top.query <> "" then doSearch(m.top.query)
    while true
        msg = wait(0, port)
        if type(msg) = "roSGNodeEvent" and msg.getField() = "query" then doSearch(msg.getData())
    end while
end sub

sub doSearch(q as string)
    words = []
    for each w in fold(q).Split(" ")
        if w <> "" then words.Push(w)
    end for
    res = { q: q, movie: [], series: [], live: [] }
    if words.Count() = 0 or Len(q.Trim()) < 2 then
        m.top.results = res
        return
    end if
    first = " " + words[0]
    for each kind in ["movie", "series", "live"]
        starts = []
        others = []
        for each it in m.idx[kind]
            ok = true
            for each w in words
                if Instr(1, it.n, w) = 0 then
                    ok = false
                    exit for
                end if
            end for
            if ok then
                if Instr(1, it.n, first) > 0 then starts.Push(it) else others.Push(it)
                if starts.Count() >= 40 then exit for
            end if
        end for
        for each it in others
            if starts.Count() >= 40 then exit for
            starts.Push(it)
        end for
        res[kind] = starts
    end for
    m.top.results = res
end sub
