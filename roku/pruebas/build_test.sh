#!/bin/bash
# Arma una copia de la app que habla con el servidor de prueba local
set -e
rm -rf /tmp/rokutest && mkdir -p /tmp/rokutest
cd /home/claude/reproductor-tv/roku
cp -r manifest source components images /tmp/rokutest/
sed -i 's#https://wnwecaapchcfhcljsvli.supabase.co#http://127.0.0.1:8099#g; s#https://translate.googleapis.com/translate_a/single#http://127.0.0.1:8099/translate#g' /tmp/rokutest/components/*.brs
sed -i "s|font:BoldSystemFontFile|common:/Fonts/Metropolis-SemiBold.ttf|g; s|font:SystemFontFile|common:/Fonts/Metropolis-Regular.ttf|g" /tmp/rokutest/components/*.xml /tmp/rokutest/components/*.brs
cd /tmp/rokutest && rm -f /tmp/rokutest.zip && zip -qr /tmp/rokutest.zip manifest source components images
echo built
