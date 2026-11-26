#!/usr/bin/env python3
"""Maneja la app en el simulador: teclas del control, esperas y capturas.
Uso: drive.py pasos.txt   (líneas: 'k down', 'w 2', 's nombre')"""
import pty, os, sys, time, select, shutil
KEYS = {"up": b"\x1b[A", "down": b"\x1b[B", "right": b"\x1b[C", "left": b"\x1b[D", "ok": b"\r",
        "back": b"\x1b[3~", "options": b"\x1b[2~", "play": b"\x1b[F"}
steps = [l.strip() for l in open(sys.argv[1]) if l.strip() and not l.startswith("#")]
deep = "lic=CC-TEST-0001,srv=http://127.0.0.1:8099,usr=demo,pwd=demo,hh=HH,hs=SS"
env = dict(os.environ, TZ="America/Tijuana")
pid, fd = pty.fork()
if pid == 0:
    os.chdir("/home/claude/rokusim")
    os.execvpe("node", ["node", "node_modules/brs-node/bin/brs.cli.js", "-s", "shot.png", "-c", "0", "-k", deep, "/tmp/rokutest.zip"], env)
out = b""
def pump(sec):
    global out
    end = time.time() + sec
    while time.time() < end:
        r, _, _ = select.select([fd], [], [], 0.1)
        if r:
            try:
                out += os.read(fd, 65536)
            except OSError:
                return
pump(6)
for st in steps:
    cmd, _, arg = st.partition(" ")
    if cmd == "k":
        for k in arg.split():
            n = 1
            if "*" in k:
                k, n = k.split("*"); n = int(n)
            for _ in range(n):
                os.write(fd, KEYS[k]); pump(0.6)
    elif cmd == "w":
        pump(float(arg))
    elif cmd == "s":
        try: os.remove("/home/claude/rokusim/shot.png")
        except FileNotFoundError: pass
        os.write(fd, b"\x13"); pump(1.5)
        if os.path.exists("/home/claude/rokusim/shot.png"):
            shutil.move("/home/claude/rokusim/shot.png", f"/home/claude/rokusim/shots/{arg}.png")
pump(1)
os.kill(pid, 9)
txt = out.decode("utf8", "replace")
open("/home/claude/rokusim/console.log", "w").write(txt)
lines = [l for l in txt.splitlines() if l.strip() and "Screenshot saved" not in l]
print("\n".join(lines[-40:]))
