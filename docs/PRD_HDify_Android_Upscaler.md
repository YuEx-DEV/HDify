# PRD — HDify: Android Image & Video Upscaler (Hardware-Accelerated)

> **Nama kerja:** HDify (boleh diganti)
> **Package:** `com.example.hdify`
> **Platform:** Android (native, arm64)
> **Dokumen ini ditujukan untuk:** Gemini Agent di Android Studio
> **Status:** Draft v1.1
> **Perubahan v1.1:** UI diganti ke Android View System (XML) + Material Components (bukan Compose) dengan aturan layout anti tumpang-tindih (Bagian 8). Ditambah logging, crash report, dan error handling (Bagian 5.8 dan 17). Perbaikan referensi nomor bagian.

---

## 0. Instruksi untuk Gemini Agent (BACA DULU)

1. Kerjakan **per fase** sesuai Bagian 12. Jangan lompat fase. Setiap fase harus **bisa di-build dan dijalankan di perangkat fisik arm64** sebelum lanjut.
2. **Dilarang membuat mock/stub/dummy** untuk engine AI. Inference harus benar-benar berjalan di GPU lewat Vulkan. Fungsi yang belum selesai harus ditandai `TODO` yang jelas dan dilaporkan, bukan dipalsukan.
3. **Dilarang diam-diam fallback ke CPU.** Jika Vulkan tidak tersedia, tampilkan pesan error yang jelas (lihat FR-HW-03).
4. Jangan mengarang API. Verifikasi versi library, nama model, dan signature dari repositori resmi (ncnn, realcugan-ncnn-vulkan, Real-ESRGAN, rife-ncnn-vulkan) sebelum dipakai. Gunakan **versi stabil terbaru** saat build dikerjakan.
5. Jika ada keputusan yang ambigu dan berdampak besar pada arsitektur, **tanyakan dulu** ke user, jangan menebak.
6. Setiap akhir fase, laporkan: apa yang selesai, apa yang belum, hasil uji di device (nama device, GPU, hasil), dan masalah yang ditemukan.
7. Tulis kode yang bersih: Kotlin untuk UI/orchestration, C++17 untuk native layer, tanpa kode mati, dengan komentar singkat pada bagian non-obvious (terutama JNI, tiling, dan sinkronisasi thread).
8. **UI wajib native Android View System (layout XML) + Material Components** sesuai Bagian 8. **Dilarang memakai Jetpack Compose.** Patuhi Aturan Layout (8.2) dan jangan menyatakan layar "selesai" sebelum lolos Checklist Verifikasi UI (8.5). Jika tidak bisa mengambil screenshot, katakan terus terang dan minta aku menguji manual.
9. **Setiap fitur wajib punya logging dan penanganan error** sesuai Bagian 17. Tidak boleh ada `catch` kosong atau error yang ditelan. Semua kegagalan memakai kode error `HD-...`.
10. Dilarang memakai `Log.d`/`println` langsung. Gunakan logger terpusat (FR-LOG-01).

---

## 1. Ringkasan Produk

HDify adalah aplikasi Android untuk **meningkatkan resolusi (upscale) dan memperbaiki kualitas gambar maupun video yang pecah/buram/blocky**, dengan pilihan skala yang bisa dipilih pengguna (**x2, x3, x4**). Untuk video, tersedia tambahan **frame interpolation** (naikkan FPS: **x2, x3, x4**, contoh 24 fps → 48/72/96 fps).

Seluruh proses AI berjalan **on-device** dan **memakai hardware (GPU via Vulkan compute)** secara nyata, bukan CPU. Encode/decode video memakai **hardware codec (MediaCodec)**.

### 1.1 Tujuan
- G1: Upscale gambar x2/x3/x4 dengan kualitas tinggi, sepenuhnya offline.
- G2: Upscale video x2/x3/x4 dengan audio tetap utuh.
- G3: Interpolasi frame video x2/x3/x4 (opsional, bisa digabung dengan upscale).
- G4: Pemakaian hardware yang **terbukti dan terverifikasi** (GPU Vulkan + hardware codec).
- G5: Stabil di perangkat mobile: tidak OOM, tidak overheat sampai crash, bisa pause/resume/cancel.

### 1.2 Non-Goals (v1)
- Tidak ada fitur cloud/server processing.
- Tidak ada editor video (trim, filter, subtitle).
- Tidak ada dukungan NPU/DSP vendor-specific (dicatat sebagai fase lanjutan, lihat Bagian 16).
- Tidak ada iklan, akun, atau login.
- Tidak mengandalkan FFmpeg (lihat keputusan di Bagian 3).

### 1.3 Target Pengguna
Pengguna Android umum yang ingin memperbaiki foto lama, screenshot, gambar anime/ilustrasi, dan video beresolusi rendah, tanpa PC dan tanpa internet.

---

## 2. Prinsip Wajib: Hardware-First (Non-Negotiable)

| # | Prinsip | Implementasi |
|---|---|---|
| H1 | Inference AI di **GPU** | ncnn dengan `opt.use_vulkan_compute = true` |
| H2 | Decode/encode video di **hardware codec** | `MediaCodec` (cek `isHardwareAccelerated()` pada Android 10+) |
| H3 | Tidak ada fallback CPU otomatis | CPU hanya aktif jika user menyalakan "Izinkan fallback CPU" (default **OFF**) |
| H4 | Pemakaian hardware bisa **dibuktikan** | Halaman "Hardware Info" + log terstruktur `backend=vulkan device=<nama GPU>` |
| H5 | Presisi hemat memori | FP16 storage/arithmetic bila didukung GPU, dengan self-test (lihat FR-HW-05) |

---

## 3. Tech Stack & Keputusan Arsitektur

