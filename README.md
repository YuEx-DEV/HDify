# HDify

Aplikasi AI Image & Video Upscaler, Video Interpolation (60 FPS), Smart Compressor, dan Background Remover offline yang berjalan langsung di GPU lokal menggunakan Vulkan API. Tersedia versi **Android (Native Kotlin/C++)** dan **Windows Desktop (Electron/Node.js)**.

Semua proses AI berjalan 100% lokal di perangkat kamu tanpa kirim data ke server cloud / tanpa API berbayar.

---

## Fitur Utama

- **AI Image Upscaler**: Perbesar resolusi foto hingga 4x (4K) menggunakan model **Real-CUGAN** (khusus ilustrasi/anime) dan **Real-ESRGAN** (anime & foto realistis). Dilengkapi slider Before/After dan opsi denoise.
- **AI Video Upscaler & 60 FPS**: Interpolasi gerakan video hingga 60/120 FPS dengan model **RIFE v4.6**, serta opsi super-resolution video ke 2K/4K via hardware encoding H.264 / H.265 (HEVC).
- **Background Remover Studio**: Hapus background instan dengan AI Auto-Segmentation, kuas manual (Hapus & Pulihkan), dan Magic Color Wand. Sudah ada pilihan warna pas foto formal (Merah SKCK/KTP, Biru Ijazah, Putih, dll).
- **Smart Video Compressor**: Kompres ukuran video hingga 50–75% lebih kecil dengan adaptive bitrate tanpa bikin video pecah/buram. Termasuk fitur anti-ireng (adaptive shadow recovery).
- **Media Downloader**: Download video/audio tanpa watermark dari TikTok, YouTube, Instagram, X (Twitter), dan SoundCloud.
- **Hardware Benchmark**: Cek spesifikasi GPU dan benchmark kecepatan komputasi tile Vulkan secara real-time.

---

## Struktur Repositori

Proyek ini menggunakan satu codebase monorepo yang membagi versi Android dan Windows:

```
├── app/                  # Source code aplikasi Android (Kotlin, Android NDK, Vulkan NCNN)
├── hdify-windows/        # Source code aplikasi Windows Desktop (Electron, Node.js, FFmpeg)
├── gradle/ & gradlew     # Gradle wrapper untuk build Android
├── install.bat           # Skrip 1-klik pasang APK debug ke HP Android via ADB
└── sync_version.js       # Skrip sinkronisasi nomor versi Android & Windows
```

---

## Menjalankan Versi Windows Desktop

### Kebutuhan:
- Windows 10/11 (64-bit)
- Node.js (v18 ke atas)
- GPU yang mendukung Vulkan (NVIDIA, AMD, atau Intel HD/UHD Graphics)

### Langkah Cepat:
```bash
cd hdify-windows
npm install
npm start
```
Atau cukup klik ganda file **`start_hdify.bat`** di dalam folder `hdify-windows`.

### Build Installer (.exe):
```bash
cd hdify-windows
npm run dist
```
File installer setup dan portable akan otomatis dibuat di folder `hdify-windows/dist/`.

---

## Menjalankan Versi Android

### Kebutuhan:
- Android Studio Ladybug / Koala atau lebih baru
- Android SDK (API 26+) & NDK 26+
- HP Android dengan GPU support Vulkan (Android 8.0+)

### Build APK Debug via Terminal:
```bash
./gradlew assembleDebug
```
Atau sambungkan HP ke laptop dengan kabel data (pastikan USB Debugging aktif) lalu jalankan:
```cmd
install.bat
```

---

## Kredit & Lisensi

- Engine AI ditenagai oleh model open-source [Real-CUGAN](https://github.com/bilibili/real-cugan), [Real-ESRGAN](https://github.com/xinntao/Real-ESRGAN), dan [RIFE](https://github.com/hzwer/ECCV2022-RIFE) port Vulkan NCNN oleh [nihui](https://github.com/nihui).
- Pengolahan video menggunakan [FFmpeg](https://ffmpeg.org/).
- Dikembangkan oleh **Yuuka** ([@yuukaonlyone_](https://www.tiktok.com/@yuukaonlyone_)).
