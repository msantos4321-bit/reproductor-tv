' CineCasa para Roku: inicio de sesión Xtream, activación (prueba automática / código),
' secciones En vivo, Películas y Series por categoría, y reproductor.

sub init()
    m.cloud = "https://wnwecaapchcfhcljsvli.supabase.co/rest/v1/rpc/"
    m.key = "sb_publishable_vnkSLd5EzN53_LBv76YxUA_QRo2u8ok"
    m.demo = "https://wnwecaapchcfhcljsvli.supabase.co/functions/v1/demo"
    m.reg = CreateObject("roRegistrySection", "cinecasa")
    m.tasks = {}
    m.reqN = 0

    m.sub = m.top.findNode("sub")
    m.form = m.top.findNode("form")
    m.formTitle = m.top.findNode("formTitle")
    m.formText = m.top.findNode("formText")
    m.formList = m.top.findNode("formList")
    m.formMsg = m.top.findNode("formMsg")
    m.home = m.top.findNode("home")
    m.sections = m.top.findNode("sections")
    m.cats = m.top.findNode("cats")
    m.grid = m.top.findNode("grid")
    m.info = m.top.findNode("info")
    m.video = m.top.findNode("video")
    m.qrBox = m.top.findNode("qrBox")
    m.qr = m.top.findNode("qr")
    m.qrCode = m.top.findNode("qrCode")
    m.pairTimer = m.top.findNode("pairTimer")
    m.pairTimer.observeField("fire", "onPairTick")
    m.pingTimer = m.top.findNode("pingTimer")
    m.pingTimer.observeField("fire", "onPingTick")
    m.qrTitle = m.top.findNode("qrTitle")
    m.qrText = m.top.findNode("qrText")
    m.qrUrl = m.top.findNode("qrUrl")
    m.owner = false
    m.ownerAsked = false
    m.vsrv = invalid        ' servidor que puso el vendedor (url, name)
    m.vsrvAll = []
    m.vsrvAsked = false
    m.customSrv = false
    m.formItems = []
    m.pairCode = ""
    m.hpCode = ""

    m.formList.observeField("itemSelected", "onFormSelect")
    m.video.observeField("state", "onVideoState")
    homeInit()

    di = CreateObject("roDeviceInfo")
    m.deviceId = "roku-" + di.GetChannelClientId()
    m.deviceName = "Roku " + di.GetModelDisplayName()
    d = CreateObject("roEVPDigest")
    d.Setup("sha256")
    ba = CreateObject("roByteArray")
    ba.FromAsciiString("cinecasa:roku:" + di.GetChannelClientId())
    m.fp = d.Process(ba)

    m.server = rget("server")
    m.user = rget("user")
    m.pass = rget("pass")
    m.code = rget("code")
    start()
end sub

' ---------------- almacenamiento ----------------
function rget(k as string) as string
    if m.reg.Exists(k) then return m.reg.Read(k)
    return ""
end function

sub rset(k as string, v as string)
    m.reg.Write(k, v)
    m.reg.Flush()
end sub

' ---------------- red ----------------
sub request(id as string, url as string, method as string, body as string, headers as object)
    m.reqN = m.reqN + 1
    t = CreateObject("roSGNode", "HttpTask")
    t.request = { id: id, url: url, method: method, body: body, headers: headers }
    t.observeField("response", "onResponse")
    m.tasks[id] = t   ' se guarda para que no se libere antes de terminar
    t.control = "RUN"
end sub

sub rpc(id as string, fn as string, args as object)
    request(id, m.cloud + fn, "POST", FormatJson(args), { apikey: m.key })
end sub

function base() as string
    s = m.server.Trim()
    if Left(LCase(s), 4) <> "http" then s = "http://" + s
    i = Instr(1, LCase(s), "/player_api.php")
    if i > 0 then s = Left(s, i - 1)
    while Right(s, 1) = "/"
        s = Left(s, Len(s) - 1)
    end while
    return s
end function

function api(action as string) as string
    u = base() + "/player_api.php?username=" + m.user.EncodeUriComponent() + "&password=" + m.pass.EncodeUriComponent()
    if action <> "" then u = u + "&action=" + action
    return u
end function

sub onResponse(e as object)
    r = e.getData()
    id = r.id
    data = r.data
    if data = invalid and r.body <> invalid and r.body <> "" then data = ParseJson(r.body)
    if id = "login" then
        onLogin(r.code, data)
    else if id = "check" or id = "ping" then
        onPing(id = "check", r.code, data)
    else if id = "owner" then
        onOwner(r.body)
    else if id = "lsrv" then
        onLsrv(data)
    else if id = "help" then
        onHelp(data)
    else if id = "trial" then
        onTrial(data)
    else if id = "activate" then
        onActivate(r.body)
    else if id = "prof" then
        onProfiles(data)
    else if id = "lib" then
        onLib(data)
    else if id = "recentMovies" or id = "recentSeries" then
        onRecent(id, data)
    else if Left(id, 5) = "cats:" then
        onCats(id, data)
    else if Left(id, 6) = "items:" then
        onItems(id, data)
    else if Left(id, 5) = "srch:" then
        onSearch(id, data)
    else if id = "vinfo" then
        onVodInfo(data)
    else if id = "sinfo" then
        onSeriesInfo(data)
    else if id = "pairNew" then
        onPairNew(r.body)
    else if id = "pairGet" then
        onPairGet(data)
    else if id = "hpPoll" then
        onHpPoll(r.body)
    end if
    m.tasks.Delete(id)
end sub

' ---------------- flujo ----------------
' Orden: 1) activación (código o prueba)  2) usuario y contraseña (la liga la pone el vendedor)  3) inicio
sub start()
    if m.code = "" and not m.owner then
        hh = rget("hh") : hs = rget("hs")
        if hh <> "" and hs <> "" and not m.ownerAsked then
            m.ownerAsked = true
            m.sub.text = "Revisando tu cuenta…"
            rpc("owner", "cc_is_owner", { p_h: hh, p_s: hs })
            return
        end if
        showActivation("")
    else if m.server = "" or m.user = "" or m.pass = "" then
        loginStart("")
    else if m.owner then
        showHome()
        loadVendorServer()
    else
        m.sub.text = "Revisando tu activación…"
        ping("check")
    end if
end sub

sub onOwner(body as string)
    if body.Trim() = "true" then m.owner = true
    start()
end sub

' ---------- revisión periódica (cada 3 min): activación, servidor del vendedor y «en uso ahora» ----------
sub ping(id as string)
    rpc(id, "cc_lic_ping", { p_code: m.code, p_device: m.deviceId, p_playing: m.video.visible, p_model: m.deviceName, p_version: "1.2.0" })
end sub

sub onPingTick()
    if m.code <> "" and not m.owner then ping("ping")
end sub

sub onPing(first as boolean, code as integer, data as object)
    st = ""
    if data <> invalid and type(data) = "roAssociativeArray" then st = Str2(data.status)
    if code <> 200 or st = "" then
        ' sin internet hacia la nube: deja ver (se revisa la próxima vez)
        if first then
            showHome()
            m.pingTimer.control = "start"
        end if
        return
    end if
    if st = "ok" then
        followServer(data.server)
        if data.server <> invalid then m.vsrv = data.server
        if data.servers <> invalid then m.vsrvAll = data.servers
        if first then
            showHome()
            m.pingTimer.control = "start"
        end if
        return
    end if
    if st = "removed" then
        ' se liberaron los aparatos del código: intenta volver a entrar
        m.pendingCode = m.code
        rpc("activate", "cc_lic_activate2", { p_code: m.code, p_device: m.deviceId, p_name: m.deviceName, p_fp: m.fp })
        return
    end if
    m.pingTimer.control = "stop"
    if m.video.visible then stopVideo()
    msg = "Tu activación ya no es válida."
    if st = "expired" then msg = "Tu plan venció. Pide tu renovación a quien te lo vendió; tu código sigue siendo el mismo."
    if st = "revoked" then msg = "Tu código fue desactivado."
    if st = "blocked" then
        msg = "Esta tele se quitó de la cuenta desde otro aparato o por tu vendedor. Si fue un error, pide que la permitan otra vez."
        ' se borran aquí los datos de la cuenta
        m.reg.Delete("server") : m.reg.Delete("user") : m.reg.Delete("pass") : m.reg.Delete("vs")
        m.server = "" : m.user = "" : m.pass = ""
    end if
    m.code = "" : m.reg.Delete("code") : m.reg.Flush()
    showActivation(msg)
end sub

' Si la cuenta usa el servidor del vendedor y él cambió la liga, se toma sola
sub followServer(srv as dynamic)
    if srv = invalid or rget("vs") <> "1" then return
    u = Str2(srv.url)
    if u = "" or m.server = "" then return
    if u <> m.server then
        m.server = u
        rset("server", u)
    end if
end sub

sub loadVendorServer()
    if m.vsrvAsked then return
    m.vsrvAsked = true
    if m.owner then
        rpc("lsrv", "cc_owner_server", { p_h: rget("hh"), p_s: rget("hs") })
    else if m.code <> "" then
        rpc("lsrv", "cc_lic_server", { p_code: m.code, p_device: m.deviceId })
    end if
end sub