| Area | Keputusan | Alasan |
|---|---|---|
| Bahasa app | Kotlin | Standar modern Android |
| UI toolkit | **Android View System (layout XML) + Material Components (Material 3)**, ViewBinding | UI native, layout eksplisit dan mudah diperiksa di Layout Editor/Preview. **Bukan Compose** |
| Navigasi | Single Activity + Fragment + Navigation Component, Bottom Navigation | Struktur sederhana dan state terjaga saat rotasi |
| State UI | ViewModel + StateFlow (collect dengan `repeatOnLifecycle`) | Aman terhadap lifecycle |
| Daftar | RecyclerView (+ ListAdapter/DiffUtil) | Wajib untuk log, riwayat, antrian |
| Logging & crash | Logger terpusat (file log + rotasi) + crash handler Kotlin dan native (Bagian 17). Timber boleh dipakai sebagai fasad | Observability tanpa layanan pihak ketiga (privasi) |
| Native | C++17 via NDK + CMake + JNI | Wajib untuk ncnn/Vulkan |
| Inference | **ncnn** (Tencent) prebuilt Android + Vulkan | Matang, ringan, backend Vulkan siap pakai, dipakai oleh engine referensi |
| GPU API | Vulkan (lewat ncnn) | Jalan di Adreno, Mali, dan GPU lain tanpa vendor SDK |
| Video I/O | `MediaExtractor`, `MediaCodec`, `MediaMuxer` | Hardware codec bawaan, tanpa lisensi tambahan. **FFmpeg dihindari di v1** (kompleksitas lisensi LGPL/GPL dan build size) |
| Konkurensi | Kotlin Coroutines + Flow; thread native terpisah untuk GPU | Pipeline paralel |
| Background | Foreground Service + WorkManager | Proses panjang tetap hidup |
| DI | Hilt | Standar |
| Penyimpanan job | Room | Riwayat & resume |
| Settings | DataStore (Preferences) | Ringan |
| Min SDK | **29 (Android 10)** | Vulkan 1.1 resmi, `ImageDecoder`, thermal API |
| Target SDK | Versi stabil terbaru | Syarat Play Store |
| ABI | **arm64-v8a** saja (x86_64 opsional untuk build tool, emulator tidak valid untuk uji performa) | Fokus ke device nyata |
| Arsitektur | MVVM + Clean-lite (UI → ViewModel → UseCase → Repository → Native) | Mudah diuji |

---

## 4. Engine & Model

### 4.1 Upscale (Super Resolution)

| Engine | Model | Skala native | Cocok untuk | Catatan |
|---|---|---|---|---|
| **Real-CUGAN** (wajib) | `se` | x2, x3, x4 | Anime, ilustrasi, kartun | Varian: conservative, no-denoise, denoise1x/2x/3x |
| **Real-CUGAN** | `pro` | x2, x3 | Anime, kualitas lebih tinggi | Tidak ada x4 native pada model pro |
| **Real-ESRGAN** (direkomendasikan, tambahan) | `realesr-general-x4v3` | x4 | Foto/gambar umum, ringan | Untuk x2/x3: jalankan x4 lalu resize turun ke skala target |
| Real-ESRGAN | `realesrgan-x4plus` | x4 | Foto kualitas tertinggi | Berat; untuk device kelas atas |
| Real-ESRGAN | `realesrgan-x4plus-anime` | x4 | Anime, lebih ringan dari x4plus | |
| Real-ESRGAN | `realesr-animevideov3` | x2, x3, x4 | **Video anime**, ringan untuk video | Pilihan utama untuk video anime di mobile |

> **Catatan penting untuk user:** Real-CUGAN dioptimalkan untuk **anime/ilustrasi**. Untuk **foto nyata dan video live-action**, preset default harus memakai **Real-ESRGAN `realesr-general-x4v3`**.

### 4.2 Frame Interpolation (video)

| Engine | Model | Catatan |
|---|---|---|
| **RIFE** (via `rife-ncnn-vulkan`) | RIFE v4.x (mis. v4.6; varian lite bila tersedia untuk device kelas bawah) | Mendukung timestep arbitrer, sehingga x2/x3/x4 bisa dihasilkan dengan menyisipkan (k−1) frame di `t = i/k` |

### 4.3 Preset Bawaan
- **Cepat:** `realesr-animevideov3` (video) / `realesr-general-x4v3` (gambar), tile besar, FP16, TTA off.
- **Seimbang (default):** Real-CUGAN `se` untuk anime, `realesr-general-x4v3` untuk foto.
- **Kualitas:** Real-CUGAN `pro` / `realesrgan-x4plus`, denoise sesuai kebutuhan.

Pemilihan default berdasarkan **tipe konten** yang dipilih user: **Anime/Ilustrasi** atau **Foto/Live-action**.

### 4.4 Pengemasan Model
- File `.param` dan `.bin` disimpan terstruktur (`assets/models/<engine>/<model>/`).
- Model dikelola oleh **Model Manager**: daftar model, ukuran, status (terpasang/belum). Model besar boleh dibuat opsional (diunduh/impor manual) untuk menjaga ukuran APK.
- Verifikasi checksum (SHA-256) tiap model saat dimuat.

---

## 5. Functional Requirements

Prioritas: **P0** = wajib v1, **P1** = penting, **P2** = nanti.

### 5.1 Hardware & Kapabilitas

| ID | Requirement | Prio |
|---|---|---|
| FR-HW-01 | Saat startup, inisialisasi Vulkan dan baca info GPU: nama device, tipe, versi Vulkan, ukuran heap memori, dukungan FP16 (storage & arithmetic), jumlah GPU. | P0 |
| FR-HW-02 | Halaman **Hardware Info** menampilkan hasil FR-HW-01 plus status codec (decoder/encoder hardware yang tersedia untuk H.264/HEVC dan resolusi maksimum). | P0 |
| FR-HW-03 | Jika Vulkan tidak tersedia/gagal init: blokir proses AI dan tampilkan error jelas. Fallback CPU hanya jika setting "Izinkan fallback CPU" aktif, dan UI harus menampilkan label **"CPU (lambat)"** selama proses. | P0 |
| FR-HW-04 | Pilih GPU (bila lebih dari satu) di Settings. | P2 |
| FR-HW-05 | **Self-test GPU** saat pertama kali/saat ganti model: proses tile kecil dan bandingkan dengan golden output (PSNR di atas ambang). Jika gagal dengan FP16, otomatis matikan FP16 dan ulangi dengan FP32; catat di log. | P0 |
| FR-HW-06 | Auto-tune **tile size** berdasarkan heap memori GPU dan skala. Bisa di-override manual di Advanced. | P0 |
| FR-HW-07 | Pantau status termal (`PowerManager.addThermalStatusListener`, `getThermalHeadroom`). Saat status mendekati throttling berat: kurangi beban (jeda singkat antar tile/frame) atau pause otomatis dengan notifikasi. | P0 |

### 5.2 Upscale Gambar

