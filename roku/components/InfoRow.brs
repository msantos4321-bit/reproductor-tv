sub init()
    m.bg = m.top.findNode("bg")
    m.hline = m.top.findNode("hline")
    m.pre = m.top.findNode("pre")
    m.logo = m.top.findNode("logo")
    m.t1 = m.top.findNode("t1")
    m.t2 = m.top.findNode("t2")
    m.t3 = m.top.findNode("t3")
    m.gStat = m.top.findNode("gStat")
    m.av = m.top.findNode("av")
    m.lab = m.top.findNode("lab")
    m.hv = m.top.findNode("hv")
    m.track = m.top.findNode("track")
    m.barA = m.top.findNode("barA")
    m.barH = m.top.findNode("barH")
    m.gCols = m.top.findNode("gCols")
    m.gChart = m.top.findNode("gChart")
    m.colLbl = []
    m.bars = []
    m.cur = invalid
    m.focused = false
    m.fontN = mkFont("font:SystemFontFile", 28)
    m.fontS = mkFont("font:SystemFontFile", 24)
    m.fontB = mkFont("font:BoldSystemFontFile", 28)
    m.fontH = mkFont("font:BoldSystemFontFile", 30)
    m.fontSB = mkFont("font:BoldSystemFontFile", 24)
end sub

function mkFont(uri as string, size as integer) as object
    f = CreateObject("roSGNode", "Font")
    f.uri = uri
    f.size = size
    return f
end function

function fv(k as string) as dynamic
    c = m.cur
    if c = invalid or not c.hasField(k) then return invalid
    return c.getField(k)
end function

function fs(k as string) as string
    v = fv(k)
    if v = invalid then return ""
    if type(v) = "roString" or type(v) = "String" then return v
    return ""
end function

function fb(k as string) as boolean
    return fv(k) = true
end function

sub onContent()
    if m.cur <> invalid then m.cur.unobserveFieldScoped("rev")
    m.cur = m.top.itemContent
    if m.cur <> invalid then m.cur.observeFieldScoped("rev", "draw")
    draw()
end sub

sub onFocus()
    f = (m.top.focusPercent > 0.5)
    if f = m.focused then return
    m.focused = f
    paint()
end sub

function rowWidth() as integer
    wd = m.top.width
    if wd = invalid or wd <= 0 then wd = 1400
    return Int(wd)
end function

sub resetAll()
    m.pre.visible = false
    m.logo.visible = false
    m.t1.visible = false
    m.t2.visible = false
    m.t3.visible = false
    m.gStat.visible = false
    m.gCols.visible = false
    m.gChart.visible = false
    m.hline.visible = false
    m.t1.wrap = false
    m.t1.maxLines = 1
    m.t1.height = 0
    m.t1.vertAlign = "top"
    m.pre.width = 150
    m.t1.font = m.fontN
    m.t3.font = m.fontB
end sub

sub draw()
    resetAll()
    if m.cur = invalid then return
    rt = fs("rt")
    w = rowWidth()
    m.bg.width = w
    if rt = "h" then
        m.t1.visible = true
        m.t1.font = m.fontH
        m.t1.translation = [16, 22]
        m.t1.width = w - 420
        m.t1.text = fs("t1")
        m.t3.visible = fs("t3") <> ""
        m.t3.font = m.fontS
        m.t3.translation = [w - 416, 8]
        m.t3.width = 400
        m.t3.text = fs("t3")
        m.hline.visible = true
        m.hline.width = w - 32
    else if rt = "stat" then
        drawStat(w)
    else if rt = "cols" then
        drawCols(w)
    else if rt = "chart" then
        drawChart(w)
    else
        drawText(w, rt = "btn")
    end if
    paint()
end sub

