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
    m.eps = m.top.findNode("eps")
    m.info = m.top.findNode("info")
    m.video = m.top.findNode("video")
    m.qrBox = m.top.findNode("qrBox")
    m.qr = m.top.findNode("qr")
    m.qrCode = m.top.findNode("qrCode")
    m.pairTimer = m.top.findNode("pairTimer")
    m.pairTimer.observeField("fire", "onPairTick")
    m.pairCode = ""
    m.hpCode = ""

    m.formList.observeField("itemSelected", "onFormSelect")
    m.sections.observeField("itemFocused", "onSectionFocus")
    m.cats.observeField("itemSelected", "onCatSelect")
    m.grid.observeField("itemSelected", "onGridSelect")
    m.grid.observeField("itemFocused", "onGridFocus")
    m.eps.observeField("itemSelected", "onEpSelect")
    m.video.observeField("state", "onVideoState")

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
    data = invalid
    if r.body <> invalid and r.body <> "" then data = ParseJson(r.body)
    if id = "login" then
        onLogin(r.code, data)
    else if id = "check" then
        onCheck(r.code, r.body)
    else if id = "trial" then
        onTrial(data)
    else if id = "activate" then
        onActivate(r.body)
    else if Left(id, 5) = "cats:" then
        onCats(data)
    else if Left(id, 6) = "items:" then
        onItems(id, data)
    else if id = "series" then
        onSeriesInfo(data)
    else if id = "pairNew" then
        onPairNew(r.body)
    else if id = "pairGet" then
        onPairGet(data)
    else if id = "hpPoll" then
        onHpPoll(r.body)
    end if
end sub

' ---------------- flujo ----------------
sub start()
    if m.server = "" or m.user = "" or m.pass = "" then
        showLogin("")
    else if m.code = "" then
        showActivation("")
    else
        m.sub.text = "Revisando tu activación…"
        rpc("check", "cc_lic_check", { p_code: m.code, p_device: m.deviceId })
    end if
end sub

sub showForm(title as string, text as string, items as object, mode as string)
    m.mode = mode
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
    srv = m.server
    if srv = "" then srv = "(toca para escribir)"
    usr = m.user
    if usr = "" then usr = "(toca para escribir)"
    pw = "(toca para escribir)"
    if m.pass <> "" then pw = "••••••"
    startPairing()
    showForm("Escribe los datos que te dio tu proveedor", "CineCasa es un reproductor: funciona con el servicio que tú ya tienes. Tus datos solo se guardan en esta tele.", ["Servidor: " + srv, "Usuario: " + usr, "Contraseña: " + pw, "Entrar", "Ver demostración"], "login")
    m.formMsg.text = msg
end sub

sub showActivation(msg as string)
    m.sub.text = "Activa este aparato"
    ref = rget("ref")
    refTxt = "¿Quién te recomendó? (opcional)"
    if ref <> "" then refTxt = "Te recomendó: " + ref
    showForm("Pruébala gratis 21 días", "Sin pedir código: tu prueba se activa al momento en hasta 5 aparatos. Si alguien te recomendó, escribe su código para que reciba su regalo cuando contrates.", ["Empezar prueba gratis", refTxt, "Ya tengo un código", "Unir a mi cuenta (desde mi celular o tablet)", "Cambiar datos del proveedor"], "activation")
    m.formMsg.text = msg
end sub

sub onFormSelect()
    i = m.formList.itemSelected
    if m.mode = "login" then
        if i = 0 then
            ask("Servidor (ej. http://servidor.com:8080)", m.server, "server")
        else if i = 1 then
            ask("Usuario", m.user, "user")
        else if i = 2 then
            ask("Contraseña", "", "pass")
        else if i = 3 then
            doLogin()
        else if i = 4 then
            m.server = m.demo : m.user = "demo" : m.pass = "demo"
            doLogin()
        end if
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
        if i = 0 then
            startHousePair()
        else if i = 1 then
            ask("Tu código (ej. CC-7F3K-92QD)", "", "code")
        else if i = 2 then
            clearProvider()
        else
            showHome()
        end if
    else if m.mode = "hpair" then
        stopHousePair()
        if m.code = "" then showActivation("") else showHome()
    end if
end sub

sub clearProvider()
    m.reg.Delete("server") : m.reg.Delete("user") : m.reg.Delete("pass") : m.reg.Flush()
    m.server = "" : m.user = "" : m.pass = ""
    showLogin("")
end sub

' ---------- Mi cuenta: unir esta tele a la cuenta de otro aparato ----------
sub showAccount(msg as string)
    m.sub.text = "Mi cuenta"
    cur = "Esta tele tiene el código " + m.code + "."
    if m.code = "" then cur = "Esta tele todavía no tiene código."
    showForm("Mi cuenta", cur + " Para usar tu misma cuenta y suscripción que en tu celular, tablet o tele Android, elige «Unir a mi cuenta».", ["Unir a mi cuenta (desde mi celular o tablet)", "Escribir mi código de activación", "Cambiar datos del proveedor", "Volver"], "account")
    m.formMsg.text = msg
