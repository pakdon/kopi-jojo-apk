# Kopi Jojo POS — Aplikasi Android

APK Android untuk https://kasir.kopijojo.com, dibangun otomatis oleh GitHub Actions.

- **Update tampilan & fitur (OTA web):** aplikasi memuat kasir.kopijojo.com, jadi setiap deploy web di Vercel langsung dipakai aplikasi saat dibuka berikutnya. Tidak perlu pasang ulang APK.
- **Update APK (OTA GitHub):** setiap push ke branch `main` membuat release baru `v1.0.<nomor build>` berisi `kopi-jojo-pos.apk`. Aplikasi yang terpasang mengecek release terbaru saat dibuka (dan tiap 3 jam), lalu menawarkan **Update Sekarang**. Bisa juga lewat **Profil Saya → Cek Update**.
- Link unduh APK terbaru (tetap): `https://github.com/<pemilik>/<repo>/releases/latest/download/kopi-jojo-pos.apk`

Fitur native: printer thermal Bluetooth (struk & laci kas), cetak laporan A4/PDF, simpan export Excel/CSV ke `Download/KopiJojo`, kamera untuk absensi & pindai barcode.

## Struktur
| Path | Isi |
|---|---|
| `capacitor.config.json` | ID aplikasi `com.kopijojo.kasir`, URL `https://kasir.kopijojo.com` |
| `www/offline.html` | Layar saat dibuka tanpa internet |
| `android-overlay/` | File native yang menimpa template Capacitor (ikon, plugin, updater, signing) |
| `scripts/prepare-android.sh` | Membuat proyek Android + menerapkan overlay |
| `.github/workflows/build-apk.yml` | Build, tanda tangan, dan rilis APK |

Secrets wajib (Settings → Secrets and variables → Actions): `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.