sub drawText(w as integer, isBtn as boolean)
    x = 16
    if isBtn then x = 28
    pre = fs("pre")
    if pre <> "" then
        m.pre.visible = true
        m.pre.text = pre
        m.pre.translation = [x, 0]
        if Len(pre) <= 3 then
            m.pre.width = 50
            x = x + 56
        else
            x = x + 160
        end if
    end if
    lg = fs("lg1")
    ab = fs("ab1")
    if lg <> "" or ab <> "" then
        m.logo.visible = true
        m.logo.translation = [x, 10]
        m.logo.uri = lg : m.logo.ab = ab : m.logo.col = fs("col1")
        x = x + 64
    end if
    t3 = fs("t3")
    rightW = 0
    if t3 <> "" then
        rightW = Len(t3) * 17 + 30
        if rightW < 160 then rightW = 160
        if rightW > 520 then rightW = 520
        m.t3.visible = true
        m.t3.text = t3
        m.t3.translation = [w - rightW - 16, 0]
        m.t3.width = rightW
    end if
    tw = w - x - rightW - 32
    t2 = fs("t2")
    m.t1.visible = true
    m.t1.text = fs("t1")
    m.t1.width = tw
    if isBtn or fb("bold") then m.t1.font = m.fontB
    if t2 <> "" then
        m.t1.translation = [x, 6]
        m.t2.visible = true
        m.t2.text = t2
        m.t2.translation = [x, 40]
        m.t2.width = tw
    else if fb("wrap") then
        m.t1.font = m.fontS
        m.t1.wrap = true
        m.t1.maxLines = 2
        m.t1.height = 64
        m.t1.translation = [x, 4]
        m.t1.vertAlign = "center"
    else
        m.t1.translation = [x, 18]
    end if
end sub

sub drawStat(w as integer)
    m.gStat.visible = true
    m.av.text = fs("av")
    m.hv.text = fs("hv")
    m.lab.text = fs("t1")
    m.lab.translation = [w / 2 - 300, 6]
    m.hv.translation = [w - 316, 4]
    tw = w - 32
    m.track.width = tw
    cx = 16 + Int(tw / 2)
    half = Int(tw / 2) - 4
    af = fv("af")
    hf = fv("hf")
    if af = invalid then af = 0
    if hf = invalid then hf = 0
    aw = Int(half * af)
    hw = Int(half * hf)
    m.barA.width = aw
    m.barA.translation = [cx - 4 - aw, 48]
    m.barH.width = hw
    m.barH.translation = [cx + 4, 48]
    b = fs("better")
    grey = "0x5C6478FF"
    blue = "0x3D9BFFFF"
    if b = "a" then
        m.barA.color = blue : m.barH.color = grey
        m.av.color = "0xFFFFFFFF" : m.hv.color = "0x8E97ADFF"
    else if b = "h" then
        m.barA.color = grey : m.barH.color = blue
        m.av.color = "0x8E97ADFF" : m.hv.color = "0xFFFFFFFF"
    else
        m.barA.color = grey : m.barH.color = grey
        m.av.color = "0xFFFFFFFF" : m.hv.color = "0xFFFFFFFF"
    end if
end sub

sub drawCols(w as integer)
    m.gCols.visible = true
    vals = fv("vals")
    if type(vals) <> "roArray" then vals = []
    n = vals.Count()
    while m.colLbl.Count() < n
        l = CreateObject("roSGNode", "Label")
        l.horizAlign = "right"
        l.vertAlign = "center"
        l.height = 68
        m.gCols.appendChild(l)
        m.colLbl.Push(l)
    end while
    head = fb("bold")
    cw = fv("cw")
    if cw = invalid or cw <= 0 then cw = 110
    x0 = w - 16 - n * cw
    for i = 0 to m.colLbl.Count() - 1
        l = m.colLbl[i]
        if i < n then
            l.visible = true
            l.text = vals[i]
            l.width = cw
            l.translation = [x0 + i * cw, 0]
            if head then
                l.font = m.fontSB : l.color = "0x8E97ADFF"
            else if i = n - 1 and fb("lastBold") then
                l.font = m.fontB : l.color = "0xFFFFFFFF"
            else
                l.font = m.fontS : l.color = "0xE6EAF2FF"
            end if
        else
            l.visible = false
        end if
    end for
    ' nombre a la izquierda (con escudo o número)
    x = 16
    pre = fs("pre")
    if pre <> "" then
        m.pre.visible = true
        m.pre.text = pre
        m.pre.width = 60
        m.pre.translation = [x, 0]
        x = x + 64
    else
        m.pre.width = 150
    end if
    lg = fs("lg1")
    ab = fs("ab1")
    if lg <> "" or ab <> "" then
        m.logo.visible = true
        m.logo.translation = [x, 10]
        m.logo.uri = lg : m.logo.ab = ab : m.logo.col = fs("col1")
        x = x + 64
    end if
    m.t1.visible = true
    m.t1.text = fs("t1")
    m.t1.translation = [x, 18]
    m.t1.width = x0 - x - 10
    if head then
        m.t1.font = m.fontSB
        m.t1.color = "0x8E97ADFF"
    else if fb("hl") then
        m.t1.font = m.fontB
    end if
    t2 = fs("t2")
    if t2 <> "" and not head then
        m.t1.translation = [x, 6]
        m.t2.visible = true
        m.t2.text = t2
        m.t2.translation = [x, 40]
        m.t2.width = x0 - x - 10
    end if