| ID | Requirement | Prio |
|---|---|---|
| FR-IMG-01 | Pilih gambar dari Photo Picker (single & multi/batch). Format input: JPEG, PNG, WebP, HEIC/HEIF (via `ImageDecoder`). | P0 |
| FR-IMG-02 | Pilihan skala: **x2, x3, x4**. Contoh: 720×720 → 1440×1440 / 2160×2160 / 2880×2880 (dimensi output harus **persis** hasil kali). | P0 |
| FR-IMG-03 | Pilih tipe konten (Anime/Foto), engine, model, level denoise (untuk Real-CUGAN). Preset "Cepat/Seimbang/Kualitas". | P0 |
| FR-IMG-04 | **Tiling** dengan overlap/padding sesuai implementasi referensi tiap engine, supaya tidak ada garis kotak (seam) dan tidak OOM pada gambar besar. | P0 |
| FR-IMG-05 | Output ke galeri via MediaStore: PNG (lossless), JPEG (kualitas 70–100), atau WebP. Pertahankan EXIF (orientasi, tanggal) bila memungkinkan. | P0 |
| FR-IMG-06 | **Preview crop cepat**: proses hanya area terpilih (mis. 256×256) untuk melihat hasil sebelum memproses seluruh gambar. | P1 |
| FR-IMG-07 | **Before/After slider** di layar hasil. | P1 |
| FR-IMG-08 | Peringatan sebelum memproses jika estimasi dimensi output melebihi batas aman perangkat (memori/encoder), dengan opsi batalkan atau turunkan skala. | P0 |
| FR-IMG-09 | Opsi **target ukuran kustom** (jalankan skala terdekat lalu resize ke ukuran target). | P2 |

### 5.3 Upscale Video

| ID | Requirement | Prio |
|---|---|---|
| FR-VID-01 | Pilih video dari Photo Picker/SAF. Format input: MP4 (H.264/HEVC) wajib; container/codec lain sebisanya selama didukung `MediaExtractor`/`MediaCodec` perangkat. | P0 |
| FR-VID-02 | Tampilkan metadata sumber: resolusi, FPS, codec, durasi, bitrate, rotasi. | P0 |
| FR-VID-03 | Pilihan skala: **x2, x3, x4**. Resolusi output dihitung dan ditampilkan **sebelum** mulai. | P0 |
| FR-VID-04 | Validasi resolusi output terhadap kapabilitas encoder (`VideoCapabilities.isSizeSupported`, `getSupportedFrameRatesFor`). Jika melebihi: tawarkan skala lebih kecil atau batas resolusi otomatis. | P0 |
| FR-VID-05 | Pertahankan **audio** dengan mux ulang track audio asli (passthrough, tanpa re-encode). Sinkron A/V terjaga. | P0 |
| FR-VID-06 | Pertahankan rotasi (`KEY_ROTATION`), color range/space sebisanya (BT.709 default), dan durasi total. | P0 |
| FR-VID-07 | Pengaturan encoder: codec **HEVC** (bila ada hardware encoder) atau **H.264**; mode bitrate (VBR/CQ bila didukung); slider kualitas dengan default adaptif terhadap resolusi output. | P0 |
| FR-VID-08 | Preview singkat: proses **3 detik pertama** untuk menilai hasil dan mengukur kecepatan sebelum memproses penuh. | P1 |
| FR-VID-09 | **Pause / Resume / Cancel**. Cancel harus berhenti < 2 detik dan membersihkan file sementara. | P0 |
| FR-VID-10 | **Checkpoint/resume**: video diproses dalam segmen (mis. per N detik atau per GOP), progres disimpan di Room sehingga proses bisa dilanjutkan setelah app dimatikan/crash. | P1 |
| FR-VID-11 | Cek ruang penyimpanan kosong sebelum mulai (estimasi ukuran output + temp). | P0 |
| FR-VID-12 | Estimasi **ETA** dari throughput terukur (target akurasi ±25% setelah 10 detik pertama). | P0 |

### 5.4 Frame Interpolation (Video)

| ID | Requirement | Prio |
|---|---|---|
| FR-INT-01 | Toggle "Interpolasi FPS" dengan pilihan **x2, x3, x4**. Contoh: 24 fps → 48 / 72 / 96 fps. | P0 |
| FR-INT-02 | Interpolasi bisa dipakai **sendiri** (tanpa upscale) atau **digabung** dengan upscale. | P0 |
| FR-INT-03 | Untuk faktor k: sisipkan (k−1) frame antara tiap pasangan frame asli di `t = i/k`, i = 1..k−1. Timestamp output rata dan monoton naik. | P0 |
| FR-INT-04 | **Deteksi pergantian scene** (scene cut): pada cut, jangan interpolasi; gandakan frame sebelumnya agar tidak muncul artefak morphing. | P0 |
| FR-INT-05 | **Batas FPS output** (Max FPS, default 120). Jika hasil kali melebihi batas, tampilkan peringatan dan opsi menurunkan faktor. Cek dukungan frame rate encoder. | P0 |
| FR-INT-06 | **Urutan pipeline** (Advanced): *Interpolasi → Upscale* (default) atau *Upscale → Interpolasi*. Default dipilih karena estimasi aliran gerak lebih stabil pada frame bersih dan RIFE berjalan di resolusi rendah. Agent boleh mengubah default berdasarkan hasil benchmark Fase 4, dengan melaporkannya. | P1 |
| FR-INT-07 | Mode interpolasi resolusi tinggi (UHD mode ala RIFE) untuk input ≥ 2K. | P2 |

### 5.5 Antrian & Proses Latar Belakang

| ID | Requirement | Prio |
|---|---|---|
| FR-Q-01 | Antrian job (gambar dan video), diproses **satu per satu** (GPU tidak dibagi paralel antar job). | P0 |
| FR-Q-02 | **Foreground Service** dengan notifikasi progres (persentase, ETA, tombol Pause/Cancel). Gunakan tipe `mediaProcessing` pada Android 15+ (tangani callback `onTimeout`), dan `dataSync` pada versi di bawahnya. | P0 |
| FR-Q-03 | Layar mati/app di background: proses tetap lanjut. Minta pengecualian battery optimization hanya bila diperlukan, dengan penjelasan ke user. | P1 |
| FR-Q-04 | Riwayat job (Room): status, parameter, durasi proses, lokasi output. | P1 |
| FR-Q-05 | Saat Battery Saver/suhu tinggi/baterai rendah: tampilkan peringatan dan opsi lanjut/tunda. | P1 |

### 5.6 Benchmark & Diagnostik

| ID | Requirement | Prio |
|---|---|---|
| FR-BM-01 | Layar **Benchmark**: jalankan tes standar (mis. tile 256×256 per engine/skala), tampilkan ms/tile dan estimasi waktu untuk gambar 1080p dan video 720p/30 fps per menit. | P1 |
| FR-BM-02 | Log diagnostik yang bisa diekspor (txt): info device, GPU, driver, model, tile size, FP16/FP32, durasi per tahap (decode, interpolate, upscale, encode). | P0 |
| FR-BM-03 | Counter performa realtime saat proses: FPS pemrosesan, waktu per frame, antrian tiap tahap. | P1 |

### 5.7 Settings

