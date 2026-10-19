#include "gpu_benchmark.h"
#include "gpu_info.h"
#include "../engines/realcugan/realcugan.h"
#include "../engines/realesrgan/realesrgan.h"
#include "../log/native_log.h"
#include <chrono>
#include <vector>

namespace hdify {

BenchmarkResult run_gpu_benchmark(AAssetManager* mgr, int engine_type, int scale, int gpuid) {
    BenchmarkResult res{};
    res.passed = false;
    res.scale = scale;
    res.ms_per_tile = 0;
    res.est_1080p_sec = 0.0;
    res.est_720p_video_sec = 0.0;
    res.fps_720p = 0.0;

    ensure_vulkan_initialized();

    int count = ncnn::get_gpu_count();
    if (count <= 0 || gpuid < 0 || gpuid >= count) {
        res.error_message = "Vulkan GPU device not found or unavailable";
        HD_LOGE("Benchmark", "stage=INIT", "HD-HW-101 Vulkan GPU device unavailable or count is 0");
        return res;
    }

    ncnn::VulkanDevice* vkdev = ncnn::get_gpu_device(gpuid);
    if (!vkdev) {
        res.error_message = "Vulkan GPU device not found or unavailable";
        HD_LOGE("Benchmark", "stage=INIT", "HD-HW-101 Vulkan GPU device unavailable");
        return res;
    }

    const int tile_w = 256;
    const int tile_h = 256;

    // Create test 256x256 RGBA pattern
    std::vector<unsigned char> test_pixels(tile_w * tile_h * 4);
    for (int y = 0; y < tile_h; y++) {
        for (int x = 0; x < tile_w; x++) {
            int idx = (y * tile_w + x) * 4;
            test_pixels[idx + 0] = static_cast<unsigned char>((x * 255) / tile_w);
            test_pixels[idx + 1] = static_cast<unsigned char>((y * 255) / tile_h);
            test_pixels[idx + 2] = static_cast<unsigned char>(((x + y) * 255) / (tile_w + tile_h));
            test_pixels[idx + 3] = 255;
        }
    }
    ncnn::Mat in_mat = ncnn::Mat(tile_w, tile_h, test_pixels.data(), (size_t)4u, 1);

    long long elapsed_ms = 0;

    if (engine_type == 0) {
        // Real-CUGAN
        res.engine_name = "Real-CUGAN (models-se)";
        RealCUGAN cugan(gpuid, false, 1);
        cugan.scale = scale;
        cugan.noise = 0;
        cugan.tilesize = tile_w;
        cugan.prepadding = 18;
        cugan.syncgap = 0;
        cugan.use_fp16 = true;

        std::string param_path = "models/realcugan/models-se/up" + std::to_string(scale) + "x-no-denoise.param";
        std::string bin_path = "models/realcugan/models-se/up" + std::to_string(scale) + "x-no-denoise.bin";

        if (cugan.load(mgr, param_path, bin_path) != 0) {
            res.error_message = "Failed to load Real-CUGAN model: " + param_path;
            HD_LOGE("Benchmark", "stage=LOAD", "%s", res.error_message.c_str());
            return res;
        }

        ncnn::Mat out_mat;
        out_mat.create(tile_w * scale, tile_h * scale, (size_t)4u, 1);

        // Timed run
        auto start = std::chrono::steady_clock::now();
        int ret = cugan.process(in_mat, out_mat);
        auto end = std::chrono::steady_clock::now();

        if (ret != 0) {
            res.error_message = "Real-CUGAN benchmark inference failed";
            HD_LOGE("Benchmark", "stage=INFERENCE", "%s", res.error_message.c_str());
            return res;
        }

        elapsed_ms = std::chrono::duration_cast<std::chrono::milliseconds>(end - start).count();
    } else {
        // Real-ESRGAN
        res.engine_name = "Real-ESRGAN (x4plus)";
        RealESRGAN esrgan(gpuid, false, 1);
        esrgan.target_scale = scale;
        esrgan.tilesize = tile_w;
        esrgan.prepadding = 10;

        std::string param_path = "models/realesrgan/realesrgan-x4plus.param";
        std::string bin_path = "models/realesrgan/realesrgan-x4plus.bin";

        if (esrgan.load(mgr, param_path, bin_path) != 0) {
            res.error_message = "Failed to load Real-ESRGAN model: " + param_path;
            HD_LOGE("Benchmark", "stage=LOAD", "%s", res.error_message.c_str());
            return res;
        }

        ncnn::Mat out_mat;
        out_mat.create(tile_w * scale, tile_h * scale, (size_t)4u, 1);

        // Timed run
        auto start = std::chrono::steady_clock::now();
        int ret = esrgan.process(in_mat, out_mat);
        auto end = std::chrono::steady_clock::now();

        if (ret != 0) {
            res.error_message = "Real-ESRGAN benchmark inference failed";
            HD_LOGE("Benchmark", "stage=INFERENCE", "%s", res.error_message.c_str());
            return res;
        }

        elapsed_ms = std::chrono::duration_cast<std::chrono::milliseconds>(end - start).count();
    }

    res.passed = true;
    res.ms_per_tile = elapsed_ms > 0 ? elapsed_ms : 1;

    // Extrapolations:
    // 1080p photo (1920x1080) with 256x256 tile size = 8 * 5 = 40 tiles
    res.est_1080p_sec = (40.0 * res.ms_per_tile) / 1000.0;

    // 720p video (1280x720) with 256x256 tile size = 5 * 3 = 15 tiles per frame
    double sec_per_720p_frame = (15.0 * res.ms_per_tile) / 1000.0;
    res.fps_720p = sec_per_720p_frame > 0.0 ? (1.0 / sec_per_720p_frame) : 0.0;
    // 1 minute of 30 fps video = 1800 frames
    res.est_720p_video_sec = 1800.0 * sec_per_720p_frame;

    HD_LOGI("Benchmark", "stage=COMPLETE", "Engine=%s scale=%d ms_per_tile=%lld est_1080p=%.2fs fps_720p=%.2f",
            res.engine_name.c_str(), scale, res.ms_per_tile, res.est_1080p_sec, res.fps_720p);

    return res;
}

} // namespace hdify
