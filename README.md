# HDify

Local offline AI image & video upscaler, 60 FPS video frame interpolation, smart video compressor, and background remover studio powered by the Vulkan API. Available for both **Android (Native Kotlin/C++)** and **Windows Desktop (Electron/Node.js)**.

All AI processing runs 100% locally on your GPU — no cloud uploads, no API keys, and no subscriptions.

---

## Key Features

- **AI Image Upscaler**: Upscale photos up to 4x (4K) using **Real-CUGAN** (optimized for illustrations & anime) and **Real-ESRGAN** (anime & real-world photos). Features an interactive Before/After comparison slider and customizable denoise levels.
- **AI Video Upscaler & 60 FPS**: Fluid motion interpolation up to 60/120 FPS using **RIFE v4.6**, with super-resolution video scaling up to 4K via hardware-accelerated H.264 / H.265 (HEVC) encoders.
- **Background Remover Studio**: One-click AI auto-segmentation, manual brush tools (Erase & Restore) with circular adaptive cursors, and Magic Color Wand. Includes formal ID/passport backdrop presets (Passport Red, Diploma Blue, Studio White, etc.).
- **Smart Video Compressor**: Reduce file sizes by 50–75% with adaptive bitrates while preserving native resolution and sharpness. Includes adaptive shadow recovery to prevent crushed dark areas.
- **Media Downloader**: Download watermark-free video and audio from TikTok, YouTube, Instagram, X (Twitter), and SoundCloud.
- **Hardware Benchmark**: Real-time GPU device detection and Vulkan compute tile speed benchmark.

---

## Repository Structure

This monorepo contains the source code for both platforms:

```
├── app/                  # Android application source code (Kotlin, Android NDK, Vulkan NCNN)
├── hdify-windows/        # Windows Desktop application (Electron, Node.js, FFmpeg)
├── gradle/ & gradlew     # Gradle wrapper for Android builds
├── install.bat           # 1-click script to install debug APK to an Android device via ADB
└── sync_version.js       # Synchronizes version numbers across Android & Windows
```

---

## Getting Started: Windows Desktop

### Requirements:
- Windows 10/11 (64-bit)
- Node.js (v18 or newer)
- Vulkan-compatible GPU (NVIDIA, AMD, or Intel HD/UHD Graphics)

### Quick Run:
```bash
cd hdify-windows
npm install
npm start
```
*Or simply double-click **`start_hdify.bat`** inside the `hdify-windows` directory.*

### Build Standalone Executables (.exe):
```bash
cd hdify-windows
npm run dist
```
The installer setup (`.exe`) and portable standalone build will be generated in `hdify-windows/dist/`.

---

## Getting Started: Android

### Requirements:
- Android Studio (Ladybug / Koala or newer)
- Android SDK (API 26+) & NDK 26+
- Android device with Vulkan GPU support (Android 8.0+)

### Build Debug APK:
```bash
./gradlew assembleDebug
```
*Or connect your phone via USB (with USB Debugging enabled) and run **`install.bat`**.*

---

## Credits & Acknowledgments

- AI neural network models powered by open-source [Real-CUGAN](https://github.com/bilibili/real-cugan), [Real-ESRGAN](https://github.com/xinntao/Real-ESRGAN), and [RIFE](https://github.com/hzwer/ECCV2022-RIFE) Vulkan NCNN ports by [nihui](https://github.com/nihui).
- Media processing powered by [FFmpeg](https://ffmpeg.org/).
- Developed by **Yuuka** ([@yuukaonlyone_](https://www.tiktok.com/@yuukaonlyone_)).