sub onLsrv(data as object)
    if data <> invalid and type(data) = "roAssociativeArray" then
        if data.server <> invalid and type(data.server) = "roAssociativeArray" then m.vsrv = data.server
        if data.servers <> invalid then m.vsrvAll = data.servers
    end if
    if m.mode = "login" then showLogin(m.formMsg.text)
end sub

function useVendor() as boolean
    return m.vsrv <> invalid and not m.customSrv and Str2(m.vsrv.url) <> ""
end function

sub loginStart(msg as string)
    loadVendorServer()
    showLogin(msg)
end sub

' ---------- ayuda del vendedor por WhatsApp (QR en la tele) ----------
sub askHelp()
    m.helpFrom = m.mode
    m.formMsg.text = "Buscando el WhatsApp de tu vendedor…"
    rpc("help", "cc_lic_info", { p_code: m.code, p_device: m.deviceId })
end sub

sub onHelp(data as object)
    ph = ""
    if data <> invalid and type(data) = "roAssociativeArray" then ph = Str2(data.help_phone)
    digits = ""
    for k = 1 to Len(ph)
        ch = Mid(ph, k, 1)
        if ch >= "0" and ch <= "9" then digits = digits + ch
    end for
    if Len(digits) < 10 then
        m.formMsg.text = "Tu vendedor todavía no puso su WhatsApp. Escríbele por donde te vendió CineCasa."
        return
    end if
    wa = digits
    if Len(wa) = 10 then wa = "52" + wa
    txt = "Hola, necesito ayuda con CineCasa en mi tele Roku. Mi código es " + m.code + "."
    showForm("Ayuda de tu vendedor", "Escanea el código con tu celular para escribirle por WhatsApp, o márcale al " + digits + ". Ya va tu código en el mensaje.", ["Volver"], "help")
    m.qrTitle.text = "WhatsApp de tu vendedor"
    m.qrText.text = "Abre la cámara de tu celular y apunta al código."
    m.qrUrl.text = digits
    m.qrCode.text = ""
    m.qr.uri = "https://api.qrserver.com/v1/create-qr-code/?size=320x320&margin=0&data=" + ("https://wa.me/" + wa + "?text=" + txt.EncodeUriComponent()).EncodeUriComponent()
    m.qrBox.visible = true
end sub

' ---------- datos que manda el celular al instalar ----------
sub onLaunchArgs()
    a = m.top.launchArgs
    if a = invalid then return
    hh = Str2(a.hh) : hs = Str2(a.hs)
    if hh <> "" and hs <> "" then
        rset("hh", hh) : rset("hs", hs)
        m.ownerAsked = false
    end if
    srv = Str2(a.srv) : usr = Str2(a.usr) : pwd = Str2(a.pwd)
    if srv <> "" and usr <> "" and pwd <> "" then
        m.server = srv : m.user = usr : m.pass = pwd
        rset("server", srv) : rset("user", usr) : rset("pass", pwd)
        if Str2(a.vs) = "1" then rset("vs", "1") else rset("vs", "0")
    end if
    lic = UCase(Str2(a.lic))
    if lic <> "" and lic <> m.code then
        m.pendingCode = lic
        showForm("Configurando con tu celular…", "Tu tele está recibiendo los datos de tu celular. Espera unos segundos.", ["Esperar"], "wait")
        m.formMsg.text = "Activando…"
        rpc("activate", "cc_lic_activate2", { p_code: lic, p_device: m.deviceId, p_name: m.deviceName, p_fp: m.fp })
    else
        start()
    end if
end sub

sub showForm(title as string, text as string, items as object, mode as string)
    m.mode = mode
    if m.brand <> invalid then m.brand.visible = true
    m.sub.visible = true
    if mode <> "login" then stopPairing()
    m.home.visible = false
    m.video.visible = false
    m.form.visible = true
    m.formTitle.text = title
    m.formText.text = text
    c = CreateObject("roSGNode", "ContentNode")
    for each it in items
        n = c.createChild("ContentNode")
        n.title = it
    end for
    m.formList.content = c
    m.formList.setFocus(true)
end sub

sub showLogin(msg as string)
    m.sub.text = "Reproductor para tu tele"
    usr = m.user
    if usr = "" then usr = "(toca para escribir)"
    pw = "(toca para escribir)"
    if m.pass <> "" then pw = "••••••"
    items = []
    if useVendor() then
        items.Push("Usuario: " + usr)
        items.Push("Contraseña: " + pw)
        items.Push("Entrar")
        items.Push("Servidor: " + Str2(m.vsrv.name) + " (cambiar)")
        title = "Escribe tu usuario y contraseña"
        txt = "La liga del servidor ya viene puesta por quien te vendió CineCasa. Solo escribe el usuario y la contraseña de tu servicio."
    else
        srv = m.server
        if srv = "" then srv = "(toca para escribir)"
        items.Push("Servidor: " + srv)
        items.Push("Usuario: " + usr)
        items.Push("Contraseña: " + pw)
        items.Push("Entrar")
        if m.vsrv <> invalid then items.Push("Usar el servidor de mi vendedor")
        title = "Escribe los datos que te dio tu proveedor"
        txt = "CineCasa es un reproductor: funciona con el servicio que tú ya tienes. Tus datos solo se guardan en esta tele."
    end if
    items.Push("Ver demostración")
    if m.code <> "" then items.Push("Pedir ayuda a mi vendedor")
    m.formItems = items
    startPairing()
    showForm(title, txt, items, "login")
    m.formMsg.text = msg
end sub

' Elegir entre los servidores del vendedor o escribir otro
sub showServerPick()
    items = []
    for each v in m.vsrvAll
        items.Push(Str2(v.name))
    end for
    items.Push("Escribir otro servidor")
    items.Push("Volver")
    m.formItems = items
    showForm("Elige el servidor", "Son los servidores que tiene guardados tu vendedor. Si tu proveedor te dio otra liga, elige «Escribir otro servidor».", items, "srvpick")
end sub

sub showActivation(msg as string)
    m.sub.text = "Activa este aparato"
    ref = rget("ref")
    refTxt = "¿Quién te recomendó? (opcional)"
    if ref <> "" then refTxt = "Te recomendó: " + ref
    showForm("Pruébala gratis 21 días", "Sin pedir código: tu prueba se activa al momento en hasta 5 aparatos. Si alguien te recomendó, escribe su código para que reciba su regalo cuando contrates.", ["Empezar prueba gratis", refTxt, "Ya tengo un código", "Unir a mi cuenta (desde mi celular o tablet)"], "activation")
    m.formMsg.text = msg
end sub

sub onFormSelect()
    i = m.formList.itemSelected
    if m.mode = "login" then
        t = m.formItems[i]
        if Left(t, 9) = "Servidor:" then
            if useVendor() then showServerPick() else ask("Servidor (ej. http://servidor.com:8080)", m.server, "server")
        else if Left(t, 8) = "Usuario:" then
            ask("Usuario", m.user, "user")
        else if Left(t, 11) = "Contraseña:" then
            ask("Contraseña", "", "pass")
        else if t = "Entrar" then
            doLogin()
        else if Left(t, 9) = "Usar el s" then
            m.customSrv = false : showLogin("")
        else if Left(t, 5) = "Pedir" then
            askHelp()
        else if Left(t, 3) = "Ver" then
            m.customSrv = true
            m.server = m.demo : m.user = "demo" : m.pass = "demo"
            doLogin()
        end if
    else if m.mode = "srvpick" then
        if i < m.vsrvAll.Count() then
            m.vsrv = m.vsrvAll[i] : m.customSrv = false : showLogin("")
        else if m.formItems[i] = "Escribir otro servidor" then
            m.customSrv = true : m.server = "" : showLogin("")
            ask("Servidor (ej. http://servidor.com:8080)", "", "server")
        else
            showLogin("")
        end if
    else if m.mode = "help" then
        if m.helpFrom = "login" then showLogin("") else showAccount("")
    else if m.mode = "wait" then
        return
    else if m.mode = "activation" then
        if i = 0 then
            m.formMsg.text = "Activando tu prueba…"
            rpc("trial", "cc_trial_auto", { p_device: m.deviceId, p_name: m.deviceName, p_fp: m.fp, p_ref: rget("ref") })
        else if i = 1 then
            ask("Código de quien te recomendó", rget("ref"), "ref")
        else if i = 2 then
            ask("Tu código (ej. CC-7F3K-92QD)", "", "code")
        else if i = 3 then
            startHousePair()
        else if i = 4 then
            clearProvider()
        end if
    else if m.mode = "account" then
        onAccountSelect(i)
    else if m.mode = "profiles" then
        if i < m.profiles.Count() then useProfile(m.profiles[i]) else useGuest()
    else if m.mode = "hpair" then
        stopHousePair()
        if m.code = "" then showActivation("") else showHome()
    end if
end sub

sub clearProvider()
    m.reg.Delete("server") : m.reg.Delete("user") : m.reg.Delete("pass") : m.reg.Delete("vs") : m.reg.Flush()
    m.server = "" : m.user = "" : m.pass = ""
    m.pingTimer.control = "stop"
    loginStart("")
end sub

' ---------- Mi cuenta: unir esta tele a la cuenta de otro aparato ----------