end sub

' Gráfica de probabilidad de ganar: arriba el local, abajo el visitante
sub drawChart(w as integer)
    m.gChart.visible = true
    vals = fv("vals")
    if type(vals) <> "roArray" then vals = []
    n = vals.Count()
    left0 = 260
    cw = w - left0 - 16
    while m.bars.Count() < 120
        r = CreateObject("roSGNode", "Rectangle")
        m.gChart.appendChild(r)
        m.bars.Push(r)
    end while
    if m.midLine = invalid then
        m.midLine = CreateObject("roSGNode", "Rectangle")
        m.midLine.color = "0x5C6478FF"
        m.midLine.height = 2
        m.gChart.appendChild(m.midLine)
    end if
    m.midLine.translation = [left0, 33]
    m.midLine.width = cw
    bw = 4
    if n > 0 then bw = Int(cw / n)
    if bw < 2 then bw = 2
    ' escala: la mayor diferencia contra 50% llena la mitad de la fila
    maxDev = 10.0
    for i = 0 to n - 1
        dv = Abs(vals[i] - 50)
        if dv > maxDev then maxDev = dv
    end for
    for i = 0 to m.bars.Count() - 1
        r = m.bars[i]
        if i < n then
            v = vals[i]
            d = v - 50
            hgt = Int(Abs(d) * 30 / maxDev)
            if hgt < 1 then hgt = 1
            r.visible = true
            r.width = bw - 2
            if bw <= 3 then r.width = bw - 1
            r.height = hgt
            if d >= 0 then
                r.translation = [left0 + i * bw, 33 - hgt]
                r.color = "0x3D9BFFFF"
            else
                r.translation = [left0 + i * bw, 35]
                r.color = "0xFF9F0AFF"
            end if
        else
            r.visible = false
        end if
    end for
    m.t1.visible = true
    m.t1.font = m.fontS
    m.t1.color = "0x3D9BFFFF"
    m.t1.text = fs("t1")
    m.t1.translation = [16, 2]
    m.t1.width = left0 - 30
    m.t2.visible = true
    m.t2.color = "0xFF9F0AFF"
    m.t2.text = fs("t2")
    m.t2.translation = [16, 38]
    m.t2.width = left0 - 30
end sub

sub paint()
    rt = fs("rt")
    f = m.focused
    if rt = "btn" then
        if f then
            m.bg.color = "0xE6EAF2FF"
            m.t1.color = "0x0B0C12FF"
            m.t3.color = "0x0B0C12FF"
            m.pre.color = "0x3A4256FF"
        else
            m.bg.color = "0x1F2433FF"
            m.t1.color = "0xFFFFFFFF"
            m.t3.color = "0xB9C2D6FF"
            m.pre.color = "0x8E97ADFF"
        end if
        return
    end if
    if rt = "h" then
        m.bg.color = "0x00000000"
        if f then m.bg.color = "0x1A1E2AFF"
        m.t1.color = "0x3D9BFFFF"
        m.t3.color = "0x8E97ADFF"
        return
    end if
    if f then m.bg.color = "0x232838FF" else m.bg.color = "0x00000000"
    if rt = "chart" then return
    if rt = "cols" and fb("bold") then return
    if fb("hl") then
        m.t1.color = "0x5EB1FFFF"
    else if fb("warn") then
        m.t1.color = "0xFF6961FF"
    else if fb("dim") then
        m.t1.color = "0x8E97ADFF"
    else
        m.t1.color = "0xE6EAF2FF"
    end if
    m.t2.color = "0x8E97ADFF"
    m.pre.color = "0x8E97ADFF"
    c3 = fs("c3")
    if c3 = "red" then
        m.t3.color = "0xFF6961FF"
    else if c3 = "green" then
        m.t3.color = "0x30D158FF"
    else if c3 = "orange" then
        m.t3.color = "0xFF9F0AFF"
    else
        m.t3.color = "0xFFFFFFFF"
    end if
end sub