- Tile size (Auto/manual), FP16 (Auto/On/Off), TTA mode (default OFF), Izinkan fallback CPU (default OFF), urutan pipeline, folder/penamaan output, format & kualitas default, tema (terang/gelap/sistem), bahasa (Indonesia, English), mode log verbose, akses ke Log & Crash Report.

### 5.8 Logging, Crash Report & Error Handling

Detail teknis ada di **Bagian 17**.

| ID | Requirement | Prio |
|---|---|---|
| FR-LOG-01 | Logger terpusat (Kotlin) dengan level VERBOSE/DEBUG/INFO/WARN/ERROR. Seluruh modul memakainya, tidak ada `println`/`Log.d` langsung. | P0 |
| FR-LOG-02 | Log ditulis ke **file** di internal storage secara asynchronous dengan rotasi (maks 5 file x 2 MB), format baku (17.1). INFO ke atas selalu aktif. DEBUG/VERBOSE hanya saat "Mode log verbose" aktif. | P0 |
| FR-LOG-03 | **Logger native (C++)**: log dari native layer masuk ke logcat dan file log yang sama, dengan tag modul (`engine`, `gpu`, `rife`, dst.). | P0 |
| FR-LOG-04 | **Crash handler Kotlin**: `Thread.setDefaultUncaughtExceptionHandler` menulis crash report (stack trace, thread, info device, job aktif, breadcrumbs) ke file sebelum proses mati. | P0 |
| FR-LOG-05 | **Crash native** (SIGSEGV, SIGABRT, dst.): signal handler native mencatat sinyal, alamat fault, dan breadcrumb terakhir. Pada Android 11+ app juga membaca `ApplicationExitInfo` saat startup berikutnya (native crash, ANR, kill karena memori). | P0 |
| FR-LOG-06 | **Breadcrumbs**: ring buffer peristiwa terakhir (job id, tahap, tile/frame ke-N, model, tile size, FP16) yang di-flush ke disk tiap pergantian tahap dan secara periodik, supaya penyebab crash native bisa dilacak. | P0 |
| FR-LOG-07 | Saat startup, deteksi **terminasi abnormal sesi sebelumnya** dan tampilkan dialog "Aplikasi berhenti tak terduga" dengan tombol Lihat Laporan / Bagikan / Tutup. | P0 |
| FR-LOG-08 | Layar **Log & Crash Report**: daftar sesi log dan crash report, filter level, pencarian, salin, bagikan, hapus. Memakai RecyclerView (bukan satu TextView raksasa). | P0 |
| FR-LOG-09 | **Ekspor laporan** (ZIP lewat FileProvider + share sheet) berisi: info perangkat (model, Android, SDK, ABI, GPU, versi Vulkan dan driver, FP16, kapabilitas codec), versi app/build, snapshot settings, konfigurasi job terakhir, file log, crash report, hasil `ApplicationExitInfo`. | P0 |
| FR-LOG-10 | **Error terstruktur**: setiap kegagalan job punya kode `HD-<KATEGORI>-<NNN>`, pesan manusiawi, dan detail teknis, disimpan di riwayat job (17.3). | P0 |
| FR-LOG-11 | Penanganan error global: `CoroutineExceptionHandler`, dan error native diterjemahkan menjadi exception Kotlin yang bermakna. Error yang bisa dipulihkan tidak boleh berujung crash. | P0 |
| FR-LOG-12 | **Privasi log**: tidak mencatat isi gambar/video. URI dan nama file di-mask (hash pendek) kecuali user mengaktifkan opsi debug. Semua data lokal, tidak ada pengiriman otomatis. | P0 |
| FR-LOG-13 | Batasi total ukuran log + crash (mis. 25 MB), hapus yang tertua otomatis, dan tangani storage penuh tanpa membuat app crash. | P1 |
| FR-LOG-14 | `StrictMode` aktif di build debug (kerja berat di main thread, kebocoran). Pelanggaran dicatat. | P1 |

---

## 6. Pipeline Video

```
Input.mp4
  │  MediaExtractor (demux)
  ├── Track audio ────────────────────────────────────────────┐ (passthrough)
  └── Track video                                              │
        │ MediaCodec Decoder (HW)                              │
        ▼                                                      │
   [Queue 1: frame mentah, bounded]                            │
        │                                                      │
        ▼  (opsional) Interpolasi RIFE  ── Vulkan GPU          │
   [Queue 2: frame, bounded]                                   │
        │                                                      │
        ▼  Upscale (Real-CUGAN / Real-ESRGAN) ── Vulkan GPU    │
   [Queue 3: frame hasil, bounded]                             │
        │                                                      │
        ▼  MediaCodec Encoder (HW)                             │
        ▼                                                      ▼
                      MediaMuxer  →  Output.mp4 → MediaStore
```

Aturan pipeline:
- Setiap tahap berjalan di **thread sendiri**, dihubungkan **queue ber-batas (bounded)** supaya memori terkendali dan tahap lambat tidak menumpuk frame.
- **Backpressure:** decoder berhenti membaca saat Queue 1 penuh.
- Konversi YUV→RGB sebelum inference dan RGB→YUV sebelum encode. Versi awal boleh di native (NEON). Optimasi lanjutan: konversi di GPU (compute shader) / zero-copy lewat `AHardwareBuffer` (P2, hanya jika benchmark menunjukkan bottleneck di sini).
- Timestamp (PTS) dikelola eksplisit. Hasil interpolasi mendapat PTS di antara frame asli.
- Memori native: gunakan **direct ByteBuffer / buffer pool** yang dipakai ulang; hindari alokasi per-frame.
- Error di satu frame: retry sekali, lalu hentikan job dengan pesan jelas (jangan menghasilkan video rusak diam-diam).

---

## 7. Arsitektur Aplikasi

### 7.1 Struktur Modul/Package

```
app/
 ├─ ui/                      # Activity tunggal, Fragment, layout XML, navigasi, tema
 │   ├─ home/ image/ video/ queue/ history/ result/ settings/ hardware/ benchmark/ logs/   # tiap fitur = Fragment + ViewModel + layout XML (ViewBinding)
 ├─ domain/                  # UseCase, model data (EngineConfig, JobConfig, ...)
 ├─ data/                    # Room, DataStore, ModelRepository, MediaStore I/O
 ├─ engine/                  # Kotlin wrapper untuk native (UpscaleEngine, InterpolationEngine)
 ├─ video/                   # Extractor/Decoder/Encoder/Muxer, pipeline, scene detector
 ├─ service/                 # ProcessingService, notifikasi, thermal monitor
 ├─ logging/                 # Logger terpusat, file log + rotasi, crash handler, breadcrumbs, ekspor laporan
 └─ di/                      # Hilt modules
native/ (CMake)
 ├─ ncnn/                    # prebuilt ncnn android vulkan (headers + libncnn.a)
 ├─ engines/
 │   ├─ realcugan.cpp/.h
 │   ├─ realesrgan.cpp/.h
 │   └─ rife.cpp/.h
 ├─ gpu/                     # info GPU, self-test, tile planner
 ├─ log/                     # logger native (logcat + file), breadcrumbs, signal handler crash
 ├─ jni/                     # JNI bridge
 └─ shaders/                 # compute shader pre/post-processing (sesuai referensi engine)
```