sub newHp()
    m.hpCode = Str(100000 + Rnd(899999)).Trim()
    t = ""
    for k = 1 to 32
        t = t + Mid("0123456789abcdef", Rnd(16), 1)
    end for
    m.hpToken = t
    rpc("hpReq", "cc_pair_request", { p_code: m.hpCode, p_token: m.hpToken })
    m.hpStarted = CreateObject("roDateTime").AsSeconds()
end sub

sub startHousePair()
    m.qrTitle.text = "Más fácil: escanea con tu celular"
    m.qrText.text = "Si ya usas CineCasa en tu celular, al escanear toca «Agregar esta tele a mi cuenta» y entra sola."
    m.qrUrl.text = "msantos4321-bit.github.io/reproductor-tv/tv"
    newHp()
    code = Left(m.hpCode, 3) + " " + Right(m.hpCode, 3)
    showForm("Código: " + code, "Escanea el QR con tu celular, o abre CineCasa en tu celular o tablet → Más → «Agregar otra tele o celular», escribe " + code + " y toca «Aprobar».", ["Cancelar"], "hpair")
    m.formMsg.text = "Esperando la aprobación… (el código dura 15 minutos)"
    url = "https://msantos4321-bit.github.io/reproductor-tv/tv/?p=" + m.hpCode
    m.qr.uri = "https://api.qrserver.com/v1/create-qr-code/?size=320x320&margin=0&data=" + url.EncodeUriComponent()
    m.qrCode.text = code
    m.qrBox.visible = true
    m.pairTimer.control = "start"
end sub

sub stopHousePair()
    m.hpCode = ""
    m.pairTimer.control = "stop"
end sub

sub onHpPoll(body as string)
    if m.mode <> "hpair" and m.mode <> "login" then return
    t = body.Trim()
    if t = "" or t = "null" then return
    p = ParseJson(t)
    if p = invalid or p.server = invalid then return
    stopHousePair()
    stopPairing()
    m.server = Str2(p.server) : m.user = Str2(p.user) : m.pass = Str2(p.pass)
    rset("server", m.server) : rset("user", m.user) : rset("pass", m.pass)
    if p.hh <> invalid then rset("hh", Str2(p.hh))
    if p.hs <> invalid then rset("hs", Str2(p.hs))
    lic = ""
    if p.lic <> invalid then lic = Str2(p.lic)
    if lic <> "" then
        m.pendingCode = UCase(lic)
        m.formMsg.text = "¡Aprobado! Activando tu suscripción…"
        rpc("activate", "cc_lic_activate2", { p_code: m.pendingCode, p_device: m.deviceId, p_name: m.deviceName, p_fp: m.fp })
    else
        m.formMsg.text = "¡Aprobado! Entrando…"
        start()
    end if
end sub

sub ask(title as string, value as string, field as string)
    m.askField = field
    d = CreateObject("roSGNode", "StandardKeyboardDialog")
    d.title = title
    d.text = value
    d.buttons = ["Listo", "Cancelar"]
    if field = "pass" then d.textEditBox.secureMode = true
    d.observeField("buttonSelected", "onAsk")
    d.observeField("wasClosed", "onAskClosed")
    m.top.dialog = d
end sub

sub onAsk()
    d = m.top.dialog
    if d = invalid then return
    v = d.text.Trim()
    ok = (d.buttonSelected = 0)
    d.close = true
    m.top.dialog = invalid
    if not ok then
        if m.mode = "home" then m.sections.setFocus(true) else m.formList.setFocus(true)
        return
    end if
    f = m.askField
    if f = "server" then
        m.server = v : showLogin("")
    else if f = "user" then
        m.user = v : showLogin("")
    else if f = "pass" then
        m.pass = v : showLogin("")
    else if f = "search" then
        if v = "" then
            m.sections.setFocus(true)
        else
            startSearch(v)
        end if
    else if f = "ref" then
        rset("ref", UCase(v)) : showActivation("")
    else if f = "code" then
        c = UCase(v.Replace(" ", ""))
        if c = "" then return
        m.pendingCode = c
        m.formMsg.text = "Activando…"
        rpc("activate", "cc_lic_activate2", { p_code: c, p_device: m.deviceId, p_name: m.deviceName, p_fp: m.fp })
    end if
end sub

sub onAskClosed()
    m.top.dialog = invalid
    if m.mode = "home" then m.sections.setFocus(true) else m.formList.setFocus(true)
end sub

sub doLogin()
    if useVendor() then m.server = Str2(m.vsrv.url)
    if m.server = "" or m.user = "" or m.pass = "" then
        if useVendor() then m.formMsg.text = "Escribe tu usuario y contraseña." else m.formMsg.text = "Llena los tres datos."
        return
    end if
    m.formMsg.text = "Conectando…"
    request("login", api(""), "GET", "", invalid)
end sub

' ---------------- configurar desde el celular (QR) ----------------
sub startPairing()
    m.qrTitle.text = "Más fácil: escanea con tu celular"
    m.qrText.text = "Si ya usas CineCasa en tu celular, al escanear toca «Agregar esta tele a mi cuenta» y entra sola."
    m.qrUrl.text = "msantos4321-bit.github.io/reproductor-tv/tv"
    m.qrBox.visible = true
    if m.pairCode <> "" then return
    newHp()   ' el QR también trae un código para unir la tele a la cuenta del celular
    rpc("pairNew", "cc_pair_new", {})
end sub

sub stopPairing()
    m.qrBox.visible = false
    m.pairTimer.control = "stop"
    m.pairCode = ""
    m.hpCode = ""
end sub

sub onPairNew(body as string)
    c = body.Replace(Chr(34), "").Trim()
    if Len(c) <> 6 then return
    m.pairCode = c
    m.pairStarted = CreateObject("roDateTime").AsSeconds()
    url = "https://msantos4321-bit.github.io/reproductor-tv/tv/?c=" + c
    if m.hpCode <> "" then url = url + "&p=" + m.hpCode
    if useVendor() then url = url + "&k=1"
    m.qr.uri = "https://api.qrserver.com/v1/create-qr-code/?size=320x320&margin=0&data=" + url.EncodeUriComponent()
    m.qrCode.text = Left(c, 3) + " " + Right(c, 3)
    m.pairTimer.control = "start"
end sub

sub onPairTick()
    if m.mode = "hpair" and m.hpCode <> "" then
        if CreateObject("roDateTime").AsSeconds() - m.hpStarted > 840 then
            startHousePair()
        else
            rpc("hpPoll", "cc_pair_poll", { p_code: m.hpCode, p_token: m.hpToken })
        end if
        return
    end if
    if m.pairCode = "" or m.mode <> "login" then return
    if CreateObject("roDateTime").AsSeconds() - m.pairStarted > 840 then
        m.pairCode = ""
        m.pairTimer.control = "stop"
        newHp()
        rpc("pairNew", "cc_pair_new", {})
        return
    end if
    rpc("pairGet", "cc_pair_get", { p_code: m.pairCode })
    if m.hpCode <> "" then rpc("hpPoll", "cc_pair_poll", { p_code: m.hpCode, p_token: m.hpToken })
end sub

sub onPairGet(data as object)
    if data = invalid or data.status = invalid then return
    if data.status = "ok" then
        stopPairing()
        if Str2(data.server) <> "" then
            m.server = Str2(data.server) : m.customSrv = true
        end if
        m.user = Str2(data.user) : m.pass = Str2(data.pass)
        m.formMsg.text = "¡Datos recibidos! Entrando…"
        doLogin()
    else if data.status = "expired" then
        m.pairCode = ""
        m.pairTimer.control = "stop"
        newHp()
        rpc("pairNew", "cc_pair_new", {})
    end if
end sub

sub onLogin(code as integer, data as object)
    if code = 200 and data <> invalid and data.user_info <> invalid and Str2(data.user_info.auth) = "1" then
        rset("server", m.server) : rset("user", m.user) : rset("pass", m.pass)
        if useVendor() then rset("vs", "1") else rset("vs", "0")
        start()
    else if code = 200 then
        showLogin("Usuario o contraseña incorrectos. Revísalos con tu proveedor.")
    else
        showLogin("No se pudo conectar con el servidor (" + Str(code).Trim() + "). Si la liga dejó de funcionar, pide la nueva a tu vendedor.")
    end if
end sub


sub onTrial(data as object)
    if data = invalid then
        m.formMsg.text = "Sin conexión. Revisa tu internet."
        return
    end if
    e = ""
    if data.error <> invalid then e = data.error
    res = ""
    if data.result <> invalid then res = data.result
    if e = "" and res = "ok" then
        m.code = data.code : rset("code", m.code)
        m.vsrvAsked = false
        start()
    else if e = "trial_used" or res = "trial_used" then
        m.formMsg.text = "Este aparato ya usó su prueba gratis. Para seguir, pide tu plan anual."
    else if e = "bad_ref" then
        m.formMsg.text = "Ese código de recomendación no existe. Revísalo o bórralo."
    else if e = "off" then
        m.formMsg.text = "Las pruebas gratis no están disponibles ahora. Pide tu código a quien te compartió la app."
    else
        m.formMsg.text = "No se pudo activar la prueba. Intenta de nuevo."
    end if
end sub

