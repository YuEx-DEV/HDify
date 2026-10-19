// HDify Real-ESRGAN engine wrapper based on xinntao/Real-ESRGAN-ncnn-vulkan
#ifndef REALESRGAN_H
#define REALESRGAN_H

#include <string>
#include <vector>
#include <atomic>
#include <functional>
#include <android/asset_manager.h>

#include <ncnn/net.h>
#include <ncnn/gpu.h>
#include <ncnn/layer.h>

class RealESRGAN
{
public:
    RealESRGAN(int gpuid, bool tta_mode = false, int num_threads = 1);
    ~RealESRGAN();

    int load(AAssetManager* mgr, const std::string& parampath, const std::string& modelpath);
    int load(const std::string& parampath, const std::string& modelpath);

    int process(const ncnn::Mat& inimage, ncnn::Mat& outimage) const;
    int process_cpu(const ncnn::Mat& inimage, ncnn::Mat& outimage) const;

    void set_cancel_flag(const std::atomic<bool>* flag);
    void set_progress_callback(std::function<void(int current, int total)> cb);

public:
    int scale;          // Model native scale (2, 3, or 4)
    int target_scale;   // User requested scale (2, 3, 4). If < scale, downscale
    int tilesize;       // Tile size (0 = auto-tuned)
    int prepadding;     // Padding for border continuity (default 10)
    bool use_fp16;      // Enable FP16 compute if supported

private:
    int init_pipelines();
    bool is_canceled() const;
    void report_progress(int current, int total) const;

    ncnn::Net net;
    const ncnn::VulkanDevice* vkdev;
    ncnn::Pipeline* realesrgan_preproc;
    ncnn::Pipeline* realesrgan_postproc;
    ncnn::Layer* bicubic_2x;
    ncnn::Layer* bicubic_3x;
    ncnn::Layer* bicubic_4x;

    bool tta_mode;
    int gpuid;
    const std::atomic<bool>* cancel_flag;
    std::function<void(int current, int total)> progress_callback;
    std::vector<unsigned char> model_bin_buf;
};

#endif // REALESRGAN_H
