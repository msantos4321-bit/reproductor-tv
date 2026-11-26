sub init()
    m.base = m.top.findNode("base")
    m.ini = m.top.findNode("ini")
    m.light = m.top.findNode("light")
    m.img = m.top.findNode("img")
    m.img.observeField("loadStatus", "onLoad")
    layout()
end sub

sub layout()
    s = m.top.size
    if s <= 0 then s = 64
    m.base.width = s : m.base.height = s
    m.light.width = s : m.light.height = s
    p = Int(s * 0.14)
    m.img.translation = [p, p]
    m.img.width = s - 2 * p : m.img.height = s - 2 * p
    m.img.loadWidth = s : m.img.loadHeight = s
    m.ini.width = s : m.ini.height = s
    f = CreateObject("roSGNode", "Font")
    f.uri = "font:BoldSystemFontFile"
    f.size = Int(s * 0.30)
    m.ini.font = f
end sub

sub onUri()
    u = m.top.uri
    m.light.visible = false
    m.ini.visible = true
    if u = invalid or u = "" then
        m.img.uri = ""
        return
    end if
    m.img.uri = u
end sub

sub onLoad()
    ok = (m.img.loadStatus = "ready")
    m.light.visible = ok
    m.ini.visible = not ok
end sub

sub onAb()
    m.ini.text = m.top.ab
end sub

sub onCol()
    c = m.top.col
    if c <> invalid and Len(c) = 6 then
        m.base.blendColor = "0x" + c + "FF"
    else
        m.base.blendColor = "0x5A5A60FF"
    end if
end sub