sub onActivate(body as string)
    r = body.Replace(Chr(34), "").Trim()
    if r = "ok" then
        m.code = m.pendingCode : rset("code", m.code)
        m.vsrvAsked = false
        start()
    else if r <> "full" and r <> "blocked" and m.mode <> "activation" and m.mode <> "login" then
        ' venía de la revisión o del celular: muestra la pantalla de activación con el motivo
        showActivation("No se pudo activar esta tele (" + r + "). Revisa tu internet o pide ayuda a tu vendedor.")
    else if r = "full" then
        showActivation("Ese código ya está en todos los aparatos que permite. Desde tu celular quita uno en Ajustes → Tu suscripción → Mis aparatos, o pide un aparato más a tu vendedor.")
    else if r = "blocked" then
        showActivation("Esta tele se quitó de ese código. Pide a tu vendedor que la permita otra vez.")
    else if r = "expired" then
        m.formMsg.text = "Ese código ya venció."
    else if r = "revoked" then
        m.formMsg.text = "Ese código está desactivado."
    else if r = "trial_used" then
        m.formMsg.text = "Este aparato ya usó su prueba gratis."
    else if r = "unknown" then
        m.formMsg.text = "Ese código no existe. Revísalo letra por letra."
    else
        m.formMsg.text = "No se pudo activar. Revisa tu internet."
    end if
end sub

' =====================================================================
' APP PRINCIPAL: menú lateral, Inicio con portada y filas, explorar,
' detalle de película/serie, mi lista, búsqueda, perfiles y progreso
' sincronizado con los demás aparatos de la cuenta.
' =====================================================================

sub homeInit()
    m.brand = m.top.findNode("brand")
    m.side = m.top.findNode("side")
    m.profName = m.top.findNode("profName")
    m.codeLbl = m.top.findNode("codeLbl")
    m.pgHome = m.top.findNode("pgHome")
    m.pgBrowse = m.top.findNode("pgBrowse")
    m.pgDetail = m.top.findNode("pgDetail")
    m.rows = m.top.findNode("rows")
    m.heroImg = m.top.findNode("heroImg")
    m.heroKicker = m.top.findNode("heroKicker")
    m.heroTitle = m.top.findNode("heroTitle")
    m.heroMeta = m.top.findNode("heroMeta")
    m.homeMsg = m.top.findNode("homeMsg")
    m.browseTitle = m.top.findNode("browseTitle")
    m.dBack = m.top.findNode("dBack")
    m.dPoster = m.top.findNode("dPoster")
    m.dTitle = m.top.findNode("dTitle")
    m.dMeta = m.top.findNode("dMeta")
    m.dPlot = m.top.findNode("dPlot")
    m.dButtons = m.top.findNode("dButtons")
    m.dSeries = m.top.findNode("dSeries")
    m.dSeasons = m.top.findNode("dSeasons")
    m.dEps = m.top.findNode("dEps")
    m.dEpPlot = m.top.findNode("dEpPlot")
    m.progTimer = m.top.findNode("progTimer")

    m.lib = { progress: {}, mylist: {}, watched: {} }
    m.scope = ""
    m.guest = false
    m.profiles = []
    m.recentMovies = invalid
    m.recentSeries = invalid
    m.section = -1
    m.browseKind = ""
    m.playMeta = invalid

    m.sections.observeField("itemSelected", "onSectionSelect")
    m.rows.observeField("rowItemFocused", "onRowFocus")
    m.rows.observeField("rowItemSelected", "onRowSelect")
    m.cats.observeField("itemSelected", "onCatSelect")
    m.grid.observeField("itemSelected", "onGridSelect")
    m.grid.observeField("itemFocused", "onGridFocus")
    m.dButtons.observeField("itemSelected", "onDetailButton")
    m.dSeasons.observeField("itemFocused", "onSeasonFocus")
    m.dEps.observeField("itemSelected", "onEpSelect")
    m.dEps.observeField("itemFocused", "onEpFocus")
    m.progTimer.observeField("fire", "onProgTick")

    m.pgSearch = m.top.findNode("pgSearch")
    m.kb = m.top.findNode("kb")
    m.sRows = m.top.findNode("sRows")
    m.sMsg = m.top.findNode("sMsg")
    m.sRecent = m.top.findNode("sRecent")
    m.sTask = invalid
    m.kb.observeField("text", "onKbText")
    m.sRows.observeField("rowItemSelected", "onSRowSelect")
    m.sRecent.observeField("itemSelected", "onRecentSel")
end sub

function nowMs() as longinteger
    s& = CreateObject("roDateTime").AsSeconds()
    return s& * 1000&
end function

function newNode(fields as object) as object
    n = CreateObject("roSGNode", "ContentNode")
    n.addFields(fields)
    return n
end function

function mins(ms as dynamic) as string
    s = Int(ms / 1000)
    h = Int(s / 3600)
    mm = Int((s - h * 3600) / 60)
    if h > 0 then return Str(h).Trim() + " h " + Str(mm).Trim() + " min"
    return Str(mm).Trim() + " min"
end function

' ---------------- entrar a la app ----------------
sub showHome()
    stopPairing()
    m.form.visible = false
    m.brand.visible = false
    m.sub.visible = false
    m.home.visible = true
    m.pgDetail.visible = false
    m.side.visible = true
    m.mode = "home"
    m.codeLbl.text = "Código " + m.code
    c = CreateObject("roSGNode", "ContentNode")
    for each t in ["Inicio", "Buscar", "En vivo", "Películas", "Series", "Mi lista", "Mi cuenta"]
        n = c.createChild("ContentNode")
        n.title = t
    end for
    m.sections.content = c
    m.sections.setFocus(true)
    m.hh = rget("hh")
    m.hs = rget("hs")
    m.recentMovies = invalid
    m.recentSeries = invalid
    if m.hh <> "" and m.hs <> "" then
        m.homeMsg.text = "Cargando tu cuenta…"
        openSection(0, false)
        rpc("prof", "cc_get", { p_h: m.hh, p_s: m.hs, p_since: "1970-01-01T00:00:00Z", p_scopes: ["_"] })
    else
        m.scope = ""
        m.profName.text = ""
        loadLocalLib()
        openSection(0, true)
    end if
end sub

' Regresa a la app sin volver a cargar todo (desde Mi cuenta)
sub backToHome()
    m.form.visible = false
    m.brand.visible = false
    m.sub.visible = false
    m.home.visible = true
    m.mode = "home"
    m.sections.setFocus(true)
end sub

sub onProfiles(data as object)
    latest = {}
    if type(data) = "roArray" then
        for each o in data
            if o.kind = "profile" then latest[o.key] = o
        end for
    end if
    profs = []
    for each k in latest
        o = latest[k]
        if o.deleted <> true and o.data <> invalid then
            nm = Str2(o.data.name)
            if nm = "" then nm = "Perfil"
            profs.Push({ id: k, name: nm })
        end if
    end for
    m.profiles = profs
    saved = rget("prof")
    for each p in profs
        if p.id = saved then
            useProfile(p)
            return
        end if
    end for
    if profs.Count() = 0 then
        m.scope = ""
        loadLocalLib()
        buildRows()
        m.rows.setFocus(true)
    else if profs.Count() = 1 then
        useProfile(profs[0])
    else
        showProfilePicker()
    end if
end sub

sub showProfilePicker()
    m.home.visible = false
    m.brand.visible = true
    m.sub.visible = true
    m.sub.text = "Perfiles"
    names = []
    for each p in m.profiles
        names.Push(p.name)
    end for
    names.Push("Invitado (no guarda nada)")
    showForm("¿Quién está viendo?", "Cada perfil tiene su propia lista y lo que estaba viendo, igual que en tu celular y tus otras teles.", names, "profiles")
    m.formMsg.text = ""
end sub

sub useProfile(p as object)
    m.guest = false
    m.scope = p.id
    rset("prof", p.id)
    m.profName.text = p.name
    m.lib = { progress: {}, mylist: {}, watched: {} }
    m.form.visible = false
    m.brand.visible = false
    m.sub.visible = false
    m.home.visible = true
    m.mode = "home"
    m.homeMsg.text = "Cargando tu lista…"
    openSection(0, false)
    rpc("lib", "cc_get", { p_h: m.hh, p_s: m.hs, p_since: "1970-01-01T00:00:00Z", p_scopes: [p.id] })
end sub

sub useGuest()
    m.guest = true
    m.scope = ""
    m.profName.text = "Invitado"
    m.lib = { progress: {}, mylist: {}, watched: {} }
    backToHome()
    openSection(0, true)
end sub

sub onLib(data as object)
    m.lib = { progress: {}, mylist: {}, watched: {} }
    if type(data) = "roArray" then
        for each o in data
            k = Str2(o.key)
            del = (o.deleted = true) or (o.data = invalid)
            if o.kind = "progress" then
                if del then m.lib.progress.Delete(k) else m.lib.progress[k] = o.data
            else if o.kind = "mylist" then
                if del then m.lib.mylist.Delete(k) else m.lib.mylist[k] = o.data
            else if o.kind = "watched" then
                if del then m.lib.watched.Delete(k) else m.lib.watched[k] = true
            end if
        end for
    end if
    if m.section = 0 and m.mode = "home" then
        buildRows()
        m.rows.setFocus(true)
    end if
