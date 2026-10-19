' Hace una petición HTTP(S) fuera del hilo de la interfaz.
' request = { id, url, method ("GET"|"POST"), body (cadena JSON), headers (assocarray) }
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
    x.AddHeader("User-Agent", "CineCasaRoku/1.0")
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
        msg = wait(30000, port)
        if type(msg) = "roUrlEvent" then
            res.code = msg.GetResponseCode()
            res.body = msg.GetString()
        else
            x.AsyncCancel()
        end if
    end if
    m.top.response = res
end sub
