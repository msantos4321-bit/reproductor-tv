sub init()
      m.img = m.top.findNode("img")
      m.bar = m.top.findNode("bar")
      m.barBg = m.top.findNode("barBg")
      m.cap = m.top.findNode("cap")
    end sub
    sub onContent()
      c = m.top.itemContent
      if c = invalid then return
      m.img.uri = c.HDPosterUrl
      f = 0
      if c.hasField("frac") then f = c.frac
      m.barBg.visible = f > 0
      m.bar.visible = f > 0
      m.bar.width = 200 * f
      m.cap.text = ""
      if c.hasField("cap") then m.cap.text = c.cap
    end sub
    sub onFocus()
      m.img.opacity = 0.75 + 0.25 * m.top.focusPercent
    end sub
