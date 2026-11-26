# Pruebas de la Roku en simulador

Se prueba la app de Roku sin tele real, con el simulador `brs-node` (npm) y un servidor falso.

1. `npm i brs-node` en una carpeta `rokusim` (aquí se usan rutas `/home/claude/rokusim`).
2. `python3 mock.py &` — nube, función deportes, proveedor Xtream y traductor de prueba en `127.0.0.1:8099`.
3. `./build_test.sh` — arma `/tmp/rokutest.zip` apuntando al servidor falso y cambia la letra del sistema por una que trae el simulador (solo en la copia de prueba).
4. `python3 drive.py pasos-s4.txt` — maneja el control (`k down*3 ok`, `w 2` espera, `s nombre` captura) y guarda capturas en `shots/`.

El simulador no muestra video (sale transparente) ni carga escudos reales; el servidor falso genera escudos con iniciales.