### 7.2 Kontrak Native (JNI) — sketsa

```kotlin
data class EngineConfig(
    val engine: EngineType,          // REAL_CUGAN, REAL_ESRGAN
    val modelId: String,             // mis. "up2x-conservative", "realesr-general-x4v3"
    val scale: Int,                  // 2, 3, 4  (skala yang diminta user)
    val denoise: Int,                // khusus Real-CUGAN (-1..3), selain itu diabaikan
    val tileSize: Int,               // 0 = auto
    val useFp16: Boolean?,           // null = auto (hasil self-test)
    val gpuIndex: Int,               // default 0
    val allowCpuFallback: Boolean    // default false
)

interface UpscaleEngine {
    fun init(config: EngineConfig): EngineInfo            // lempar error jelas jika Vulkan gagal
    fun upscale(input: Bitmap, output: Bitmap, cb: ProgressCallback): Result
    fun upscaleFrame(inBuf: ByteBuffer, w: Int, h: Int, outBuf: ByteBuffer): Result
    fun cancel()
    fun release()
}

interface InterpolationEngine {
    fun init(modelId: String, gpuIndex: Int, useFp16: Boolean?): EngineInfo
    // t di (0,1): posisi frame sisipan antara frameA dan frameB
    fun interpolate(frameA: ByteBuffer, frameB: ByteBuffer, w: Int, h: Int,
                    t: Float, out: ByteBuffer): Result
    fun release()
}

data class EngineInfo(
    val backend: String,        // "vulkan" | "cpu"
    val deviceName: String,
    val fp16Active: Boolean,
    val tileSize: Int
)
```

Aturan JNI:
- Tidak ada alokasi besar di sisi Java per frame; gunakan direct buffer.
- Semua exception native diterjemahkan ke error Kotlin yang bermakna (bukan crash).
- `cancel()` harus thread-safe dan menghentikan loop tile dengan cepat.
- `EngineInfo.backend` wajib diisi jujur dan ditampilkan di UI.

### 7.3 Kustom Skala pada Model Skala-Tetap
Untuk model dengan skala native tunggal (mis. Real-ESRGAN x4), skala yang diminta user (x2/x3) dicapai dengan: jalankan skala native, lalu **resize turun** (area/Lanczos, di GPU bila memungkinkan) ke dimensi target persis. Dimensi output tetap harus presisi (lihat FR-IMG-02).

---

## 8. UI/UX

### 8.1 Keputusan: UI Native Android View System (XML)

- UI dibangun dengan **layout XML + Material Components (Material 3)**, **bukan Jetpack Compose**. Alasan: layout lebih mudah diprediksi dan diperiksa lewat Layout Editor/Preview, dan masalah tumpang tindih lebih mudah dicegah dengan constraint yang eksplisit.
- Pola: **Single Activity + Fragment + Navigation Component**, **ViewBinding** (tanpa `findViewById`), ViewModel + StateFlow.
- Tema: `Theme.Material3.DayNight.NoActionBar`. Warna dan tipografi lewat theme dan `TextAppearance` (tanpa ukuran teks ad-hoc di tiap view). Dark mode wajib.
- Layout ditulis di **XML**, bukan disusun lewat kode, kecuali custom view (mis. Before/After slider).
- Semua teks di `strings.xml` (Indonesia sebagai default, English tersedia).
- Jika proyek sudah terlanjur memakai Compose, **migrasikan seluruh UI** ke View System dan hapus dependensi Compose yang tidak dipakai.

### 8.2 Aturan Layout Wajib (anti tumpang-tindih dan teks memanjang)

| Kode | Aturan |
|---|---|
| UI-R1 | **Struktur layar:** root `ConstraintLayout`/`CoordinatorLayout`, konten utama di dalam `NestedScrollView` (atau `RecyclerView`) sehingga bisa di-scroll. Tombol aksi utama (mis. Mulai) ditaruh **sticky di bawah, di luar area scroll**, atau konten diberi padding bawah setinggi tombol. Tombol tidak boleh menimpa konten. |
| UI-R2 | **Ukuran:** container yang berisi teks tidak boleh punya tinggi/lebar tetap. Gunakan `wrap_content`, `0dp` + constraint, atau `minHeight`. Dilarang `match_parent` pada anak langsung `ConstraintLayout`. Dilarang menggeser posisi dengan margin besar atau `translationX/Y` untuk menyusun layout biasa. |
| UI-R3 | **Teks:** setiap `TextView` harus punya salah satu: (a) boleh wrap banyak baris di dalam container yang ikut membesar, atau (b) `maxLines` + `ellipsize`. Nama file, path, nama codec/GPU: `maxLines="1"` + `ellipsize` (`middle` untuk nama file, `end` untuk lainnya). Pesan error: wrap penuh, detail teknis dipindah ke tombol **Detail** (bottom sheet), bukan dijejalkan di layar. |
| UI-R4 | **Baris horizontal:** teks dan elemen di sebelahnya (ikon, tombol, nilai) wajib memakai `0dp` + constraint (`app:layout_constrainedWidth="true"`) atau `layout_weight`, sehingga teks panjang tidak mendorong elemen lain keluar layar. Untuk kumpulan elemen yang boleh turun baris, pakai `ChipGroup` atau `Flow`. |
| UI-R5 | **Daftar/teks panjang:** log, riwayat, antrian wajib `RecyclerView`. Dilarang satu `TextView` raksasa untuk log, dilarang `ListView` di dalam `ScrollView`, dan hindari scroll bersarang yang berebut gestur. |
| UI-R6 | **Insets (edge-to-edge):** tangani `WindowInsets` (status bar, navigation/gesture bar, display cutout, keyboard/IME) lewat `ViewCompat.setOnApplyWindowInsetsListener` atau `fitsSystemWindows`. Konten dan tombol tidak boleh tertutup. Android 15+ memaksa edge-to-edge pada target SDK 35 ke atas, jadi ini wajib benar. |
| UI-R7 | **Satuan dan spacing:** `dp` untuk ukuran, `sp` untuk teks. Grid 8dp, margin layar 16dp, jarak antar-seksi 24dp, touch target minimal 48dp. Nilai berulang disimpan di `dimens.xml`. |
| UI-R8 | **Kepadatan konten:** satu layar satu tujuan utama. Info sekunder masuk ke Card, bagian yang bisa dilipat, atau bottom sheet. Hindari dinding teks: ringkasan ditampilkan sebagai baris label-nilai, chip, atau kartu. Penjelasan panjang disembunyikan di "Pelajari lebih lanjut". |
| UI-R9 | **Ukuran layar dan font:** tampil benar pada font scale 1.0 sampai 2.0, display size besar, layar kecil (360x640dp) sampai tablet (600dp ke atas), portrait dan landscape. Pada layar lebar, konten dibatasi lebarnya (maks sekitar 600dp, di-center) agar tidak melar. Two-pane adalah P2. |
| UI-R10 | **Lokalisasi:** tidak ada teks hardcode di layout atau kode. Asumsikan teks Indonesia bisa sekitar 40% lebih panjang dari English. |
| UI-R11 | **Komponen standar:** pilih skala dengan `MaterialButtonToggleGroup` (`singleSelection` + `selectionRequired`, tombol x2/x3/x4). Interpolasi dengan `MaterialSwitch` + toggle group yang di-disable saat switch mati. Progres dengan `LinearProgressIndicator`. Dialog dengan `MaterialAlertDialogBuilder`. Pengaturan lanjutan dengan `BottomSheetDialogFragment`. |
| UI-R12 | **State layar:** setiap layar punya state Loading, Empty, dan Error yang jelas dan tidak menimpa konten lain. |
| UI-R13 | **Dilarang:** menumpuk view dengan `FrameLayout`/`translation` untuk menyusun layout biasa, weight bersarang berlapis, `ScrollView` yang tidak benar-benar bisa scroll, dan teks tanpa batas di `LinearLayout` horizontal tanpa weight. |

