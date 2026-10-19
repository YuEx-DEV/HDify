// rife implemented with ncnn library

#include "rife_ops.h"

#include "warp.comp.hex.h"
#include "warp_pack4.comp.hex.h"
#include "warp_pack8.comp.hex.h"

using namespace ncnn;

Warp::Warp()
{
    support_vulkan = true;
    // Only the pack1 layout is guaranteed to compile on modern ncnn; ncnn will
    // feed elempack=1 blobs to this layer as long as vulkan packing stays off.
    support_vulkan_packing = false;

    pipeline_warp = 0;
    pipeline_warp_pack4 = 0;
    pipeline_warp_pack8 = 0;
}

// Compiles a GLSL compute shader once (cached) and creates the pipeline.
// Returns nullptr instead of crashing when the shader cannot be compiled.
static Pipeline* create_warp_pipeline(const VulkanDevice* vkdev, const char* comp_data, int comp_data_size,
                                      std::vector<uint32_t>& spirv, ncnn::Mutex& lock, const Option& opt)
{
    {
        ncnn::MutexLockGuard guard(lock);
        if (spirv.empty())
        {
            int ret = compile_spirv_module(comp_data, comp_data_size, opt, spirv);
            if (ret != 0)
                spirv.clear();
        }
    }

    if (spirv.empty())
        return 0;

    std::vector<vk_specialization_type> specializations(0);

    Pipeline* pipeline = new Pipeline(vkdev);
    pipeline->set_optimal_local_size_xyz();
    if (pipeline->create(spirv.data(), spirv.size() * 4, specializations) != 0)
    {
        delete pipeline;
        return 0;
    }
    return pipeline;
}

int Warp::create_pipeline(const Option& opt)
{
    if (!vkdev)
        return 0;

    // pack1 (required)
    {
        static std::vector<uint32_t> spirv;
        static ncnn::Mutex lock;
        pipeline_warp = create_warp_pipeline(vkdev, warp_comp_data, sizeof(warp_comp_data), spirv, lock, opt);
        if (!pipeline_warp)
        {
            NCNN_LOGE("Warp: failed to create pack1 pipeline");
            return -1;
        }
    }

    // pack4 (optional, only used if ncnn ever hands us packed blobs)
    {
        static std::vector<uint32_t> spirv;
        static ncnn::Mutex lock;
        pipeline_warp_pack4 = create_warp_pipeline(vkdev, warp_pack4_comp_data, sizeof(warp_pack4_comp_data), spirv, lock, opt);
    }

    // pack8 is intentionally not created: ncnn >= 2025 removed Vulkan pack8
    // layouts, and compiling warp_pack8 produces an empty SPIR-V module.
    pipeline_warp_pack8 = 0;

    return 0;
}

int Warp::destroy_pipeline(const Option& opt)
{
    delete pipeline_warp;
    pipeline_warp = 0;

    delete pipeline_warp_pack4;
    pipeline_warp_pack4 = 0;

    delete pipeline_warp_pack8;
    pipeline_warp_pack8 = 0;

    return 0;
}

int Warp::forward(const std::vector<Mat>& bottom_blobs, std::vector<Mat>& top_blobs, const Option& opt) const
{
    const Mat& image_blob = bottom_blobs[0];
    const Mat& flow_blob = bottom_blobs[1];

    int w = image_blob.w;
    int h = image_blob.h;
    int channels = image_blob.c;

    Mat& top_blob = top_blobs[0];
    top_blob.create(w, h, channels);
    if (top_blob.empty())
        return -100;

    #pragma omp parallel for num_threads(opt.num_threads)
    for (int q = 0; q < channels; q++)
    {
        float* outptr = top_blob.channel(q);

        const Mat image = image_blob.channel(q);

        const float* fxptr = flow_blob.channel(0);
        const float* fyptr = flow_blob.channel(1);

        for (int y = 0; y < h; y++)
        {
            for (int x = 0; x < w; x++)
            {
                float flow_x = fxptr[0];
                float flow_y = fyptr[0];

                float sample_x = x + flow_x;
                float sample_y = y + flow_y;

                // bilinear interpolate
                float v;
                {
                    int x0 = floor(sample_x);
                    int y0 = floor(sample_y);
                    int x1 = x0 + 1;
                    int y1 = y0 + 1;

                    x0 = std::min(std::max(x0, 0), w - 1);
                    y0 = std::min(std::max(y0, 0), h - 1);
                    x1 = std::min(std::max(x1, 0), w - 1);
                    y1 = std::min(std::max(y1, 0), h - 1);

                    float alpha = sample_x - x0;
                    float beta = sample_y - y0;

                    float v0 = image.row(y0)[x0];
                    float v1 = image.row(y0)[x1];
                    float v2 = image.row(y1)[x0];
                    float v3 = image.row(y1)[x1];

                    float v4 = v0 * (1 - alpha) + v1 * alpha;
                    float v5 = v2 * (1 - alpha) + v3 * alpha;

                    v = v4 * (1 - beta) + v5 * beta;
                }

                outptr[0] = v;

                outptr += 1;

                fxptr += 1;
                fyptr += 1;
            }
        }
    }

    return 0;
}

int Warp::forward(const std::vector<VkMat>& bottom_blobs, std::vector<VkMat>& top_blobs, VkCompute& cmd, const Option& opt) const
{
    const VkMat& image_blob = bottom_blobs[0];
    const VkMat& flow_blob = bottom_blobs[1];

    int w = image_blob.w;
    int h = image_blob.h;
    int channels = image_blob.c;
    size_t elemsize = image_blob.elemsize;
    int elempack = image_blob.elempack;

    VkMat& top_blob = top_blobs[0];
    top_blob.create(w, h, channels, elemsize, elempack, opt.blob_vkallocator);
    if (top_blob.empty())
        return -100;

    std::vector<VkMat> bindings(3);
    bindings[0] = image_blob;
    bindings[1] = flow_blob;
    bindings[2] = top_blob;

    std::vector<vk_constant_type> constants(4);
    constants[0].i = top_blob.w;
    constants[1].i = top_blob.h;
    constants[2].i = top_blob.c;
    constants[3].i = top_blob.cstep;

    const Pipeline* pipeline = pipeline_warp;
    if (elempack == 8)
        pipeline = pipeline_warp_pack8;
    else if (elempack == 4)
        pipeline = pipeline_warp_pack4;

    if (!pipeline)
    {
        NCNN_LOGE("Warp: no pipeline for elempack=%d", elempack);
        return -1;
    }

    cmd.record_pipeline(pipeline, bindings, constants, top_blob);

    return 0;
}
