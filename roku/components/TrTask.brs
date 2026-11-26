' Traduce al español las jugadas que ESPN da en inglés (igual que la app de Android),
' en grupos, sin trabar la pantalla. req = lista de textos; res = { texto: traducción }.
sub init()
    m.top.functionName = "trMain"
end sub

sub trMain()
    port = CreateObject("roMessagePort")
    m.top.observeField("req", port)
    ' lo que pidieron antes de que este hilo empezara a escuchar
    first = m.top.req
    if type(first) = "roArray" and first.Count() > 0 then doAll(first)
    while true
        msg = wait(0, port)
        if type(msg) = "roSGNodeEvent" then
            lst = msg.getData()
            if type(lst) = "roArray" and lst.Count() > 0 then doAll(lst)
        end if
    end while
end sub

sub doAll(lst as object)
    out = {}
    batch = []
    size = 0
    for each t in lst
        if batch.Count() > 0 and (size + Len(t) > 1500 or batch.Count() >= 20) then
            trBatch(batch, out)
            batch = []
            size = 0
        end if
        batch.Push(t)
        size = size + Len(t) + 1
    end for
    if batch.Count() > 0 then trBatch(batch, out)
    m.top.res = out
end sub

sub trBatch(batch as object, out as object)
    q = ""
    for i = 0 to batch.Count() - 1
        if i > 0 then q = q + Chr(10)
        q = q + batch[i]
    end for
    x = CreateObject("roUrlTransfer")
    x.SetUrl("https://translate.googleapis.com/translate_a/single?client=gtx&sl=en&tl=es&dt=t&q=" + x.Escape(q))
    x.SetCertificatesFile("common:/certs/ca-bundle.crt")
    x.InitClientCertificates()
    x.AddHeader("User-Agent", "Mozilla/5.0")
    body = x.GetToString()
    if body = "" then return
    d = ParseJson(body)
    if type(d) <> "roArray" or d.Count() = 0 or type(d[0]) <> "roArray" then return
    full = ""
    for each seg in d[0]
        if type(seg) = "roArray" and seg.Count() > 0 and seg[0] <> invalid then full = full + seg[0]
    end for
    parts = full.Split(Chr(10))
    if parts.Count() <> batch.Count() then
        ' si no cuadran las líneas, se traducen de una en una
        if batch.Count() > 1 then
            for each t in batch
                trBatch([t], out)
            end for
        end if
        return
    end if
    for i = 0 to batch.Count() - 1
        v = parts[i].Trim()
        if v <> "" then out[batch[i]] = v
    end for
end sub
