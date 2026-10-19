// HDify Real-CUGAN engine wrapper based on nihui/realcugan-ncnn-vulkan
#ifndef REALCUGAN_H
#define REALCUGAN_H

#include <string>
#include <vector>
#include <atomic>
#include <functional>
#include <android/asset_manager.h>

// ncnn
#include <ncnn/net.h>
#include <ncnn/gpu.h>
#include <ncnn/layer.h>

class FeatureCache;

class RealCUGAN
{
public:
    RealCUGAN(int gpuid, bool tta_mode = false, int num_threads = 1);
    ~RealCUGAN();

    int load(AAssetManager* mgr, const std::string& parampath, const std::string& modelpath);
    int load(const std::string& parampath, const std::string& modelpath);

    int process(const ncnn::Mat& inimage, ncnn::Mat& outimage) const;
    int process_cpu(const ncnn::Mat& inimage, ncnn::Mat& outimage) const;

    int process_se(const ncnn::Mat& inimage, ncnn::Mat& outimage) const;
    int process_cpu_se(const ncnn::Mat& inimage, ncnn::Mat& outimage) const;

    int process_se_rough(const ncnn::Mat& inimage, ncnn::Mat& outimage) const;
    int process_cpu_se_rough(const ncnn::Mat& inimage, ncnn::Mat& outimage) const;

    int process_se_very_rough(const ncnn::Mat& inimage, ncnn::Mat& outimage) const;
    int process_cpu_se_very_rough(const ncnn::Mat& inimage, ncnn::Mat& outimage) const;

    void set_cancel_flag(const std::atomic<bool>* flag);
    void set_progress_callback(std::function<void(int current, int total)> cb);

protected:
    int init_pipelines();

    int process_se_stage0(const ncnn::Mat& inimage, const std::vector<std::string>& names, const std::vector<std::string>& outnames, const ncnn::Option& opt, FeatureCache& cache) const;
    int process_se_stage2(const ncnn::Mat& inimage, const std::vector<std::string>& names, ncnn::Mat& outimage, const ncnn::Option& opt, FeatureCache& cache) const;
    int process_se_sync_gap(const ncnn::Mat& inimage, const std::vector<std::string>& names, const ncnn::Option& opt, FeatureCache& cache) const;

    int process_se_very_rough_stage0(const ncnn::Mat& inimage, const std::vector<std::string>& names, const std::vector<std::string>& outnames, const ncnn::Option& opt, FeatureCache& cache) const;
    int process_se_very_rough_sync_gap(const ncnn::Mat& inimage, const std::vector<std::string>& names, const ncnn::Option& opt, FeatureCache& cache) const;

    int process_cpu_se_stage0(const ncnn::Mat& inimage, const std::vector<std::string>& names, const std::vector<std::string>& outnames, FeatureCache& cache) const;
    int process_cpu_se_stage2(const ncnn::Mat& inimage, const std::vector<std::string>& names, ncnn::Mat& outimage, FeatureCache& cache) const;
    int process_cpu_se_sync_gap(const ncnn::Mat& inimage, const std::vector<std::string>& names, FeatureCache& cache) const;

    int process_cpu_se_very_rough_stage0(const ncnn::Mat& inimage, const std::vector<std::string>& names, const std::vector<std::string>& outnames, FeatureCache& cache) const;
    int process_cpu_se_very_rough_sync_gap(const ncnn::Mat& inimage, const std::vector<std::string>& names, FeatureCache& cache) const;

    bool is_canceled() const;
    void report_progress(int current, int total) const;

public:
    // realcugan parameters
    int noise;
    int scale;
    int tilesize;
    int prepadding;
    int syncgap;
    bool use_fp16;

private:
    ncnn::VulkanDevice* vkdev;
    ncnn::Net net;
    ncnn::Pipeline* realcugan_preproc;
    ncnn::Pipeline* realcugan_postproc;
    ncnn::Pipeline* realcugan_4x_postproc;
    ncnn::Layer* bicubic_2x;
    ncnn::Layer* bicubic_3x;
    ncnn::Layer* bicubic_4x;
    bool tta_mode;

    const std::atomic<bool>* cancel_flag;
    std::function<void(int, int)> progress_callback;
    std::vector<unsigned char> model_bin_buf;
};

#endif // REALCUGAN_H
