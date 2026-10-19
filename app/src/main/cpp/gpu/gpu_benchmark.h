#ifndef GPU_BENCHMARK_H
#define GPU_BENCHMARK_H

#include <string>
#include <android/asset_manager.h>

namespace hdify {

struct BenchmarkResult {
    bool passed;
    std::string engine_name;
    int scale;
    long long ms_per_tile;
    double est_1080p_sec;
    double est_720p_video_sec;
    double fps_720p;
    std::string error_message;
};

/**
 * Runs standardized GPU inference benchmark on a 256x256 tile (FR-BM-01).
 * @param mgr Asset manager for loading model weights.
 * @param engine_type 0 for Real-CUGAN, 1 for Real-ESRGAN.
 * @param scale Desired upscale factor (2, 3, or 4).
 * @param gpuid Vulkan GPU device index (0 for default).
 */
BenchmarkResult run_gpu_benchmark(AAssetManager* mgr, int engine_type, int scale, int gpuid = 0);

} // namespace hdify

#endif // GPU_BENCHMARK_H
