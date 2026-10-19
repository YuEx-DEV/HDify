#include "gpu_selftest.h"
#include "gpu_info.h"
#include "../engines/realcugan/realcugan.h"
#include "../log/native_log.h"
#include <chrono>
#include <cmath>
#include <vector>

namespace hdify {

static bool validate_output(const ncnn::Mat& out, int expected_w, int expected_h) {
    if (out.empty() || out.w != expected_w || out.h != expected_h) {
        return false;
    }
    const unsigned char* ptr = (const unsigned char*)out.data;
    size_t total_bytes = (size_t)out.w * out.h * out.elempack;
    bool has_nonzero = false;
    for (size_t i = 0; i < total_bytes; i++) {
        if (ptr[i] > 0) {
            has_nonzero = true;
            break;
        }
    }
    return has_nonzero;
}

SelfTestResult run_gpu_self_test(AAssetManager* mgr, int gpuid) {
    SelfTestResult res{};
    res.passed = false;
    res.fp16_passed = false;
    res.fp32_fallback_used = false;
    res.duration_ms = 0;

    auto start_time = std::chrono::steady_clock::now();

    ensure_vulkan_initialized();

    int count = ncnn::get_gpu_count();
    if (count <= 0 || gpuid < 0 || gpuid >= count) {
        res.error_code = "HD-HW-101";
        res.message = "Vulkan GPU device not found or unavailable";
        HD_LOGE("GPU", "stage=SELF_TEST", "HD-HW-101 Vulkan GPU device not found or count is 0");
        return res;
    }

    ncnn::VulkanDevice* vkdev = ncnn::get_gpu_device(gpuid);
    if (!vkdev) {
        res.error_code = "HD-HW-101";
        res.message = "Vulkan GPU device not found or unavailable";
        HD_LOGE("GPU", "stage=SELF_TEST", "HD-HW-101 Vulkan GPU device not found");
        return res;
    }

    res.device_name = safe_str(vkdev->info.device_name(), "Vulkan GPU");

    // Create 32x32 test pattern (RGBA)
    const int test_w = 32;
    const int test_h = 32;
    std::vector<unsigned char> test_pixels(test_w * test_h * 4);
    for (int y = 0; y < test_h; y++) {
        for (int x = 0; x < test_w; x++) {
            int idx = (y * test_w + x) * 4;
            test_pixels[idx + 0] = static_cast<unsigned char>((x * 255) / test_w);
            test_pixels[idx + 1] = static_cast<unsigned char>((y * 255) / test_h);
            test_pixels[idx + 2] = static_cast<unsigned char>(((x + y) * 255) / (test_w + test_h));
            test_pixels[idx + 3] = 255;
        }
    }

    ncnn::Mat in_mat = ncnn::Mat(test_w, test_h, test_pixels.data(), (size_t)4u, 1);

    const std::string param_path = "models/realcugan/models-se/up2x-conservative.param";
    const std::string bin_path = "models/realcugan/models-se/up2x-conservative.bin";

    // Step 1: Attempt FP16 test
    HD_LOGI("GPU", "stage=SELF_TEST", "Starting GPU self-test on %s (testing FP16)", res.device_name.c_str());

    bool fp16_ok = false;
    {
        RealCUGAN cugan(gpuid, false, 1);
        cugan.scale = 2;
        cugan.noise = -1;
        cugan.tilesize = 32;
        cugan.prepadding = 18;
        cugan.syncgap = 0;
        cugan.use_fp16 = true;

        if (cugan.load(mgr, param_path, bin_path) == 0) {
            ncnn::Mat out_mat;
            out_mat.create(test_w * 2, test_h * 2, (size_t)4u, 1);
            int ret = cugan.process(in_mat, out_mat);
            if (ret == 0 && validate_output(out_mat, test_w * 2, test_h * 2)) {
                fp16_ok = true;
            }
        }
    }

    if (fp16_ok) {
        res.passed = true;
        res.fp16_passed = true;
        res.fp32_fallback_used = false;
        res.message = "GPU self-test passed with FP16 precision";
        HD_LOGI("GPU", "stage=SELF_TEST", "GPU self-test passed on %s (FP16 active)", res.device_name.c_str());
    } else {
        HD_LOGW("GPU", "stage=SELF_TEST", "GPU self-test FP16 failed on %s, attempting FP32 fallback...", res.device_name.c_str());

        // Step 2: Attempt FP32 fallback
        RealCUGAN cugan_fp32(gpuid, false, 1);
        cugan_fp32.scale = 2;
        cugan_fp32.noise = -1;
        cugan_fp32.tilesize = 32;
        cugan_fp32.prepadding = 18;
        cugan_fp32.syncgap = 0;
        cugan_fp32.use_fp16 = false; // FP32

        if (cugan_fp32.load(mgr, param_path, bin_path) == 0) {
            ncnn::Mat out_mat;
            out_mat.create(test_w * 2, test_h * 2, (size_t)4u, 1);
            int ret = cugan_fp32.process(in_mat, out_mat);
            if (ret == 0 && validate_output(out_mat, test_w * 2, test_h * 2)) {
                res.passed = true;
                res.fp16_passed = false;
                res.fp32_fallback_used = true;
                res.message = "GPU self-test passed using FP32 fallback (FP16 unsupported/unstable)";
                HD_LOGI("GPU", "stage=SELF_TEST", "GPU self-test passed on %s with FP32 fallback", res.device_name.c_str());
            }
        }

        if (!res.passed) {
            res.error_code = "HD-HW-105";
            res.message = "GPU self-test failed on both FP16 and FP32";
            HD_LOGE("GPU", "stage=SELF_TEST", "HD-HW-105 GPU self-test failed on %s", res.device_name.c_str());
        }
    }

    auto end_time = std::chrono::steady_clock::now();
    res.duration_ms = std::chrono::duration_cast<std::chrono::milliseconds>(end_time - start_time).count();
    return res;
}

} // namespace hdify
