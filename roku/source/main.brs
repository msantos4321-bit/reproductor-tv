sub Main(args as dynamic)
    screen = CreateObject("roSGScreen")
    port = CreateObject("roMessagePort")
    screen.setMessagePort(port)
    scene = screen.CreateScene("MainScene")
    screen.show()
    ' El celular abre la tele con sus datos (código, proveedor y cuenta) para que entre sola
    if args <> invalid and (args.lic <> invalid or args.srv <> invalid or args.hh <> invalid) then scene.launchArgs = args
    inp = CreateObject("roInput")
    inp.SetMessagePort(port)
    while true
        msg = wait(0, port)
        if type(msg) = "roSGScreenEvent" and msg.isScreenClosed() then return
        if type(msg) = "roInputEvent" and msg.IsInput() then
            info = msg.GetInfo()
            if info <> invalid and (info.lic <> invalid or info.srv <> invalid or info.hh <> invalid) then scene.launchArgs = info
        end if
    end while
end sub