### 8.3 Navigasi dan Daftar Layar

**Bottom Navigation (4 tab):** Beranda, Antrian, Riwayat, Pengaturan. Diagnostik (Hardware Info, Benchmark, Log & Crash Report) berada di **Pengaturan > Diagnostik**.

| Layar | Isi |
|---|---|
| **Beranda** | Dua kartu besar: *Gambar* dan *Video*. Satu baris status hardware (mis. "GPU: Vulkan aktif"). Tidak ada teks panjang. |
| **Gambar** | Pilih gambar, tipe konten, skala (**x2 / x3 / x4**), preset, tombol *Preview Crop* dan *Mulai*. Tampilkan dimensi sumber menjadi dimensi hasil. |
| **Video** | Pilih video, kartu metadata, skala (**x2 / x3 / x4**), toggle *Interpolasi FPS* (**x2 / x3 / x4**, tampilkan "24 fps menjadi 48 fps"), codec/kualitas, kartu ringkasan (resolusi akhir, FPS akhir, estimasi waktu dan ukuran), tombol *Preview 3 detik* dan *Mulai*. |
| **Advanced** (bottom sheet) | Engine dan model, denoise, tile, FP16, urutan pipeline, TTA. |
| **Antrian/Progres** | Daftar job (RecyclerView): progres, ETA, FPS proses, tahap aktif, Pause/Cancel. |
| **Hasil** | Before/After slider (gambar), pemutar video, Bagikan/Buka di galeri/Hapus. |
| **Riwayat** | Daftar job selesai/gagal beserta kode error bila gagal. |
| **Hardware Info** | GPU, versi Vulkan, FP16, memori, codec HW, tombol *Self-Test* dan *Ekspor Log*. |
| **Benchmark** | Jalankan tes, tabel hasil, estimasi waktu. |
| **Log & Crash Report** | Daftar sesi log dan crash report, filter level, pencarian, salin/bagikan/hapus (FR-LOG-08). Build debug menambah tombol *Uji Crash*. |
| **Pengaturan** | Sesuai 5.7. |

### 8.4 Pesan Error di UI

Error ditampilkan dengan: judul singkat, penjelasan 1 sampai 2 kalimat berisi saran tindakan, **kode error** (mis. `HD-ENC-501`), tombol **Detail** (bottom sheet berisi info teknis) dan **Bagikan log**. Contoh: "Resolusi output 5760x3240 melebihi batas encoder perangkat. Pilih x2 atau batasi ke 4K." Selalu tampilkan dimensi hasil dan peringatan **sebelum** user menekan Mulai.

### 8.5 Checklist Verifikasi UI (wajib untuk setiap layar)

Sebuah layar dianggap selesai hanya jika sudah diperiksa pada kombinasi di bawah dan **tidak ada** view yang saling menimpa, teks terpotong tanpa ellipsis, tombol yang tak terjangkau, atau scroll horizontal tak disengaja.

- **Ukuran layar:** 360x640dp, 411x891dp, tablet 600dp ke atas.
- **Font scale:** 1.0, 1.3, 2.0.
- **Tema:** terang dan gelap.
- **Bahasa:** Indonesia dan English.
- **Orientasi:** portrait dan landscape.
- **Data uji ekstrem:** nama file 80+ karakter, nama GPU panjang, pesan error 3 paragraf, 200 item riwayat, 5.000 baris log, angka besar (mis. 3840x2160 menjadi 15360x8640).

---

## 9. Non-Functional Requirements

| Kategori | Requirement |
|---|---|
| **Performa** | Target waktu absolut ditetapkan setelah benchmark Fase 1 (bukan janji di PRD). Aplikasi wajib menampilkan ETA jujur. GPU harus **terisi nyata** selama inference (diverifikasi, lihat Bagian 11). Pipeline video harus meng-overlap decode, inference, dan encode. |
| **Memori** | Tidak OOM pada gambar 12 MP (mis. 4000×3000) skala x2 di perangkat RAM 6 GB. Gunakan tiling dan buffer pool. Output gambar sangat besar ditulis bertahap bila diperlukan. |
| **Termal** | Respons terhadap throttling (FR-HW-07). Tidak ada proses yang memaksa perangkat mencapai shutdown termal. |
| **Baterai** | Foreground service hanya aktif saat ada job. Tidak ada wakelock tersisa setelah job selesai. |
| **Stabilitas** | Crash rate target < 1% sesi. Native crash harus bisa didiagnosa (symbolication, log). |
| **Kompatibilitas** | Android 10+, arm64-v8a. GPU: Adreno, Mali, dan lainnya yang mendukung Vulkan. Siapkan **daftar GPU/driver bermasalah** (mis. FP16 bug) yang otomatis memakai FP32. |
| **Privasi** | 100% on-device. Tanpa upload. Tanpa analytics pihak ketiga di v1. Izin minimum (Photo Picker, tanpa `READ_EXTERNAL_STORAGE` luas). |
| **Penyimpanan** | Scoped storage; file sementara di cache app dan dibersihkan otomatis. |
| **Ukuran** | Ukuran APK/AAB dilaporkan; model besar opsional. |
| **Lokalisasi** | Indonesia (default) dan English. |
| **Kualitas UI** | Lolos Checklist 8.5 dan AC-UI-1 sampai AC-UI-8. Tidak ada jank di layar daftar dan tidak ada kerja berat di main thread (StrictMode bersih di debug). |
| **Observability** | Semua job, error, dan crash tercatat dan bisa diekspor (FR-LOG, Bagian 17). Crash native dapat dilacak sampai job, tahap, dan tile/frame terakhir. |