end sub

sub loadLocalLib()
    m.lib = { progress: {}, mylist: {}, watched: {} }
    t = rget("lib_local")
    if t <> "" then
        d = ParseJson(t)
        if d <> invalid and d.progress <> invalid then m.lib = d
    end if
end sub

' Guarda un cambio (progreso, mi lista, visto) aquí y en la nube del perfil
sub libPut(kind as string, key as string, data as dynamic, deleted as boolean)
    bucket = m.lib.progress
    if kind = "mylist" then bucket = m.lib.mylist
    if kind = "watched" then bucket = m.lib.watched
    if deleted then
        bucket.Delete(key)
    else if kind = "watched" then
        bucket[key] = true
    else
        bucket[key] = data
    end if
    if m.scope <> "" and m.hh <> "" then
        it = { scope: m.scope, kind: kind, key: key, deleted: deleted }
        if data <> invalid and not deleted then it.data = data
        rpc("put" + Str(m.reqN + 1).Trim(), "cc_put", { p_h: m.hh, p_s: m.hs, p_items: [it] })
    else if not m.guest then
        rset("lib_local", FormatJson(m.lib))
    end if
end sub

' ---------------- menú lateral ----------------
sub onSectionSelect()
    openSection(m.sections.itemSelected, true)
end sub

sub openSection(i as integer, focus as boolean)
    if i = 1 then
        showSearchPage(focus)
        return
    end if
    if i = 6 then
        showAccount("")
        return
    end if
    m.section = i
    m.pgDetail.visible = false
    m.pgSearch.visible = false
    m.pgHome.visible = (i = 0)
    m.pgBrowse.visible = (i <> 0)
    if i = 0 then
        buildRows()
        if focus then m.rows.setFocus(true)
        return
    end if
    m.grid.content = CreateObject("roSGNode", "ContentNode")
    m.cats.content = CreateObject("roSGNode", "ContentNode")
    m.info.text = "Cargando…"
    if i = 5 then
        m.browseKind = "mylist"
        m.browseTitle.text = "Mi lista"
        setCats(["Todo", "Películas", "Series"])
        showMyList(0)
        if focus then m.grid.setFocus(true)
        return
    end if
    kinds = ["", "", "live", "movie", "series"]
    titles = ["", "", "TV en vivo", "Películas", "Series"]
    m.browseKind = kinds[i]
    m.browseTitle.text = titles[i]
    actions = ["", "", "get_live_categories", "get_vod_categories", "get_series_categories"]
    req("cats:" + m.browseKind, api(actions[i]), { parse: true })
    if focus then m.cats.setFocus(true)
end sub

' Petición GET al proveedor que ya llega convertida (y opcionalmente ordenada, filtrada y recortada)
sub req(id as string, url as string, opts as object)
    m.reqN = m.reqN + 1
    t = CreateObject("roSGNode", "HttpTask")
    r = { id: id, url: url, method: "GET", body: "", headers: invalid }
    r.Append(opts)
    t.request = r
    t.observeField("response", "onResponse")
    m.tasks[id] = t
    t.control = "RUN"
end sub

sub setCats(names as object)
    c = CreateObject("roSGNode", "ContentNode")
    for each t in names
        n = c.createChild("ContentNode")
        n.title = t
    end for
    m.cats.content = c
end sub

' ---------------- Inicio ----------------
sub buildRows()
    root = CreateObject("roSGNode", "ContentNode")
    ' Continuar viendo
    arr = []
    for each k in m.lib.progress
        p = m.lib.progress[k]
        if p <> invalid then arr.Push(p)
    end for
    arr.SortBy("up", "r")
    if arr.Count() > 0 then
        row = root.createChild("ContentNode")
        row.title = "Continuar viendo"
        for each p in arr
            f = 0
            if p.dur <> invalid and p.dur > 0 then f = p.pos / p.dur
            kind = Str2(p.kind)
            cap = ""
            sub_ = "Te quedan " + mins(p.dur - p.pos)
            if kind = "episode" then
                cap = "T" + Str2(p.s) + " E" + Str2(p.e)
                sub_ = Str2(p.st) + " · " + sub_
            end if
            n = row.createChild("ContentNode")
            n.title = Str2(p.t)
            n.HDPosterUrl = Str2(p.img)
            n.addFields({ kind: kind, xid: Str2(p.id), sid: Str2(p.sid), frac: f, cap: cap, desc: sub_ })
        end for
    end if
    ' Mi lista
    ml = []
    for each k in m.lib.mylist
        s = m.lib.mylist[k]
        if s <> invalid then ml.Push(s)
    end for
    ml.SortBy("added", "r")
    if ml.Count() > 0 then
        row = root.createChild("ContentNode")
        row.title = "Mi lista"
        for each s in ml
            n = row.createChild("ContentNode")
            n.title = Str2(s.title)
            n.HDPosterUrl = Str2(s.image)
            kind = Str2(s.kind)
            lbl = "Película"
            if kind = "series" then lbl = "Serie"
            n.addFields({ kind: kind, xid: Str2(s.id), sid: Str2(s.id), frac: 0, cap: "", desc: lbl })
        end for
    end if
    ' Recientes
    if m.recentMovies <> invalid and m.recentMovies.Count() > 0 then
        row = root.createChild("ContentNode")
        row.title = "Películas recién agregadas"
        for each it in m.recentMovies
            n = row.createChild("ContentNode")
            n.title = Str2(it.name)
            n.HDPosterUrl = img(it.stream_icon)
            yr = Str2(it.year)
            lbl = "Película"
            if yr <> "" then lbl = lbl + " · " + yr
            n.addFields({ kind: "movie", xid: Str2(it.stream_id), sid: "", frac: 0, cap: "", desc: lbl, ext: Str2(it.container_extension) })
        end for
    end if
    if m.recentSeries <> invalid and m.recentSeries.Count() > 0 then
        row = root.createChild("ContentNode")
        row.title = "Series con capítulos nuevos"
        for each it in m.recentSeries
            n = row.createChild("ContentNode")
            n.title = Str2(it.name)
            n.HDPosterUrl = img(it.cover)
            n.addFields({ kind: "series", xid: Str2(it.series_id), sid: Str2(it.series_id), frac: 0, cap: "", desc: "Serie · " + Str2(it.genre) })
        end for
    end if
    m.rows.content = root
    if root.getChildCount() = 0 then
        m.homeMsg.text = "Cargando…"
    else
        m.homeMsg.text = ""
        onRowFocus()
    end if
    if m.recentMovies = invalid then
        m.recentMovies = []
        req("recentMovies", api("get_vod_streams"), { parse: true, sortKey: "added", limit: 25 })
    end if
    if m.recentSeries = invalid then
        m.recentSeries = []
        req("recentSeries", api("get_series"), { parse: true, sortKey: "last_modified", limit: 25 })
    end if
end sub

function img(v as dynamic) as string
    s = Str2(v)
    if Left(s, 4) <> "http" then return "pkg:/images/poster.png"
    return s
end function

sub onRecent(id as string, data as object)
    if type(data) <> "roArray" then data = []
    if id = "recentMovies" then m.recentMovies = data else m.recentSeries = data
    if m.section = 0 and m.mode = "home" then
        hadFocus = m.rows.hasFocus()
        buildRows()
        if hadFocus or m.sections.hasFocus() = false then m.rows.setFocus(true)
    end if
end sub

sub onRowFocus()
    if m.rows.content = invalid then return
    f = m.rows.rowItemFocused
    r = 0 : c = 0
    if f <> invalid and f.Count() = 2 then
        r = f[0] : c = f[1]
    end if
    row = m.rows.content.getChild(r)
    if row = invalid then return
    it = row.getChild(c)
    if it = invalid then return
    m.heroImg.uri = it.HDPosterUrl
    m.heroKicker.text = UCase(row.title)
    m.heroTitle.text = it.title
    m.heroMeta.text = it.desc
end sub

sub onRowSelect()
    f = m.rows.rowItemSelected
    it = m.rows.content.getChild(f[0]).getChild(f[1])
    if it <> invalid then openDetail(it, m.rows)
end sub

' ---------------- Explorar / Mi lista / Búsqueda ----------------
sub onCats(id as string, data as object)
    kind = Mid(id, 6)
    if kind <> m.browseKind then return
    c = CreateObject("roSGNode", "ContentNode")
    n = c.createChild("ContentNode")
    n.title = "Todo"
    n.addFields({ catId: "" })
    if type(data) = "roArray" then
        for each it in data
            n = c.createChild("ContentNode")
            n.title = Str2(it.category_name)
            n.addFields({ catId: Str2(it.category_id) })
        end for
    end if
    m.cats.content = c
    loadItems(0)
end sub

sub onCatSelect()
    i = m.cats.itemSelected
    if m.browseKind = "mylist" then
        showMyList(i)
    else if m.browseKind = "search" then
        showSearch(i)
    else
        loadItems(i)
    end if
    m.grid.setFocus(true)
end sub

