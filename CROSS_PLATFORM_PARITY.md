# HDify Cross-Platform Parity Standard (Android & Windows)

Dokumen ini adalah aturan evaluasi dan standar wajib pengembangan aplikasi **HDify** untuk versi **Android** dan **Native Windows Desktop**:

---

## 1. Single Source of Truth & Version Parity
* Nomor versi aplikasi Android dan Windows **WAJIB SELALU SAMA** di setiap rilis.
* Tidak boleh ada versi yang tertinggal (misal Android v2.2.4 sedangkan Windows v2.2.5).
* Untuk menaikkan versi serentak, jalankan:
  ```bash
  node sync_version.js <versi_baru>
  # atau klik ganda: bump_version.bat
  ```
  Skrip ini otomatis menyinkronkan:
  - `app/build.gradle` (`versionCode` auto-increment + `versionName`)
  - `hdify-windows/package.json` (`version`)
  - `hdify-windows/index.html` (Titlebar & Footer)
  - `hdify-windows/README.md` (Tautan installer)

---

## 2. Bug Fix & Improvement Synchronization
* **Setiap perbaikan bug, penyesuaian parameter, atau optimasi kualitas pada salah satu platform WAJIB langsung dianalisis dan diterapkan ke platform lainnya secara serentak sebelum perilisan.**
  - *Contoh Kasus Kompresi Video*: Ketika bitrate floor dan anti-macroblocking diterapkan di Android (`CompressPreset.kt` & `VideoCompressorProcessor.kt`), parameter yang setara (`-profile:v high`, `-preset medium`, `-pix_fmt yuv420p`, `-g 30`, dan quality floor) langsung diterapkan ke Windows (`main.js` & `app.js`).
  - *Contoh Kasus Bahasa / Desain*: Penambahan bahasa atau perbaikan UI wajib tercermin di kedua aplikasi.

---

## 3. Clean Release Artifacts
* **Windows Desktop (`hdify-windows/dist/`)**:
  - Telah dipasang hook `predist: "node clean-old-dist.js"`.
  - Setiap kali `npm run dist` dijalankan, file installer dan portable versi lama akan otomatis dihapus bersih, menyisakan hanya file rilis terbaru (menghindari duplikasi ratusan megabyte).
* **Android (`app/build/outputs/apk/`)**:
  - Gradle menghasilkan `app-debug.apk` atau `app-release.apk` terbaru tanpa menumpuk installer kadaluarsa.
