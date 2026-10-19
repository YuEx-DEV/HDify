/**
 * HDify Desktop - Core Application Logic
 * Native Windows Edition (Electron + Vulkan NCNN + FFmpeg)
 * Developed by Yuuka (@yuukaonlyone_)
 */

(function () {
    'use strict';

    // ==========================================
    // 1. BILINGUAL DICTIONARY (Formal & Concise)
    // ==========================================
    const i18n = {
        id: {
            app_title: 'HDify • Ultra HD Engine (Desktop)',
            app_subtitle: 'Monochrome • Material You',
            nav_home: 'Beranda',
            nav_image: 'AI Image Upscaler',
            nav_video: 'AI Video Upscaler',
            nav_bgremover: 'Hapus Background',
            nav_compressor: 'Kompres Video',
            nav_downloader: 'Downloader Media',
            nav_hardware: 'Hardware & Benchmark',
            nav_queue: 'Antrian Tugas',
            nav_settings: 'Pengaturan',

            title_minimize: 'Minimize',
            title_maximize: 'Maximize',
            title_close: 'Tutup',
            title_zoom_in: 'Perbesar',
            title_zoom_out: 'Perkecil',
            title_zoom_reset: 'Reset Zoom (100%)',
            btn_undo: '↶ Urungkan',
            title_undo: 'Urungkan langkah sebelumnya',
            btn_redo: '↷ Ulangi',
            title_redo: 'Ulangi langkah berikutnya',
            btn_reset: 'Reset',
            color_transparent: 'Transparan',
            color_white: 'Putih',
            color_red: 'Merah (Pas Foto)',
            color_blue: 'Biru (Pas Foto)',
            color_black: 'Hitam',
            color_custom: 'Pilih Warna Kustom',
            custom_color_title: 'Palet Warna Kustom',
            color_swatches_title: 'Warna Pas Foto & Studio',
            btn_apply_color: 'Terapkan',
            btn_eyedropper_title: 'Ambil warna dari layar / foto',
            bg_workspace_title: 'Kanvas Kerja',

            home_hero_badge: '◆ Material You • Desktop Edition',
            home_title: 'Pusat Peningkatan Media',
            home_desc: 'Solusi pemrosesan AI lokal berkecepatan tinggi dengan akselerasi GPU Vulkan untuk foto, video, penghapusan latar belakang, dan pengunduh media.',
            home_card_image_title: 'AI Image Upscaler',
            home_card_image_desc: 'Tingkatkan resolusi foto dan anime hingga 4x Ultra HD tanpa pecah menggunakan model Real-CUGAN dan Real-ESRGAN.',
            home_badge_image: 'Real-CUGAN & ESRGAN • Hingga 4x',
            home_card_video_title: 'AI Video Upscaler',
            home_card_video_desc: 'Interpolasi frame video menjadi super mulus dengan encoder hardware FFmpeg dan GPU Vulkan.',
            home_badge_video: 'Interpolasi RIFE • HEVC Hardware',
            home_card_bg_title: 'Penghapus Background',
            home_card_bg_desc: 'Potong latar belakang otomatis dengan segmentasi AI, kuas presisi, dan penggantian warna pas foto formal.',
            home_badge_bg: 'Segmentasi Otomatis AI • Presisi',
            home_card_compress_title: 'Kompresi Video',
            home_card_compress_desc: 'Kecilkan ukuran file video secara drastis tanpa menurunkan resolusi asli dan frame rate untuk upload lancar di media sosial.',
            home_badge_compress: 'Smart Bitrate • Resolusi Asli',
            home_card_downloader_title: 'Pengunduh Media',
            home_card_downloader_desc: 'Unduh video dan audio tanpa watermark dari TikTok, YouTube, SoundCloud, Instagram, X (Twitter), dan Douyin.',
            home_badge_downloader: 'Multiplatform • Kualitas Asli',

            // Image Upscale
            image_page_desc: 'Tingkatkan resolusi gambar hingga 4x Ultra HD menggunakan neural network lokal Vulkan NCNN.',
            image_dropzone_title: 'Pilih atau Tarik Foto ke Sini',
            image_dropzone_hint: 'Mendukung file JPG, PNG, WEBP, dan BMP',
            image_source_title: 'Foto Sumber',
            compare_title: 'Hasil Peningkatan AI',
            compare_before: 'Sebelum',
            compare_after: 'Sesudah (AI)',
            compare_hint: 'Geser slider untuk membandingkan • Gulir mouse atau klik 2x untuk perbesar & geser detail',
            btn_zoom_fit: 'Layar',
            options_title: 'Konfigurasi Model & Peningkatan',
            options_desc: 'Pilih engine yang sesuai dengan tipe konten gambar Anda.',
            opt_scale_label: 'Faktor Perbesaran',
            opt_custom_res_title: 'Kustom Resolusi Target',
            opt_custom_res_desc: 'Tentukan resolusi lebar x tinggi secara manual.',
            custom_width_label: 'Lebar (px)',
            custom_height_label: 'Tinggi (px)',
            opt_content_type_label: 'Tipe Konten & Engine',
            type_illustration: 'Ilustrasi / 2D (Real-CUGAN)',
            type_photo: 'Foto Nyata / Umum (Real-ESRGAN)',
            opt_preset_label: 'Preset Performa',
            perf_fast: 'Cepat',
            perf_balanced: 'Seimbang',
            perf_quality: 'Kualitas',
            opt_variant_label: 'Varian Model Real-CUGAN',
            variant_se: 'Standar (se)',
            variant_pro: 'Kualitas Tinggi (pro)',
            opt_esrgan_model_label: 'Pilihan Model Real-ESRGAN',
            esrgan_x4plus: 'x4plus (Foto Kualitas Tinggi)',
            esrgan_x4plus_anime: 'x4plus-anime (Anime HD)',
            esrgan_animevideov3: 'animevideov3 (Video / Ringan)',
            video_model_label: 'Model AI Video Super-Resolution',
            video_model_animev3: 'animevideov3 (Video Cepat / Rekomendasi)',
            video_model_x4plus: 'x4plus (Foto & Video Realistik)',
            video_model_x4plus_anime: 'x4plus-anime (Anime HD)',
            opt_denoise_label: 'Pereduksi Noise (Denoise)',
            denoise_default: 'Konservatif (-1)',
            denoise_none: 'Tanpa Denoise (0)',
            btn_preview_crop: 'Pratinjau Crop',
            btn_start_upscale: 'Mulai Upscale',
            crop_processing: 'Memproses Crop...',
            status_processing: 'Memproses...',
            status_preparing_ai: 'Mempersiapkan engine AI...',
            status_processing_tile: 'Memproses tile {current}/{total} ({percent}%)',
            btn_cancel: 'Batalkan',
            btn_open_folder: 'Buka Folder Hasil',

            // Video Upscale
            video_page_desc: 'Interpolasi frame video super mulus dan peningkatan resolusi dengan encoder hardware FFmpeg & Vulkan.',
            video_dropzone_title: 'Pilih atau Tarik Video ke Sini',
            video_dropzone_hint: 'Mendukung MP4, MKV, MOV, dan WEBM',
            video_info_title: 'Informasi Video Sumber',
            btn_change_video: 'Ganti Video',
            video_scale_label: 'Skala Resolusi',
            video_codec_label: 'Target Video Encoder',
            video_bitrate_quality_label: 'Kualitas Bitrate',
            rife_title: 'Interpolasi Frame (RIFE)',
            rife_desc: 'Urutan: Interpolasi Frame dilanjutkan Upscale Resolusi',
            rife_multiplier_label: 'Pengganda Frame Rate',
            rife_uhd_title: 'Mode UHD (Resolusi >= 2K)',
            rife_uhd_desc: 'Perhitungan optical flow teroptimasi untuk resolusi tinggi.',
            btn_preview_video: 'Pratinjau 3d',
            btn_start_video_upscale: 'Mulai Upscale',
            video_processing: 'Memproses video...',

            // Background Remover
            bgremover_desc: 'Potong latar belakang secara otomatis, perbaiki detail dengan kuas presisi, dan ganti warna latar belakang.',
            bg_dropzone_title: 'Pilih atau Tarik Foto Objek',
            bg_dropzone_hint: 'Mendukung format gambar potret, produk, atau pas foto',
            bg_auto_seg_btn: 'Segmentasi Otomatis (AI)',
            bg_manual_tools_title: 'Alat Penyempurnaan Manual',
            tool_autocut: 'Auto Color',
            tool_erase: 'Hapus',
            tool_restore: 'Pulihkan',
            tool_pan: 'Geser & Zoom',
            brush_size_label: 'Ukuran Kuas',
            tolerance_label: 'Toleransi Warna',
            magic_contiguous_title: 'Hanya Piksel Terhubung',
            magic_contiguous_desc: 'Klik warna latar untuk menghapus area yang tersambung.',
            bg_color_label: 'Pilihan Warna Latar Belakang',
            bg_canvas_hint: 'Gulir mouse atau klik 2x untuk perbesar • Gunakan alat Geser & Zoom untuk navigasi detail',
            bg_handoff_title: 'Lanjutkan ke AI Upscaler',
            bg_handoff_desc: 'Tingkatkan resolusi subjek hasil potong hingga 4K',
            btn_change_photo: 'Ganti Foto',
            btn_save_photo: 'Simpan Foto',
            btn_save_gallery: 'Simpan ke Galeri',

            // Video Compressor
            compressor_desc: 'Optimalkan ukuran file video dengan bitrate adaptif tanpa merusak ketajaman dan resolusi asli.',
            compress_dropzone_title: 'Pilih Video untuk Dikompres',
            compress_dropzone_hint: 'Mendukung file MP4, MKV, MOV, dan WEBM',
            compress_mode_title: 'Pilih Mode Kompresi',
            mode_max_quality_title: 'Kualitas Maksimal (~30% kompresi)',
            mode_max_quality_desc: 'Resolusi & FPS asli • Kualitas visual optimal',
            mode_balanced_title: 'Seimbang (~50% kompresi)',
            mode_balanced_desc: 'Resolusi & FPS asli • Pengurangan ukuran optimal',
            mode_saver_title: 'Hemat Penyimpanan (~70% kompresi)',
            mode_saver_desc: 'Resolusi & FPS asli • Ukuran file minimal',
            mode_custom_title: 'Kustom',
            mode_custom_desc: 'Konfigurasi manual bitrate, resolusi, dan codec',
            target_bitrate_label: 'Target Bitrate Video',
            res_scale_label: 'Resolusi Output',
            res_original: 'Asli',
            compress_codec_label: 'Format Kompresi Video',
            compress_hdr_title: 'Efek Visual HDR Dinamis',
            compress_hdr_desc: 'Meningkatkan rentang kontras dinamis dan kejelasan detail bayangan tanpa distorsi warna.',
            compress_est_title: 'Estimasi Kompresi',
            compress_est_note: 'Bitrate disesuaikan secara adaptif sesuai standar encoding media sosial terkini untuk memastikan video tetap tajam saat diunggah.',
            status_compressing: 'Mengompres video...',
            btn_start_compress: 'Mulai Kompresi Video',
            unit_est: 'Est.',
            unit_from: 'dari',
            unit_savings: 'Penghematan',

            // Media Downloader
            downloader_desc: 'Unduh video HD tanpa watermark, audio MP3 jernih, dan foto carousel dari TikTok, YouTube, SoundCloud, Instagram, X, dan Douyin.',
            downloader_placeholder: 'Tempel tautan video di sini...',
            dl_clip_detected: 'Tautan terdeteksi di clipboard:',
            btn_paste: 'Tempel',
            btn_analyze: 'Analisis Tautan',
            btn_analyzing: 'Menganalisis...',
            dl_features_title: 'Fitur Downloader',
            feat_insta_title: 'Instagram Carousel',
            feat_insta_desc: 'Unduh semua foto & video dari postingan slide carousel.',
            feat_sc_title: 'SoundCloud HD',
            feat_sc_desc: 'Unduh audio format MP3 kualitas terbaik langsung dari sumber.',
            feat_prev_title: 'Pratinjau Terintegrasi',
            feat_prev_desc: 'Putar video atau dengarkan audio sebelum mengunduh.',
            feat_verif_title: 'Verifikasi Integritas',
            feat_verif_desc: 'Memastikan file lengkap dan tidak korup setelah proses unduh.',
            feat_ai_title: 'Integrasi AI Upscaler',
            feat_ai_desc: 'Kirim hasil unduhan langsung ke AI Upscaler untuk ditingkatkan.',
            downloader_media_title: 'Judul Media',
            downloader_choose_quality: 'Pilih Kualitas Unduhan',
            downloader_preview_title: 'Pratinjau Media',
            btn_preview_media: 'Pratinjau Media',
            status_downloading: 'Mengunduh...',
            btn_download_file: 'Unduh File',
            btn_open_file: 'Buka File',
            btn_dl_send_upscale: 'Tingkatkan di AI Upscaler',
            btn_dl_send_compress: 'Kompres di Video Kompresor',

            // Hardware & Benchmark
            hardware_desc: 'Informasi akselerasi grafis, perangkat komputasi Vulkan, dan performa pemrosesan GPU.',
            sys_spec_title: 'Spesifikasi Sistem & GPU',
            hw_gpu: 'GPU / Akselerator Grafis',
            hw_cpu: 'Prosesor (CPU)',
            hw_ram: 'Memori RAM Sistem',
            hw_os: 'Sistem Operasi',
            benchmark_title: 'Uji Performa Komputasi Vulkan',
            benchmark_subtitle: 'Jalankan pengujian tile inferensi AI untuk mengukur kecepatan GPU Anda.',
            btn_run_benchmark: 'Mulai Uji Komputasi GPU',
            btn_running_bench: 'Menjalankan Uji Komputasi...',
            bench_ms_tile: 'Kecepatan per Tile',
            bench_1080p: 'Estimasi Foto 1080p',
            bench_fps: 'Performa Video 720p',
            bench_passed: '✓ Lolos Uji Akselerasi Vulkan',
            unit_seconds: 'detik',

            // Queue
            queue_desc: 'Daftar riwayat pemrosesan dan antrian file.',
            btn_clear_history: 'Bersihkan Riwayat',
            queue_empty: 'Belum ada tugas dalam antrian.',
            btn_open_file_item: 'Buka File',
            status_success: 'Berhasil',
            status_completed: 'Selesai',
            status_downloaded: 'Berhasil Diunduh',
            status_failed: 'Gagal',

            // Settings
            settings_desc: 'Kelola preferensi bahasa, tema, dan direktori penyimpanan.',
            settings_lang_title: 'Bahasa / Language',
            settings_lang_desc: 'Pilih bahasa tampilan antarmuka.',
            settings_folder_title: 'Folder Penyimpanan Hasil',
            settings_folder_desc: 'Lokasi default untuk menyimpan file foto dan video hasil pemrosesan.',
            settings_models_title: 'Manajer Model AI (Vulkan NCNN)',
            settings_models_desc: 'Status neural network lokal dan mesin akselerasi GPU.',
            model_ready: '✓ Siap / Aktif',
            model_missing: 'Belum Terpasang',
            btn_change_folder: 'Ubah',
            section_developer: 'Pengembang & Informasi',
            settings_dev_title: 'Dikembangkan oleh Yuuka',
            settings_dev_desc: '@yuukaonlyone_ • Klik untuk mengunjungi profil TikTok',

            // Alerts
            alert_crop_failed: 'Gagal membuat pratinjau: ',
            alert_upscale_failed: 'Gagal memproses gambar: ',
            alert_video_meta_failed: 'Gagal membaca metadata video: ',
            alert_video_upscale_failed: 'Peningkatan video gagal: ',
            alert_compress_read_failed: 'Gagal membaca file video: ',
            alert_compress_failed: 'Gagal kompresi video: ',
            alert_enter_url: 'Silakan masukkan tautan media terlebih dahulu.',
            alert_analyze_failed: 'Gagal menganalisis tautan: ',
            alert_download_failed: 'Unduhan gagal: ',
            alert_bench_failed: 'Uji benchmark gagal: '
        },
        en: {
            app_title: 'HDify • Ultra HD Engine (Desktop)',
            app_subtitle: 'Monochrome • Material You',
            nav_home: 'Home',
            nav_image: 'AI Image Upscaler',
            nav_video: 'AI Video Upscaler',
            nav_bgremover: 'Background Remover',
            nav_compressor: 'Video Compressor',
            nav_downloader: 'Media Downloader',
            nav_hardware: 'Hardware & Benchmark',
            nav_queue: 'Task Queue',
            nav_settings: 'Settings',

            title_minimize: 'Minimize',
            title_maximize: 'Maximize',
            title_close: 'Close',
            title_zoom_in: 'Zoom In',
            title_zoom_out: 'Zoom Out',
            title_zoom_reset: 'Reset Zoom (100%)',
            btn_undo: '↶ Undo',
            title_undo: 'Undo previous step',
            btn_redo: '↷ Redo',
            title_redo: 'Redo next step',
            btn_reset: 'Reset',
            color_transparent: 'Transparent',
            color_white: 'White',
            color_red: 'Red (ID Photo)',
            color_blue: 'Blue (ID Photo)',
            color_black: 'Black',
            color_custom: 'Choose Custom Color',
            custom_color_title: 'Custom Color Palette',
            color_swatches_title: 'ID Photo & Studio Colors',
            btn_apply_color: 'Apply',
            btn_eyedropper_title: 'Pick color from screen / photo',
            bg_workspace_title: 'Workspace Canvas',

            home_hero_badge: '◆ Material You • Desktop Edition',
            home_title: 'Media Enhancement Hub',
            home_desc: 'High-speed local AI processing with Vulkan GPU acceleration for photos, videos, background removal, and media downloader.',
            home_card_image_title: 'AI Image Upscaler',
            home_card_image_desc: 'Upscale photos and anime up to 4x Ultra HD without pixelation using Real-CUGAN and Real-ESRGAN models.',
            home_badge_image: 'Real-CUGAN & ESRGAN • Up to 4x',
            home_card_video_title: 'AI Video Upscaler',
            home_card_video_desc: 'Smooth video frame interpolation using FFmpeg hardware encoder and Vulkan GPU.',
            home_badge_video: 'RIFE Interpolation • Hardware HEVC',
            home_card_bg_title: 'Background Remover',
            home_card_bg_desc: 'Automatic background cutout with AI segmentation, precision brushes, and formal background replacement.',
            home_badge_bg: 'AI Auto Segmentation • Precision',
            home_card_compress_title: 'Video Compressor',
            home_card_compress_desc: 'Drastically reduce video file size while maintaining original resolution and FPS for smooth social uploads.',
            home_badge_compress: 'Smart Bitrate • Original Resolution',
            home_card_downloader_title: 'Media Downloader',
            home_card_downloader_desc: 'Download videos and audio without watermarks from TikTok, YouTube, SoundCloud, Instagram, X, and Douyin.',
            home_badge_downloader: 'Multiplatform • Original Quality',

            // Image Upscale
            image_page_desc: 'Upscale images up to 4x Ultra HD using local Vulkan NCNN neural networks.',
            image_dropzone_title: 'Select or Drag Photo Here',
            image_dropzone_hint: 'Supports JPG, PNG, WEBP, and BMP files',
            image_source_title: 'Source Photo',
            compare_title: 'AI Upscale Result',
            compare_before: 'Before',
            compare_after: 'After (AI)',
            compare_hint: 'Drag slider to compare • Scroll mouse or double-click to zoom & pan details',
            btn_zoom_fit: 'Fit',
            options_title: 'Model & Enhancement Configuration',
            options_desc: 'Choose the engine optimized for your image content type.',
            opt_scale_label: 'Resolution Scale',
            opt_custom_res_title: 'Custom Target Resolution',
            opt_custom_res_desc: 'Specify custom width x height resolution manually.',
            custom_width_label: 'Width (px)',
            custom_height_label: 'Height (px)',
            opt_content_type_label: 'Content Type & Engine',
            type_illustration: 'Illustration / 2D (Real-CUGAN)',
            type_photo: 'Real Photo / General (Real-ESRGAN)',
            opt_preset_label: 'Performance Preset',
            perf_fast: 'Fast',
            perf_balanced: 'Balanced',
            perf_quality: 'Quality',
            opt_variant_label: 'Real-CUGAN Model Variant',
            variant_se: 'Standard (se)',
            variant_pro: 'High Quality (pro)',
            opt_esrgan_model_label: 'Real-ESRGAN Model Selection',
            esrgan_x4plus: 'x4plus (High Quality Photo)',
            esrgan_x4plus_anime: 'x4plus-anime (Anime HD)',
            esrgan_animevideov3: 'animevideov3 (Fast Animation)',
            video_model_label: 'AI Video Super-Resolution Model',
            video_model_animev3: 'animevideov3 (Fast Video / Recommended)',
            video_model_x4plus: 'x4plus (Realistic Photo & Video)',
            video_model_x4plus_anime: 'x4plus-anime (Anime HD)',
            opt_denoise_label: 'Noise Reduction (Denoise)',
            denoise_default: 'Conservative (-1)',
            denoise_none: 'No Denoise (0)',
            btn_preview_crop: 'Crop Preview',
            btn_start_upscale: 'Start Upscale',
            crop_processing: 'Processing Crop...',
            status_processing: 'Processing...',
            status_preparing_ai: 'Preparing AI model...',
            status_processing_tile: 'Processing tile {current}/{total} ({percent}%)',
            btn_cancel: 'Cancel',
            btn_open_folder: 'Open Output Folder',

            // Video Upscale
            video_page_desc: 'Smooth video frame interpolation and super-resolution with FFmpeg hardware & Vulkan encoders.',
            video_dropzone_title: 'Select or Drag Video Here',
            video_dropzone_hint: 'Supports MP4, MKV, MOV, and WEBM',
            video_info_title: 'Source Video Information',
            btn_change_video: 'Change Video',
            video_scale_label: 'Resolution Scale',
            video_codec_label: 'Target Video Encoder',
            video_bitrate_quality_label: 'Bitrate Quality',
            rife_title: 'FPS Interpolation (RIFE)',
            rife_desc: 'Sequence: Frame Interpolation followed by Resolution Upscale',
            rife_multiplier_label: 'Frame Rate Multiplier',
            rife_uhd_title: 'UHD Mode (Resolution >= 2K)',
            rife_uhd_desc: 'Optimized optical flow calculation for high resolutions.',
            btn_preview_video: '3s Preview',
            btn_start_video_upscale: 'Start Upscale',
            video_processing: 'Processing video...',

            // Background Remover
            bgremover_desc: 'Automatically remove backgrounds, refine edges with precision brushes, and replace formal background colors.',
            bg_dropzone_title: 'Select or Drag Subject Photo',
            bg_dropzone_hint: 'Supports portrait, product, or ID photos',
            bg_auto_seg_btn: 'Auto Segmentation (AI)',
            bg_manual_tools_title: 'Manual Refinement Tools',
            tool_autocut: 'Auto Color',
            tool_erase: 'Erase',
            tool_restore: 'Restore',
            tool_pan: 'Pan & Zoom',
            brush_size_label: 'Brush Size',
            tolerance_label: 'Color Tolerance',
            magic_contiguous_title: 'Connected Pixels Only',
            magic_contiguous_desc: 'Click background color to remove adjacent matching areas.',
            bg_color_label: 'Background Color Options',
            bg_canvas_hint: 'Scroll mouse or double-click to zoom • Use Pan & Zoom tool to navigate details',
            bg_handoff_title: 'Continue to AI Upscaler',
            bg_handoff_desc: 'Enhance cutout subject resolution up to 4K',
            btn_change_photo: 'Change Photo',
            btn_save_photo: 'Save Photo',
            btn_save_gallery: 'Save to Gallery',

            // Video Compressor
            compressor_desc: 'Optimize video size with adaptive bitrates without sacrificing original sharpness or resolution.',
            compress_dropzone_title: 'Select Video to Compress',
            compress_dropzone_hint: 'Supports MP4, MKV, MOV, and WEBM',
            compress_mode_title: 'Select Compression Mode',
            mode_max_quality_title: 'Maximum Quality (~30% compression)',
            mode_max_quality_desc: 'Source resolution & FPS • Optimal visual quality',
            mode_balanced_title: 'Balanced (~50% compression)',
            mode_balanced_desc: 'Source resolution & FPS • Optimal size reduction',
            mode_saver_title: 'Storage Saver (~70% compression)',
            mode_saver_desc: 'Source resolution & FPS • Minimal file size',
            mode_custom_title: 'Custom',
            mode_custom_desc: 'Manual configuration of bitrate, resolution, and codec',
            target_bitrate_label: 'Target Video Bitrate',
            res_scale_label: 'Output Resolution',
            res_original: 'Original',
            compress_codec_label: 'Video Codec Format',
            compress_hdr_title: 'Dynamic HDR Visual Effect',
            compress_hdr_desc: 'Enhances dynamic contrast range and shadow detail clarity without color distortion.',
            compress_est_title: 'Compression Estimation',
            compress_est_note: 'Bitrate is adaptively adjusted to match modern social media encoding standards, ensuring sharp video when uploaded.',
            status_compressing: 'Compressing video...',
            btn_start_compress: 'Start Video Compression',
            unit_est: 'Est.',
            unit_from: 'of',
            unit_savings: 'Savings',

            // Media Downloader
            downloader_desc: 'Download watermark-free HD videos, clear MP3 audio, and carousel slides from TikTok, YouTube, SoundCloud, Instagram, X, and Douyin.',
            downloader_placeholder: 'Paste media link here...',
            dl_clip_detected: 'Link detected in clipboard:',
            btn_paste: 'Paste',
            btn_analyze: 'Analyze Link',
            btn_analyzing: 'Analyzing...',
            dl_features_title: 'Downloader Features',
            feat_insta_title: 'Instagram Carousel',
            feat_insta_desc: 'Download all photos & videos from carousel slide posts.',
            feat_sc_title: 'SoundCloud HD',
            feat_sc_desc: 'Download best quality MP3 audio directly from source.',
            feat_prev_title: 'Integrated Preview',
            feat_prev_desc: 'Play video or listen to audio before downloading.',
            feat_verif_title: 'Integrity Verification',
            feat_verif_desc: 'Ensures complete, uncorrupted files after download.',
            feat_ai_title: 'AI Upscaler Handoff',
            feat_ai_desc: 'Send downloads directly to AI Upscaler for enhancement.',
            downloader_media_title: 'Media Title',
            downloader_choose_quality: 'Select Download Quality',
            downloader_preview_title: 'Media Preview',
            btn_preview_media: 'Preview Media',
            status_downloading: 'Downloading...',
            btn_download_file: 'Download File',
            btn_open_file: 'Open File',
            btn_dl_send_upscale: 'Enhance in AI Upscaler',
            btn_dl_send_compress: 'Compress in Video Compressor',

            // Hardware & Benchmark
            hardware_desc: 'Graphics acceleration details, Vulkan compute devices, and GPU processing performance.',
            sys_spec_title: 'System & GPU Specifications',
            hw_gpu: 'GPU / Graphics Accelerator',
            hw_cpu: 'Processor (CPU)',
            hw_ram: 'System RAM Memory',
            hw_os: 'Operating System',
            benchmark_title: 'Vulkan Compute Benchmark',
            benchmark_subtitle: 'Run AI inference tile testing to measure GPU computing speed.',
            btn_run_benchmark: 'Run GPU Benchmark',
            btn_running_bench: 'Running Compute Benchmark...',
            bench_ms_tile: 'Speed per Tile',
            bench_1080p: 'Est. 1080p Photo',
            bench_fps: '720p Video Performance',
            bench_passed: '✓ Passed Vulkan Acceleration Test',
            unit_seconds: 'seconds',

            // Queue
            queue_desc: 'Processing history and task queue list.',
            btn_clear_history: 'Clear History',
            queue_empty: 'No tasks in queue yet.',
            btn_open_file_item: 'Open File',
            status_success: 'Success',
            status_completed: 'Completed',
            status_downloaded: 'Downloaded',
            status_failed: 'Failed',

            // Settings
            settings_desc: 'Manage language preferences, themes, and output directories.',
            settings_lang_title: 'Language',
            settings_lang_desc: 'Choose user interface language.',
            settings_folder_title: 'Output Storage Folder',
            settings_folder_desc: 'Default location for processed photos and videos.',
            settings_models_title: 'AI Models Manager (Vulkan NCNN)',
            settings_models_desc: 'Status of local neural networks and GPU acceleration engine.',
            model_ready: '✓ Ready / Active',
            model_missing: 'Not Installed',
            btn_change_folder: 'Change',
            section_developer: 'Developer & Info',
            settings_dev_title: 'Developed by Yuuka',
            settings_dev_desc: '@yuukaonlyone_ • Click to visit TikTok profile',

            // Alerts
            alert_crop_failed: 'Failed to generate crop preview: ',
            alert_upscale_failed: 'Failed to upscale image: ',
            alert_video_meta_failed: 'Failed to read video metadata: ',
            alert_video_upscale_failed: 'Video upscaling failed: ',
            alert_compress_read_failed: 'Failed to read video file: ',
            alert_compress_failed: 'Video compression failed: ',
            alert_enter_url: 'Please enter a media link first.',
            alert_analyze_failed: 'Failed to analyze link: ',
            alert_download_failed: 'Download failed: ',
            alert_bench_failed: 'Benchmark test failed: '
        }
    };

    let currentLang = localStorage.getItem('hdify_lang') || 'id';

    function setLanguage(lang) {
        if (!i18n[lang]) return;
        currentLang = lang;
        localStorage.setItem('hdify_lang', lang);

        document.querySelectorAll('[data-i18n]').forEach(el => {
            const key = el.getAttribute('data-i18n');
            if (i18n[lang][key]) {
                el.textContent = i18n[lang][key];
            }
        });

        document.querySelectorAll('[data-i18n-placeholder]').forEach(el => {
            const key = el.getAttribute('data-i18n-placeholder');
            if (i18n[lang][key]) {
                el.placeholder = i18n[lang][key];
            }
        });

        document.querySelectorAll('[data-i18n-title]').forEach(el => {
            const key = el.getAttribute('data-i18n-title');
            if (i18n[lang][key]) {
                el.title = i18n[lang][key];
            }
        });

        document.title = i18n[lang].app_title;
        const titlebarText = document.getElementById('titlebar-text');
        if (titlebarText) titlebarText.textContent = i18n[lang].app_title;

        const btnId = document.getElementById('btn-lang-id');
        const btnEn = document.getElementById('btn-lang-en');
        if (btnId && btnEn) {
            btnId.classList.toggle('active', lang === 'id');
            btnEn.classList.toggle('active', lang === 'en');
        }

        document.querySelectorAll('#btn-group-settings-lang .btn-group-item').forEach(btn => {
            btn.classList.toggle('active', btn.dataset.lang === lang);
        });

        // Trigger dynamic subviews updates
        if (typeof updateSavingsEstimate === 'function') updateSavingsEstimate();
        if (typeof renderQueue === 'function') renderQueue();
        if (typeof loadHardwareInfo === 'function') loadHardwareInfo();
        if (typeof loadAiModelsInfo === 'function') loadAiModelsInfo();
    }

    // ==========================================
    // 2. NAVIGATION & WINDOW CONTROLS
    // ==========================================
    function initNavigation() {
        const navItems = document.querySelectorAll('.nav-item');
        const viewSections = document.querySelectorAll('.view-section');

        window.switchView = function (targetViewId) {
            navItems.forEach(item => {
                item.classList.toggle('active', item.dataset.view === targetViewId);
            });
            viewSections.forEach(section => {
                section.classList.toggle('active', section.id === targetViewId);
            });

            if (targetViewId === 'view-hardware') {
                loadHardwareInfo();
            } else if (targetViewId === 'view-queue') {
                renderQueue();
            } else if (targetViewId === 'view-settings') {
                loadAiModelsInfo();
            } else if (targetViewId === 'view-downloader') {
                if (typeof window.checkClipboardForMedia === 'function') window.checkClipboardForMedia();
            }
        };

        navItems.forEach(item => {
            item.addEventListener('click', () => window.switchView(item.dataset.view));
        });

        document.querySelectorAll('.feature-card[data-target]').forEach(card => {
            card.addEventListener('click', () => window.switchView(card.dataset.target));
        });

        // Window controls
        document.getElementById('btn-win-min')?.addEventListener('click', () => window.hdifyApi.minimize());
        document.getElementById('btn-win-max')?.addEventListener('click', () => window.hdifyApi.maximize());
        document.getElementById('btn-win-close')?.addEventListener('click', () => window.hdifyApi.close());

        // Language buttons
        document.getElementById('btn-lang-id')?.addEventListener('click', () => setLanguage('id'));
        document.getElementById('btn-lang-en')?.addEventListener('click', () => setLanguage('en'));
        document.querySelectorAll('#btn-group-settings-lang .btn-group-item').forEach(btn => {
            btn.addEventListener('click', () => setLanguage(btn.dataset.lang));
        });

        // Developer TikTok Links (Exact profile with verified tracking params)
        const openTikTok = () => window.hdifyApi.openExternal('https://www.tiktok.com/@yuukaonlyone_?_r=1&_t=ZS-9ANT1Wtl7kF');
        document.getElementById('btn-open-dev-tiktok')?.addEventListener('click', openTikTok);
        document.getElementById('btn-dev-card-settings')?.addEventListener('click', openTikTok);
    }

    // ==========================================
    // 3. TASK QUEUE & HISTORY MANAGEMENT
    // ==========================================
    let taskQueue = [];
    try {
        taskQueue = JSON.parse(localStorage.getItem('hdify_queue')) || [];
    } catch (_) {
        taskQueue = [];
    }

    function saveQueue() {
        localStorage.setItem('hdify_queue', JSON.stringify(taskQueue));
    }

    function addQueueTask(task) {
        taskQueue.unshift(task);
        if (taskQueue.length > 50) taskQueue.pop();
        saveQueue();
        renderQueue();
    }

    function renderQueue() {
        const container = document.getElementById('queue-list-container');
        if (!container) return;

        if (taskQueue.length === 0) {
            container.innerHTML = `<div style="text-align: center; padding: 50px 0; color: var(--text-tertiary);">${i18n[currentLang].queue_empty}</div>`;
            return;
        }

        container.innerHTML = taskQueue.map(item => {
            return `
                <div class="card" style="padding: 14px 18px; margin-bottom: 0; display: flex; justify-content: space-between; align-items: center;">
                    <div>
                        <div style="font-weight: 700; font-size: 14px;">${escapeHtml(item.title)}</div>
                        <div style="font-size: 12px; color: var(--text-secondary); margin-top: 2px;">
                            ${item.type} • ${new Date(item.timestamp).toLocaleTimeString()} • ${item.status}
                        </div>
                    </div>
                    <div style="display: flex; gap: 8px;">
                        ${item.outputPath ? `<button class="btn btn-secondary" style="padding: 6px 12px; font-size: 12px;" onclick="window.hdifyApi.showItemInFolder('${escapePath(item.outputPath)}')">${i18n[currentLang].btn_open_file_item}</button>` : ''}
                    </div>
                </div>
            `;
        }).join('');
    }

    document.getElementById('btn-clear-queue')?.addEventListener('click', () => {
        taskQueue = [];
        saveQueue();
        renderQueue();
    });

    // ==========================================
    // 4. SETTINGS & OUTPUT DIRECTORY
    // ==========================================
    let currentOutputDir = '';

    async function initSettings() {
        try {
            currentOutputDir = await window.hdifyApi.getDefaultOutputDir();
            const input = document.getElementById('input-output-dir');
            if (input) input.value = currentOutputDir;
        } catch (e) {
            console.error('Failed to get default output dir:', e);
        }

        document.getElementById('btn-choose-output-dir')?.addEventListener('click', async () => {
            const dir = await window.hdifyApi.openDirectoryDialog();
            if (dir) {
                currentOutputDir = dir;
                document.getElementById('input-output-dir').value = dir;
            }
        });
    }

    function getSafeFilePath(file) {
        if (!file) return '';
        if (typeof file === 'string') return file;
        if (window.hdifyApi && typeof window.hdifyApi.getPathForFile === 'function') {
            try {
                const resolved = window.hdifyApi.getPathForFile(file);
                if (resolved) return resolved;
            } catch (_) {}
        }
        return file.path || '';
    }

    const modelLocales = {
        'models-se': {
            type: { id: 'Anime / Ilustrasi 2D', en: 'Anime / 2D Illustration' },
            desc: { id: 'Model ringan dan cepat dengan konsumsi memori rendah, optimal untuk ilustrasi 2D dan gambar digital.', en: 'Fast, lightweight model with low memory usage, optimized for 2D illustration.' }
        },
        'models-pro': {
            type: { id: 'Anime / Restorasi Garis', en: 'Anime / Line Restoration' },
            desc: { id: 'Model resolusi tinggi untuk merestorasi detail garis dan menghapus noise kompresi kuat.', en: 'High-resolution model restoring line art and removing heavy compression artifacts.' }
        },
        'models-nose': {
            type: { id: 'Denoise & Restorasi Kompresi', en: 'Denoise & Artifact Restoration' },
            desc: { id: 'Membersihkan noise dan artefak kompresi tanpa mengubah resolusi asli gambar.', en: 'Cleans noise and compression artifacts without altering the original image resolution.' }
        },
        'models/realesrgan-x4plus': {
            type: { id: 'Foto Nyata / Pemandangan', en: 'Realistic Photos / Landscapes' },
            desc: { id: 'Neural network canggih untuk mempertajam tekstur foto manusia, alam, dan produk.', en: 'Advanced neural network enhancing textures of human photos, nature, and products.' }
        },
        'models/realesrgan-x4plus-anime': {
            type: { id: 'Anime & Manga Detail', en: 'Anime & Manga Detail' },
            desc: { id: 'Dikhususkan untuk anime dengan mempertahankan ketajaman warna dan garis tepi halus.', en: 'Specialized for anime, preserving color vibrance and clean line edges.' }
        },
        'models/realesr-animevideov3': {
            type: { id: 'Video Frame Super-Resolution (x2/x3/x4)', en: 'Video Frame Super-Resolution (x2/x3/x4)' },
            desc: { id: 'Model temporal ringan untuk frame video super-resolution multi-scale real-time.', en: 'Lightweight temporal model for real-time multi-scale video super-resolution.' }
        },
        'rife-v4.6': {
            type: { id: 'Interpolasi Frame 60/120 FPS', en: '60/120 FPS Frame Interpolation' },
            desc: { id: 'Deep neural network FlowNet untuk melipatgandakan frame rate video (2x/3x/4x) menjadi 60fps/120fps.', en: 'Deep FlowNet neural network multiplying video frame rates (2x/3x/4x) into 60fps/120fps.' }
        },
        'vulkan-runtime': {
            type: { id: 'Mesin Akselerasi Hardware GPU', en: 'Hardware GPU Acceleration Engine' },
            desc: { id: 'Pipeline akselerasi komputasi grafis Vulkan native untuk inferensi neural network berkecepatan tinggi.', en: 'Native Vulkan graphics compute acceleration for high-speed AI processing.' }
        }
    };

    async function loadAiModelsInfo() {
        const container = document.getElementById('settings-models-container');
        if (!container) return;

        try {
            const info = await window.hdifyApi.getAiModelsInfo();
            container.innerHTML = info.models.map(m => {
                const loc = modelLocales[m.folder];
                const typeText = loc ? loc.type[currentLang] : m.type;
                const descText = loc ? loc.desc[currentLang] : m.desc;
                const statusBadge = m.installed ? i18n[currentLang].model_ready : i18n[currentLang].model_missing;
                return `
                <div style="background: rgba(255,255,255,0.03); border: 1px solid var(--bg-glass-stroke); border-radius: var(--radius-md); padding: 12px 16px;">
                    <div style="display: flex; justify-content: space-between; align-items: center;">
                        <span style="font-weight: 700; font-size: 14px;">${escapeHtml(m.name)}</span>
                        <span class="feature-badge" style="margin-top: 0; background: ${m.installed ? 'rgba(34, 197, 94, 0.15)' : 'rgba(239, 68, 68, 0.15)'}; color: ${m.installed ? '#4ade80' : '#f87171'}; border-color: ${m.installed ? 'rgba(34, 197, 94, 0.3)' : 'rgba(239, 68, 68, 0.3)'};">
                            ${statusBadge}
                        </span>
                    </div>
                    <div style="font-size: 11px; color: var(--accent-liquid); margin-top: 2px;">${escapeHtml(typeText)} • ${escapeHtml(m.folder)}</div>
                    <div style="font-size: 12px; color: var(--text-secondary); margin-top: 6px;">${escapeHtml(descText)}</div>
                </div>
            `;
            }).join('');
        } catch (err) {
            console.error(err);
        }
    }

    // ==========================================
    // 5. VIEW: AI IMAGE UPSCALER
    // ==========================================
    let selectedImageFile = null;
    let selectedScale = 2;
    let selectedDenoise = -1;
    let selectedTileSize = 200;
    let selectedTta = false;
    let selectedContentType = 'cugan';
    let selectedCuganVariant = 'se';
    let selectedEngine = 'realcugan-se';
    let customResEnabled = false;
    let imageNaturalWidth = 0;
    let imageNaturalHeight = 0;
    let lastUpscaledPath = null;

    function initImageUpscaler() {
        const dropzone = document.getElementById('image-dropzone');
        const fileInput = document.getElementById('image-file-input');
        const imageSourceCard = document.getElementById('image-source-card');
        const imageResultCard = document.getElementById('image-result-card');
        const imgSourceThumb = document.getElementById('img-source-thumb');
        const imageSourceMeta = document.getElementById('image-source-meta');
        const imageResultMeta = document.getElementById('image-result-meta');
        const btnChangeImagePhoto = document.getElementById('btn-change-image-photo');
        const btnChangeImageResult = document.getElementById('btn-change-image-result');
        const btnOpenUpscaledImage = document.getElementById('btn-open-upscaled-image');
        const btnShowFolderImage = document.getElementById('btn-show-folder-image');

        const compareBox = document.getElementById('compare-box');
        const compareStage = document.getElementById('compare-stage');
        const compareStageBefore = document.getElementById('compare-stage-before');
        const compareHandle = document.getElementById('compare-handle');
        const compareDivider = document.getElementById('compare-divider');
        const btnZoomOut = document.getElementById('btn-compare-zoom-out');
        const btnZoomReset = document.getElementById('btn-compare-zoom-reset');
        const btnZoomIn = document.getElementById('btn-compare-zoom-in');
        const btnZoomToggle = document.getElementById('btn-compare-zoom-toggle');
        const zoomIndicator = document.getElementById('compare-zoom-indicator');
        const imgOriginal = document.getElementById('img-original');
        const imgResult = document.getElementById('img-result');
        const btnStart = document.getElementById('btn-start-image-upscale');
        const btnCrop = document.getElementById('btn-preview-crop');
        const btnOpenFolder = document.getElementById('btn-open-image-folder');
        const progressBox = document.getElementById('image-progress-box');
        const progressBar = document.getElementById('image-progress-bar');
        const progressPct = document.getElementById('image-progress-pct');

        // Custom resolution elements
        const switchCustomRes = document.getElementById('switch-custom-res');
        const boxCustomRes = document.getElementById('box-custom-res');
        const inputCustomWidth = document.getElementById('input-custom-width');
        const inputCustomHeight = document.getElementById('input-custom-height');

        // Engine & settings groups
        const groupCuganVariant = document.getElementById('group-cugan-variant');
        const groupEsrganModel = document.getElementById('group-esrgan-model');
        const groupDenoise = document.getElementById('group-denoise');
        let selectedEsrganModel = 'realesrgan-x4plus';

        function updateCustomResInputs() {
            if (imageNaturalWidth > 0 && imageNaturalHeight > 0) {
                if (inputCustomWidth) inputCustomWidth.value = Math.round(imageNaturalWidth * selectedScale);
                if (inputCustomHeight) inputCustomHeight.value = Math.round(imageNaturalHeight * selectedScale);
            }
        }

        function updateSourceMeta(filePath, w, h) {
            if (!imageSourceMeta) return;
            const ext = filePath.split(/[/\\]/).pop().split('.').pop().toUpperCase() || 'IMG';
            const targetW = (customResEnabled && inputCustomWidth?.value) ? inputCustomWidth.value : Math.round(w * selectedScale);
            const targetH = (customResEnabled && inputCustomHeight?.value) ? inputCustomHeight.value : Math.round(h * selectedScale);

            imageSourceMeta.innerHTML = `
                <span class="feature-badge" style="background: rgba(255,255,255,0.06); padding: 5px 12px; border-radius: var(--radius-full); font-size: 12px; font-weight: 600;">📐 ${w} × ${h} px</span>
                <span class="feature-badge" style="background: rgba(99, 102, 241, 0.15); color: #818cf8; border: 1px solid rgba(99, 102, 241, 0.3); padding: 5px 12px; border-radius: var(--radius-full); font-size: 12px; font-weight: 600;">➔ Target ${selectedScale}x: ${targetW} × ${targetH} px</span>
                <span class="feature-badge" style="background: rgba(255,255,255,0.06); padding: 5px 12px; border-radius: var(--radius-full); font-size: 12px; font-weight: 600;">📁 ${ext}</span>
            `;
        }

        function updateResultMeta(origW, origH, outPath, isCrop) {
            if (!imageResultMeta) return;
            if (isCrop) {
                imageResultMeta.innerHTML = `
                    <span class="feature-badge" style="background: rgba(245, 158, 11, 0.15); color: #fbbf24; border: 1px solid rgba(245, 158, 11, 0.3); padding: 5px 12px; border-radius: var(--radius-full); font-size: 12px; font-weight: 600;">⚡ Pratinjau Tile (1 Detik)</span>
                    <span class="feature-badge" style="background: rgba(255,255,255,0.06); padding: 5px 12px; border-radius: var(--radius-full); font-size: 12px; font-weight: 600;">Model: ${selectedEngine}</span>
                `;
            } else {
                const outW = (customResEnabled && inputCustomWidth?.value) ? inputCustomWidth.value : Math.round(origW * selectedScale);
                const outH = (customResEnabled && inputCustomHeight?.value) ? inputCustomHeight.value : Math.round(origH * selectedScale);
                imageResultMeta.innerHTML = `
                    <span class="feature-badge" style="background: rgba(34, 197, 94, 0.15); color: #4ade80; border: 1px solid rgba(34, 197, 94, 0.3); padding: 5px 12px; border-radius: var(--radius-full); font-size: 12px; font-weight: 600;">✓ Selesai ${selectedScale}x</span>
                    <span class="feature-badge" style="background: rgba(255,255,255,0.06); padding: 5px 12px; border-radius: var(--radius-full); font-size: 12px; font-weight: 600;">📐 ${outW} × ${outH} px</span>
                    <span class="feature-badge" style="background: rgba(255,255,255,0.06); padding: 5px 12px; border-radius: var(--radius-full); font-size: 12px; font-weight: 600;">Engine: ${selectedEngine}</span>
                `;
            }
        }

        let isPickingImage = false;
        async function handlePickImage() {
            if (isPickingImage) return;
            isPickingImage = true;
            try {
                if (window.hdifyApi && window.hdifyApi.openFileDialog) {
                    const picked = await window.hdifyApi.openFileDialog({ type: 'image' });
                    if (picked) {
                        loadImagePath(picked);
                    }
                    return;
                }
                fileInput.click();
            } finally {
                isPickingImage = false;
            }
        }

        fileInput.addEventListener('click', (e) => e.stopPropagation());
        dropzone.addEventListener('click', handlePickImage);
        btnChangeImagePhoto?.addEventListener('click', handlePickImage);
        btnChangeImageResult?.addEventListener('click', handlePickImage);

        dropzone.addEventListener('dragover', (e) => {
            e.preventDefault();
            dropzone.classList.add('drag-active');
        });
        dropzone.addEventListener('dragleave', () => dropzone.classList.remove('drag-active'));
        dropzone.addEventListener('drop', (e) => {
            e.preventDefault();
            dropzone.classList.remove('drag-active');
            if (e.dataTransfer && e.dataTransfer.files.length > 0) {
                const p = getSafeFilePath(e.dataTransfer.files[0]);
                if (p) loadImagePath(p);
            }
        });

        fileInput.addEventListener('change', () => {
            if (fileInput.files && fileInput.files.length > 0) {
                const p = getSafeFilePath(fileInput.files[0]);
                if (p) loadImagePath(p);
            }
            fileInput.value = '';
        });

        window.loadImagePath = function (filePath) {
            if (!filePath || typeof filePath !== 'string' || !filePath.trim()) return;
            selectedImageFile = filePath;
            const normalizedUrl = 'file:///' + filePath.replace(/\\/g, '/');

            // Hide dropzone, Show source card, Keep comparison slider HIDDEN
            dropzone.style.display = 'none';
            if (imageSourceCard) imageSourceCard.style.display = 'block';
            if (imageResultCard) imageResultCard.style.display = 'none';

            if (imgSourceThumb) imgSourceThumb.src = normalizedUrl;
            if (imgOriginal) imgOriginal.src = normalizedUrl;

            const tempImg = new Image();
            tempImg.onload = () => {
                imageNaturalWidth = tempImg.naturalWidth;
                imageNaturalHeight = tempImg.naturalHeight;
                updateCustomResInputs();
                updateSourceMeta(filePath, imageNaturalWidth, imageNaturalHeight);
            };
            tempImg.src = normalizedUrl;

            btnStart.disabled = false;
            btnCrop.disabled = false;
            btnOpenFolder.style.display = 'none';
            if (typeof resetCompareTransform === 'function') resetCompareTransform();
            if (typeof setSliderPosition === 'function') setSliderPosition(50);
        };

        // Resolution Scale buttons (x2, x3, x4)
        document.querySelectorAll('#btn-group-scale .btn-group-item').forEach(btn => {
            btn.addEventListener('click', () => {
                document.querySelectorAll('#btn-group-scale .btn-group-item').forEach(b => b.classList.remove('active'));
                btn.classList.add('active');
                selectedScale = parseInt(btn.dataset.scale);
                updateCustomResInputs();
                if (selectedImageFile && imageNaturalWidth > 0) {
                    updateSourceMeta(selectedImageFile, imageNaturalWidth, imageNaturalHeight);
                }
            });
        });

        // Custom Target Resolution Switch
        switchCustomRes?.addEventListener('change', () => {
            customResEnabled = switchCustomRes.checked;
            if (boxCustomRes) {
                boxCustomRes.style.display = customResEnabled ? 'block' : 'none';
            }
            if (customResEnabled) updateCustomResInputs();
        });

        // Content Type & Engine (Real-CUGAN vs Real-ESRGAN)
        document.querySelectorAll('#btn-group-content-type .btn-group-item').forEach(btn => {
            btn.addEventListener('click', () => {
                document.querySelectorAll('#btn-group-content-type .btn-group-item').forEach(b => b.classList.remove('active'));
                btn.classList.add('active');
                selectedContentType = btn.dataset.type;

                if (selectedContentType === 'cugan') {
                    if (groupCuganVariant) groupCuganVariant.style.display = 'block';
                    if (groupDenoise) groupDenoise.style.display = 'block';
                    if (groupEsrganModel) groupEsrganModel.style.display = 'none';
                    selectedEngine = 'realcugan-' + selectedCuganVariant;
                } else {
                    if (groupCuganVariant) groupCuganVariant.style.display = 'none';
                    if (groupDenoise) groupDenoise.style.display = 'none';
                    if (groupEsrganModel) groupEsrganModel.style.display = 'block';
                    selectedEngine = selectedEsrganModel;
                }
            });
        });

        // Real-ESRGAN Model Selection buttons (x4plus, x4plus-anime, animevideov3)
        document.querySelectorAll('#btn-group-esrgan-model .btn-group-item').forEach(btn => {
            btn.addEventListener('click', () => {
                document.querySelectorAll('#btn-group-esrgan-model .btn-group-item').forEach(b => b.classList.remove('active'));
                btn.classList.add('active');
                selectedEsrganModel = btn.dataset.model;
                if (selectedContentType === 'esrgan') {
                    selectedEngine = selectedEsrganModel;
                }
            });
        });

        // Performance Preset buttons (fast, balanced, quality)
        document.querySelectorAll('#btn-group-perf-preset .btn-group-item').forEach(btn => {
            btn.addEventListener('click', () => {
                document.querySelectorAll('#btn-group-perf-preset .btn-group-item').forEach(b => b.classList.remove('active'));
                btn.classList.add('active');
                const preset = btn.dataset.preset;
                if (preset === 'fast') {
                    selectedTileSize = 100;
                    selectedTta = false;
                } else if (preset === 'quality') {
                    selectedTileSize = 200;
                    selectedTta = false;
                } else {
                    selectedTileSize = 200;
                    selectedTta = false;
                }
            });
        });

        // Real-CUGAN Model Variant (se vs pro)
        document.querySelectorAll('#btn-group-cugan-variant .btn-group-item').forEach(btn => {
            btn.addEventListener('click', () => {
                document.querySelectorAll('#btn-group-cugan-variant .btn-group-item').forEach(b => b.classList.remove('active'));
                btn.classList.add('active');
                selectedCuganVariant = btn.dataset.variant;
                if (selectedContentType === 'cugan') {
                    selectedEngine = 'realcugan-' + selectedCuganVariant;
                }
            });
        });

        // Denoise group buttons
        document.querySelectorAll('#btn-group-denoise .btn-group-item').forEach(btn => {
            btn.addEventListener('click', () => {
                document.querySelectorAll('#btn-group-denoise .btn-group-item').forEach(b => b.classList.remove('active'));
                btn.classList.add('active');
                selectedDenoise = parseInt(btn.dataset.denoise);
            });
        });

        // Compare slider & Zoom/Pan interaction engine (Parity with Android BeforeAfterSliderView)
        let compareZoom = 1.0;
        let comparePanX = 0;
        let comparePanY = 0;
        const MIN_ZOOM = 1.0;
        const MAX_ZOOM = 6.0;

        let isDraggingSlider = false;
        let isPanning = false;
        let panStartX = 0;
        let panStartY = 0;
        let sliderRafId = null;

        function setSliderPosition(pct) {
            const clampPct = Math.max(0, Math.min(100, pct));
            if (compareBox) {
                compareBox.style.setProperty('--slider-pos', clampPct + '%');
            }
        }

        function updateSliderFromEvent(clientX) {
            if (sliderRafId) return;
            sliderRafId = requestAnimationFrame(() => {
                sliderRafId = null;
                if (!compareBox) return;
                const rect = compareBox.getBoundingClientRect();
                if (rect.width > 0) {
                    const pos = ((clientX - rect.left) / rect.width) * 100;
                    setSliderPosition(pos);
                }
            });
        }

        function clampComparePan() {
            if (!compareBox || !compareStage) return;
            const boxRect = compareBox.getBoundingClientRect();
            const stageW = compareStage.offsetWidth || boxRect.width;
            const stageH = compareStage.offsetHeight || boxRect.height;

            const scaledW = stageW * compareZoom;
            const scaledH = stageH * compareZoom;

            const viewW = boxRect.width || stageW;
            const viewH = boxRect.height || stageH;

            const maxPanX = Math.max(0, (scaledW - viewW) / 2);
            const maxPanY = Math.max(0, (scaledH - viewH) / 2);

            comparePanX = Math.max(-maxPanX, Math.min(maxPanX, comparePanX));
            comparePanY = Math.max(-maxPanY, Math.min(maxPanY, comparePanY));
        }

        function syncStageDimensions() {
            if (compareStage && compareStageBefore && compareStage.offsetWidth > 0 && compareStage.offsetHeight > 0) {
                compareStageBefore.style.width = compareStage.offsetWidth + 'px';
                compareStageBefore.style.height = compareStage.offsetHeight + 'px';
            }
        }

        imgResult?.addEventListener('load', syncStageDimensions);
        imgOriginal?.addEventListener('load', syncStageDimensions);
        window.addEventListener('resize', syncStageDimensions);

        function applyCompareTransform() {
            syncStageDimensions();
            clampComparePan();
            const tf = `translate(${comparePanX}px, ${comparePanY}px) scale(${compareZoom})`;
            if (compareStage) compareStage.style.transform = tf;
            if (compareStageBefore) compareStageBefore.style.transform = tf;

            if (compareBox) {
                if (compareZoom > 1.05) {
                    compareBox.classList.add('is-zoomed');
                } else {
                    compareBox.classList.remove('is-zoomed');
                }
            }

            if (btnZoomReset) {
                btnZoomReset.textContent = compareZoom.toFixed(1) + 'x';
            }
            if (zoomIndicator) {
                if (compareZoom <= 1.05) {
                    const fitLabel = (i18n[currentLang] && i18n[currentLang].zoom_fit) || 'Layar';
                    zoomIndicator.textContent = `1.0x (${fitLabel})`;
                    zoomIndicator.style.color = 'var(--text-tertiary)';
                } else {
                    zoomIndicator.textContent = `${compareZoom.toFixed(1)}x`;
                    zoomIndicator.style.color = '#38bdf8';
                }
            }
        }

        function resetCompareTransform() {
            compareZoom = 1.0;
            comparePanX = 0;
            comparePanY = 0;
            applyCompareTransform();
        }

        function setCompareZoom(targetZoom, clientX, clientY) {
            const prevZoom = compareZoom;
            const newZoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, targetZoom));
            if (Math.abs(newZoom - prevZoom) < 0.001) return;

            const boxRect = compareBox ? compareBox.getBoundingClientRect() : { left: 0, top: 0, width: 0, height: 0 };
            const centerX = boxRect.left + boxRect.width / 2;
            const centerY = boxRect.top + boxRect.height / 2;

            const focusX = (clientX !== undefined && clientX !== null) ? clientX : centerX;
            const focusY = (clientY !== undefined && clientY !== null) ? clientY : centerY;

            const dx = focusX - centerX;
            const dy = focusY - centerY;
            const scaleRatio = newZoom / prevZoom;

            comparePanX = (comparePanX - dx) * scaleRatio + dx;
            comparePanY = (comparePanY - dy) * scaleRatio + dy;
            compareZoom = newZoom;

            if (compareZoom <= 1.01) {
                compareZoom = 1.0;
                comparePanX = 0;
                comparePanY = 0;
            }

            applyCompareTransform();
        }

        // Zoom button controls
        btnZoomIn?.addEventListener('click', (e) => {
            e.stopPropagation();
            setCompareZoom(Math.min(MAX_ZOOM, Math.round((compareZoom + 0.5) * 10) / 10));
        });

        btnZoomOut?.addEventListener('click', (e) => {
            e.stopPropagation();
            setCompareZoom(Math.max(MIN_ZOOM, Math.round((compareZoom - 0.5) * 10) / 10));
        });

        btnZoomReset?.addEventListener('click', (e) => {
            e.stopPropagation();
            resetCompareTransform();
        });

        btnZoomToggle?.addEventListener('click', (e) => {
            e.stopPropagation();
            if (compareZoom > 1.1) {
                resetCompareTransform();
            } else {
                let target = 2.5;
                if (imageNaturalWidth > 0 && compareStage?.offsetWidth > 0) {
                    const nativeRatio = imageNaturalWidth / compareStage.offsetWidth;
                    if (nativeRatio >= 1.5 && nativeRatio <= MAX_ZOOM) {
                        target = Math.round(nativeRatio * 10) / 10;
                    }
                }
                setCompareZoom(target);
            }
        });

        // Mouse Wheel Zoom (Focal Point Zoom)
        compareBox?.addEventListener('wheel', (e) => {
            if (!imageResultCard || imageResultCard.style.display === 'none') return;
            e.preventDefault();
            const factor = e.deltaY < 0 ? 1.25 : 0.8;
            const nextZoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, compareZoom * factor));
            setCompareZoom(nextZoom, e.clientX, e.clientY);
        }, { passive: false });

        // Double-click toggle (1.0x <-> 2.5x)
        compareBox?.addEventListener('dblclick', (e) => {
            if (e.target.closest('.compare-zoom-controls')) return;
            if (e.target.closest('#compare-handle')) return;
            if (compareZoom > 1.2) {
                resetCompareTransform();
            } else {
                setCompareZoom(2.5, e.clientX, e.clientY);
            }
        });

        // Determine whether a pointer down should drag the divider slider or pan the image
        function shouldDragSlider(clientX, target) {
            if (target && (target.closest('#compare-handle') || target.closest('#compare-divider'))) {
                return true;
            }
            if (compareZoom <= 1.05) {
                return true;
            }
            if (!compareBox) return false;
            const boxRect = compareBox.getBoundingClientRect();
            const sliderPosStr = getComputedStyle(compareBox).getPropertyValue('--slider-pos') || '50%';
            const sliderPct = parseFloat(sliderPosStr) || 50;
            const dividerScreenX = boxRect.left + (boxRect.width * (sliderPct / 100));
            return Math.abs(clientX - dividerScreenX) <= 28;
        }

        // Pointer event listeners (Mouse & Touch)
        compareHandle?.addEventListener('mousedown', (e) => {
            isDraggingSlider = true;
            isPanning = false;
            e.preventDefault();
            e.stopPropagation();
            updateSliderFromEvent(e.clientX);
        });

        compareBox?.addEventListener('mousedown', (e) => {
            if (e.target.closest('.compare-zoom-controls')) return;
            const clientX = e.clientX;
            const clientY = e.clientY;

            if (shouldDragSlider(clientX, e.target)) {
                isDraggingSlider = true;
                isPanning = false;
                updateSliderFromEvent(clientX);
            } else if (compareZoom > 1.0) {
                isDraggingSlider = false;
                isPanning = true;
                panStartX = clientX - comparePanX;
                panStartY = clientY - comparePanY;
                compareBox.classList.add('is-panning');
            }
            e.preventDefault();
        });

        window.addEventListener('mousemove', (e) => {
            if (isDraggingSlider) {
                updateSliderFromEvent(e.clientX);
            } else if (isPanning && compareZoom > 1.0) {
                comparePanX = e.clientX - panStartX;
                comparePanY = e.clientY - panStartY;
                applyCompareTransform();
            }
        });

        window.addEventListener('mouseup', () => {
            if (isDraggingSlider) isDraggingSlider = false;
            if (isPanning) {
                isPanning = false;
                compareBox?.classList.remove('is-panning');
            }
        });

        // Touch handling
        compareHandle?.addEventListener('touchstart', (e) => {
            isDraggingSlider = true;
            isPanning = false;
            if (e.touches && e.touches[0]) {
                updateSliderFromEvent(e.touches[0].clientX);
            }
        }, { passive: true });

        compareBox?.addEventListener('touchstart', (e) => {
            if (e.target.closest('.compare-zoom-controls')) return;
            if (!e.touches || e.touches.length === 0) return;
            const touch = e.touches[0];
            const clientX = touch.clientX;
            const clientY = touch.clientY;

            if (shouldDragSlider(clientX, e.target)) {
                isDraggingSlider = true;
                isPanning = false;
                updateSliderFromEvent(clientX);
            } else if (compareZoom > 1.0) {
                isDraggingSlider = false;
                isPanning = true;
                panStartX = clientX - comparePanX;
                panStartY = clientY - comparePanY;
                compareBox.classList.add('is-panning');
            }
        }, { passive: true });

        window.addEventListener('touchmove', (e) => {
            if (!e.touches || e.touches.length === 0) return;
            const touch = e.touches[0];
            if (isDraggingSlider) {
                updateSliderFromEvent(touch.clientX);
            } else if (isPanning && compareZoom > 1.0) {
                comparePanX = touch.clientX - panStartX;
                comparePanY = touch.clientY - panStartY;
                applyCompareTransform();
            }
        }, { passive: true });

        window.addEventListener('touchend', () => {
            if (isDraggingSlider) isDraggingSlider = false;
            if (isPanning) {
                isPanning = false;
                compareBox?.classList.remove('is-panning');
            }
        });

        // Quick Crop Preview (1-Second center tile test)
        btnCrop.addEventListener('click', async () => {
            if (!selectedImageFile) return;
            btnCrop.disabled = true;
            btnCrop.textContent = i18n[currentLang].crop_processing || 'Memproses Crop...';

            try {
                const res = await window.hdifyApi.runImageCropPreview({
                    inputPath: selectedImageFile,
                    engine: selectedEngine,
                    modelName: selectedEsrganModel,
                    scale: selectedScale,
                    denoise: selectedDenoise
                });

                if (res && res.success) {
                    lastUpscaledPath = res.outputPath;
                    if (imageSourceCard) imageSourceCard.style.display = 'none';
                    if (imageResultCard) imageResultCard.style.display = 'block';
                    imgResult.src = 'file:///' + res.outputPath.replace(/\\/g, '/') + '?t=' + Date.now();
                    setSliderPosition(50);
                    resetCompareTransform();
                    updateResultMeta(imageNaturalWidth, imageNaturalHeight, res.outputPath, true);
                }
            } catch (err) {
                alert(i18n[currentLang].alert_crop_failed + err.message);
            } finally {
                btnCrop.disabled = false;
                btnCrop.textContent = i18n[currentLang].btn_preview_crop;
            }
        });

        // Upscale Start Trigger
        btnStart.addEventListener('click', async () => {
            if (!selectedImageFile) return;

            btnStart.disabled = true;
            btnCrop.disabled = true;
            progressBox.style.display = 'block';
            progressBar.style.width = '0%';
            progressPct.textContent = '0%';
            const imgStatus = document.getElementById('image-progress-status');
            if (imgStatus) {
                imgStatus.textContent = i18n[currentLang].status_preparing_ai || 'Mempersiapkan engine AI...';
            }

            if (!currentOutputDir && window.hdifyApi?.getDefaultOutputDir) {
                currentOutputDir = await window.hdifyApi.getDefaultOutputDir();
            }
            const ext = selectedImageFile.substring(selectedImageFile.lastIndexOf('.'));
            const outName = `HDify_Image_${Date.now()}${ext || '.png'}`;
            const outPath = `${currentOutputDir}\\${outName}`;

            const params = {
                inputPath: selectedImageFile,
                outputPath: outPath,
                engine: selectedEngine,
                modelName: selectedEsrganModel,
                scale: selectedScale,
                denoise: selectedDenoise,
                tileSize: selectedTileSize,
                tta: selectedTta,
                width: imageNaturalWidth,
                height: imageNaturalHeight
            };

            if (customResEnabled && inputCustomWidth && inputCustomHeight) {
                const cw = parseInt(inputCustomWidth.value);
                const ch = parseInt(inputCustomHeight.value);
                if (cw > 0 && ch > 0) {
                    params.customWidth = cw;
                    params.customHeight = ch;
                }
            }

            try {
                const res = await window.hdifyApi.runImageUpscale(params);
                if (res && res.success) {
                    lastUpscaledPath = outPath;
                    progressBar.style.width = '100%';
                    progressPct.textContent = '100%';
                    const imgStatus = document.getElementById('image-progress-status');
                    if (imgStatus) imgStatus.innerHTML = '<span style="color: #4ade80; font-weight: 600;">✓ Selesai</span>';

                    if (imageSourceCard) imageSourceCard.style.display = 'none';
                    if (imageResultCard) imageResultCard.style.display = 'block';
                    imgResult.src = 'file:///' + outPath.replace(/\\/g, '/') + '?t=' + Date.now();
                    setSliderPosition(50);
                    resetCompareTransform();
                    updateResultMeta(imageNaturalWidth, imageNaturalHeight, outPath, false);

                    btnOpenFolder.style.display = 'inline-flex';
                    addQueueTask({
                        id: 'img_' + Date.now(),
                        type: 'Image Upscale',
                        title: outName,
                        status: i18n[currentLang].status_success,
                        timestamp: Date.now(),
                        outputPath: outPath
                    });

                    setTimeout(() => {
                        if (progressBox) progressBox.style.display = 'none';
                        if (imgStatus) imgStatus.textContent = i18n[currentLang].status_processing || 'Memproses...';
                    }, 1200);
                }
            } catch (err) {
                alert(i18n[currentLang].alert_upscale_failed + err.message);
                if (progressBox) progressBox.style.display = 'none';
            } finally {
                btnStart.disabled = false;
                btnCrop.disabled = false;
            }
        });

        btnOpenFolder.addEventListener('click', () => {
            if (lastUpscaledPath) window.hdifyApi.showItemInFolder(lastUpscaledPath);
        });

        btnOpenUpscaledImage?.addEventListener('click', () => {
            if (lastUpscaledPath && window.hdifyApi && window.hdifyApi.openPath) {
                window.hdifyApi.openPath(lastUpscaledPath);
            }
        });

        btnShowFolderImage?.addEventListener('click', () => {
            if (lastUpscaledPath && window.hdifyApi && window.hdifyApi.showItemInFolder) {
                window.hdifyApi.showItemInFolder(lastUpscaledPath);
            } else if (window.hdifyApi && window.hdifyApi.openFolder) {
                window.hdifyApi.openFolder(currentOutputDir);
            }
        });

        document.getElementById('btn-cancel-image-upscale')?.addEventListener('click', async () => {
            await window.hdifyApi.cancelImageUpscale();
            progressBox.style.display = 'none';
            btnStart.disabled = false;
            btnCrop.disabled = false;
        });
    }

    // ==========================================
    // 6. VIEW: AI VIDEO UPSCALER
    // ==========================================
    let selectedVideoFile = null;
    let videoMetadata = null;
    let selectedVideoScale = 1;
    let selectedVideoCodec = 'hevc';
    let videoQualityPct = 85;
    let rifeEnabled = true;
    let rifeMultiplier = '2x';
    let rifeUhd = false;

    function initVideoUpscaler() {
        const dropzone = document.getElementById('video-dropzone');
        const fileInput = document.getElementById('video-file-input');
        const infoBox = document.getElementById('video-info-box');
        const metaTags = document.getElementById('video-meta-tags');
        const btnChangeVideo = document.getElementById('btn-change-video-upscale');
        const btnStart = document.getElementById('btn-start-video-upscale');
        const btnPreview = document.getElementById('btn-preview-video-3s');
        const progressBox = document.getElementById('video-progress-box');
        const progressBar = document.getElementById('video-progress-bar');
        const progressPct = document.getElementById('video-progress-pct');
        const progressStatus = document.getElementById('video-progress-status');
        const progressDetails = document.getElementById('video-progress-details');
        const progressActions = document.getElementById('video-progress-actions');
        const sliderQuality = document.getElementById('slider-video-quality');
        const valQualityPct = document.getElementById('val-video-bitrate-pct');
        const switchRife = document.getElementById('switch-video-rife');
        const boxRifeOptions = document.getElementById('box-rife-options');
        const switchUhd = document.getElementById('switch-video-uhd');

        let isPickingVideo = false;
        const handleOpenVideoUpscaleDialog = async () => {
            if (isPickingVideo) return;
            isPickingVideo = true;
            try {
                if (window.hdifyApi && window.hdifyApi.openFileDialog) {
                    const picked = await window.hdifyApi.openFileDialog({ type: 'video' });
                    if (picked) {
                        loadVideoFile(picked);
                    }
                    return;
                }
                fileInput.click();
            } finally {
                isPickingVideo = false;
            }
        };

        fileInput.addEventListener('click', (e) => e.stopPropagation());
        dropzone.addEventListener('click', handleOpenVideoUpscaleDialog);
        btnChangeVideo?.addEventListener('click', handleOpenVideoUpscaleDialog);

        dropzone.addEventListener('dragover', (e) => {
            e.preventDefault();
            dropzone.classList.add('drag-active');
        });
        dropzone.addEventListener('dragleave', () => dropzone.classList.remove('drag-active'));
        dropzone.addEventListener('drop', (e) => {
            e.preventDefault();
            dropzone.classList.remove('drag-active');
            if (e.dataTransfer && e.dataTransfer.files.length > 0) {
                const p = getSafeFilePath(e.dataTransfer.files[0]);
                if (p) loadVideoFile(p);
            }
        });

        fileInput.addEventListener('change', () => {
            if (fileInput.files && fileInput.files.length > 0) {
                const p = getSafeFilePath(fileInput.files[0]);
                if (p) loadVideoFile(p);
            }
            fileInput.value = '';
        });

        window.loadVideoFile = async function (filePath) {
            if (!filePath || typeof filePath !== 'string' || !filePath.trim()) return;
            selectedVideoFile = filePath;
            try {
                videoMetadata = await window.hdifyApi.getVideoMetadata(filePath);
                infoBox.style.display = 'block';
                metaTags.innerHTML = `
                    <span class="feature-badge">${videoMetadata.width}x${videoMetadata.height}</span>
                    <span class="feature-badge">${videoMetadata.fps} FPS</span>
                    <span class="feature-badge">${Math.round(videoMetadata.duration)} ${i18n[currentLang].unit_seconds}</span>
                    <span class="feature-badge">${(videoMetadata.sizeBytes / (1024 * 1024)).toFixed(1)} MB</span>
                `;
                btnStart.disabled = false;
                btnPreview.disabled = false;
            } catch (err) {
                console.error(err);
                alert(i18n[currentLang].alert_video_meta_failed + err.message);
            }
        };

        let selectedVideoModel = 'realesr-animevideov3';
        const groupVideoModel = document.getElementById('group-video-model');

        // Video Scale buttons (x1, x2, x3, x4)
        document.querySelectorAll('#btn-group-video-scale .btn-group-item').forEach(btn => {
            btn.addEventListener('click', () => {
                document.querySelectorAll('#btn-group-video-scale .btn-group-item').forEach(b => b.classList.remove('active'));
                btn.classList.add('active');
                selectedVideoScale = parseInt(btn.dataset.scale);
                if (groupVideoModel) {
                    groupVideoModel.style.display = selectedVideoScale > 1 ? 'block' : 'none';
                }
            });
        });

        // Video Super-Resolution Model selection buttons
        document.querySelectorAll('#btn-group-video-model .btn-group-item').forEach(btn => {
            btn.addEventListener('click', () => {
                document.querySelectorAll('#btn-group-video-model .btn-group-item').forEach(b => b.classList.remove('active'));
                btn.classList.add('active');
                selectedVideoModel = btn.dataset.model;
            });
        });

        // Target Video Encoder buttons (HEVC vs H.264)
        document.querySelectorAll('#btn-group-video-codec .btn-group-item').forEach(btn => {
            btn.addEventListener('click', () => {
                document.querySelectorAll('#btn-group-video-codec .btn-group-item').forEach(b => b.classList.remove('active'));
                btn.classList.add('active');
                selectedVideoCodec = btn.dataset.codec;
            });
        });

        // Bitrate Quality slider
        sliderQuality?.addEventListener('input', () => {
            videoQualityPct = parseInt(sliderQuality.value);
            if (valQualityPct) valQualityPct.textContent = `${videoQualityPct}%`;
        });

        // RIFE toggle
        switchRife?.addEventListener('change', () => {
            rifeEnabled = switchRife.checked;
            if (boxRifeOptions) {
                boxRifeOptions.style.display = rifeEnabled ? 'block' : 'none';
            }
        });

        // Multiplier buttons (2x, 3x, 4x FPS)
        document.querySelectorAll('#btn-group-video-multiplier .btn-group-item').forEach(btn => {
            btn.addEventListener('click', () => {
                document.querySelectorAll('#btn-group-video-multiplier .btn-group-item').forEach(b => b.classList.remove('active'));
                btn.classList.add('active');
                rifeMultiplier = btn.dataset.multiplier;
            });
        });

        // UHD switch
        switchUhd?.addEventListener('change', () => {
            rifeUhd = switchUhd.checked;
        });

        function resetVideoProgressCard() {
            if (progressBox) progressBox.style.display = 'none';
            if (progressBar) {
                progressBar.style.width = '0%';
                progressBar.style.background = '';
                progressBar.style.boxShadow = '';
            }
            if (progressPct) progressPct.textContent = '0%';
            if (progressStatus) progressStatus.textContent = i18n[currentLang].status_processing || 'Memproses video...';
            if (progressDetails) progressDetails.textContent = 'Menyiapkan pipeline pemrosesan...';
            if (progressActions) {
                progressActions.innerHTML = `<button class="btn btn-secondary" id="btn-cancel-video-upscale">${i18n[currentLang].btn_cancel || 'Batalkan'}</button>`;
            }
        }

        function showVideoProgressSuccess(outputPath, sizeBytes) {
            if (!progressBox) return;
            progressBox.style.display = 'block';
            if (progressBar) {
                progressBar.style.width = '100%';
                progressBar.style.background = '#22c55e';
                progressBar.style.boxShadow = '0 0 14px rgba(34, 197, 94, 0.45)';
            }
            if (progressPct) {
                progressPct.innerHTML = '<span style="color: #4ade80; font-size: 13px; font-weight: 700;">100% Selesai</span>';
            }
            if (progressStatus) {
                progressStatus.innerHTML = '<span style="color: #4ade80; display: inline-flex; align-items: center; gap: 6px;"><svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor"><path d="M9 16.2L4.8 12l-1.4 1.4L9 19 21 7l-1.4-1.4L9 16.2z"/></svg> Video Berhasil Diproses!</span>';
            }
            const filename = outputPath ? outputPath.split(/[\\/]/).pop() : 'video.mp4';
            const sizeStr = sizeBytes ? formatBytes(sizeBytes) : '';
            if (progressDetails) {
                progressDetails.innerHTML = `<span style="color: var(--text-primary); font-weight: 600;">${escapeHtml(filename)}</span> <span style="color: var(--text-secondary); margin-left: 4px;">(${sizeStr})</span>`;
            }
            if (progressActions) {
                progressActions.innerHTML = `
                    <button class="btn btn-secondary" id="btn-open-video-upscale" data-path="${escapeHtml(outputPath)}" style="padding: 6px 14px; font-size: 12px;">
                        <svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor" style="vertical-align: middle; margin-right: 4px;"><path d="M8 5v14l11-7z"/></svg> Buka Video
                    </button>
                    <button class="btn btn-secondary" id="btn-show-folder-upscale" data-path="${escapeHtml(outputPath)}" style="padding: 6px 14px; font-size: 12px;">
                        <svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor" style="vertical-align: middle; margin-right: 4px;"><path d="M10 4H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2h-8l-2-2z"/></svg> Folder
                    </button>
                    <button class="btn btn-primary" id="btn-done-video-upscale" style="padding: 6px 14px; font-size: 12px;">Tutup</button>
                `;
            }
        }

        progressActions?.addEventListener('click', async (e) => {
            const btn = e.target.closest('button');
            if (!btn) return;
            if (btn.id === 'btn-cancel-video-upscale') {
                await window.hdifyApi.cancelVideoUpscale();
                resetVideoProgressCard();
                btnStart.disabled = false;
                btnPreview.disabled = false;
                btnStart.innerHTML = `<svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor"><path d="M8 5v14l11-7z"/></svg> <span>${i18n[currentLang].btn_start_video_upscale || 'Mulai Upscale'}</span>`;
            } else if (btn.id === 'btn-open-video-upscale') {
                const target = btn.dataset.path;
                if (target) {
                    if (window.hdifyApi.openPath) {
                        window.hdifyApi.openPath(target);
                    } else {
                        window.hdifyApi.openExternal(target);
                    }
                }
            } else if (btn.id === 'btn-show-folder-upscale') {
                const target = btn.dataset.path;
                if (target) window.hdifyApi.showItemInFolder(target);
            } else if (btn.id === 'btn-done-video-upscale') {
                resetVideoProgressCard();
            }
        });

        async function executeVideoUpscale(isPreview = false) {
            if (!selectedVideoFile) return;
            btnStart.disabled = true;
            btnPreview.disabled = true;
            btnStart.innerHTML = `<span style="display:inline-block; width:14px; height:14px; border:2px solid #fff; border-top-color:transparent; border-radius:50%; animation:spin 0.8s linear infinite; margin-right:8px; vertical-align:middle;"></span> ${isPreview ? 'Membuat Pratinjau...' : 'Memproses Video...'}`;

            resetVideoProgressCard();
            progressBox.style.display = 'block';

            if (!currentOutputDir && window.hdifyApi?.getDefaultOutputDir) {
                currentOutputDir = await window.hdifyApi.getDefaultOutputDir();
            }
            const prefix = isPreview ? 'HDify_VideoPreview_' : 'HDify_VideoUpscale_';
            const outName = `${prefix}${Date.now()}.mp4`;
            const outPath = `${currentOutputDir}\\${outName}`;

            const params = {
                inputPath: selectedVideoFile,
                outputPath: outPath,
                scale: selectedVideoScale,
                videoModel: selectedVideoModel,
                codec: selectedVideoCodec,
                bitrateQuality: videoQualityPct,
                rifeEnabled: rifeEnabled,
                rifeMultiplier: rifeMultiplier,
                uhdMode: rifeUhd,
                isPreview: isPreview,
                sourceFps: videoMetadata ? videoMetadata.fps : 30,
                duration: videoMetadata ? videoMetadata.duration : 0
            };

            try {
                const res = await window.hdifyApi.runVideoUpscale(params);
                if (res && res.success) {
                    showVideoProgressSuccess(res.outputPath || outPath, res.sizeBytes);
                    addQueueTask({
                        id: 'vid_' + Date.now(),
                        type: isPreview ? 'Video Preview (3s)' : 'Video Super-Resolution & RIFE',
                        title: outName,
                        status: i18n[currentLang].status_success,
                        timestamp: Date.now(),
                        outputPath: outPath
                    });
                }
            } catch (e) {
                alert(i18n[currentLang].alert_video_upscale_failed + e.message);
                resetVideoProgressCard();
            } finally {
                btnStart.disabled = false;
                btnPreview.disabled = false;
                btnStart.innerHTML = `<svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor"><path d="M8 5v14l11-7z"/></svg> <span>${i18n[currentLang].btn_start_video_upscale || 'Mulai Upscale'}</span>`;
            }
        }

        btnPreview?.addEventListener('click', () => executeVideoUpscale(true));
        btnStart?.addEventListener('click', () => executeVideoUpscale(false));
    }

    // ==========================================
    // 7. VIEW: BACKGROUND REMOVER (Interactive)
    // ==========================================
    let bgCanvas = null;
    let bgCtx = null;
    let originalImageObj = null;
    let activeBgTool = 'autocut'; // 'autocut' | 'erase' | 'restore' | 'pan'
    let brushSize = 30;
    let autoTolerance = 35;
    let selectedBgFill = 'transparent';
    let isDrawing = false;
    let bgHistory = [];
    let bgRedoStack = [];

    // Pan & Zoom state
    let zoomLevel = 1.0;
    let panX = 0;
    let panY = 0;
    let isPanning = false;
    let startPanX = 0;
    let startPanY = 0;

    function initBackgroundRemover() {
        const dropzone = document.getElementById('bg-dropzone');
        const fileInput = document.getElementById('bg-file-input');
        const editorContainer = document.getElementById('bg-editor-container');
        const canvasWrapper = document.getElementById('bg-canvas-wrapper');
        const btnChangePhoto = document.getElementById('btn-change-bg-photo');
        const btnAutoSeg = document.getElementById('btn-auto-segmentation');
        const btnBgZoomOut = document.getElementById('btn-bg-zoom-out');
        const btnBgZoomReset = document.getElementById('btn-bg-zoom-reset');
        const btnBgZoomIn = document.getElementById('btn-bg-zoom-in');
        const btnBgZoomToggle = document.getElementById('btn-bg-zoom-toggle');
        const bgZoomIndicator = document.getElementById('bg-zoom-indicator');
        bgCanvas = document.getElementById('bg-canvas');
        if (!bgCanvas) return;
        bgCtx = bgCanvas.getContext('2d', { willReadFrequently: true });

        let isPickingBgPhoto = false;
        const handleOpenBgPhotoDialog = async () => {
            if (isPickingBgPhoto) return;
            isPickingBgPhoto = true;
            try {
                if (window.hdifyApi && window.hdifyApi.openFileDialog) {
                    const picked = await window.hdifyApi.openFileDialog({ type: 'image' });
                    if (picked) {
                        loadBgPhoto(picked);
                    }
                    return;
                }
                fileInput.click();
            } finally {
                isPickingBgPhoto = false;
            }
        };

        fileInput.addEventListener('click', (e) => e.stopPropagation());
        dropzone.addEventListener('click', handleOpenBgPhotoDialog);
        btnChangePhoto?.addEventListener('click', handleOpenBgPhotoDialog);

        dropzone.addEventListener('dragover', (e) => {
            e.preventDefault();
            dropzone.classList.add('drag-active');
        });
        dropzone.addEventListener('dragleave', () => dropzone.classList.remove('drag-active'));
        dropzone.addEventListener('drop', (e) => {
            e.preventDefault();
            dropzone.classList.remove('drag-active');
            if (e.dataTransfer && e.dataTransfer.files.length > 0) {
                const p = getSafeFilePath(e.dataTransfer.files[0]);
                if (p) loadBgPhoto(p);
            }
        });

        fileInput.addEventListener('change', () => {
            if (fileInput.files && fileInput.files.length > 0) {
                const p = getSafeFilePath(fileInput.files[0]);
                if (p) loadBgPhoto(p);
            }
            fileInput.value = '';
        });

        function loadBgPhoto(filePath) {
            if (!filePath || typeof filePath !== 'string' || !filePath.trim()) return;
            const img = new Image();
            img.onload = () => {
                originalImageObj = img;
                bgCanvas.width = img.width;
                bgCanvas.height = img.height;
                bgCtx.clearRect(0, 0, bgCanvas.width, bgCanvas.height);
                bgCtx.drawImage(img, 0, 0);

                bgHistory = [bgCtx.getImageData(0, 0, bgCanvas.width, bgCanvas.height)];
                bgRedoStack = [];

                zoomLevel = 1.0;
                panX = 0;
                panY = 0;
                updateCanvasTransform();
                updateBrushCursor();

                editorContainer.style.display = 'grid';
                dropzone.style.display = 'none';
            };
            img.src = 'file:///' + filePath.replace(/\\/g, '/');
        }

        // Tools selection
        const btnAutocut = document.getElementById('btn-tool-autocut');
        const btnErase = document.getElementById('btn-tool-erase');
        const btnRestore = document.getElementById('btn-tool-restore');
        const btnPan = document.getElementById('btn-tool-pan');

        function updateBrushCursor() {
            if (!bgCanvas) return;
            if (activeBgTool === 'pan') {
                bgCanvas.style.cursor = 'grab';
                if (canvasWrapper) canvasWrapper.style.cursor = 'grab';
                return;
            }
            if (activeBgTool === 'autocut') {
                bgCanvas.style.cursor = 'crosshair';
                if (canvasWrapper) canvasWrapper.style.cursor = 'default';
                return;
            }
            if (activeBgTool === 'erase' || activeBgTool === 'restore') {
                // Circular brush cursor 'O' matching exact brushSize
                const d = Math.max(8, Math.min(128, Math.round(brushSize)));
                const c = d / 2;
                const r = Math.max(1, (d - 2) / 2);
                const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="${d}" height="${d}" viewBox="0 0 ${d} ${d}">` +
                    `<circle cx="${c}" cy="${c}" r="${r}" fill="none" stroke="rgba(0,0,0,0.85)" stroke-width="2.5"/>` +
                    `<circle cx="${c}" cy="${c}" r="${r}" fill="none" stroke="#ffffff" stroke-width="1.5"/>` +
                    `<circle cx="${c}" cy="${c}" r="1" fill="#ffffff"/>` +
                    `</svg>`;
                const cursorUrl = `url("data:image/svg+xml,${encodeURIComponent(svg)}") ${c} ${c}, crosshair`;
                bgCanvas.style.cursor = cursorUrl;
                if (canvasWrapper) canvasWrapper.style.cursor = cursorUrl;
            }
        }

        function setActiveTool(tool) {
            activeBgTool = tool;
            btnAutocut?.classList.toggle('active', tool === 'autocut');
            btnErase?.classList.toggle('active', tool === 'erase');
            btnRestore?.classList.toggle('active', tool === 'restore');
            btnPan?.classList.toggle('active', tool === 'pan');
            updateBrushCursor();
        }

        btnAutocut?.addEventListener('click', () => setActiveTool('autocut'));
        btnErase?.addEventListener('click', () => setActiveTool('erase'));
        btnRestore?.addEventListener('click', () => setActiveTool('restore'));
        btnPan?.addEventListener('click', () => setActiveTool('pan'));

        // Zoom & Pan Controls (Parity with MaskDrawingView)
        function clampBgPan() {
            if (!bgCanvas || !canvasWrapper) return;
            const wrapRect = canvasWrapper.getBoundingClientRect();
            const canvasW = bgCanvas.offsetWidth || wrapRect.width;
            const canvasH = bgCanvas.offsetHeight || wrapRect.height;

            const scaledW = canvasW * zoomLevel;
            const scaledH = canvasH * zoomLevel;

            const viewW = wrapRect.width || canvasW;
            const viewH = wrapRect.height || canvasH;

            const maxPanX = Math.max(0, (scaledW - viewW) / 2);
            const maxPanY = Math.max(0, (scaledH - viewH) / 2);

            panX = Math.max(-maxPanX, Math.min(maxPanX, panX));
            panY = Math.max(-maxPanY, Math.min(maxPanY, panY));
        }

        function updateCanvasTransform() {
            clampBgPan();
            if (bgCanvas) {
                bgCanvas.style.transform = `translate(${panX}px, ${panY}px) scale(${zoomLevel})`;
                bgCanvas.style.transformOrigin = 'center center';
            }

            if (canvasWrapper) {
                if (zoomLevel > 1.05) {
                    canvasWrapper.classList.add('is-zoomed');
                } else {
                    canvasWrapper.classList.remove('is-zoomed');
                }
            }

            if (btnBgZoomReset) {
                btnBgZoomReset.textContent = zoomLevel.toFixed(1) + 'x';
            }
            if (bgZoomIndicator) {
                if (zoomLevel <= 1.05) {
                    const fitLabel = (i18n[currentLang] && i18n[currentLang].zoom_fit) || 'Layar';
                    bgZoomIndicator.textContent = `1.0x (${fitLabel})`;
                    bgZoomIndicator.style.color = 'var(--text-tertiary)';
                } else {
                    bgZoomIndicator.textContent = `${zoomLevel.toFixed(1)}x`;
                    bgZoomIndicator.style.color = '#38bdf8';
                }
            }
        }

        function setBgZoom(targetZoom, clientX, clientY) {
            const prevZoom = zoomLevel;
            const newZoom = Math.max(1.0, Math.min(6.0, targetZoom));
            if (Math.abs(newZoom - prevZoom) < 0.001) return;

            const wrapRect = canvasWrapper ? canvasWrapper.getBoundingClientRect() : { left: 0, top: 0, width: 0, height: 0 };
            const centerX = wrapRect.left + wrapRect.width / 2;
            const centerY = wrapRect.top + wrapRect.height / 2;

            const focusX = (clientX !== undefined && clientX !== null) ? clientX : centerX;
            const focusY = (clientY !== undefined && clientY !== null) ? clientY : centerY;

            const dx = focusX - centerX;
            const dy = focusY - centerY;
            const scaleRatio = newZoom / prevZoom;

            panX = (panX - dx) * scaleRatio + dx;
            panY = (panY - dy) * scaleRatio + dy;
            zoomLevel = newZoom;

            if (zoomLevel <= 1.01) {
                zoomLevel = 1.0;
                panX = 0;
                panY = 0;
            }

            updateCanvasTransform();
        }

        btnBgZoomIn?.addEventListener('click', (e) => {
            e.stopPropagation();
            setBgZoom(Math.min(6.0, Math.round((zoomLevel + 0.5) * 10) / 10));
        });

        btnBgZoomOut?.addEventListener('click', (e) => {
            e.stopPropagation();
            setBgZoom(Math.max(1.0, Math.round((zoomLevel - 0.5) * 10) / 10));
        });

        btnBgZoomReset?.addEventListener('click', (e) => {
            e.stopPropagation();
            zoomLevel = 1.0;
            panX = 0;
            panY = 0;
            updateCanvasTransform();
        });

        btnBgZoomToggle?.addEventListener('click', (e) => {
            e.stopPropagation();
            if (zoomLevel > 1.1) {
                zoomLevel = 1.0;
                panX = 0;
                panY = 0;
                updateCanvasTransform();
            } else {
                let target = 2.5;
                if (originalImageObj && bgCanvas?.offsetWidth > 0) {
                    const ratio = originalImageObj.width / bgCanvas.offsetWidth;
                    if (ratio >= 1.5 && ratio <= 6.0) target = Math.round(ratio * 10) / 10;
                }
                setBgZoom(target);
            }
        });

        canvasWrapper?.addEventListener('wheel', (e) => {
            if (!editorContainer || editorContainer.style.display === 'none') return;
            e.preventDefault();
            const factor = e.deltaY < 0 ? 1.25 : 0.8;
            setBgZoom(zoomLevel * factor, e.clientX, e.clientY);
        }, { passive: false });

        canvasWrapper?.addEventListener('dblclick', (e) => {
            if (e.target.closest('.compare-zoom-controls')) return;
            if (zoomLevel > 1.2) {
                zoomLevel = 1.0;
                panX = 0;
                panY = 0;
                updateCanvasTransform();
            } else {
                setBgZoom(2.5, e.clientX, e.clientY);
            }
        });

        // Sliders
        const sliderBrush = document.getElementById('slider-brush-size');
        const valBrush = document.getElementById('val-brush-size');
        sliderBrush?.addEventListener('input', () => {
            brushSize = parseInt(sliderBrush.value);
            if (valBrush) valBrush.textContent = `${brushSize}px`;
            updateBrushCursor();
        });

        const sliderTol = document.getElementById('slider-tolerance');
        const valTol = document.getElementById('val-tolerance');
        sliderTol?.addEventListener('input', () => {
            autoTolerance = parseInt(sliderTol.value);
            if (valTol) valTol.textContent = autoTolerance;
        });

        // --- Modern Dark Glassmorphic Custom Color Picker Engine ---
        const btnCustomColorChip = document.getElementById('btn-custom-color-chip');
        const bgColorPopover = document.getElementById('bg-color-popover');
        const btnClosePopover = document.getElementById('btn-close-color-popover');
        const colorSbArea = document.getElementById('color-sb-area');
        const colorSbHandle = document.getElementById('color-sb-handle');
        const colorHueSlider = document.getElementById('color-hue-slider');
        const btnEyedropper = document.getElementById('btn-eyedropper');
        const colorPreviewBox = document.getElementById('color-preview-box');
        const colorHexInput = document.getElementById('color-hex-input');
        const btnColorApply = document.getElementById('btn-color-apply');
        const bgColorCurrentName = document.getElementById('bg-color-current-name');

        let currentHsv = { h: 160, s: 0.91, v: 0.73 }; // Default Emerald #10B981

        function hsvToRgb(h, s, v) {
            const f = (n, k = (n + h / 60) % 6) => v - v * s * Math.max(Math.min(k, 4 - k, 1), 0);
            return [
                Math.round(f(5) * 255),
                Math.round(f(3) * 255),
                Math.round(f(1) * 255)
            ];
        }

        function rgbToHex(r, g, b) {
            return '#' + [r, g, b].map(x => x.toString(16).padStart(2, '0')).join('').toUpperCase();
        }

        function hexToRgb(hex) {
            hex = hex.replace(/^#/, '');
            if (hex.length === 3) hex = hex.split('').map(c => c + c).join('');
            if (hex.length !== 6) return null;
            const num = parseInt(hex, 16);
            if (isNaN(num)) return null;
            return [(num >> 16) & 255, (num >> 8) & 255, num & 255];
        }

        function hexToHsv(hex) {
            const rgb = hexToRgb(hex);
            if (!rgb) return null;
            let [r, g, b] = rgb;
            r /= 255; g /= 255; b /= 255;
            const max = Math.max(r, g, b), min = Math.min(r, g, b);
            let h, s, v = max;
            const d = max - min;
            s = max === 0 ? 0 : d / max;
            if (max === min) {
                h = 0;
            } else {
                switch (max) {
                    case r: h = (g - b) / d + (g < b ? 6 : 0); break;
                    case g: h = (b - r) / d + 2; break;
                    case b: h = (r - g) / d + 4; break;
                }
                h *= 60;
            }
            return { h: Math.round(h), s, v };
        }

        function syncColorPickerUI(updateCanvas = true) {
            const [r, g, b] = hsvToRgb(currentHsv.h, currentHsv.s, currentHsv.v);
            const hex = rgbToHex(r, g, b);

            // Update Hue slider & SB area background
            if (colorHueSlider) colorHueSlider.value = currentHsv.h;
            if (colorSbArea) colorSbArea.style.backgroundColor = `hsl(${currentHsv.h}, 100%, 50%)`;

            // Position SB handle
            if (colorSbArea && colorSbHandle) {
                const rect = colorSbArea.getBoundingClientRect();
                const width = rect.width > 0 ? rect.width : (colorSbArea.offsetWidth || 256);
                const height = rect.height > 0 ? rect.height : (colorSbArea.offsetHeight || 130);
                colorSbHandle.style.left = `${Math.round(currentHsv.s * width)}px`;
                colorSbHandle.style.top = `${Math.round((1 - currentHsv.v) * height)}px`;
            }

            // Update Preview Box & Hex Input
            if (colorPreviewBox) colorPreviewBox.style.backgroundColor = hex;
            if (colorHexInput && document.activeElement !== colorHexInput) {
                colorHexInput.value = hex.replace('#', '');
            }

            // Update custom color chip background and icon contrast
            if (btnCustomColorChip) {
                btnCustomColorChip.dataset.color = hex;
                if (btnCustomColorChip.classList.contains('active') || btnCustomColorChip.classList.contains('has-color')) {
                    btnCustomColorChip.style.backgroundColor = hex;
                    btnCustomColorChip.classList.add('has-color');
                    const brightness = (r * 299 + g * 587 + b * 114) / 1000;
                    btnCustomColorChip.style.color = brightness > 160 ? '#18181b' : '#ffffff';
                }
            }

            // Highlight matching swatch button
            document.querySelectorAll('.swatch-btn').forEach(btn => {
                if (btn.dataset.color.toUpperCase() === hex.toUpperCase()) {
                    btn.classList.add('active');
                } else {
                    btn.classList.remove('active');
                }
            });

            if (updateCanvas) {
                selectedBgFill = hex;
                applyCanvasBackground();
                if (bgColorCurrentName) bgColorCurrentName.textContent = hex;
            }
        }

        // 2D Saturation / Value Drag Handling
        let isDraggingSB = false;

        function handleSbDrag(e) {
            if (!colorSbArea) return;
            const rect = colorSbArea.getBoundingClientRect();
            const clientX = e.clientX !== undefined ? e.clientX : (e.touches && e.touches[0].clientX) || 0;
            const clientY = e.clientY !== undefined ? e.clientY : (e.touches && e.touches[0].clientY) || 0;
            const x = Math.max(0, Math.min(clientX - rect.left, rect.width));
            const y = Math.max(0, Math.min(clientY - rect.top, rect.height));

            currentHsv.s = rect.width > 0 ? (x / rect.width) : 0;
            currentHsv.v = rect.height > 0 ? (1 - (y / rect.height)) : 0;

            if (colorSbHandle) {
                colorSbHandle.style.left = `${x}px`;
                colorSbHandle.style.top = `${y}px`;
            }

            document.querySelectorAll('.palette-row .color-chip').forEach(c => c.classList.remove('active'));
            if (btnCustomColorChip) btnCustomColorChip.classList.add('active');

            syncColorPickerUI(true);
        }

        colorSbArea?.addEventListener('pointerdown', (e) => {
            isDraggingSB = true;
            try { colorSbArea.setPointerCapture(e.pointerId); } catch (_) {}
            handleSbDrag(e);
        });

        colorSbArea?.addEventListener('pointermove', (e) => {
            if (isDraggingSB) handleSbDrag(e);
        });

        const stopSbDrag = (e) => {
            if (isDraggingSB) {
                isDraggingSB = false;
                try { colorSbArea?.releasePointerCapture(e.pointerId); } catch (_) {}
            }
        };
        colorSbArea?.addEventListener('pointerup', stopSbDrag);
        colorSbArea?.addEventListener('pointercancel', stopSbDrag);

        // Hue Slider
        colorHueSlider?.addEventListener('input', () => {
            currentHsv.h = parseInt(colorHueSlider.value) || 0;
            document.querySelectorAll('.palette-row .color-chip').forEach(c => c.classList.remove('active'));
            if (btnCustomColorChip) btnCustomColorChip.classList.add('active');
            syncColorPickerUI(true);
        });

        // Hex Input
        colorHexInput?.addEventListener('input', () => {
            let val = colorHexInput.value.replace(/[^0-9a-fA-F]/g, '').slice(0, 6);
            colorHexInput.value = val;
            if (val.length === 6) {
                const hsv = hexToHsv('#' + val);
                if (hsv) {
                    currentHsv = hsv;
                    document.querySelectorAll('.palette-row .color-chip').forEach(c => c.classList.remove('active'));
                    if (btnCustomColorChip) btnCustomColorChip.classList.add('active');
                    syncColorPickerUI(true);
                }
            }
        });

        // Apply Button
        btnColorApply?.addEventListener('click', () => {
            let val = colorHexInput?.value.trim().replace(/^#/, '') || '10B981';
            if (val.length === 3) val = val.split('').map(c => c + c).join('');
            if (val.length === 6) {
                const hsv = hexToHsv('#' + val);
                if (hsv) currentHsv = hsv;
            }
            document.querySelectorAll('.palette-row .color-chip').forEach(c => c.classList.remove('active'));
            if (btnCustomColorChip) btnCustomColorChip.classList.add('active');
            syncColorPickerUI(true);
            bgColorPopover?.classList.remove('open');
        });

        // Eyedropper API
        if (window.EyeDropper) {
            btnEyedropper?.addEventListener('click', async () => {
                try {
                    const eyeDropper = new window.EyeDropper();
                    const result = await eyeDropper.open();
                    if (result && result.sRGBHex) {
                        const hsv = hexToHsv(result.sRGBHex);
                        if (hsv) {
                            currentHsv = hsv;
                            document.querySelectorAll('.palette-row .color-chip').forEach(c => c.classList.remove('active'));
                            if (btnCustomColorChip) btnCustomColorChip.classList.add('active');
                            syncColorPickerUI(true);
                        }
                    }
                } catch (_) {
                    // Eyedropper canceled
                }
            });
        } else {
            if (btnEyedropper) btnEyedropper.style.display = 'none';
        }

        // Quick Swatches
        document.querySelectorAll('.swatch-btn').forEach(swatch => {
            swatch.addEventListener('click', () => {
                const hex = swatch.dataset.color;
                if (!hex) return;
                const hsv = hexToHsv(hex);
                if (hsv) {
                    currentHsv = hsv;
                    document.querySelectorAll('.palette-row .color-chip').forEach(c => c.classList.remove('active'));
                    if (btnCustomColorChip) btnCustomColorChip.classList.add('active');
                    syncColorPickerUI(true);
                }
            });
        });

        // Palette Chips Selection
        document.querySelectorAll('.palette-row .color-chip').forEach(chip => {
            chip.addEventListener('click', () => {
                if (chip.id === 'btn-custom-color-chip') {
                    chip.classList.add('has-color');
                    const isOpen = bgColorPopover?.classList.contains('open');
                    if (isOpen) {
                        bgColorPopover.classList.remove('open');
                    } else {
                        bgColorPopover?.classList.add('open');
                        requestAnimationFrame(() => {
                            syncColorPickerUI(false);
                        });
                    }
                    document.querySelectorAll('.palette-row .color-chip').forEach(c => c.classList.remove('active'));
                    chip.classList.add('active');
                    selectedBgFill = chip.dataset.color || '#10B981';
                    applyCanvasBackground();
                    syncColorPickerUI(false);
                    if (bgColorCurrentName) bgColorCurrentName.textContent = selectedBgFill;
                    return;
                }

                document.querySelectorAll('.palette-row .color-chip').forEach(c => c.classList.remove('active'));
                chip.classList.add('active');
                selectedBgFill = chip.dataset.color;
                applyCanvasBackground();
                bgColorPopover?.classList.remove('open');
                if (bgColorCurrentName) {
                    bgColorCurrentName.textContent = chip.dataset.name || (selectedBgFill === 'transparent' ? 'Transparan' : selectedBgFill);
                }
            });
        });

        // Close popover
        btnClosePopover?.addEventListener('click', () => {
            bgColorPopover?.classList.remove('open');
        });

        // Dismiss when clicking outside
        document.addEventListener('pointerdown', (e) => {
            if (!bgColorPopover || !bgColorPopover.classList.contains('open')) return;
            if (!bgColorPopover.contains(e.target) && !btnCustomColorChip?.contains(e.target)) {
                bgColorPopover.classList.remove('open');
            }
        });

        // Initialize UI values
        syncColorPickerUI(false);

        function applyCanvasBackground() {
            if (!bgCanvas) return;
            if (selectedBgFill === 'transparent') {
                bgCanvas.style.background = "url('data:image/svg+xml,%3Csvg xmlns=\\'http://www.w3.org/2000/svg\\' width=\\'16\\' height=\\'16\\' fill=\\'%23262626\\'%3E%3Crect width=\\'8\\' height=\\'8\\'/%3E%3Crect x=\\'8\\' y=\\'8\\' width=\\'8\\' height=\\'8\\'/%3E%3C/svg%3E')";
            } else {
                bgCanvas.style.background = selectedBgFill;
            }
        }

        function getCanvasCoordinates(e) {
            const rect = bgCanvas.getBoundingClientRect();
            const scaleX = bgCanvas.width / rect.width;
            const scaleY = bgCanvas.height / rect.height;
            const clientX = e.clientX || (e.touches && e.touches[0].clientX) || 0;
            const clientY = e.clientY || (e.touches && e.touches[0].clientY) || 0;
            return {
                x: Math.round((clientX - rect.left) * scaleX),
                y: Math.round((clientY - rect.top) * scaleY)
            };
        }

        function saveState() {
            bgHistory.push(bgCtx.getImageData(0, 0, bgCanvas.width, bgCanvas.height));
            if (bgHistory.length > 25) bgHistory.shift();
            bgRedoStack = [];
        }

        let panRafId = null;
        canvasWrapper?.addEventListener('mousedown', (e) => {
            if (e.target.closest('.compare-zoom-controls')) return;

            // Pan mode: if Pan tool is active, middle click, Spacebar held, or clicked on empty area of wrapper when zoomed
            if (activeBgTool === 'pan' || e.button === 1 || e.spaceKey || (e.target !== bgCanvas && zoomLevel > 1.0)) {
                isPanning = true;
                startPanX = e.clientX - panX;
                startPanY = e.clientY - panY;
                canvasWrapper.classList.add('is-panning');
                if (bgCanvas) bgCanvas.style.transition = 'none';
                e.preventDefault();
                return;
            }

            if (e.target === bgCanvas) {
                const { x, y } = getCanvasCoordinates(e);
                if (activeBgTool === 'autocut') {
                    saveState();
                    const isContiguous = document.getElementById('switch-magic-contiguous')?.checked !== false;
                    floodFillOrColorRemove(x, y, isContiguous);
                } else {
                    isDrawing = true;
                    saveState();
                    lastBrushX = x;
                    lastBrushY = y;
                    drawBrush(x, y, true);
                }
            }
        });

        window.addEventListener('mouseup', () => {
            isDrawing = false;
            lastBrushX = null;
            lastBrushY = null;
            if (isPanning) {
                isPanning = false;
                canvasWrapper?.classList.remove('is-panning');
                if (bgCanvas) bgCanvas.style.transition = '';
            }
        });

        window.addEventListener('mousemove', (e) => {
            if (isPanning) {
                panX = e.clientX - startPanX;
                panY = e.clientY - startPanY;
                if (!panRafId) {
                    panRafId = requestAnimationFrame(() => {
                        panRafId = null;
                        updateCanvasTransform();
                    });
                }
                return;
            }

            if (!isDrawing) return;
            const { x, y } = getCanvasCoordinates(e);
            drawBrush(x, y);
        });

        // Touch event handling for touchscreens & tablets
        canvasWrapper?.addEventListener('touchstart', (e) => {
            if (e.target.closest('.compare-zoom-controls')) return;
            if (!e.touches || e.touches.length === 0) return;
            const touch = e.touches[0];

            if (activeBgTool === 'pan' || (e.target !== bgCanvas && zoomLevel > 1.0)) {
                isPanning = true;
                startPanX = touch.clientX - panX;
                startPanY = touch.clientY - panY;
                canvasWrapper.classList.add('is-panning');
                if (bgCanvas) bgCanvas.style.transition = 'none';
                return;
            }

            if (e.target === bgCanvas) {
                const { x, y } = getCanvasCoordinates(touch);
                if (activeBgTool === 'autocut') {
                    saveState();
                    const isContiguous = document.getElementById('switch-magic-contiguous')?.checked !== false;
                    floodFillOrColorRemove(x, y, isContiguous);
                } else {
                    isDrawing = true;
                    saveState();
                    lastBrushX = x;
                    lastBrushY = y;
                    drawBrush(x, y, true);
                }
            }
        }, { passive: true });

        window.addEventListener('touchmove', (e) => {
            if (!e.touches || e.touches.length === 0) return;
            const touch = e.touches[0];
            if (isPanning) {
                panX = touch.clientX - startPanX;
                panY = touch.clientY - startPanY;
                if (!panRafId) {
                    panRafId = requestAnimationFrame(() => {
                        panRafId = null;
                        updateCanvasTransform();
                    });
                }
                return;
            }

            if (!isDrawing) return;
            const { x, y } = getCanvasCoordinates(touch);
            drawBrush(x, y);
        }, { passive: true });

        window.addEventListener('touchend', () => {
            isDrawing = false;
            lastBrushX = null;
            lastBrushY = null;
            if (isPanning) {
                isPanning = false;
                canvasWrapper?.classList.remove('is-panning');
                if (bgCanvas) bgCanvas.style.transition = '';
            }
        });

        let lastBrushX = null;
        let lastBrushY = null;

        function drawBrush(x, y, isStart = false) {
            if (!bgCanvas || !bgCtx) return;
            const rect = bgCanvas.getBoundingClientRect();
            const scale = bgCanvas.width / (rect.width || 1);
            const canvasRadius = (brushSize / 2) * scale;
            const canvasStrokeWidth = brushSize * scale;

            bgCtx.save();
            if (activeBgTool === 'erase') {
                bgCtx.globalCompositeOperation = 'destination-out';
                bgCtx.fillStyle = '#000000';
                bgCtx.strokeStyle = '#000000';
                bgCtx.lineWidth = canvasStrokeWidth;
                bgCtx.lineCap = 'round';
                bgCtx.lineJoin = 'round';

                bgCtx.beginPath();
                if (isStart || lastBrushX === null || lastBrushY === null) {
                    bgCtx.arc(x, y, canvasRadius, 0, Math.PI * 2);
                    bgCtx.fill();
                } else {
                    bgCtx.moveTo(lastBrushX, lastBrushY);
                    bgCtx.lineTo(x, y);
                    bgCtx.stroke();
                }
            } else if (activeBgTool === 'restore' && originalImageObj) {
                bgCtx.beginPath();
                if (isStart || lastBrushX === null || lastBrushY === null) {
                    bgCtx.arc(x, y, canvasRadius, 0, Math.PI * 2);
                } else {
                    bgCtx.lineWidth = canvasStrokeWidth;
                    bgCtx.lineCap = 'round';
                    bgCtx.lineJoin = 'round';
                    bgCtx.moveTo(lastBrushX, lastBrushY);
                    bgCtx.lineTo(x, y);
                }
                bgCtx.clip();
                bgCtx.drawImage(originalImageObj, 0, 0);
            }
            bgCtx.restore();

            lastBrushX = x;
            lastBrushY = y;
        }

        function floodFillOrColorRemove(startX, startY, contiguous) {
            const imgData = bgCtx.getImageData(0, 0, bgCanvas.width, bgCanvas.height);
            const data = imgData.data;
            const w = imgData.width;
            const h = imgData.height;

            const startIndex = (startY * w + startX) * 4;
            const targetR = data[startIndex];
            const targetG = data[startIndex + 1];
            const targetB = data[startIndex + 2];
            const targetA = data[startIndex + 3];

            if (targetA === 0) return;

            const tolSq = autoTolerance * autoTolerance * 3;

            if (!contiguous) {
                for (let i = 0; i < data.length; i += 4) {
                    const dr = data[i] - targetR;
                    const dg = data[i + 1] - targetG;
                    const db = data[i + 2] - targetB;
                    const distSq = dr * dr + dg * dg + db * db;
                    if (distSq <= tolSq) {
                        data[i + 3] = 0;
                    }
                }
            } else {
                const visited = new Uint8Array(w * h);
                const queue = [startX, startY];
                visited[startY * w + startX] = 1;

                while (queue.length > 0) {
                    const cy = queue.pop();
                    const cx = queue.pop();
                    const idx = (cy * w + cx) * 4;
                    data[idx + 3] = 0;

                    const neighbors = [
                        [cx + 1, cy],
                        [cx - 1, cy],
                        [cx, cy + 1],
                        [cx, cy - 1]
                    ];

                    for (let n = 0; n < neighbors.length; n++) {
                        const nx = neighbors[n][0];
                        const ny = neighbors[n][1];
                        if (nx >= 0 && nx < w && ny >= 0 && ny < h) {
                            const nIndex = ny * w + nx;
                            if (visited[nIndex] === 0) {
                                visited[nIndex] = 1;
                                const pIdx = nIndex * 4;
                                const dr = data[pIdx] - targetR;
                                const dg = data[pIdx + 1] - targetG;
                                const db = data[pIdx + 2] - targetB;
                                const distSq = dr * dr + dg * dg + db * db;
                                if (distSq <= tolSq && data[pIdx + 3] > 0) {
                                    queue.push(nx, ny);
                                }
                            }
                        }
                    }
                }
            }

            bgCtx.putImageData(imgData, 0, 0);
        }

        // AI Auto Segmentation: Multi-border clustering & perimeter flood fill (matching Android engine)
        btnAutoSeg?.addEventListener('click', () => {
            if (!bgCanvas || !originalImageObj) return;
            saveState();

            const imgData = bgCtx.getImageData(0, 0, bgCanvas.width, bgCanvas.height);
            const data = imgData.data;
            const w = imgData.width;
            const h = imgData.height;
            const tolSq = autoTolerance * autoTolerance * 3;
            const visited = new Uint8Array(w * h);
            const queue = [];

            // 1. Sample border perimeter (top, bottom, left, right)
            const stepX = Math.max(1, Math.floor(w / 16));
            const stepY = Math.max(1, Math.floor(h / 16));
            const borderSamples = [];

            for (let x = 0; x < w; x += stepX) {
                const topIdx = x * 4;
                const botIdx = ((h - 1) * w + x) * 4;
                borderSamples.push([data[topIdx], data[topIdx + 1], data[topIdx + 2]]);
                borderSamples.push([data[botIdx], data[botIdx + 1], data[botIdx + 2]]);
            }
            for (let y = 0; y < h; y += stepY) {
                const leftIdx = (y * w) * 4;
                const rightIdx = (y * w + (w - 1)) * 4;
                borderSamples.push([data[leftIdx], data[leftIdx + 1], data[leftIdx + 2]]);
                borderSamples.push([data[rightIdx], data[rightIdx + 1], data[rightIdx + 2]]);
            }

            // Cluster into up to 8 representative background shades
            const bgPalette = [];
            const clusterDistSq = 30 * 30 * 3;
            for (const s of borderSamples) {
                const exists = bgPalette.some(p => {
                    const dr = p[0] - s[0];
                    const dg = p[1] - s[1];
                    const db = p[2] - s[2];
                    return (dr * dr + dg * dg + db * db) < clusterDistSq;
                });
                if (!exists) {
                    bgPalette.push(s);
                    if (bgPalette.length >= 8) break;
                }
            }

            function isBgColor(r, g, b) {
                for (let i = 0; i < bgPalette.length; i++) {
                    const p = bgPalette[i];
                    const dr = p[0] - r;
                    const dg = p[1] - g;
                    const db = p[2] - b;
                    if (dr * dr + dg * dg + db * db <= tolSq) return true;
                }
                return false;
            }

            // 2. Seed border pixels into queue
            for (let x = 0; x < w; x++) {
                queue.push(x, 0);
                queue.push(x, h - 1);
                visited[x] = 1;
                visited[(h - 1) * w + x] = 1;
            }
            for (let y = 0; y < h; y++) {
                queue.push(0, y);
                queue.push(w - 1, y);
                visited[y * w] = 1;
                visited[y * w + (w - 1)] = 1;
            }

            while (queue.length > 0) {
                const cy = queue.pop();
                const cx = queue.pop();
                const idx = (cy * w + cx) * 4;

                const r = data[idx];
                const g = data[idx + 1];
                const b = data[idx + 2];

                if (isBgColor(r, g, b) || cy === 0 || cy === h - 1 || cx === 0 || cx === w - 1) {
                    data[idx + 3] = 0; // erase background

                    const neighbors = [
                        [cx + 1, cy],
                        [cx - 1, cy],
                        [cx, cy + 1],
                        [cx, cy - 1]
                    ];
                    for (let n = 0; n < neighbors.length; n++) {
                        const nx = neighbors[n][0];
                        const ny = neighbors[n][1];
                        if (nx >= 0 && nx < w && ny >= 0 && ny < h) {
                            const nIdx = ny * w + nx;
                            if (visited[nIdx] === 0) {
                                visited[nIdx] = 1;
                                queue.push(nx, ny);
                            }
                        }
                    }
                }
            }

            bgCtx.putImageData(imgData, 0, 0);
        });

        // Undo & Redo & Reset
        document.getElementById('btn-bg-undo')?.addEventListener('click', () => {
            if (bgHistory.length > 1) {
                bgRedoStack.push(bgHistory.pop());
                const prevState = bgHistory[bgHistory.length - 1];
                bgCtx.putImageData(prevState, 0, 0);
            }
        });

        document.getElementById('btn-bg-redo')?.addEventListener('click', () => {
            if (bgRedoStack.length > 0) {
                const nextState = bgRedoStack.pop();
                bgHistory.push(nextState);
                bgCtx.putImageData(nextState, 0, 0);
            }
        });

        document.getElementById('btn-bg-reset')?.addEventListener('click', () => {
            if (originalImageObj) {
                bgCtx.clearRect(0, 0, bgCanvas.width, bgCanvas.height);
                bgCtx.drawImage(originalImageObj, 0, 0);
                saveState();
                zoomLevel = 1.0;
                panX = 0;
                panY = 0;
                updateCanvasTransform();
            }
        });

        // Save Photo
        document.getElementById('btn-save-bg')?.addEventListener('click', async () => {
            const tempCanvas = document.createElement('canvas');
            tempCanvas.width = bgCanvas.width;
            tempCanvas.height = bgCanvas.height;
            const tCtx = tempCanvas.getContext('2d');

            if (selectedBgFill !== 'transparent') {
                tCtx.fillStyle = selectedBgFill;
                tCtx.fillRect(0, 0, tempCanvas.width, tempCanvas.height);
            }
            tCtx.drawImage(bgCanvas, 0, 0);

            if (!currentOutputDir && window.hdifyApi?.getDefaultOutputDir) {
                currentOutputDir = await window.hdifyApi.getDefaultOutputDir();
            }
            const dataUrl = tempCanvas.toDataURL('image/png');
            const outName = `HDify_Cutout_${Date.now()}.png`;
            const outPath = `${currentOutputDir}\\${outName}`;

            await window.hdifyApi.saveDataUrl({ dataUrl, outputPath: outPath });
            window.hdifyApi.showItemInFolder(outPath);
            addQueueTask({
                id: 'bg_' + Date.now(),
                type: 'Background Cutout',
                title: outName,
                status: i18n[currentLang].status_success,
                timestamp: Date.now(),
                outputPath: outPath
            });
        });

        // Send cutout to Upscaler
        document.getElementById('btn-send-to-upscale')?.addEventListener('click', async () => {
            const dataUrl = bgCanvas.toDataURL('image/png');
            const tempPath = await window.hdifyApi.saveTempImage(dataUrl);

            window.switchView('view-image');
            window.loadImagePath(tempPath);
        });
    }

    // ==========================================
    // 8. VIEW: VIDEO COMPRESSOR
    // ==========================================
    let selectedCompressVideo = null;
    let compressMeta = null;
    let selectedCompressPreset = 'balanced';
    let targetBitrateMbps = 4.0;
    let selectedCompressRes = 'original';
    let selectedCompressCodec = 'h264';
    let compressHdr = false;

    function initVideoCompressor() {
        const dropzone = document.getElementById('compress-dropzone');
        const fileInput = document.getElementById('compress-file-input');
        const container = document.getElementById('compress-controls-container');
        const btnChangeVideo = document.getElementById('btn-change-compress-video');
        const metaTags = document.getElementById('compress-meta-tags');
        const estDisplay = document.getElementById('compress-est-size-display');
        const estBadge = document.getElementById('compress-savings-badge');
        const customOptions = document.getElementById('custom-compress-options');
        const sliderBitrate = document.getElementById('slider-target-bitrate');
        const valBitrate = document.getElementById('val-target-bitrate');
        const switchHdr = document.getElementById('switch-compress-force-hdr');
        const btnStart = document.getElementById('btn-start-compress');
        const progressBox = document.getElementById('compress-progress-box');
        const progressBar = document.getElementById('compress-progress-bar');
        const progressPct = document.getElementById('compress-progress-pct');
        const progressStatus = document.getElementById('compress-progress-status');
        const progressDetails = document.getElementById('compress-progress-details');
        const progressActions = document.getElementById('compress-progress-actions');

        let isPickingCompressVideo = false;
        const handleOpenCompressDialog = async () => {
            if (isPickingCompressVideo) return;
            isPickingCompressVideo = true;
            try {
                if (window.hdifyApi && window.hdifyApi.openFileDialog) {
                    const picked = await window.hdifyApi.openFileDialog({ type: 'video' });
                    if (picked) {
                        loadCompressFile(picked);
                    }
                    return;
                }
                fileInput.click();
            } finally {
                isPickingCompressVideo = false;
            }
        };

        fileInput.addEventListener('click', (e) => e.stopPropagation());
        dropzone.addEventListener('click', handleOpenCompressDialog);
        btnChangeVideo?.addEventListener('click', handleOpenCompressDialog);

        dropzone.addEventListener('dragover', (e) => {
            e.preventDefault();
            dropzone.classList.add('drag-active');
        });
        dropzone.addEventListener('dragleave', () => dropzone.classList.remove('drag-active'));
        dropzone.addEventListener('drop', (e) => {
            e.preventDefault();
            dropzone.classList.remove('drag-active');
            if (e.dataTransfer && e.dataTransfer.files.length > 0) {
                const p = getSafeFilePath(e.dataTransfer.files[0]);
                if (p) loadCompressFile(p);
            }
        });

        fileInput.addEventListener('change', () => {
            if (fileInput.files && fileInput.files.length > 0) {
                const p = getSafeFilePath(fileInput.files[0]);
                if (p) loadCompressFile(p);
            }
            fileInput.value = '';
        });

        window.loadCompressFile = async function (filePath) {
            if (!filePath || typeof filePath !== 'string' || !filePath.trim()) return;
            selectedCompressVideo = filePath;
            try {
                compressMeta = await window.hdifyApi.getVideoMetadata(filePath);
                container.style.display = 'block';
                if (metaTags) {
                    metaTags.innerHTML = `
                        <span class="feature-badge">${compressMeta.width}x${compressMeta.height}</span>
                        <span class="feature-badge">${compressMeta.fps} FPS</span>
                        <span class="feature-badge">${Math.round(compressMeta.duration)} ${i18n[currentLang].unit_seconds}</span>
                        <span class="feature-badge">${(compressMeta.sizeBytes / (1024 * 1024)).toFixed(1)} MB</span>
                    `;
                }
                updateSavingsEstimate();
            } catch (err) {
                alert(i18n[currentLang].alert_compress_read_failed + err.message);
            }
        };

        window.updateSavingsEstimate = function () {
            if (!compressMeta || !estDisplay || !estBadge) return;
            const originalMb = compressMeta.sizeBytes / (1024 * 1024);
            const savingsText = i18n[currentLang].unit_savings || 'Penghematan';

            const pixels = (compressMeta.width || 1920) * (compressMeta.height || 1080);
            const fps = compressMeta.fps || 30;
            const fpsFactor = Math.min(2.0, Math.max(0.85, fps / 30.0));
            const origBitrateMbps = (compressMeta.bitrate && compressMeta.bitrate > 500000)
                ? (compressMeta.bitrate / 1000000)
                : (pixels >= 3840 * 2160 ? 35 * fpsFactor : (pixels >= 1920 * 1080 ? 12 * fpsFactor : 6 * fpsFactor));

            let qualityFloor = 3.0 * fpsFactor;
            let ceiling = 8.5 * fpsFactor;
            let ratio = 0.50;

            if (selectedCompressPreset === 'smart') {
                ratio = 0.70;
                if (pixels >= 3840 * 2160) { qualityFloor = 18.0 * fpsFactor; ceiling = 32.0 * fpsFactor; }
                else if (pixels >= 2560 * 1440) { qualityFloor = 11.0 * fpsFactor; ceiling = 20.0 * fpsFactor; }
                else if (pixels >= 1920 * 1080) { qualityFloor = 6.0 * fpsFactor; ceiling = 12.0 * fpsFactor; }
                else if (pixels >= 1280 * 720) { qualityFloor = 3.5 * fpsFactor; ceiling = 6.5 * fpsFactor; }
                else { qualityFloor = 1.8 * fpsFactor; ceiling = 3.5 * fpsFactor; }
            } else if (selectedCompressPreset === 'balanced') {
                ratio = 0.50;
                if (pixels >= 3840 * 2160) { qualityFloor = 13.0 * fpsFactor; ceiling = 22.0 * fpsFactor; }
                else if (pixels >= 2560 * 1440) { qualityFloor = 7.5 * fpsFactor; ceiling = 14.0 * fpsFactor; }
                else if (pixels >= 1920 * 1080) { qualityFloor = 4.2 * fpsFactor; ceiling = 8.5 * fpsFactor; }
                else if (pixels >= 1280 * 720) { qualityFloor = 2.4 * fpsFactor; ceiling = 4.5 * fpsFactor; }
                else { qualityFloor = 1.2 * fpsFactor; ceiling = 2.5 * fpsFactor; }
            } else if (selectedCompressPreset === 'saver') {
                ratio = 0.30;
                if (pixels >= 3840 * 2160) { qualityFloor = 9.0 * fpsFactor; ceiling = 14.0 * fpsFactor; }
                else if (pixels >= 2560 * 1440) { qualityFloor = 5.2 * fpsFactor; ceiling = 9.0 * fpsFactor; }
                else if (pixels >= 1920 * 1080) { qualityFloor = 3.0 * fpsFactor; ceiling = 5.5 * fpsFactor; }
                else if (pixels >= 1280 * 720) { qualityFloor = 1.6 * fpsFactor; ceiling = 3.0 * fpsFactor; }
                else { qualityFloor = 0.9 * fpsFactor; ceiling = 1.8 * fpsFactor; }
            }

            if (selectedCompressPreset === 'custom') {
                targetBitrateMbps = parseFloat(sliderBitrate.value);
            } else {
                const rawTarget = origBitrateMbps * ratio;
                if (origBitrateMbps <= qualityFloor * 1.15) {
                    targetBitrateMbps = Math.max(origBitrateMbps * 0.90, qualityFloor * 0.75);
                } else {
                    targetBitrateMbps = Math.max(qualityFloor, Math.min(rawTarget, ceiling));
                }
            }

            const estBytes = (targetBitrateMbps * 1000000 / 8) * (compressMeta.duration || 1);
            const estMb = Math.min(originalMb * 0.98, Math.max(0.1, estBytes / (1024 * 1024))).toFixed(1);
            const savedPct = Math.max(5, Math.min(95, Math.round((1 - (parseFloat(estMb) / originalMb)) * 100)));
            const badgeText = `${savedPct}% ${savingsText}`;

            estDisplay.textContent = `${originalMb.toFixed(1)} MB → ~${estMb} MB`;
            estBadge.textContent = badgeText;
        };

        // 4 Mode Selection Cards
        document.querySelectorAll('#mode-card-list-compress .mode-card').forEach(card => {
            card.addEventListener('click', () => {
                document.querySelectorAll('#mode-card-list-compress .mode-card').forEach(c => c.classList.remove('active'));
                card.classList.add('active');
                selectedCompressPreset = card.dataset.preset;
                if (customOptions) {
                    customOptions.style.display = selectedCompressPreset === 'custom' ? 'block' : 'none';
                }
                updateSavingsEstimate();
            });
        });

        // Codec selector
        document.querySelectorAll('#btn-group-compress-codec .btn-group-item').forEach(btn => {
            btn.addEventListener('click', () => {
                document.querySelectorAll('#btn-group-compress-codec .btn-group-item').forEach(b => b.classList.remove('active'));
                btn.classList.add('active');
                selectedCompressCodec = btn.dataset.codec;
            });
        });

        sliderBitrate?.addEventListener('input', () => {
            if (valBitrate) valBitrate.textContent = `${parseFloat(sliderBitrate.value).toFixed(1)} Mbps`;
            updateSavingsEstimate();
        });

        document.querySelectorAll('#btn-group-compress-res .btn-group-item').forEach(btn => {
            btn.addEventListener('click', () => {
                document.querySelectorAll('#btn-group-compress-res .btn-group-item').forEach(b => b.classList.remove('active'));
                btn.classList.add('active');
                selectedCompressRes = btn.dataset.res;
            });
        });

        switchHdr?.addEventListener('change', () => {
            compressHdr = switchHdr.checked;
        });

        function resetCompressProgressCard() {
            if (progressBox) progressBox.style.display = 'none';
            if (progressBar) {
                progressBar.style.width = '0%';
                progressBar.style.background = '';
                progressBar.style.boxShadow = '';
            }
            if (progressPct) progressPct.textContent = '0%';
            if (progressStatus) progressStatus.textContent = i18n[currentLang].status_compressing || 'Mengompres video...';
            if (progressDetails) progressDetails.textContent = 'Menyiapkan encoder kompresi...';
            if (progressActions) {
                progressActions.innerHTML = `<button class="btn btn-secondary" id="btn-cancel-compress">${i18n[currentLang].btn_cancel || 'Batalkan'}</button>`;
            }
        }

        function showCompressProgressSuccess(outputPath, newSizeBytes, originalSizeBytes) {
            if (!progressBox) return;
            progressBox.style.display = 'block';
            if (progressBar) {
                progressBar.style.width = '100%';
                progressBar.style.background = '#22c55e';
                progressBar.style.boxShadow = '0 0 14px rgba(34, 197, 94, 0.45)';
            }
            if (progressPct) {
                progressPct.innerHTML = '<span style="color: #4ade80; font-size: 13px; font-weight: 700;">100% Selesai</span>';
            }
            if (progressStatus) {
                progressStatus.innerHTML = '<span style="color: #4ade80; display: inline-flex; align-items: center; gap: 6px;"><svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor"><path d="M9 16.2L4.8 12l-1.4 1.4L9 19 21 7l-1.4-1.4L9 16.2z"/></svg> Kompresi Video Berhasil!</span>';
            }

            const filename = outputPath ? outputPath.split(/[\\/]/).pop() : 'video.mp4';
            let summaryText = '';
            if (originalSizeBytes && newSizeBytes && originalSizeBytes > newSizeBytes) {
                const savedPct = Math.round(((originalSizeBytes - newSizeBytes) / originalSizeBytes) * 100);
                summaryText = `<span style="color: #4ade80; font-weight: 600;">${formatBytes(originalSizeBytes)} → ${formatBytes(newSizeBytes)} (Hemat ${savedPct}%)</span> • <span style="color: var(--text-secondary);">${escapeHtml(filename)}</span>`;
            } else {
                summaryText = `<span style="color: var(--text-primary); font-weight: 600;">${escapeHtml(filename)}</span> <span style="color: var(--text-secondary); margin-left: 4px;">(${formatBytes(newSizeBytes)})</span>`;
            }

            if (progressDetails) {
                progressDetails.innerHTML = summaryText;
            }

            if (progressActions) {
                progressActions.innerHTML = `
                    <button class="btn btn-secondary" id="btn-open-video-compress" data-path="${escapeHtml(outputPath)}" style="padding: 6px 14px; font-size: 12px;">
                        <svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor" style="vertical-align: middle; margin-right: 4px;"><path d="M8 5v14l11-7z"/></svg> Buka Video
                    </button>
                    <button class="btn btn-secondary" id="btn-show-folder-compress" data-path="${escapeHtml(outputPath)}" style="padding: 6px 14px; font-size: 12px;">
                        <svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor" style="vertical-align: middle; margin-right: 4px;"><path d="M10 4H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2h-8l-2-2z"/></svg> Folder
                    </button>
                    <button class="btn btn-primary" id="btn-done-video-compress" style="padding: 6px 14px; font-size: 12px;">Tutup</button>
                `;
            }
        }

        progressActions?.addEventListener('click', async (e) => {
            const btn = e.target.closest('button');
            if (!btn) return;
            if (btn.id === 'btn-cancel-compress') {
                await window.hdifyApi.cancelVideoCompress();
                resetCompressProgressCard();
                btnStart.disabled = false;
                btnStart.textContent = i18n[currentLang].btn_start_compress || 'Mulai Kompresi Video';
            } else if (btn.id === 'btn-open-video-compress') {
                const target = btn.dataset.path;
                if (target) {
                    if (window.hdifyApi.openPath) {
                        window.hdifyApi.openPath(target);
                    } else {
                        window.hdifyApi.openExternal(target);
                    }
                }
            } else if (btn.id === 'btn-show-folder-compress') {
                const target = btn.dataset.path;
                if (target) window.hdifyApi.showItemInFolder(target);
            } else if (btn.id === 'btn-done-video-compress') {
                resetCompressProgressCard();
            }
        });

        btnStart.addEventListener('click', async () => {
            if (!selectedCompressVideo) return;
            btnStart.disabled = true;
            btnStart.innerHTML = `<span style="display:inline-block; width:14px; height:14px; border:2px solid #fff; border-top-color:transparent; border-radius:50%; animation:spin 0.8s linear infinite; margin-right:8px; vertical-align:middle;"></span> Mengompres Video...`;

            resetCompressProgressCard();
            progressBox.style.display = 'block';

            if (!currentOutputDir && window.hdifyApi?.getDefaultOutputDir) {
                currentOutputDir = await window.hdifyApi.getDefaultOutputDir();
            }
            const outName = `HDify_Compressed_${Date.now()}.mp4`;
            const outPath = `${currentOutputDir}\\${outName}`;

            const params = {
                inputPath: selectedCompressVideo,
                outputPath: outPath,
                targetBitrateMbps: targetBitrateMbps,
                codec: selectedCompressCodec,
                resolution: selectedCompressRes,
                forceHdr: compressHdr,
                duration: compressMeta ? compressMeta.duration : 0,
                sourceFps: compressMeta ? compressMeta.fps : 30
            };

            try {
                const res = await window.hdifyApi.runVideoCompress(params);
                if (res && res.success) {
                    const origBytes = compressMeta ? compressMeta.sizeBytes : 0;
                    showCompressProgressSuccess(res.outputPath || outPath, res.sizeBytes, origBytes);
                    addQueueTask({
                        id: 'comp_' + Date.now(),
                        type: 'Video Compression',
                        title: outName,
                        status: i18n[currentLang].status_completed,
                        timestamp: Date.now(),
                        outputPath: outPath
                    });
                }
            } catch (err) {
                alert(i18n[currentLang].alert_compress_failed + err.message);
                resetCompressProgressCard();
            } finally {
                btnStart.disabled = false;
                btnStart.textContent = i18n[currentLang].btn_start_compress || 'Mulai Kompresi Video';
            }
        });
    }

    // ==========================================
    // 9. VIEW: MEDIA DOWNLOADER
    // ==========================================
    let extractedMediaData = null;
    let selectedDownloadOption = null;
    let lastDownloadedFilePath = null;

    function initMediaDownloader() {
        const urlInput = document.getElementById('downloader-url-input');
        const btnPaste = document.getElementById('btn-downloader-paste');
        const btnAnalyze = document.getElementById('btn-downloader-analyze');
        const clipBanner = document.getElementById('clipboard-banner');
        const clipUrlText = document.getElementById('clip-url-text');
        const btnClipPaste = document.getElementById('btn-clip-paste');
        const resultCard = document.getElementById('downloader-result-card');
        const thumb = document.getElementById('downloader-thumb');
        const badge = document.getElementById('downloader-platform-badge');
        const title = document.getElementById('downloader-title');
        const author = document.getElementById('downloader-author');
        const optionsList = document.getElementById('downloader-options-list');
        const btnDownload = document.getElementById('btn-downloader-start');
        const progressBox = document.getElementById('downloader-progress-box');
        const progressBar = document.getElementById('downloader-progress-bar');
        const progressPct = document.getElementById('downloader-progress-pct');
        const btnPreviewToggle = document.getElementById('btn-downloader-preview-toggle');
        const previewBox = document.getElementById('downloader-preview-box');
        const videoPreview = document.getElementById('downloader-video-preview');
        const audioPreview = document.getElementById('downloader-audio-preview');
        const imagePreview = document.getElementById('downloader-image-preview');
        const previewError = document.getElementById('downloader-preview-error');
        const postActions = document.getElementById('downloader-post-actions');

        function extractCleanUrl(text) {
            if (!text || typeof text !== 'string') return '';
            let str = text.trim();
            // Prepend https:// if user pasted domain without protocol
            if (/^(vt\.tiktok\.com|vm\.tiktok\.com|tiktok\.com|youtube\.com|youtu\.be|music\.youtube\.com|instagram\.com|instagr\.am|twitter\.com|x\.com|t\.co|soundcloud\.com|on\.soundcloud\.com|v\.douyin\.com|douyin\.com)/i.test(str)) {
                str = 'https://' + str;
            }
            const match = str.match(/(https?:\/\/[^\s<>"'{}|\\^`]+)/i);
            if (!match) return '';
            return match[1].replace(/[.,;:!?)\]>'"-]+$/, '').trim();
        }

        function isSupportedMediaUrl(url) {
            return /(tiktok\.com|douyin\.com|iesdouyin\.com|youtube\.com|youtu\.be|instagram\.com|instagr\.am|twitter\.com|x\.com|t\.co|soundcloud\.com|sndcdn\.com)/i.test(url);
        }

        function detectPlatform(url) {
            if (!url) return null;
            const lower = url.toLowerCase().trim();
            if (lower.includes('music.youtube.com')) return 'YT Music';
            if (lower.includes('youtube.com') || lower.includes('youtu.be')) return 'YouTube';
            if (lower.includes('tiktok.com')) return 'TikTok';
            if (lower.includes('douyin.com') || lower.includes('iesdouyin.com')) return 'Douyin';
            if (lower.includes('instagram.com') || lower.includes('instagr.am')) return 'Instagram';
            if (lower.includes('twitter.com') || lower.includes('x.com') || lower.includes('t.co') || lower.includes('fxtwitter') || lower.includes('vxtwitter')) return 'X (Twitter)';
            if (lower.includes('soundcloud.com') || lower.includes('on.soundcloud.com') || lower.includes('sndcdn.com')) return 'SoundCloud';
            return null;
        }

        function highlightPlatformChip(platformName) {
            const chips = document.querySelectorAll('#downloader-platform-chips .platform-chip');
            chips.forEach(chip => {
                const p = chip.dataset.platform;
                if (platformName && (p === platformName || (platformName === 'YouTube Music' && p === 'YT Music') || (platformName === 'X' && p === 'X (Twitter)'))) {
                    chip.classList.add('active');
                } else {
                    chip.classList.remove('active');
                }
            });
        }

        // Setup interactive clicks on platform chips
        const platformChips = document.querySelectorAll('#downloader-platform-chips .platform-chip');
        platformChips.forEach(chip => {
            chip.addEventListener('click', async () => {
                const chipPlat = chip.dataset.platform;
                highlightPlatformChip(chipPlat);

                // If clipboard has a matching URL, auto-paste and analyze
                try {
                    let clipText = '';
                    if (window.hdifyApi?.getClipboardText) {
                        clipText = await window.hdifyApi.getClipboardText();
                    } else if (navigator.clipboard?.readText) {
                        clipText = await navigator.clipboard.readText();
                    }
                    const clean = extractCleanUrl(clipText);
                    const clipPlat = detectPlatform(clean);
                    if (clean && clipPlat === chipPlat) {
                        urlInput.value = clean;
                        if (clipBanner) clipBanner.style.display = 'none';
                        btnAnalyze.click();
                        return;
                    }
                } catch (_) {}

                urlInput.focus();
                urlInput.placeholder = `Tempel tautan ${chipPlat} di sini...`;
            });
        });

        window.checkClipboardForMedia = async function () {
            try {
                let text = '';
                if (window.hdifyApi?.getClipboardText) {
                    text = await window.hdifyApi.getClipboardText();
                } else if (navigator.clipboard?.readText) {
                    text = await navigator.clipboard.readText();
                }
                const cleanUrl = extractCleanUrl(text);
                if (cleanUrl && isSupportedMediaUrl(cleanUrl)) {
                    const detected = detectPlatform(cleanUrl);
                    if (detected) highlightPlatformChip(detected);

                    if (urlInput.value.trim() === cleanUrl) {
                        if (clipBanner) clipBanner.style.display = 'none';
                        return;
                    }
                    if (clipBanner && clipUrlText) {
                        const platTag = detected ? `[${detected}] ` : '';
                        clipUrlText.textContent = `${platTag}${cleanUrl.length > 40 ? cleanUrl.substring(0, 37) + '...' : cleanUrl}`;
                        clipBanner.dataset.cleanUrl = cleanUrl;
                        clipBanner.style.display = 'flex';
                    }
                } else {
                    if (clipBanner) clipBanner.style.display = 'none';
                }
            } catch (_) {}
        };

        window.addEventListener('focus', () => {
            if (typeof window.checkClipboardForMedia === 'function') window.checkClipboardForMedia();
        });
        window.checkClipboardForMedia();

        // Periodic clipboard checking when view-downloader is active
        setInterval(() => {
            const activeNav = document.querySelector('.nav-item.active');
            if (activeNav && activeNav.dataset.view === 'view-downloader') {
                if (typeof window.checkClipboardForMedia === 'function') window.checkClipboardForMedia();
            }
        }, 2000);

        urlInput.addEventListener('keydown', (e) => {
            if (e.key === 'Enter') {
                e.preventDefault();
                btnAnalyze.click();
            }
        });

        urlInput.addEventListener('paste', () => {
            setTimeout(() => {
                const clean = extractCleanUrl(urlInput.value);
                if (clean) {
                    urlInput.value = clean;
                    const p = detectPlatform(clean);
                    if (p) highlightPlatformChip(p);
                    if (clipBanner) clipBanner.style.display = 'none';
                }
            }, 30);
        });

        urlInput.addEventListener('input', () => {
            const clean = extractCleanUrl(urlInput.value);
            if (clean) {
                const p = detectPlatform(clean);
                if (p) highlightPlatformChip(p);
                if (clean === clipBanner?.dataset?.cleanUrl && clipBanner) {
                    clipBanner.style.display = 'none';
                }
            } else {
                highlightPlatformChip(null);
            }
        });

        btnClipPaste?.addEventListener('click', () => {
            const targetUrl = clipBanner.dataset.cleanUrl || extractCleanUrl(urlInput.value);
            if (targetUrl) {
                urlInput.value = targetUrl;
                const p = detectPlatform(targetUrl);
                if (p) highlightPlatformChip(p);
                if (clipBanner) clipBanner.style.display = 'none';
                btnAnalyze.click();
            }
        });

        btnPaste.addEventListener('click', async () => {
            try {
                let text = '';
                if (window.hdifyApi?.getClipboardText) {
                    text = await window.hdifyApi.getClipboardText();
                } else if (navigator.clipboard?.readText) {
                    text = await navigator.clipboard.readText();
                }
                const cleanUrl = extractCleanUrl(text);
                urlInput.value = cleanUrl || (text ? text.trim() : '');
                const p = detectPlatform(urlInput.value);
                if (p) highlightPlatformChip(p);
                if (cleanUrl && clipBanner) clipBanner.style.display = 'none';
            } catch (_) {}
        });

        btnAnalyze.addEventListener('click', async () => {
            const raw = urlInput.value.trim();
            const cleanUrl = extractCleanUrl(raw) || raw;
            if (!cleanUrl || !cleanUrl.startsWith('http')) {
                return alert(i18n[currentLang].alert_enter_url || 'Silakan masukkan tautan media yang valid.');
            }
            urlInput.value = cleanUrl;
            const detected = detectPlatform(cleanUrl);
            if (detected) highlightPlatformChip(detected);

            btnAnalyze.disabled = true;
            btnAnalyze.textContent = i18n[currentLang].btn_analyzing || 'Menganalisis...';
            postActions.style.display = 'none';
            previewBox.style.display = 'none';
            if (previewError) previewError.style.display = 'none';
            if (videoPreview) { videoPreview.pause(); videoPreview.src = ''; }
            if (audioPreview) { audioPreview.pause(); audioPreview.src = ''; }
            if (imagePreview) { imagePreview.src = ''; }

            try {
                const data = await window.hdifyApi.extractMediaInfo(cleanUrl);
                extractedMediaData = data;
                resultCard.style.display = 'block';

                if (data.platform) highlightPlatformChip(data.platform);

                const thumbSrc = data.thumbnail || data.rawThumbnail;
                if (thumbSrc) {
                    thumb.src = thumbSrc;
                    thumb.style.display = 'block';
                    thumb.onerror = () => {
                        if (data.rawThumbnail && thumb.src !== data.rawThumbnail) {
                            thumb.src = data.rawThumbnail;
                        } else {
                            thumb.style.display = 'none';
                        }
                    };
                } else {
                    thumb.style.display = 'none';
                }

                badge.textContent = data.platform;
                title.textContent = data.title;
                author.textContent = data.author;

                optionsList.innerHTML = data.options.map((opt, i) => {
                    const isChecked = i === 0 ? 'checked' : '';
                    return `
                        <label style="display: flex; align-items: center; gap: 10px; padding: 10px 14px; background: rgba(255,255,255,0.03); border: 1px solid var(--bg-glass-stroke); border-radius: var(--radius-md); cursor: pointer;">
                            <input type="radio" name="download_quality" value="${i}" ${isChecked}>
                            <div style="flex: 1;">
                                <div style="font-weight: 600; font-size: 14px;">${escapeHtml(opt.label)}</div>
                                <div style="font-size: 11px; color: var(--text-secondary);">${opt.format} • ${opt.quality}</div>
                            </div>
                        </label>
                    `;
                }).join('');

                selectedDownloadOption = data.options[0];
                document.querySelectorAll('input[name="download_quality"]').forEach(radio => {
                    radio.addEventListener('change', () => {
                        selectedDownloadOption = data.options[parseInt(radio.value)];
                        if (previewBox.style.display === 'block') {
                            loadPreviewStream();
                        }
                    });
                });

            } catch (err) {
                alert(i18n[currentLang].alert_analyze_failed + err.message);
            } finally {
                btnAnalyze.disabled = false;
                btnAnalyze.textContent = i18n[currentLang].btn_analyze || 'Analisis Tautan';
            }
        });

        // Media Preview Toggle
        async function loadPreviewStream() {
            if (!selectedDownloadOption || !selectedDownloadOption.url) return;
            previewBox.style.display = 'block';
            if (previewError) previewError.style.display = 'none';

            let streamUrl = selectedDownloadOption.previewUrl;
            if (!streamUrl && window.hdifyApi?.getStreamProxyUrl) {
                streamUrl = await window.hdifyApi.getStreamProxyUrl(selectedDownloadOption.url);
            }
            if (!streamUrl) streamUrl = selectedDownloadOption.url;

            if (selectedDownloadOption.format === 'VIDEO') {
                if (audioPreview) { audioPreview.style.display = 'none'; audioPreview.pause(); audioPreview.src = ''; }
                if (imagePreview) { imagePreview.style.display = 'none'; imagePreview.src = ''; }
                if (videoPreview) {
                    videoPreview.style.display = 'block';
                    videoPreview.onerror = () => {
                        if (videoPreview.src !== selectedDownloadOption.url) {
                            videoPreview.src = selectedDownloadOption.url;
                            videoPreview.load();
                        } else if (previewError) {
                            previewError.textContent = 'Pratinjau langsung tidak dapat dimuat di browser. File tetap dapat langsung diunduh secara utuh.';
                            previewError.style.display = 'block';
                        }
                    };
                    videoPreview.src = streamUrl;
                    videoPreview.load();
                    videoPreview.play().catch(() => {});
                }
            } else if (selectedDownloadOption.format === 'AUDIO') {
                if (videoPreview) { videoPreview.style.display = 'none'; videoPreview.pause(); videoPreview.src = ''; }
                if (imagePreview) { imagePreview.style.display = 'none'; imagePreview.src = ''; }
                if (audioPreview) {
                    audioPreview.style.display = 'block';
                    audioPreview.onerror = () => {
                        if (audioPreview.src !== selectedDownloadOption.url) {
                            audioPreview.src = selectedDownloadOption.url;
                            audioPreview.load();
                        } else if (previewError) {
                            previewError.textContent = 'Pratinjau audio tidak dapat diputar di browser. File tetap dapat langsung diunduh.';
                            previewError.style.display = 'block';
                        }
                    };
                    audioPreview.src = streamUrl;
                    audioPreview.load();
                    audioPreview.play().catch(() => {});
                }
            } else if (selectedDownloadOption.format === 'IMAGE') {
                if (videoPreview) { videoPreview.style.display = 'none'; videoPreview.pause(); videoPreview.src = ''; }
                if (audioPreview) { audioPreview.style.display = 'none'; audioPreview.pause(); audioPreview.src = ''; }
                if (imagePreview) {
                    imagePreview.style.display = 'block';
                    imagePreview.onerror = () => {
                        if (imagePreview.src !== selectedDownloadOption.url) {
                            imagePreview.src = selectedDownloadOption.url;
                        } else if (previewError) {
                            previewError.textContent = 'Gagal memuat gambar. File tetap dapat langsung diunduh.';
                            previewError.style.display = 'block';
                        }
                    };
                    imagePreview.src = streamUrl;
                }
            }
        }


        btnPreviewToggle?.addEventListener('click', () => {
            if (previewBox.style.display === 'none' || !previewBox.style.display) {
                loadPreviewStream();
            } else {
                previewBox.style.display = 'none';
                if (previewError) previewError.style.display = 'none';
                if (videoPreview) { videoPreview.pause(); videoPreview.src = ''; }
                if (audioPreview) { audioPreview.pause(); audioPreview.src = ''; }
                if (imagePreview) { imagePreview.src = ''; }
            }
        });

        btnDownload.addEventListener('click', async () => {
            if (!selectedDownloadOption || !extractedMediaData) return;
            btnDownload.disabled = true;
            btnDownload.innerHTML = `<span style="display:inline-block; width:14px; height:14px; border:2px solid #fff; border-top-color:transparent; border-radius:50%; animation:spin 0.8s linear infinite; margin-right:8px; vertical-align:middle;"></span> ${i18n[currentLang].status_downloading || 'Mengunduh...'}`;
            progressBox.style.display = 'block';
            progressBar.style.width = '0%';
            progressPct.textContent = '0%';
            postActions.style.display = 'none';

            const safeTitle = (extractedMediaData.title || 'media').substring(0, 30).replace(/[^a-zA-Z0-9_-]/g, '_');
            const filename = `HDify_${extractedMediaData.platform}_${safeTitle}_${Date.now()}`;

            try {
                const res = await window.hdifyApi.downloadMedia({
                    url: selectedDownloadOption.url,
                    audioUrl: selectedDownloadOption.audioUrl || null,
                    filename: filename,
                    format: selectedDownloadOption.format,
                    quality: selectedDownloadOption.quality
                });

                if (res && res.success) {
                    lastDownloadedFilePath = res.outputPath;
                    progressBar.style.width = '100%';
                    progressBar.style.background = '#22c55e';
                    progressBar.style.boxShadow = '0 0 14px rgba(34, 197, 94, 0.45)';
                    progressPct.innerHTML = '<span style="color: #4ade80; font-size: 13px; font-weight: 700;">100% Selesai</span>';
                    const statusText = document.getElementById('downloader-progress-status');
                    if (statusText) statusText.innerHTML = '<span style="color: #4ade80;">File Berhasil Diunduh!</span>';
                    postActions.style.display = 'flex';

                    addQueueTask({
                        id: 'dl_' + Date.now(),
                        type: 'Media Downloader',
                        title: filename,
                        status: i18n[currentLang].status_downloaded || 'Terunduh',
                        timestamp: Date.now(),
                        outputPath: res.outputPath
                    });
                }
            } catch (err) {
                alert((i18n[currentLang].alert_download_failed || 'Gagal mengunduh file: ') + err.message);
                progressBox.style.display = 'none';
            } finally {
                btnDownload.disabled = false;
                btnDownload.textContent = i18n[currentLang].btn_download_file || 'Unduh File';
            }
        });

        // Post Download quick routing
        document.getElementById('btn-dl-open-file')?.addEventListener('click', () => {
            if (lastDownloadedFilePath) window.hdifyApi.showItemInFolder(lastDownloadedFilePath);
        });

        document.getElementById('btn-dl-send-upscale')?.addEventListener('click', () => {
            if (!lastDownloadedFilePath) return;
            const isVideo = lastDownloadedFilePath.endsWith('.mp4') || lastDownloadedFilePath.endsWith('.mkv') || lastDownloadedFilePath.endsWith('.mov') || lastDownloadedFilePath.endsWith('.webm');

            if (isVideo) {
                window.switchView('view-video');
                window.loadVideoFile(lastDownloadedFilePath);
            } else {
                window.switchView('view-image');
                window.loadImagePath(lastDownloadedFilePath);
            }
        });

        document.getElementById('btn-dl-send-compress')?.addEventListener('click', () => {
            if (!lastDownloadedFilePath) return;
            window.switchView('view-compressor');
            window.loadCompressFile(lastDownloadedFilePath);
        });
    }

    // ==========================================
    // 10. VIEW: HARDWARE & VULKAN BENCHMARK
    // ==========================================
    async function loadHardwareInfo() {
        const list = document.getElementById('hardware-details-list');
        if (!list) return;

        try {
            const info = await window.hdifyApi.getHardwareInfo();
            const gpuNames = info.gpus.map(g => g.name).join(', ') || 'Vulkan GPU Device';

            list.innerHTML = `
                <div class="card" style="padding: 14px; margin-bottom: 0;">
                    <div style="font-size: 11px; color: var(--text-secondary);">${i18n[currentLang].hw_gpu}</div>
                    <div style="font-size: 14px; font-weight: 700; color: #ffffff; margin-top: 4px;">${escapeHtml(gpuNames)}</div>
                </div>
                <div class="card" style="padding: 14px; margin-bottom: 0;">
                    <div style="font-size: 11px; color: var(--text-secondary);">${i18n[currentLang].hw_cpu}</div>
                    <div style="font-size: 14px; font-weight: 700; color: #ffffff; margin-top: 4px;">${escapeHtml(info.cpu)} (${info.cores} Cores)</div>
                </div>
                <div class="card" style="padding: 14px; margin-bottom: 0;">
                    <div style="font-size: 11px; color: var(--text-secondary);">${i18n[currentLang].hw_ram}</div>
                    <div style="font-size: 14px; font-weight: 700; color: #ffffff; margin-top: 4px;">${info.totalMemoryGb} GB DDR</div>
                </div>
                <div class="card" style="padding: 14px; margin-bottom: 0;">
                    <div style="font-size: 11px; color: var(--text-secondary);">${i18n[currentLang].hw_os}</div>
                    <div style="font-size: 14px; font-weight: 700; color: #ffffff; margin-top: 4px;">${escapeHtml(info.os)}</div>
                </div>
            `;
        } catch (e) {
            console.error('Failed to load hardware info:', e);
        }
    }

    function initBenchmark() {
        const btnRun = document.getElementById('btn-run-benchmark');
        const resultBox = document.getElementById('benchmark-result-box');
        const valMs = document.getElementById('bench-val-ms');
        const val1080p = document.getElementById('bench-val-1080p');
        const valFps = document.getElementById('bench-val-fps');

        btnRun?.addEventListener('click', async () => {
            btnRun.disabled = true;
            btnRun.textContent = i18n[currentLang].btn_running_bench;

            try {
                const res = await window.hdifyApi.runBenchmark();
                if (res && res.passed) {
                    resultBox.style.display = 'block';
                    valMs.textContent = `${res.msPerTile} ms`;
                    val1080p.textContent = `${res.est1080pSec} ${i18n[currentLang].unit_seconds}`;
                    valFps.textContent = `${res.fps720p} FPS`;
                } else {
                    alert(`${i18n[currentLang].alert_bench_failed}${res.errorMessage || 'Unknown error'}`);
                }
            } catch (err) {
                alert(i18n[currentLang].alert_bench_failed + err.message);
            } finally {
                btnRun.disabled = false;
                btnRun.textContent = i18n[currentLang].btn_run_benchmark;
            }
        });
    }

    function formatEtaSeconds(sec) {
        if (!sec || sec <= 0) return '';
        if (sec < 60) return `${sec} dtk`;
        const mins = Math.floor(sec / 60);
        const s = sec % 60;
        if (mins < 60) return `${mins}m ${s}d`;
        const hrs = Math.floor(mins / 60);
        const remMins = mins % 60;
        return `${hrs}j ${remMins}m`;
    }

    // ==========================================
    // 11. GLOBAL PROGRESS LISTENER
    // ==========================================
    function initGlobalProgressListener() {
        window.hdifyApi.onProgress((data) => {
            if (!data) return;
            const pct = Math.max(0, Math.min(100, Math.round(data.percent || 0)));

            if (data.type === 'image-upscale') {
                const bar = document.getElementById('image-progress-bar');
                const txt = document.getElementById('image-progress-pct');
                const status = document.getElementById('image-progress-status');
                if (bar) bar.style.width = pct + '%';
                if (txt) txt.textContent = pct + '%';

                if (status) {
                    if (data.stage === 'preparing' || (pct === 0 && !data.current)) {
                        status.textContent = i18n[currentLang]?.status_preparing_ai || 'Mempersiapkan engine AI...';
                    } else if (data.stage === 'processing' || data.current !== undefined) {
                        const cur = data.current || Math.max(1, Math.round((pct / 100) * (data.total || 1)));
                        const tot = data.total || 1;
                        const template = i18n[currentLang]?.status_processing_tile || 'Memproses tile {current}/{total} ({percent}%)';
                        status.textContent = template
                            .replace('{current}', cur)
                            .replace('{total}', tot)
                            .replace('{percent}', pct);
                    } else if (data.stage === 'completed' || pct >= 100) {
                        status.innerHTML = '<span style="color: #4ade80; font-weight: 600;">✓ Selesai</span>';
                    }
                }
            } else if (data.type === 'video-upscale') {
                const bar = document.getElementById('video-progress-bar');
                const txt = document.getElementById('video-progress-pct');
                const details = document.getElementById('video-progress-details');
                const status = document.getElementById('video-progress-status');
                if (bar) bar.style.width = pct + '%';
                if (txt) txt.textContent = pct + '%';
                if (status && (data.status || data.stage) && !data.completed) {
                    status.textContent = data.stage || data.status;
                }
                if (details && !data.completed) {
                    const framePart = (data.currentFrame !== undefined && data.totalFrames) ? `Frame ${data.currentFrame} / ${data.totalFrames}` : '';
                    const pctPart = `(${pct}%)`;
                    const fpsPart = data.fps ? `${data.fps} FPS` : '';
                    const speedPart = data.speed ? `${data.speed}` : '';
                    const etaPart = (data.etaSec !== undefined && data.etaSec > 0) ? `Sisa ${formatEtaSeconds(data.etaSec)}` : '';
                    const parts = [framePart, pctPart, fpsPart, speedPart, etaPart].filter(Boolean);
                    details.textContent = parts.length > 0 ? parts.join(' • ') : `Memproses video... (${pct}%)`;
                }
            } else if (data.type === 'video-compress') {
                const bar = document.getElementById('compress-progress-bar');
                const txt = document.getElementById('compress-progress-pct');
                const details = document.getElementById('compress-progress-details');
                const status = document.getElementById('compress-progress-status');
                if (bar) bar.style.width = pct + '%';
                if (txt) txt.textContent = pct + '%';
                if (status && (data.status || data.stage) && !data.completed) {
                    status.textContent = data.stage || data.status;
                }
                if (details && !data.completed) {
                    const framePart = (data.currentFrame !== undefined && data.totalFrames) ? `Frame ${data.currentFrame} / ${data.totalFrames}` : '';
                    const pctPart = `(${pct}%)`;
                    const fpsPart = data.fps ? `${data.fps} FPS` : '';
                    const speedPart = data.speed ? `${data.speed}` : '';
                    const etaPart = (data.etaSec !== undefined && data.etaSec > 0) ? `Sisa ${formatEtaSeconds(data.etaSec)}` : '';
                    const parts = [framePart, pctPart, fpsPart, speedPart, etaPart].filter(Boolean);
                    details.textContent = parts.length > 0 ? parts.join(' • ') : `Mengompres video... (${pct}%)`;
                }
            } else if (data.type === 'media-download') {
                const bar = document.getElementById('downloader-progress-bar');
                const txt = document.getElementById('downloader-progress-pct');
                const status = document.getElementById('downloader-progress-status');
                if (bar) {
                    bar.style.width = pct + '%';
                    bar.style.background = '';
                    bar.style.boxShadow = '';
                }
                if (txt) {
                    if (data.downloadedBytes && data.totalBytes && data.totalBytes > 0) {
                        txt.textContent = `${pct}% (${formatBytes(data.downloadedBytes)} / ${formatBytes(data.totalBytes)})`;
                    } else if (data.downloadedBytes) {
                        txt.textContent = `${formatBytes(data.downloadedBytes)}`;
                    } else {
                        txt.textContent = pct + '%';
                    }
                }
                if (status && data.stage) {
                    status.textContent = data.stage;
                }
            }
        });
    }

    // ==========================================
    // 12. UTILITY HELPERS
    // ==========================================
    function escapeHtml(str) {
        if (!str) return '';
        return String(str).replace(/[&<>"']/g, function (m) {
            return {
                '&': '&amp;',
                '<': '&lt;',
                '>': '&gt;',
                '"': '&quot;',
                "'": '&#039;'
            }[m];
        });
    }

    function escapePath(str) {
        if (!str) return '';
        return str.replace(/\\/g, '\\\\');
    }

    function formatBytes(bytes) {
        if (!bytes || bytes <= 0) return '0 B';
        const k = 1024;
        const sizes = ['B', 'KB', 'MB', 'GB', 'TB'];
        const i = Math.floor(Math.log(bytes) / Math.log(k));
        return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
    }

    // ==========================================
    // 13. INITIALIZE APP
    // ==========================================
    document.addEventListener('DOMContentLoaded', () => {
        initNavigation();
        initSettings();
        initImageUpscaler();
        initVideoUpscaler();
        initBackgroundRemover();
        initVideoCompressor();
        initMediaDownloader();
        initBenchmark();
        initGlobalProgressListener();
        setLanguage(currentLang);
        renderQueue();
    });

})();