end sub

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
        m.formList.setFocus(true)
        return
    end if
    f = m.askField
    if f = "server" then
        m.server = v : showLogin("")
    else if f = "user" then
        m.user = v : showLogin("")
    else if f = "pass" then
        m.pass = v : showLogin("")
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
    m.formList.setFocus(true)
end sub

sub doLogin()
    if m.server = "" or m.user = "" or m.pass = "" then
        m.formMsg.text = "Llena los tres datos."
        return
    end if
    m.formMsg.text = "Conectando…"
    request("login", api(""), "GET", "", invalid)
end sub

' ---------------- configurar desde el celular (QR) ----------------
sub startPairing()
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
        m.server = Str2(data.server) : m.user = Str2(data.user) : m.pass = Str2(data.pass)
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
        start()
    else if code = 200 then
        showLogin("Usuario o contraseña incorrectos. Revísalos con tu proveedor.")
    else
        showLogin("No se pudo conectar con el servidor (" + Str(code).Trim() + "). Revisa la dirección.")
    end if
end sub

sub onCheck(code as integer, body as string)
    r = body.Replace(Chr(34), "").Trim()
    if r = "ok" then
        showHome()
    else if code <> 200 then
        showHome() ' sin internet hacia la nube: deja ver (se revisa la próxima vez)
    else
        m.code = "" : m.reg.Delete("code") : m.reg.Flush()
        msg = "Tu activación ya no es válida."
        if r = "expired" then msg = "Tu plan venció. Pide tu renovación a quien te lo vendió."
        if r = "revoked" then msg = "Tu código fue desactivado."
        showActivation(msg)
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
        showHome()
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
        showHome()
    else if r = "full" then
        m.formMsg.text = "Ese código ya se usó en todos los aparatos que permite."
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

' ---------------- inicio ----------------
sub showHome()
    m.mode = "home"
    m.form.visible = false
    m.home.visible = true
    m.sub.text = "Código " + m.code
    c = CreateObject("roSGNode", "ContentNode")
    for each t in ["En vivo", "Películas", "Series", "Mi cuenta"]
        n = c.createChild("ContentNode")
        n.title = t
    end for
    m.sections.content = c
    m.section = -1
    m.sections.setFocus(true)
    onSectionFocus()
end sub

sub onSectionFocus()
    i = m.sections.itemFocused
    if i < 0 then i = 0
    if i = m.section then return
    m.section = i
    m.eps.visible = false
    m.grid.visible = true
    m.grid.content = CreateObject("roSGNode", "ContentNode")
    m.cats.content = CreateObject("roSGNode", "ContentNode")
    if i = 3 then
        m.info.text = "Une esta tele a tu cuenta, escribe tu código o cambia los datos del proveedor."
        return
    end if
    actions = ["get_live_categories", "get_vod_categories", "get_series_categories"]
    m.info.text = "Cargando…"
    request("cats:" + Str(i).Trim(), api(actions[i]), "GET", "", invalid)
end sub

sub onCats(data as object)
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
    m.info.text = ""
    loadItems(0)
end sub

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

sub onCatSelect()
    loadItems(m.cats.itemSelected)
    m.grid.setFocus(true)
end sub

sub loadItems(ci as integer)
    if m.cats.content = invalid or m.cats.content.getChildCount() = 0 then return
    cat = m.cats.content.getChild(ci).catId
    actions = ["get_live_streams", "get_vod_streams", "get_series"]
    u = api(actions[m.section])
    if cat <> "" then u = u + "&category_id=" + cat
    m.info.text = "Cargando…"
    m.eps.visible = false
    m.grid.visible = true
    request("items:" + Str(m.section).Trim(), u, "GET", "", invalid)
end sub

sub onItems(id as string, data as object)
    sec = Val(Mid(id, 7))
    if sec <> m.section then return
    c = CreateObject("roSGNode", "ContentNode")
    count = 0
    if type(data) = "roArray" then
        for each it in data
            if count >= 600 then exit for
            n = c.createChild("ContentNode")
            n.shortDescriptionLine1 = Str2(it.name)
            img = ""
            if sec < 2 then img = Str2(it.stream_icon) else img = Str2(it.cover)
            if Left(img, 4) <> "http" then img = ""
            if img = "" then img = "pkg:/images/poster.png"
            n.HDPosterUrl = img
            n.FHDPosterUrl = img
            if sec = 2 then
                n.addFields({ xid: Str2(it.series_id), ext: "" })
            else
                ext = "m3u8"
                if sec = 1 and Str2(it.container_extension) <> "" then ext = Str2(it.container_extension)
                n.addFields({ xid: Str2(it.stream_id), ext: ext })
            end if
            count = count + 1
        end for
    end if
    m.grid.content = c
    if count = 0 then
        m.info.text = "No hay nada en esta categoría."
    else
        m.info.text = Str(count).Trim() + " títulos"
    end if