sub loadItems(ci as integer)
    if m.cats.content = invalid or m.cats.content.getChildCount() = 0 then return
    cat = m.cats.content.getChild(ci).catId
    actions = { live: "get_live_streams", movie: "get_vod_streams", series: "get_series" }
    u = api(actions[m.browseKind])
    if cat <> "" then u = u + "&category_id=" + cat
    m.info.text = "Cargando…"
    sk = ""
    if m.browseKind = "movie" then sk = "added"
    if m.browseKind = "series" then sk = "last_modified"
    if m.browseKind = "live" then sk = ""
    req("items:" + m.browseKind, u, { parse: true, sortKey: sk, limit: 600 })
end sub

sub fillGrid(kind as string, data as object)
    c = CreateObject("roSGNode", "ContentNode")
    count = 0
    if type(data) = "roArray" then
        for each it in data
            n = c.createChild("ContentNode")
            t = Str2(it.name)
            if t = "" then t = Str2(it.title)
            n.title = t
            n.shortDescriptionLine1 = t
            k = kind
            if k = "mixed" then k = Str2(it.kind)
            if k = "series" then
                p = img(it.cover)
                if it.image <> invalid then p = img(it.image)
                n.HDPosterUrl = p
                id = Str2(it.series_id)
                if id = "" then id = Str2(it.id)
                n.addFields({ kind: "series", xid: id, sid: id, ext: "" })
            else
                p = img(it.stream_icon)
                if it.image <> invalid then p = img(it.image)
                n.HDPosterUrl = p
                id = Str2(it.stream_id)
                if id = "" then id = Str2(it.id)
                n.addFields({ kind: k, xid: id, sid: "", ext: Str2(it.container_extension) })
            end if
            n.FHDPosterUrl = n.HDPosterUrl
            count = count + 1
        end for
    end if
    m.grid.content = c
    if count = 0 then
        m.info.text = "No hay nada aquí todavía."
    else
        m.info.text = Str(count).Trim() + " títulos · OK para ver"
    end if
end sub

sub onItems(id as string, data as object)
    kind = Mid(id, 7)
    if kind <> m.browseKind then return
    fillGrid(kind, data)
end sub

sub showMyList(filter as integer)
    arr = []
    for each k in m.lib.mylist
        s = m.lib.mylist[k]
        if s <> invalid then
            kk = Str2(s.kind)
            if filter = 0 or (filter = 1 and kk = "movie") or (filter = 2 and kk = "series") then arr.Push(s)
        end if
    end for
    arr.SortBy("added", "r")
    fillGrid("mixed", arr)
    if arr.Count() = 0 then m.info.text = "Tu lista está vacía. En el detalle de una película o serie elige «Agregar a Mi lista»."
end sub

sub startSearch(q as string)
    if q = "" then return
    m.section = 1
    m.browseKind = "search"
    m.pgHome.visible = false
    m.pgDetail.visible = false
    m.pgBrowse.visible = true
    m.browseTitle.text = "Buscar: " + q
    setCats(["Películas", "Series"])
    m.grid.content = CreateObject("roSGNode", "ContentNode")
    m.info.text = "Buscando…"
    m.searchRes = [invalid, invalid]
    q = LCase(q)
    req("srch:movie", api("get_vod_streams"), { parse: true, filter: q })
    req("srch:series", api("get_series"), { parse: true, filter: q })
    m.grid.setFocus(true)
end sub

sub onSearch(id as string, data as object)
    if m.browseKind <> "search" then return
    if type(data) <> "roArray" then data = []
    if id = "srch:movie" then m.searchRes[0] = data else m.searchRes[1] = data
    i = m.cats.itemFocused
    if i < 0 then i = 0
    showSearch(i)
end sub

sub showSearch(i as integer)
    d = m.searchRes[i]
    if d = invalid then
        m.info.text = "Buscando…"
        return
    end if
    kinds = ["movie", "series"]
    fillGrid(kinds[i], d)
    if d.Count() = 0 then
        other = "series"
        if i = 1 then other = "películas"
        m.info.text = "Sin resultados aquí. Revisa en " + other + "."
    end if
end sub

sub onGridFocus()
    if m.grid.content = invalid then return
    it = m.grid.content.getChild(m.grid.itemFocused)
    if it <> invalid then m.info.text = it.title
end sub

sub onGridSelect()
    it = m.grid.content.getChild(m.grid.itemSelected)
    if it = invalid then return
    if it.kind = "live" then
        play(it.title, base() + "/live/" + m.user + "/" + m.pass + "/" + it.xid + ".m3u8", "hls", true, invalid, 0)
    else
        openDetail(it, m.grid)
    end if
end sub

' ---------------- Detalle ----------------
sub openDetail(it as object, from as object)
    m.dItem = { kind: it.kind, xid: it.xid, sid: it.sid, title: it.title, poster: it.HDPosterUrl, ext: "" }
    if it.hasField("ext") then m.dItem.ext = it.ext
    m.dFrom = from
    m.mode = "detail"
    m.side.visible = false
    m.pgHome.visible = false
    m.pgBrowse.visible = false
    m.pgSearch.visible = false
    m.pgDetail.visible = true
    m.dBack.uri = it.HDPosterUrl
    m.dPoster.uri = it.HDPosterUrl
    m.dTitle.text = it.title
    m.dMeta.text = "Cargando…"
    m.dPlot.text = ""
    m.dSeries.visible = false
    setButtons([], [])
    if it.kind = "movie" then
        req("vinfo", api("get_vod_info") + "&vod_id=" + it.xid, { parse: true })
    else
        sid = it.sid
        if sid = "" then sid = it.xid
        m.dItem.kind = "series"
        m.dItem.xid = sid
        m.dItem.sid = sid
        req("sinfo", api("get_series_info") + "&series_id=" + sid, { parse: true })
    end if
    m.dButtons.setFocus(true)
end sub

sub closeDetail()
    m.pgDetail.visible = false
    m.side.visible = true
    m.mode = "home"
    if m.section = 0 then
        m.pgHome.visible = true
        buildRows()
        m.rows.setFocus(true)
    else if m.section = 1 then
        m.pgSearch.visible = true
        m.sRows.setFocus(true)
    else
        m.pgBrowse.visible = true
        if m.browseKind = "mylist" then showMyList(m.cats.itemSelected)
        m.grid.setFocus(true)
    end if
end sub

sub setButtons(labels as object, actions as object)
    c = CreateObject("roSGNode", "ContentNode")
    for each t in labels
        n = c.createChild("ContentNode")
        n.title = t
    end for
    m.dButtons.content = c
    m.dActions = actions
end sub

function listKey() as string
    return m.dItem.kind + ":" + m.dItem.xid
end function

function listLabel() as string
    if m.lib.mylist[listKey()] <> invalid then return "Quitar de Mi lista"
    return "Agregar a Mi lista"
end function

function joinMeta(parts as object) as string
    out = ""
    for each p in parts
        if p <> invalid and p <> "" and p <> "0" then
            if out <> "" then out = out + "   ·   "
            out = out + p
        end if
    end for
    return out
end function

sub onVodInfo(d as object)
    if m.mode <> "detail" or d = invalid then
        if m.mode = "detail" then m.dMeta.text = "No se pudo cargar la información."
        if m.mode = "detail" then refreshMovieButtons()
        return
    end if
    info = d.info
    if info = invalid then info = {}
    md = d.movie_data
    if md <> invalid and Str2(md.container_extension) <> "" then m.dItem.ext = Str2(md.container_extension)
    if m.dItem.ext = "" then m.dItem.ext = "mp4"
    yr = Left(Str2(info.releasedate), 4)
    rating = Str2(info.rating)
    if rating <> "" then rating = "Calificación " + Left(rating, 3)
    m.dItem.year = yr
    m.dMeta.text = joinMeta([yr, Str2(info.duration), Str2(info.genre), rating])
    plot = Str2(info.plot)
    if plot = "" then plot = Str2(info.description)
    m.dPlot.text = plot
    bd = ""
    if type(info.backdrop_path) = "roArray" and info.backdrop_path.Count() > 0 then bd = Str2(info.backdrop_path[0])
    if Left(bd, 4) = "http" then m.dBack.uri = bd
    if Left(Str2(info.movie_image), 4) = "http" then m.dPoster.uri = Str2(info.movie_image)
    refreshMovieButtons()
end sub

sub refreshMovieButtons()
    p = m.lib.progress["movie:" + m.dItem.xid]
    if p <> invalid then
        setButtons(["Continuar (te quedan " + mins(p.dur - p.pos) + ")", "Ver desde el inicio", listLabel()], ["resume", "start", "list"])
    else
        setButtons(["Ver película", listLabel()], ["start", "list"])
    end if
end sub

