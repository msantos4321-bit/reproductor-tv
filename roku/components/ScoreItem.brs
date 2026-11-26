sub init()
    m.bg = m.top.findNode("bg")
    m.liveBar = m.top.findNode("liveBar")
    m.topL = m.top.findNode("top")
    m.star = m.top.findNode("star")
    m.gEv = m.top.findNode("gEv")
    m.gOne = m.top.findNode("gOne")
    m.lgA = m.top.findNode("lgA")
    m.lgH = m.top.findNode("lgH")
    m.nA = m.top.findNode("nA")
    m.nH = m.top.findNode("nH")
    m.sA = m.top.findNode("sA")
    m.sH = m.top.findNode("sH")
    m.lgOne = m.top.findNode("lgOne")
    m.chBox = m.top.findNode("chBox")
    m.chImg = m.top.findNode("chImg")
    m.evA = m.top.findNode("evA")
    m.evH = m.top.findNode("evH")
    m.one = m.top.findNode("one")
    m.foot = m.top.findNode("foot")
    m.cur = invalid
end sub

function fv(c as object, k as string) as dynamic
    if c.hasField(k) then return c.getField(k)
    return invalid
end function

function fs(c as object, k as string) as string
    v = fv(c, k)
    if v = invalid then return ""
    return v
end function

function fb(c as object, k as string) as boolean
    v = fv(c, k)
    return v = true
end function

sub onContent()
    c = m.top.itemContent
    if m.cur <> invalid then m.cur.unobserveFieldScoped("rev")
    m.cur = c
    if c = invalid then return
    c.observeFieldScoped("rev", "draw")
    draw()
end sub

sub draw()
    c = m.cur
    if c = invalid then return
    k = fs(c, "k")
    live = fb(c, "live")
    m.liveBar.visible = live
    m.topL.text = fs(c, "top")
    if live then m.topL.color = "0xFF453AFF" else m.topL.color = "0x8E97ADFF"
    m.star.visible = fb(c, "mine")
    m.foot.text = fs(c, "foot")
    if k = "ev" then
        m.gEv.visible = true
        m.gOne.visible = false
        team(m.lgA, m.nA, m.sA, fs(c, "alg"), fs(c, "aab"), fs(c, "acol"), fs(c, "an"), fs(c, "asc"), fb(c, "aw"), fb(c, "hw"))
        team(m.lgH, m.nH, m.sH, fs(c, "hlg"), fs(c, "hab"), fs(c, "hcol"), fs(c, "hn"), fs(c, "hsc"), fb(c, "hw"), fb(c, "aw"))
    else
        m.gEv.visible = false
        m.gOne.visible = true
        m.one.text = fs(c, "hn")
        isCh = (k = "ch")
        hasEv = isCh and fs(c, "alg") + fs(c, "aab") <> ""
        m.lgOne.visible = not isCh
        if not isCh then
            m.lgOne.uri = fs(c, "hlg") : m.lgOne.ab = fs(c, "hab") : m.lgOne.col = fs(c, "hcol")
        end if
        m.evA.visible = hasEv
        m.evH.visible = hasEv
        img = fs(c, "img")
        m.chBox.visible = isCh and not hasEv
        m.chImg.visible = isCh and not hasEv and img <> ""
        if isCh and not hasEv then m.chImg.uri = img
        if hasEv then
            m.evA.uri = fs(c, "alg") : m.evA.ab = fs(c, "aab") : m.evA.col = fs(c, "acol")
            m.evH.uri = fs(c, "hlg") : m.evH.ab = fs(c, "hab") : m.evH.col = fs(c, "hcol")
        end if
    end if
end sub

sub team(lg as object, n as object, s as object, uri as string, ab as string, col as string, name as string, score as string, win as boolean, lost as boolean)
    lg.uri = uri : lg.ab = ab : lg.col = col
    n.text = name
    s.text = score
    if lost then
        n.color = "0x8E97ADFF" : s.color = "0x8E97ADFF"
    else
        n.color = "0xE6EAF2FF" : s.color = "0xFFFFFFFF"
    end if
end sub

sub onFocus()
    f = m.top.focusPercent
    if f > 0.5 then m.bg.color = "0x262B3AFF" else m.bg.color = "0x171A24FF"
end sub