end sub

sub onGridFocus()
    if m.grid.content = invalid then return
    it = m.grid.content.getChild(m.grid.itemFocused)
    if it <> invalid then m.info.text = it.shortDescriptionLine1
end sub

sub onGridSelect()
    it = m.grid.content.getChild(m.grid.itemSelected)
    if it = invalid then return
    if m.section = 2 then
        m.info.text = "Cargando episodios…"
        m.seriesTitle = it.shortDescriptionLine1
        request("series", api("get_series_info") + "&series_id=" + it.xid, "GET", "", invalid)
    else if m.section = 0 then
        play(it.shortDescriptionLine1, base() + "/live/" + m.user + "/" + m.pass + "/" + it.xid + ".m3u8", "hls", true)
    else
        play(it.shortDescriptionLine1, base() + "/movie/" + m.user + "/" + m.pass + "/" + it.xid + "." + it.ext, fmt(it.ext), false)
    end if
end sub

function fmt(ext as string) as string
    e = LCase(ext)
    if e = "m3u8" then return "hls"
    if e = "mkv" then return "mkv"
    if e = "ts" then return "ts"
    return "mp4"
end function

sub onSeriesInfo(data as object)
    c = CreateObject("roSGNode", "ContentNode")
    count = 0
    if data <> invalid and data.episodes <> invalid then
        seasons = []
        for each s in data.episodes
            seasons.Push(s)
        end for
        seasons.Sort()
        for each s in seasons
            for each ep in data.episodes[s]
                n = c.createChild("ContentNode")
                t = ""
                t = Str2(ep.title)
                n.title = "T" + s + " · E" + Str2(ep.episode_num) + "   " + t
                ext = "mp4"
                if Str2(ep.container_extension) <> "" then ext = Str2(ep.container_extension)
                n.addFields({ xid: Str2(ep.id), ext: ext })
                count = count + 1
            end for
        end for
    end if
    if count = 0 then
        m.info.text = "Esta serie no tiene episodios."
        return
    end if
    m.eps.content = c
    m.grid.visible = false
    m.eps.visible = true
    m.eps.setFocus(true)
    m.info.text = m.seriesTitle
end sub

sub onEpSelect()
    it = m.eps.content.getChild(m.eps.itemSelected)
    if it = invalid then return
    play(m.seriesTitle + " · " + it.title, base() + "/series/" + m.user + "/" + m.pass + "/" + it.xid + "." + it.ext, fmt(it.ext), false)
end sub

' ---------------- reproductor ----------------
sub play(title as string, url as string, format as string, live as boolean)
    c = CreateObject("roSGNode", "ContentNode")
    c.title = title
    c.url = url
    c.streamFormat = format
    c.live = live
    m.returnFocus = m.top.focusedChild
    m.video.content = c
    m.video.visible = true
    m.video.control = "play"
    m.video.setFocus(true)
end sub

sub stopVideo()
    m.video.control = "stop"
    m.video.visible = false
    if m.eps.visible then
        m.eps.setFocus(true)
    else
        m.grid.setFocus(true)
    end if
end sub

sub onVideoState()
    s = m.video.state
    if s = "error" then
        m.info.text = "No se pudo reproducir (" + m.video.errorMsg + ")."
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
        if m.code = "" then showActivation("") else showHome()
        return true
    end if
    if m.mode <> "home" then return false
    if key = "right" then
        if m.sections.hasFocus() then
            if m.section = 3 then return true
            m.cats.setFocus(true)
            return true
        else if m.cats.hasFocus() then
            if m.eps.visible then m.eps.setFocus(true) else m.grid.setFocus(true)
            return true
        end if
    else if key = "left" then
        if m.cats.hasFocus() then
            m.sections.setFocus(true)
            return true
        else if m.grid.hasFocus() or m.eps.hasFocus() then
            m.cats.setFocus(true)
            return true
        end if
    else if key = "OK" and m.sections.hasFocus() and m.section = 3 then
        showAccount("")
        return true
    else if key = "back" then
        if m.eps.hasFocus() then
            m.eps.visible = false
            m.grid.visible = true
            m.grid.setFocus(true)
            return true
        else if m.grid.hasFocus() then
            m.cats.setFocus(true)
            return true
        else if m.cats.hasFocus() then
            m.sections.setFocus(true)
            return true
        end if
    end if
    return false
end function
