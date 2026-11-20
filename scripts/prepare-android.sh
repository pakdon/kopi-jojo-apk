#!/usr/bin/env bash
# Membuat proyek Android dari template Capacitor lalu menimpa dengan file di android-overlay/.
set -euo pipefail
cd "$(dirname "$0")/.."
rm -rf android
npx cap add android
RES=android/app/src/main/res
# Splash & ikon bawaan Capacitor diganti ikon Kopi Jojo
rm -rf $RES/drawable-land-* $RES/drawable-port-* $RES/drawable/splash.png $RES/mipmap-*
cp -R android-overlay/. android/
# Nama repo untuk cek update OTA (diisi otomatis di GitHub Actions)
if [ -n "${GITHUB_REPOSITORY:-}" ]; then
  sed -i "s#GANTI/REPO#${GITHUB_REPOSITORY}#" $RES/values/update.xml
fi
npx cap sync android
chmod +x android/gradlew
echo "Proyek Android siap."
