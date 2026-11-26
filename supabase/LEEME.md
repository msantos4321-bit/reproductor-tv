# Nube de CineCasa (Supabase «cinecasa»)

Los archivos se aplican **una sola vez y en orden** (01 → 12) en el editor SQL de Supabase.
Muchas funciones se escribieron de nuevo en archivos posteriores: la versión que vale es la del
archivo más nuevo. Por eso **no se debe volver a correr un archivo viejo** (por ejemplo, el 03
reemplazaría `cc_lic_activate` por una versión sin «aparatos quitados» ni «varias apps»).

| Archivo | Qué agrega | Aplicado |
|---|---|---|
| 01_cinecasa | Hogares, perfiles, sincronización, unir aparatos | ✔ |
| 02_licencias | Códigos de activación | ✔ |
| 03_negocio | Centro de control: distribuidores, créditos, sesiones | ✔ |
| 04_fallas | Reportes de cierres | ✔ |
| 05_prueba_unica | Una prueba gratis por aparato | ✔ |
| 06_recomendados | Recomendados | ✔ |
| 07_prueba_auto_y_pagos | Prueba automática, liga de pago | ✔ |
| 08_tele_con_qr | Configurar la tele con QR | ✔ |
| 09_varias_apps | CineCasa y Vista en el mismo panel | ✔ |
| 10_servidores_y_aparatos | Servidores por distribuidor, quitar un aparato | ✔ |
| 11_auditoria | Prueba sin distribuidor pide su liga, ayuda al vencer, QR con llave, sesiones que vencen, reportes limpios | ✔ (4 nov 2026) |
| 12_deportes | Caché de marcadores (`sports_cache`, solo la usa la función) y su limpieza diaria | ✔ (25 nov 2026) |

El 11 sí se puede volver a correr sin problema.

## Probar antes de aplicar

En `pruebas/` hay una prueba que levanta un Postgres local con los 11 archivos y revisa los casos
principales (servidores, ayuda al vencer, QR con llave, sesiones, permisos):

```
cd supabase/pruebas && npm install && node prueba.mjs
```


## Funciones (edge functions)

| Función | Para qué | Llave |
|---|---|---|
| `demo` | Catálogo de demostración para Google Play | pública |
| `deportes` | Marcadores, momios, calendario, tabla, lesionados y noticias (ESPN y TheSportsDB, gratis). Guarda los datos en `sports_cache` para que todas las teles lean de ahí | pública (solo lectura) |

El código de `deportes` está en `functions/deportes/index.ts`. Se publica desde Supabase (o con la herramienta de Supabase) con «verificar JWT» apagado.
