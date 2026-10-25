sub init()
    m.bgr = m.top.findNode("bgr")
    m.img = m.top.findNode("img")
    m.bar = m.top.findNode("bar")
    m.barBg = m.top.findNode("barBg")
    m.cap = m.top.findNode("cap")
    m.top.observeField("width", "onSize")
    m.top.observeField("height", "onSize")
end sub

sub onSize()
    w = m.top.width : h = m.top.height
    if w <= 0 or h <= 0 then return
    m.bgr.width = w : m.bgr.height = h
    m.img.width = w : m.img.height = h
    m.barBg.translation = [10, h - 18] : m.barBg.width = w - 20
    m.bar.translation = [10, h - 18]
    m.cap.translation = [0, h - 50] : m.cap.width = w
    onContent()
end sub

sub onContent()
    c = m.top.itemContent
    if c = invalid then return
    m.img.uri = c.HDPosterUrl
    f = 0
    if c.hasField("frac") then f = c.frac
    m.barBg.visible = f > 0
    m.bar.visible = f > 0
    w = m.top.width
    if w <= 0 then w = 220
    m.bar.width = (w - 20) * f
    m.cap.text = ""
    if c.hasField("cap") then m.cap.text = c.cap
end sub

sub onFocus()
    m.img.opacity = 0.75 + 0.25 * m.top.focusPercent
end sub