sub onSeriesInfo(d as object)
    if m.mode <> "detail" then return
    if d = invalid or d.episodes = invalid then
        m.dMeta.text = "No se pudo cargar la serie."
        return
    end if
    info = d.info
    if info = invalid then info = {}
    m.sInfo = info
    rating = Str2(info.rating)
    if rating <> "" then rating = "Calificación " + Left(rating, 3)
    m.dMeta.text = joinMeta([Left(Str2(info.releaseDate), 4), Str2(info.genre), rating])
    m.dPlot.text = Str2(info.plot)
    bd = ""
    if type(info.backdrop_path) = "roArray" and info.backdrop_path.Count() > 0 then bd = Str2(info.backdrop_path[0])
    if Left(bd, 4) = "http" then m.dBack.uri = bd
    if Left(Str2(info.cover), 4) = "http" then
        m.dPoster.uri = Str2(info.cover)
        m.dItem.poster = Str2(info.cover)
    end if
    ' temporadas en orden
    keys = []
    for each s in d.episodes
        keys.Push({ k: s, n: Val(s) })
    end for
    keys.SortBy("n")
    m.sEps = d.episodes
    m.sKeys = []
    c = CreateObject("roSGNode", "ContentNode")
    for each o in keys
        m.sKeys.Push(o.k)
        n = c.createChild("ContentNode")
        n.title = "Temporada " + o.k
    end for
    m.dSeasons.content = c
    m.dSeries.visible = true
    ' episodio en curso
    m.sCont = invalid
    best = invalid
    for each k in m.lib.progress
        p = m.lib.progress[k]
        if p <> invalid and Str2(p.kind) = "episode" and Str2(p.sid) = m.dItem.sid then
            if best = invalid or p.up > best.up then best = p
        end if
    end for
    m.sCont = best
    si = 0
    if best <> invalid then
        for i = 0 to m.sKeys.Count() - 1
            if Val(m.sKeys[i]) = best.s then si = i
        end for
    end if
    m.dSeasons.jumpToItem = si
    fillEps(si)
    refreshSeriesButtons()
end sub

sub refreshSeriesButtons()
    if m.sCont <> invalid then
        setButtons(["Continuar T" + Str2(m.sCont.s) + " E" + Str2(m.sCont.e) + " (te quedan " + mins(m.sCont.dur - m.sCont.pos) + ")", listLabel()], ["resumeEp", "list"])
    else
        setButtons(["Ver desde el primer episodio", listLabel()], ["firstEp", "list"])
    end if
end sub

sub onSeasonFocus()
    if m.dSeries.visible = false then return
    fillEps(m.dSeasons.itemFocused)
end sub

sub fillEps(si as integer)
    if m.sKeys = invalid or si < 0 or si >= m.sKeys.Count() then return
    m.curSeason = m.sKeys[si]
    eps = m.sEps[m.curSeason]
    m.curEps = eps
    c = CreateObject("roSGNode", "ContentNode")
    for each ep in eps
        n = c.createChild("ContentNode")
        t = "E" + Str2(ep.episode_num) + "   " + Str2(ep.title)
        id = Str2(ep.id)
        if m.lib.progress["episode:" + id] <> invalid then
            p = m.lib.progress["episode:" + id]
            t = t + "   ·   te quedan " + mins(p.dur - p.pos)
        else if m.lib.watched[id] = true then
            t = t + "   ·   visto"
        end if
        n.title = t
    end for
    m.dEps.content = c
    m.dEpPlot.text = ""
end sub

sub onEpFocus()
    if m.curEps = invalid then return
    ep = m.curEps[m.dEps.itemFocused]
    if ep = invalid then return
    plot = ""
    if ep.info <> invalid then plot = Str2(ep.info.plot)
    m.dEpPlot.text = plot
end sub

sub onEpSelect()
    ep = m.curEps[m.dEps.itemSelected]
    if ep = invalid then return
    st = 0
    p = m.lib.progress["episode:" + Str2(ep.id)]
    if p <> invalid then st = Int(p.pos / 1000)
    playEpisode(ep, m.curSeason, st)
end sub

sub playEpisode(ep as object, season as dynamic, startSec as integer)
    ext = Str2(ep.container_extension)
    if ext = "" then ext = "mp4"
    num = Int(Val(Str2(ep.episode_num)))
    s = Int(Val(Str2(season)))
    t = Str2(ep.title)
    meta = { kind: "episode", id: Int(Val(Str2(ep.id))), sid: Int(Val(m.dItem.sid)), t: m.dItem.title, st: "T" + Str(s).Trim() + " E" + Str(num).Trim() + " · " + t, img: m.dItem.poster, s: s, e: num }
    play(m.dItem.title + " · T" + Str(s).Trim() + " E" + Str(num).Trim(), base() + "/series/" + m.user + "/" + m.pass + "/" + Str2(ep.id) + "." + ext, fmt(ext), false, meta, startSec)
end sub

function findEpisode(epId as string) as object
    for each k in m.sEps
        for each ep in m.sEps[k]
            if Str2(ep.id) = epId then return { ep: ep, season: k }
        end for
    end for
    return invalid
end function

sub onDetailButton()
    a = m.dActions[m.dButtons.itemSelected]
    if a = invalid then return
    if a = "list" then
        k = listKey()
        if m.lib.mylist[k] <> invalid then
            libPut("mylist", k, invalid, true)
        else
            libPut("mylist", k, { kind: m.dItem.kind, id: Int(Val(m.dItem.xid)), title: m.dItem.title, image: m.dItem.poster, added: nowMs() }, false)
        end if
        i = m.dButtons.itemFocused
        if m.dItem.kind = "movie" then refreshMovieButtons() else refreshSeriesButtons()
        m.dButtons.jumpToItem = i
    else if a = "start" or a = "resume" then
        st = 0
        p = m.lib.progress["movie:" + m.dItem.xid]
        if a = "resume" and p <> invalid then st = Int(p.pos / 1000)
        ext = m.dItem.ext
        if ext = "" then ext = "mp4"
        meta = { kind: "movie", id: Int(Val(m.dItem.xid)), sid: 0, t: m.dItem.title, st: Str2(m.dItem.year), img: m.dItem.poster, s: 0, e: 0 }
        play(m.dItem.title, base() + "/movie/" + m.user + "/" + m.pass + "/" + m.dItem.xid + "." + ext, fmt(ext), false, meta, st)
    else if a = "resumeEp" and m.sCont <> invalid then
        f = findEpisode(Str2(m.sCont.id))
        if f <> invalid then playEpisode(f.ep, f.season, Int(m.sCont.pos / 1000))
    else if a = "firstEp" then
        if m.sKeys <> invalid and m.sKeys.Count() > 0 then
            eps = m.sEps[m.sKeys[0]]
            if eps <> invalid and eps.Count() > 0 then playEpisode(eps[0], m.sKeys[0], 0)
        end if
    end if
end sub


' ---------------- Buscar (como en Android: resultados mientras escribes) ----------------
sub showSearchPage(focus as boolean)
    m.section = 1
    m.browseKind = "search"
    m.pgHome.visible = false
    m.pgBrowse.visible = false
    m.pgDetail.visible = false
    m.pgSearch.visible = true
    b = api("")
    if m.sTask = invalid or m.sBase <> b then
        m.sBase = b
        m.sTask = CreateObject("roSGNode", "SearchTask")
        m.sTask.base = b
        m.sTask.observeField("results", "onSearchResults")
        m.sTask.observeField("status", "onSearchStatus")
        m.sTask.control = "RUN"
    end if
    fillRecent()
    onKbText()
    if focus then m.kb.setFocus(true)
end sub

sub onSearchStatus()
    if m.sTask.status = "loading" then
        m.sMsg.text = "Preparando la búsqueda (solo la primera vez)…"
    else if m.sTask.status = "ready" and Len(m.kb.text.Trim()) < 2 then
        m.sMsg.text = "Escribe parte del nombre de una película, serie o canal."
    end if
end sub

sub onKbText()
    q = m.kb.text
    if Len(q.Trim()) < 2 then
        m.sRows.content = CreateObject("roSGNode", "ContentNode")
        if m.sTask <> invalid and m.sTask.status = "ready" then m.sMsg.text = "Escribe parte del nombre de una película, serie o canal."
        return
    end if
    if m.sTask <> invalid then m.sTask.query = q
end sub

sub onSearchResults()
    r = m.sTask.results
    if r = invalid or r.q <> m.kb.text then return
    root = CreateObject("roSGNode", "ContentNode")
    total = 0
    labels = { movie: "Películas", series: "Series", live: "Canales en vivo" }
    for each kind in ["movie", "series", "live"]
        arr = r[kind]
        if arr <> invalid and arr.Count() > 0 then
            row = root.createChild("ContentNode")
            row.title = labels[kind] + " (" + Str(arr.Count()).Trim() + ")"
            for each it in arr
                n = row.createChild("ContentNode")
                n.title = it.t
                n.HDPosterUrl = img(it.img)
                ext = ""
                if it.ext <> invalid then ext = it.ext
                n.addFields({ kind: kind, xid: it.id, sid: it.id, ext: ext, frac: 0, cap: "", desc: "" })
                total = total + 1
            end for
        end if
    end for
    m.sRows.content = root
    if total = 0 then
        m.sMsg.text = "No encontramos «" + m.kb.text + "». Prueba con otra palabra del título."
    else
        m.sMsg.text = ""
    end if
end sub

sub onSRowSelect()
    f = m.sRows.rowItemSelected
    it = m.sRows.content.getChild(f[0]).getChild(f[1])
    if it = invalid then return
    addRecent(m.kb.text.Trim())
    if it.kind = "live" then
        play(it.title, base() + "/live/" + m.user + "/" + m.pass + "/" + it.xid + ".m3u8", "hls", true, invalid, 0)
    else
        openDetail(it, m.sRows)
    end if
end sub

function recentList() as object
    d = ParseJson(rget("recentSearch"))
    if type(d) <> "roArray" then d = []
    return d
end function

