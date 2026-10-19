# HDify • Ultra HD Engine (Native Windows Edition)

Aplikasi desktop native Windows untuk pemrosesan media berbasis AI lokal dengan akselerasi GPU Vulkan dan FFmpeg hardware encoding.

---

### Fitur Utama (100% Identik & Setara dengan Versi Android):

1. **AI Image Upscaler**:
   - Model **Real-CUGAN** (SE & Pro) dan **Real-ESRGAN** (Plus & Anime).
   - Skala hingga **4x Ultra HD**.
   - Kontrol **Denoise** (`-1` s.d. `3`) dan mode **TTA** (*Test-Time Augmentation*).
   - **Ukuran Tile Komputasi GPU**: Auto (0), 100px (Hemat VRAM), 200px (Seimbang), 400px (Cepat).
   - **Pratinjau Crop Cepat (1 Detik)**: Menguji ketajaman area tengah 300x300 instan sebelum memproses seluruh gambar.
   - Slider interaktif Sebelum/Sesudah (*Before/After split view*) yang dapat digeser langsung.

2. **AI Video Upscaler & 60 FPS**:
   - **Skala Super-Resolution**: 1x (Hanya FPS), 2x (Super HD), 4x (Ultra 4K).
   - **Interpolasi Gerakan**: 60 FPS (Standar Halus), 120 FPS (Ultra Smooth), atau Pertahankan FPS Asli.
   - Pilihan encoder hardware **H.264 (AVC)** atau **H.265 (HEVC)**.
   - Slider target bitrate video kustom (2 s.d. 40 Mbps).

3. **Penghapus Background (Interactive Editor)**:
   - Tool **Auto-Color Magic** (hapus latar belakang otomatis sekali klik).
   - Opsi **"Hanya Area Menyatu (Contiguous)"**: Memilih antara penghapusan flood-fill area yang terhubung atau seluruh warna serupa di foto.
   - **Mode Geser (Pan & Zoom)**: Tombol pembesaran (+, -, 100%) dan drag canvas untuk merapikan tepian rambut/pakaian secara presisi.
   - Kuas manual **Hapus (Erase)** & **Pulihkan (Restore)** dengan slider ukuran kuas.
   - Ganti latar belakang transparan atau warna pas foto formal (**Merah KTP/Ijazah**, **Biru Dokumen**, **Putih**, atau **Custom**).
   - Tombol **"Tingkatkan Objek di Upscaler"** untuk langsung mengirim hasil potongan ke AI Image Upscaler 4K.

4. **Kompresi Video Pintar**:
   - Preset: **Smart Bitrate** (mempertahankan resolusi dan FPS asli video sumber), **Hemat Seimbang (~50%)**, **Super Hemat (~75%)**, dan **Kustom**.
   - Pilihan format kompresi: **H.264 / AVC** vs **H.265 / HEVC**.
   - Switch **"Pertahankan FPS Asli"**.
   - Switch **"Paksa Warna HDR 10+ / Wide Color Gamut"** dengan filter adaptive shadow recovery & vibrance untuk mencerahkan area gelap tanpa membuat wajah hitam (*anti-ireng*).
   - Estimasi penghematan ukuran file secara real-time.

5. **Pengunduh Media Multiplatform**:
   - Unduh video tanpa watermark dan audio dari **TikTok**, **YouTube**, **Instagram**, **SoundCloud**, **X (Twitter)**, dan **Douyin**.
   - Dukungan unduhan carousel foto / slide gambar.
   - **Pratinjau Media**: Pemutar video dan audio langsung di aplikasi sebelum mengunduh.
   - **Aksi Pasca-Unduh**: Tombol pintas langsung "Buka File", "Tingkatkan di AI Upscaler", atau "Kompres di Video Kompresor".

6. **Hardware & Vulkan Benchmark**:
   - Deteksi spesifikasi sistem (CPU, RAM, GPU adapter).
   - Benchmark uji komputasi GPU Vulkan untuk mengukur kecepatan inferensi tile AI per milidetik.

7. **Antrian Tugas & Riwayat**:
   - Pencatatan otomatis riwayat tugas dan tombol buka file langsung di File Explorer.

8. **Pengaturan & Manajer Model**:
   - **Manajer Model AI (Vulkan NCNN)**: Menampilkan status keterpasangan seluruh varian model Real-CUGAN & Real-ESRGAN beserta deskripsinya.
   - Dukungan Multibahasa: **Bahasa Indonesia** (formal & padat) dan **English (US)**.
   - Profil pengembang resmi: `@yuukaonlyone_` (TikTok).

---

### File Executable (.exe) Siap Pakai:

Aplikasi sudah berhasil dikompilasi menjadi executable mandiri Windows x64 yang siap dibagikan ke teman tanpa perlu install Node.js ataupun dependensi lainnya:

1. **Versi Portable (Langsung Jalan / Tanpa Perlu Install)**:
   - Lokasi: **`dist/HDify_Portable_v2.2.8.exe`**
   - Ukuran: ~306 MB (sudah lengkap membundel seluruh model AI Real-CUGAN, Real-ESRGAN, RIFE, FFmpeg, FFprobe, dan engine Vulkan).
   - Cara pakai: Cukup klik ganda (double click) file tersebut, langsung jalan!

2. **Versi Installer Setup (NSIS Wizard)**:
   - Lokasi: **`dist/HDify_Setup_v2.2.8.exe`**
   - Ukuran: ~307 MB
   - Cara pakai: Wizard instalasi Windows standar dengan pilihan lokasi instalasi, shortcut desktop, dan menu start.

3. **Versi Unpacked Folder**:
   - Lokasi: **`dist/win-unpacked/HDify.exe`**

---

### Cara Menjalankan dalam Mode Development:
1. **Cara Cepat (1-Klik)**:
   - Cukup klik ganda file **`start_hdify.bat`** di dalam folder ini.
2. **Melalui Terminal**:
   ```cmd
   npm start
   ```

### Cara Membangun Ulang File .exe:
```cmd
npm run dist
```