---

## 10. Edge Case & Penanganan Error

| Kasus | Penanganan |
|---|---|
| Vulkan tidak ada / init gagal | Error jelas, blokir proses (FR-HW-03) |
| Hasil GPU rusak (artefak hitam/warna aneh) | Self-test gagal → matikan FP16 → ulangi; jika tetap gagal, tandai GPU bermasalah dan beri tahu user |
| OOM / alokasi GPU gagal | Kecilkan tile otomatis dan coba ulang (maks N kali), lalu error jelas |
| Output melebihi kapabilitas encoder | Peringatan sebelum mulai dan opsi turunkan skala/batas resolusi |
| Video VFR (variable frame rate) | Deteksi; normalisasi ke CFR atau tangani PTS apa adanya, dengan peringatan bila interpolasi tidak akurat |
| Video HDR/10-bit | v1: tolak dengan pesan jelas atau konversi ke SDR 8-bit dengan peringatan (pilih satu, laporkan ke user) |
| Video dengan rotasi | Terapkan/pertahankan `KEY_ROTATION` |
| Tidak ada track audio | Lanjut tanpa audio |
| Format audio tidak didukung muxer | Re-encode audio ke AAC sebagai fallback |
| App dimatikan saat proses | Resume dari checkpoint (FR-VID-10) |
| Storage penuh di tengah proses | Hentikan, bersihkan temp, beri tahu user |
| Telepon masuk / app di-background | Service tetap berjalan; tidak crash |
| Thermal throttling berat | Pause otomatis + notifikasi, resume setelah suhu turun |
| Scene cut | Duplikasi frame, bukan interpolasi (FR-INT-04) |
| Gambar dengan alpha (PNG transparan) | Proses RGB lewat engine, proses alpha dengan resize terpisah, lalu gabungkan |
| Gambar sangat kecil (< 32 px) | Beri padding aman atau tolak dengan pesan |
| Crash native (SIGSEGV/abort) saat inference | Signal handler native mencatat sinyal + breadcrumb. Saat startup berikutnya tampilkan dialog crash dan tawarkan resume dari checkpoint |
| `VK_ERROR_DEVICE_LOST` / GPU hang | Hentikan job dengan `HD-HW-104`, catat detail driver, coba ulang sekali dengan tile lebih kecil/FP32, lalu tampilkan error jelas |
| Log penuh / storage penuh | Hapus log tertua dan lewati penulisan log tanpa crash (FR-LOG-13) |
| Teks sangat panjang di UI (nama file, pesan error) | Ellipsize/wrap sesuai 8.2, detail lengkap di layar Detail atau Log |

---

## 11. Verifikasi & Acceptance Criteria

### 11.1 Acceptance Criteria Fungsional

| ID | Kriteria |
|---|---|
| AC-1 | Gambar 720×720 diproses x2/x3/x4 menghasilkan tepat 1440×1440 / 2160×2160 / 2880×2880, tanpa seam kotak yang terlihat. |
| AC-2 | Video 24 fps dengan interpolasi x2/x3/x4 menghasilkan 48/72/96 fps (toleransi ±0.01), durasi sama (±1 frame), audio sinkron (≤ 40 ms). |
| AC-3 | Video dengan upscale saja menghasilkan resolusi persis sesuai skala (atau batas encoder yang telah dikonfirmasi user). |
| AC-4 | Output dapat diputar di galeri bawaan dan pemutar pihak ketiga (mis. VLC). |
| AC-5 | Cancel menghentikan job < 2 detik; Pause/Resume berfungsi; resume setelah app dimatikan melanjutkan dari checkpoint. |
| AC-6 | Gambar 12 MP x2 selesai tanpa OOM di perangkat uji RAM 6 GB. |
| AC-7 | Saat thermal throttling berat, app melakukan mitigasi (jeda/pause) tanpa crash. |

### 11.2 Acceptance Criteria Hardware (kunci permintaan produk)

| ID | Kriteria | Cara verifikasi |
|---|---|---|
| AC-HW-1 | Log menampilkan `backend=vulkan device=<nama GPU>` untuk setiap job; UI menampilkan hal yang sama. | Cek log + UI |
| AC-HW-2 | Tidak ada jalur CPU yang aktif kecuali setting fallback dinyalakan. | Review kode + uji dengan Vulkan sengaja dimatikan: harus muncul error, bukan proses lambat diam-diam |
| AC-HW-3 | **GPU benar-benar sibuk** saat inference. | Profiler vendor/Android GPU Inspector/Perfetto: utilisasi GPU tinggi, beban CPU relatif rendah selama tahap inference. Lampirkan screenshot/trace di laporan Fase 1 dan 4 |
| AC-HW-4 | Decoder dan encoder yang dipakai adalah hardware codec. | Log nama codec (`c2.qti...`, `c2.mtk...`, `c2.exynos...`, dst.) dan `isHardwareAccelerated()` |
| AC-HW-5 | Self-test GPU lolos pada perangkat uji; jika FP16 bermasalah, fallback FP32 otomatis terbukti berjalan. | Uji terhadap perangkat dengan Mali dan Adreno bila tersedia |

### 11.3 Acceptance Criteria UI