sub addRecent(q as string)
    if Len(q) < 2 then return
    d = recentList()
    out = [q]
    for each x in d
        if LCase(x) <> LCase(q) and out.Count() < 8 then out.Push(x)
    end for
    rset("recentSearch", FormatJson(out))
    fillRecent()
end sub

sub fillRecent()
    c = CreateObject("roSGNode", "ContentNode")
    for each x in recentList()
        n = c.createChild("ContentNode")
        n.title = x
    end for
    m.sRecent.content = c
end sub

sub onRecentSel()
    n = m.sRecent.content.getChild(m.sRecent.itemSelected)
    if n = invalid then return
    m.kb.text = n.title
    m.kb.setFocus(true)
end sub

' ---------------- Mi cuenta ----------------
sub showAccount(msg as string)
    m.home.visible = false
    m.brand.visible = true
    m.sub.visible = true
    m.sub.text = "Mi cuenta"
    cur = "Esta tele tiene el código " + m.code + "."
    if m.code = "" then cur = "Esta tele todavía no tiene código."
    items = ["Unir a mi cuenta (desde mi celular o tablet)", "Escribir mi código de activación"]
    if m.profiles.Count() > 0 then items.Push("Cambiar de perfil")
    items.Push("Cambiar datos del proveedor")
    if m.code <> "" then items.Push("Pedir ayuda a mi vendedor (WhatsApp)")
    items.Push("Volver")
    m.accItems = items
    showForm("Mi cuenta", cur + " Para usar tu misma cuenta, tu lista y lo que estabas viendo en tu celular, tablet o tele Android, elige «Unir a mi cuenta».", items, "account")
    m.formMsg.text = msg
end sub

sub onAccountSelect(i as integer)
    t = m.accItems[i]
    if Left(t, 4) = "Unir" then
        startHousePair()
    else if Left(t, 8) = "Escribir" then
        ask("Tu código (ej. CC-7F3K-92QD)", "", "code")
    else if t = "Cambiar de perfil" then
        showProfilePicker()
    else if Left(t, 14) = "Cambiar datos " then
        clearProvider()
    else if Left(t, 5) = "Pedir" then
        askHelp()
    else
        backToHome()
    end if
end sub

' ---------------- reproductor ----------------
sub play(title as string, url as string, format as string, live as boolean, meta as dynamic, startSec as integer)
    c = CreateObject("roSGNode", "ContentNode")
    c.title = title
    c.url = url
    c.streamFormat = format
    c.live = live
    if startSec > 5 then c.PlayStart = startSec - 3
    m.returnFocus = m.top.focusedChild
    m.playMeta = meta
    m.video.content = c
    m.video.visible = true
    m.video.control = "play"
    m.video.setFocus(true)
    if meta <> invalid then m.progTimer.control = "start"
end sub

sub onProgTick()
    saveProgress()
end sub

sub saveProgress()
    mt = m.playMeta
    if mt = invalid then return
    ps = m.video.position
    dur = m.video.duration
    if dur = invalid or dur <= 0 or ps = invalid then return
    key = mt.kind + ":" + Str(mt.id).Trim()
    finished = (ps >= dur * 0.92) or (dur - ps < 90 and dur > 600)
    if finished then
        if m.lib.progress[key] <> invalid then libPut("progress", key, invalid, true)
        if mt.kind = "episode" then libPut("watched", Str(mt.id).Trim(), { w: true }, false)
    else if ps > 15 then
        if mt.kind = "episode" then
            old = []
            for each k in m.lib.progress
                p = m.lib.progress[k]
                if k <> key and p <> invalid and Str2(p.kind) = "episode" and Str2(p.sid) = Str(mt.sid).Trim() then old.Push(k)
            end for
            for each k in old
                libPut("progress", k, invalid, true)
            end for
        end if
        data = { kind: mt.kind, id: mt.id, sid: mt.sid, t: mt.t, st: mt.st, img: mt.img, pos: Int(ps * 1000), dur: Int(dur * 1000), up: nowMs(), s: mt.s, e: mt.e }
        libPut("progress", key, data, false)
    end if
end sub

sub stopVideo()
    saveProgress()
    m.progTimer.control = "stop"
    m.playMeta = invalid
    m.video.control = "stop"
    m.video.visible = false
    if m.mode = "detail" then
        if m.dItem.kind = "movie" then
            refreshMovieButtons()
        else
            ' actualiza el episodio en curso y las marcas
            best = invalid
            for each k in m.lib.progress
                p = m.lib.progress[k]
                if p <> invalid and Str2(p.kind) = "episode" and Str2(p.sid) = m.dItem.sid then
                    if best = invalid or p.up > best.up then best = p
                end if
            end for
            m.sCont = best
            i = m.dEps.itemFocused
            fillEps(m.dSeasons.itemFocused)
            m.dEps.jumpToItem = i
            refreshSeriesButtons()
        end if
    end if
    if m.returnFocus <> invalid then
        m.returnFocus.setFocus(true)
    else if m.mode = "detail" then
        m.dButtons.setFocus(true)
    else
        m.grid.setFocus(true)
    end if
end sub

sub onVideoState()
    s = m.video.state
    if s = "error" then
        m.info.text = "No se pudo reproducir (" + m.video.errorMsg + ")."
        m.dMeta.text = "No se pudo reproducir. Intenta de nuevo o elige otra versión."
        stopVideo()
    else if s = "finished" then
        stopVideo()
    end if
end sub

' ---------------- control remoto ----------------
function onKeyEvent(key as string, press as boolean) as boolean
    if not press then return false
    if m.video.visible then
        if key = "back" then
            stopVideo()
            return true
        end if
        return false
    end if
    if key = "back" and (m.mode = "account" or m.mode = "hpair") then
        stopHousePair()
        if m.code = "" then
            showActivation("")
        else if m.home.visible = false and m.mode = "hpair" then
            showAccount("")
        else
            backToHome()
        end if
        return true
    end if
    if m.mode = "detail" then
        if key = "back" then
            closeDetail()
            return true
        else if key = "down" and m.dButtons.hasFocus() and m.dSeries.visible then
            m.dEps.setFocus(true)
            return true
        else if key = "up" and (m.dEps.hasFocus() or m.dSeasons.hasFocus()) then
            m.dButtons.setFocus(true)
            return true
        else if key = "left" and m.dEps.hasFocus() then
            m.dSeasons.setFocus(true)
            return true
        else if key = "right" and m.dSeasons.hasFocus() then
            m.dEps.setFocus(true)
            return true
        end if
        return false
    end if
    if m.mode <> "home" then return false
    if m.pgSearch.visible and not m.sections.hasFocus() then
        if key = "right" and (m.kb.isInFocusChain() or m.sRecent.hasFocus()) then
            if m.sRows.content <> invalid and m.sRows.content.getChildCount() > 0 then m.sRows.setFocus(true)
            return true
        else if key = "left" and m.sRows.hasFocus() then
            m.kb.setFocus(true)
            return true
        else if key = "left" and (m.kb.isInFocusChain() or m.sRecent.hasFocus()) then
            m.sections.setFocus(true)
            return true
        else if key = "down" and m.kb.isInFocusChain() then
            if m.sRecent.content <> invalid and m.sRecent.content.getChildCount() > 0 then m.sRecent.setFocus(true)
            return true
        else if key = "up" and m.sRecent.hasFocus() then
            m.kb.setFocus(true)
            return true
        else if key = "back" then
            if m.sRows.hasFocus() or m.sRecent.hasFocus() then m.kb.setFocus(true) else m.sections.setFocus(true)
            return true
        end if
        return false
    end if
    if key = "right" and m.sections.hasFocus() then
        if m.pgSearch.visible then
            m.kb.setFocus(true)
        else if m.pgHome.visible then
            m.rows.setFocus(true)
        else if m.cats.content <> invalid and m.cats.content.getChildCount() > 0 then
            m.cats.setFocus(true)
        else
            m.grid.setFocus(true)
        end if
        return true
    else if key = "right" and m.cats.hasFocus() then
        m.grid.setFocus(true)
        return true
    else if key = "left" then
        if m.grid.hasFocus() then
            m.cats.setFocus(true)
            return true
        else if m.rows.hasFocus() or m.cats.hasFocus() then
            m.sections.setFocus(true)
            return true
        end if
    else if key = "back" then
        if m.grid.hasFocus() then
            m.cats.setFocus(true)
            return true
        else if m.rows.hasFocus() or m.cats.hasFocus() then
            m.sections.setFocus(true)
            return true
        end if
    end if
    return false
end function

' ---------------- utilidades ----------------
function Str2(v as dynamic) as string
    if v = invalid then return ""
    t = type(v)
    if t = "roString" or t = "String" then return v
    if t = "roInt" or t = "Integer" or t = "roFloat" or t = "Float" or t = "Double" or t = "roDouble" or t = "LongInteger" or t = "roLongInteger" then return Str(v).Trim()
    if t = "roBoolean" or t = "Boolean" then
        if v then return "1"
        return "0"
    end if
    return ""
end function

function fmt(ext as string) as string
    e = LCase(ext)
    if e = "m3u8" then return "hls"
    if e = "mkv" then return "mkv"
    if e = "ts" then return "ts"
    return "mp4"
end function
