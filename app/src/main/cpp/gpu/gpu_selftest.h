#ifndef GPU_SELFTEST_H
#define GPU_SELFTEST_H

#include <string>
#include <android/asset_manager.h>

namespace hdify {

struct SelfTestResult {
    bool passed;
    bool fp16_passed;
    bool fp32_fallback_used;
    std::string device_name;
    std::string message;
    std::string error_code;
    long long duration_ms;
};

/**
 * Runs GPU self-test by executing a small tile inference on GPU (FR-HW-05).
 * Tests FP16 first; if FP16 fails or produces corrupted pixels, tests FP32 fallback.
 */
SelfTestResult run_gpu_self_test(AAssetManager* mgr, int gpuid = 0);

} // namespace hdify

#endif // GPU_SELFTEST_H