| ID | Kriteria |
|---|---|
| AC-UI-1 | Pada 360x640dp, font scale 2.0, tema terang dan gelap: tidak ada view yang saling menimpa dan semua tombol aksi tetap terjangkau (scroll atau sticky). |
| AC-UI-2 | Tidak ada teks terpotong tanpa ellipsis. Nama file 80+ karakter, nama GPU panjang, dan pesan error panjang tampil rapi tanpa mendorong elemen lain keluar layar. |
| AC-UI-3 | Tidak ada scroll horizontal yang tidak disengaja di layar mana pun. |
| AC-UI-4 | Konten tidak tertutup status bar, navigation/gesture bar, cutout, maupun keyboard, di Android 10 dan Android 15+. |
| AC-UI-5 | Daftar panjang (log 5.000 baris, riwayat 200 item) scroll mulus karena memakai RecyclerView. |
| AC-UI-6 | Rotasi layar serta perubahan tema/bahasa tidak menghilangkan state penting (pilihan skala, job aktif) dan tidak merusak layout. |
| AC-UI-7 | Lint bersih dari `HardcodedText`, dan semua ikon/tombol gambar punya `contentDescription`. |
| AC-UI-8 | Screenshot semua layar utama (kombinasi 8.5) dilampirkan di laporan fase. |

### 11.4 Acceptance Criteria Logging dan Crash

| ID | Kriteria |
|---|---|
| AC-LOG-1 | Exception Kotlin yang dipicu sengaja (tombol uji di build debug) menghasilkan crash report, dialog muncul di launch berikutnya, dan laporan bisa diekspor. |
| AC-LOG-2 | Crash native yang dipicu sengaja (build debug) menghasilkan catatan sinyal + breadcrumb terakhir (job, tahap, tile/frame). Pada Android 11+ muncul entri `ApplicationExitInfo`. |
| AC-LOG-3 | Log native dan Kotlin muncul berurutan dalam satu file log dengan format baku. |
| AC-LOG-4 | Ekspor ZIP berisi semua item FR-LOG-09 dan bisa dibuka di perangkat lain. |
| AC-LOG-5 | Setiap kegagalan job menampilkan kode `HD-...`, tersimpan di riwayat, dan bisa ditemukan di log. |
| AC-LOG-6 | Rotasi log berfungsi (total ukuran tidak melebihi batas) dan storage penuh tidak membuat app crash. |
| AC-LOG-7 | Log tidak berisi isi media maupun path/URI mentah (kecuali opsi debug aktif). |
| AC-LOG-8 | Tombol *Uji Crash* hanya ada di build debug, tidak ada di release. |

---

## 12. Rencana Fase untuk Agent (dengan Definition of Done)

### Fase 0 — Fondasi Proyek
- Buat proyek Android (Kotlin, **View System + XML, Material Components, ViewBinding, Navigation Component**, Hilt, Room, DataStore), NDK + CMake, ABI arm64-v8a, minSdk 29.
- Integrasikan **ncnn prebuilt Android + Vulkan** ke CMake.
- Halaman Hardware Info sederhana: init Vulkan, tampilkan info GPU (FR-HW-01/02).
- Bangun **kerangka UI** sesuai Bagian 8: Single Activity, Bottom Navigation 4 tab, tema Material 3 DayNight, layar Beranda, Pengaturan, Hardware Info, dan Log & Crash Report.
- Bangun **logging dan crash handling** sesuai Bagian 17: logger Kotlin + native, file log berotasi, crash handler Kotlin + native, breadcrumbs, dialog crash saat startup, ekspor ZIP (FR-LOG-01 sampai FR-LOG-10).
- **DoD:** app ter-build dan berjalan di device fisik; Hardware Info menampilkan nama GPU, versi Vulkan, dan dukungan FP16 yang benar; semua layar yang ada lolos AC-UI-1 sampai AC-UI-8; AC-LOG-1 sampai AC-LOG-4 lolos.

### Fase 1 — Upscale Gambar (Real-CUGAN)
### Fase 2 — Engine Tambahan + Penyempurnaan Gambar
### Fase 3 — Video Upscale (tanpa interpolasi)
### Fase 4 — Frame Interpolation (RIFE)
### Fase 5 — Ketahanan & Produksi
### Fase 6 (Opsional) — Optimasi Lanjutan

---

## 17. Logging, Crash Report & Error Handling (Detail)

### 17.1 Format Log Baku

```
2026-10-07 14:03:22.481 I/Upscale [job=42 stage=UPSCALE tile=12/80] backend=vulkan device=<GPU> fp16=true tile=256
2026-10-07 14:03:22.912 W/Thermal [job=42] status=MODERATE headroom=0.82 -> jeda adaptif 200ms
2026-10-07 14:03:30.004 E/Encoder [job=42 stage=ENCODE frame=1204] HD-ENC-502 encoder gagal: <pesan>
```

Field: timestamp (ms), level (V/D/I/W/E), tag modul, konteks job (`job`, `stage`, `tile`/`frame`), lalu pesan. Kode error `HD-...` ditulis pada baris ERROR. Kotlin dan native menulis ke **file yang sama** dengan format ini.

### 17.2 Alur Penanganan Crash

**Crash Kotlin/Java**
1. `Thread.setDefaultUncaughtExceptionHandler` menulis `crash_<timestamp>.txt` ke `filesDir/crashes/` secara sinkron (flush + fsync): stack trace, nama thread, info device, versi app, job aktif, breadcrumbs.
2. Teruskan ke handler default agar proses berakhir normal.

**Crash native (C++)**
1. Pasang handler lewat `sigaction` untuk `SIGSEGV`, `SIGABRT`, `SIGBUS`, `SIGFPE`, `SIGILL`, dengan `sigaltstack` agar tetap bisa jalan saat stack overflow.
2. Di dalam handler hanya boleh memakai fungsi async-signal-safe (`write()`, tanpa `malloc`/`printf`). Tulis: sinyal, alamat fault, thread, dan isi breadcrumb dari buffer yang sudah dialokasikan di awal.
3. Boleh menambahkan backtrace (alamat + build-id agar bisa di-symbolize). Setelah selesai, **teruskan ke handler sebelumnya** supaya tombstone Android tetap dibuat.
4. Simpan debug symbols tiap build (`debugSymbolLevel = FULL` pada NDK) untuk symbolication. Jika R8/minify aktif, simpan juga `mapping.txt` per build.

**Startup berikutnya**
1. Cek flag "clean exit" dan file crash yang belum ditandai dibaca.
2. Pada Android 11+ (API 30) baca `ActivityManager.getHistoricalProcessExitReasons()`: `REASON_CRASH`, `REASON_CRASH_NATIVE`, `REASON_ANR`, `REASON_LOW_MEMORY`, `REASON_SIGNALED`, dan trace-nya bila tersedia.
3. Tampilkan dialog (FR-LOG-07) dan kaitkan dengan job yang sedang berjalan agar bisa di-resume dari checkpoint.

### 17.3 Taksonomi Kode Error

Format: `HD-<KATEGORI>-<NNN>`.
HW (101-105), MDL (201-203), IMG (301-304), VID (401-405), ENC (501-503), STO (601-603), SYS (901-999).
