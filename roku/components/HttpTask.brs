' Hace una petición HTTP(S) fuera del hilo de la interfaz.
' request = { id, url, method ("GET"|"POST"), body (cadena JSON), headers (assocarray),
'             parse (bool: devuelve data ya convertido), sortKey ("added"|"last_modified"), limit (int),
'             filter (texto en minúsculas a buscar en "name") }
sub init()
    m.top.functionName = "doRequest"
end sub

sub doRequest()
    r = m.top.request
    port = CreateObject("roMessagePort")
    x = CreateObject("roUrlTransfer")
    x.SetMessagePort(port)
    x.SetUrl(r.url)
    x.SetCertificatesFile("common:/certs/ca-bundle.crt")
    x.InitClientCertificates()
    x.EnableEncodings(true)
    x.RetainBodyOnError(true)
    x.AddHeader("User-Agent", "CineCasaRoku/1.1")
    if r.headers <> invalid then
        for each k in r.headers
            x.AddHeader(k, r.headers[k])
        end for
    end if
    ok = false
    if r.method = "POST" then
        x.AddHeader("Content-Type", "application/json")
        ok = x.AsyncPostFromString(r.body)
    else
        ok = x.AsyncGetToString()
    end if
    res = { id: r.id, code: -1, body: "" }
    if ok then
        msg = wait(45000, port)
        if type(msg) = "roUrlEvent" then
            res.code = msg.GetResponseCode()
            res.body = msg.GetString()
        else
            x.AsyncCancel()
        end if
    end if
    if r.parse = true then
        d = invalid
        if res.body <> "" then d = ParseJson(res.body)
        if type(d) = "roArray" then
            if r.filter <> invalid and r.filter <> "" then
                out = []
                for each it in d
                    if it.name <> invalid and type(it.name) = "roString" and Instr(1, LCase(it.name), r.filter) > 0 then out.Push(it)
                    if out.Count() >= 120 then exit for
                end for
                d = out
            end if
            if r.sortKey <> invalid and r.sortKey <> "" then
                k = r.sortKey
                for each it in d
                    v = it[k]
                    n = 0
                    if v <> invalid then
                        if type(v) = "roString" or type(v) = "String" then n = Val(v) else n = v
                    end if
                    it["_s"] = n
                end for
                d.SortBy("_s", "r")
            end if
            if r.limit <> invalid and r.limit > 0 and d.Count() > r.limit then
                out = []
                for i = 0 to r.limit - 1
                    out.Push(d[i])
                end for
                d = out
            end if
        end if
        res.data = d
        res.body = ""
    end if
    m.top.response = res
end sub
