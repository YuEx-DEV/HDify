// HDify Real-ESRGAN engine implementation
#include "realesrgan.h"
#include "tile_planner.h"
#include "../../log/native_log.h"
#include "../../log/breadcrumbs.h"

#include <algorithm>
#include <vector>
#include <cmath>
#include <cstring>

#if NCNN_VULKAN
#include <ncnn/command.h>
#endif

// SPIR-V shader sources embedded from GLSL compute shaders
#include "realesrgan_preproc.comp.hex.h"
#include "realesrgan_postproc.comp.hex.h"
#include "realesrgan_preproc_tta.comp.hex.h"
#include "realesrgan_postproc_tta.comp.hex.h"

RealESRGAN::RealESRGAN(int _gpuid, bool _tta_mode, int num_threads)
    : scale(4),
      target_scale(4),
      tilesize(0),
      prepadding(10),
      use_fp16(true),
      vkdev(nullptr),
      realesrgan_preproc(nullptr),
      realesrgan_postproc(nullptr),
      bicubic_2x(nullptr),
      bicubic_3x(nullptr),
      bicubic_4x(nullptr),
      tta_mode(_tta_mode),
      gpuid(_gpuid),
      cancel_flag(nullptr),
      progress_callback(nullptr)
{
    net.opt.use_vulkan_compute = (gpuid >= 0);
    net.opt.use_fp16_packed = true;
    net.opt.use_fp16_storage = true;
    net.opt.use_fp16_arithmetic = false;
    net.opt.use_int8_storage = false;
    net.opt.use_int8_arithmetic = false;
    net.opt.num_threads = num_threads;

#if NCNN_VULKAN
    if (net.opt.use_vulkan_compute)
    {
        vkdev = ncnn::get_gpu_device(gpuid);
        net.set_vulkan_device(vkdev);
    }
#endif
}

RealESRGAN::~RealESRGAN()
{
#if NCNN_VULKAN
    if (vkdev)
    {
        ncnn::vkDeviceWaitIdle(vkdev->vkdevice());
    }
    delete realesrgan_preproc;
    delete realesrgan_postproc;

    if (bicubic_2x)
    {
        bicubic_2x->destroy_pipeline(net.opt);
        delete bicubic_2x;
    }
    if (bicubic_3x)
    {
        bicubic_3x->destroy_pipeline(net.opt);
        delete bicubic_3x;
    }
    if (bicubic_4x)
    {
        bicubic_4x->destroy_pipeline(net.opt);
        delete bicubic_4x;
    }
#endif
}

void RealESRGAN::set_cancel_flag(const std::atomic<bool>* flag)
{
    cancel_flag = flag;
}

void RealESRGAN::set_progress_callback(std::function<void(int current, int total)> cb)
{
    progress_callback = cb;
}

bool RealESRGAN::is_canceled() const
{
    return cancel_flag && cancel_flag->load(std::memory_order_relaxed);
}

void RealESRGAN::report_progress(int current, int total) const
{
    if (progress_callback)
    {
        progress_callback(current, total);
    }
}

int RealESRGAN::load(AAssetManager* mgr, const std::string& parampath, const std::string& modelpath)
{
    if (!mgr) return -1;

    net.opt.use_vulkan_compute = vkdev ? true : false;
    net.opt.use_fp16_packed = (vkdev && use_fp16);
    net.opt.use_fp16_storage = (vkdev && use_fp16);
    net.opt.use_fp16_arithmetic = false;
    net.opt.use_int8_storage = true;

    net.set_vulkan_device(vkdev);

    int r1 = net.load_param(mgr, parampath.c_str());
    int r2 = net.load_model(mgr, modelpath.c_str());

    // Memory buffer fallback if direct Android asset mmap/streaming failed
    if (r2 != 0 && mgr)
    {
        AAsset* asset = AAssetManager_open(mgr, modelpath.c_str(), AASSET_MODE_BUFFER);
        if (asset)
        {
            off_t file_len = AAsset_getLength(asset);
            if (file_len > 0)
            {
                model_bin_buf.resize(file_len);
                off_t total_read = 0;
                while (total_read < file_len)
                {
                    int n = AAsset_read(asset, model_bin_buf.data() + total_read, file_len - total_read);
                    if (n <= 0) break;
                    total_read += n;
                }
                if (total_read == file_len)
                {
                    size_t consumed = net.load_model(model_bin_buf.data());
                    if (consumed > 0)
                    {
                        r2 = 0;
                        hdify::NativeLogger::getInstance().log(hdify::LogLevel::INFO, "RealESRGAN", "",
                            "RealESRGAN::load model loaded via memory buffer (%zu bytes)", consumed);
                    }
                }
            }
            AAsset_close(asset);
        }
    }

    if (r1 != 0 || r2 != 0)
    {
        hdify::NativeLogger::getInstance().log(hdify::LogLevel::ERROR, "RealESRGAN", "",
            "Gagal load_param/load_model untuk %s (r1=%d, r2=%d)", parampath.c_str(), r1, r2);
        return -1;
    }

    // Determine native scale from model path name
    if (modelpath.find("-x2") != std::string::npos) scale = 2;
    else if (modelpath.find("-x3") != std::string::npos) scale = 3;
    else scale = 4;

    return init_pipelines();
}

int RealESRGAN::load(const std::string& parampath, const std::string& modelpath)
{
    net.opt.use_vulkan_compute = vkdev ? true : false;
    net.opt.use_fp16_packed = (vkdev && use_fp16);
    net.opt.use_fp16_storage = (vkdev && use_fp16);
    net.opt.use_fp16_arithmetic = false;
    net.opt.use_int8_storage = true;

    net.set_vulkan_device(vkdev);

    if (net.load_param(parampath.c_str()) != 0) return -1;
    if (net.load_model(modelpath.c_str()) != 0) return -2;

    if (modelpath.find("-x2") != std::string::npos) scale = 2;
    else if (modelpath.find("-x3") != std::string::npos) scale = 3;
    else scale = 4;

    return init_pipelines();
}

int RealESRGAN::init_pipelines()
{
#if NCNN_VULKAN
    if (net.opt.use_vulkan_compute && vkdev)
    {
        std::vector<ncnn::vk_specialization_type> specializations(1);
        specializations[0].i = 0; // bgr = 0 (Android RGBA standard)

        std::vector<uint32_t> spirv_preproc;
        if (tta_mode)
            ncnn::compile_spirv_module(realesrgan_preproc_tta_comp_data, sizeof(realesrgan_preproc_tta_comp_data), net.opt, spirv_preproc);
        else
            ncnn::compile_spirv_module(realesrgan_preproc_comp_data, sizeof(realesrgan_preproc_comp_data), net.opt, spirv_preproc);

        realesrgan_preproc = new ncnn::Pipeline(vkdev);
        realesrgan_preproc->set_optimal_local_size_xyz(8, 8, 3);
        realesrgan_preproc->create(spirv_preproc.data(), spirv_preproc.size() * 4, specializations);

        std::vector<uint32_t> spirv_postproc;
        if (tta_mode)
            ncnn::compile_spirv_module(realesrgan_postproc_tta_comp_data, sizeof(realesrgan_postproc_tta_comp_data), net.opt, spirv_postproc);
        else
            ncnn::compile_spirv_module(realesrgan_postproc_comp_data, sizeof(realesrgan_postproc_comp_data), net.opt, spirv_postproc);

        realesrgan_postproc = new ncnn::Pipeline(vkdev);
        realesrgan_postproc->set_optimal_local_size_xyz(8, 8, 3);
        realesrgan_postproc->create(spirv_postproc.data(), spirv_postproc.size() * 4, specializations);

        // Alpha channel bicubic layers
        auto* mutable_vkdev = const_cast<ncnn::VulkanDevice*>(vkdev);

        bicubic_2x = ncnn::create_layer("Interp");
        bicubic_2x->vkdev = mutable_vkdev;
        ncnn::ParamDict pd2;
        pd2.set(0, 3); pd2.set(1, 2.f); pd2.set(2, 2.f);
        bicubic_2x->load_param(pd2);
        bicubic_2x->create_pipeline(net.opt);

        bicubic_3x = ncnn::create_layer("Interp");
        bicubic_3x->vkdev = mutable_vkdev;
        ncnn::ParamDict pd3;
        pd3.set(0, 3); pd3.set(1, 3.f); pd3.set(2, 3.f);
        bicubic_3x->load_param(pd3);
        bicubic_3x->create_pipeline(net.opt);

        bicubic_4x = ncnn::create_layer("Interp");
        bicubic_4x->vkdev = mutable_vkdev;
        ncnn::ParamDict pd4;
        pd4.set(0, 3); pd4.set(1, 4.f); pd4.set(2, 4.f);
        bicubic_4x->load_param(pd4);
        bicubic_4x->create_pipeline(net.opt);
    }
#endif
    return 0;
}

static bool downscale_box(const unsigned char* src, int sw, int sh, unsigned char* dst, int dw, int dh, int channels, const std::atomic<bool>* cancel_flag)
{
    if (sw == dw && sh == dh)
    {
        memcpy(dst, src, (size_t)dw * dh * channels);
        return true;
    }

    // High-precision area-weighted downscaling to preserve all AI neural details
    float x_ratio = (float)sw / (float)dw;
    float y_ratio = (float)sh / (float)dh;

    for (int dy = 0; dy < dh; dy++)
    {
        if (cancel_flag && (dy & 15) == 0 && cancel_flag->load(std::memory_order_relaxed))
        {
            return false;
        }

        float sy_start = dy * y_ratio;
        float sy_end = (dy + 1) * y_ratio;
        int y0 = (int)sy_start;
        int y1 = std::min((int)std::ceil(sy_end), sh);

        for (int dx = 0; dx < dw; dx++)
        {
            float sx_start = dx * x_ratio;
            float sx_end = (dx + 1) * x_ratio;
            int x0 = (int)sx_start;
            int x1 = std::min((int)std::ceil(sx_end), sw);

            float total_weight = 0.f;
            float acc[4] = {0.f, 0.f, 0.f, 0.f};

            for (int sy = y0; sy < y1; sy++)
            {
                float y_weight = 1.f;
                if (sy < sy_start) y_weight -= (sy_start - sy);
                if (sy + 1 > sy_end) y_weight -= (sy + 1 - sy_end);
                y_weight = std::max(0.f, y_weight);

                const unsigned char* src_row = src + (sy * sw) * channels;
                for (int sx = x0; sx < x1; sx++)
                {
                    float x_weight = 1.f;
                    if (sx < sx_start) x_weight -= (sx_start - sx);
                    if (sx + 1 > sx_end) x_weight -= (sx + 1 - sx_end);
                    x_weight = std::max(0.f, x_weight);

                    float w_pixel = x_weight * y_weight;
                    total_weight += w_pixel;

                    const unsigned char* p = src_row + sx * channels;
                    for (int c = 0; c < channels; c++)
                    {
                        acc[c] += p[c] * w_pixel;
                    }
                }
            }

            unsigned char* out = dst + (dy * dw + dx) * channels;
            float inv_w = (total_weight > 0.0001f) ? (1.f / total_weight) : 1.f;
            for (int c = 0; c < channels; c++)
            {
                int val = (int)std::round(acc[c] * inv_w);
                out[c] = (unsigned char)std::clamp(val, 0, 255);
            }
        }
    }
    return true;
}

int RealESRGAN::process(const ncnn::Mat& inimage, ncnn::Mat& outimage) const
{
#if NCNN_VULKAN
    if (!net.opt.use_vulkan_compute || !vkdev)
    {
        return process_cpu(inimage, outimage);
    }

    if (is_canceled()) return -2;

    const unsigned char* pixeldata = (const unsigned char*)inimage.data;
    const int w = inimage.w;
    const int h = inimage.h;
    const int channels = (inimage.elempack > 1) ? inimage.elempack : (inimage.elemsize == 4 ? 4 : 3);

    int tile_size = tilesize;
    if (tile_size <= 0)
    {
        uint32_t heap_budget_mb = vkdev ? vkdev->get_heap_budget() : 1024;
        tile_size = hdify::plan_realesrgan_tile_size(heap_budget_mb, scale, 0);
    }
    const int TILE_SIZE_X = tile_size;
    const int TILE_SIZE_Y = tile_size;

    auto* mutable_vkdev = const_cast<ncnn::VulkanDevice*>(vkdev);
    ncnn::VkAllocator* blob_vkallocator = mutable_vkdev->acquire_blob_allocator();
    ncnn::VkAllocator* staging_vkallocator = mutable_vkdev->acquire_staging_allocator();

    ncnn::Option opt = net.opt;
    opt.blob_vkallocator = blob_vkallocator;
    opt.workspace_vkallocator = blob_vkallocator;
    opt.staging_vkallocator = staging_vkallocator;

    const int xtiles = (w + TILE_SIZE_X - 1) / TILE_SIZE_X;
    const int ytiles = (h + TILE_SIZE_Y - 1) / TILE_SIZE_Y;
    const int total_tiles = xtiles * ytiles;

    // Buffer output native model scale
    ncnn::Mat out_native;
    out_native.create(w * scale, h * scale, (size_t)channels, 1);

    int tile_count = 0;
    report_progress(0, total_tiles);

    const size_t in_out_tile_elemsize = opt.use_fp16_storage ? 2u : 4u;

    for (int yi = 0; yi < ytiles; yi++)
    {
        if (is_canceled())
        {
            if (vkdev) ncnn::vkDeviceWaitIdle(vkdev->vkdevice());
            mutable_vkdev->reclaim_blob_allocator(blob_vkallocator);
            mutable_vkdev->reclaim_staging_allocator(staging_vkallocator);
            return -2;
        }

        const int in_tile_y0 = std::max(yi * TILE_SIZE_Y - prepadding, 0);
        const int in_tile_y1 = std::min((yi + 1) * TILE_SIZE_Y + prepadding, h);

        ncnn::Mat in;
        if (opt.use_fp16_storage && opt.use_int8_storage)
        {
            in = ncnn::Mat(w, (in_tile_y1 - in_tile_y0), (unsigned char*)pixeldata + in_tile_y0 * w * channels, (size_t)channels, 1);
        }
        else
        {
            if (channels == 3)
            {
                in = ncnn::Mat::from_pixels(pixeldata + in_tile_y0 * w * channels, ncnn::Mat::PIXEL_RGB, w, in_tile_y1 - in_tile_y0);
            }
            else
            {
                in = ncnn::Mat::from_pixels(pixeldata + in_tile_y0 * w * channels, ncnn::Mat::PIXEL_RGBA, w, in_tile_y1 - in_tile_y0);
            }
        }

        ncnn::VkCompute cmd(vkdev);
        ncnn::VkMat in_gpu;
        cmd.record_clone(in, in_gpu, opt);

        const int out_tile_y0 = yi * TILE_SIZE_Y;
        const int out_tile_y1 = std::min((yi + 1) * TILE_SIZE_Y, h);

        ncnn::VkMat out_gpu;
        if (opt.use_fp16_storage && opt.use_int8_storage)
        {
            out_gpu.create(w * scale, (out_tile_y1 - out_tile_y0) * scale, (size_t)channels, 1, blob_vkallocator);
        }
        else
        {
            out_gpu.create(w * scale, (out_tile_y1 - out_tile_y0) * scale, channels, (size_t)4u, 1, blob_vkallocator);
        }

        for (int xi = 0; xi < xtiles; xi++)
        {
            if (is_canceled())
            {
                cmd.reset();
                out_gpu.release();
                in_gpu.release();
                if (vkdev) ncnn::vkDeviceWaitIdle(vkdev->vkdevice());
                mutable_vkdev->reclaim_blob_allocator(blob_vkallocator);
                mutable_vkdev->reclaim_staging_allocator(staging_vkallocator);
                return -2;
            }

            const int tile_w_nopad = std::min((xi + 1) * TILE_SIZE_X, w) - xi * TILE_SIZE_X;
            const int tile_h_nopad = std::min((yi + 1) * TILE_SIZE_Y, h) - yi * TILE_SIZE_Y;

            int tile_x0 = xi * TILE_SIZE_X - prepadding;
            int tile_x1 = std::min((xi + 1) * TILE_SIZE_X, w) + prepadding;
            int tile_y0 = yi * TILE_SIZE_Y - prepadding;
            int tile_y1 = std::min((yi + 1) * TILE_SIZE_Y, h) + prepadding;

            ncnn::VkMat in_tile_gpu;
            in_tile_gpu.create(tile_x1 - tile_x0, tile_y1 - tile_y0, 3, in_out_tile_elemsize, 1, blob_vkallocator);

            ncnn::VkMat in_alpha_tile_gpu;
            if (channels == 4)
            {
                in_alpha_tile_gpu.create(tile_w_nopad, tile_h_nopad, 1, in_out_tile_elemsize, 1, blob_vkallocator);
            }

            std::vector<ncnn::VkMat> pre_bindings = { in_gpu, in_tile_gpu, in_alpha_tile_gpu };
            std::vector<ncnn::vk_constant_type> pre_constants(13);
            pre_constants[0].i = in_gpu.w;
            pre_constants[1].i = in_gpu.h;
            pre_constants[2].i = in_gpu.cstep;
            pre_constants[3].i = in_tile_gpu.w;
            pre_constants[4].i = in_tile_gpu.h;
            pre_constants[5].i = in_tile_gpu.cstep;
            pre_constants[6].i = prepadding;
            pre_constants[7].i = prepadding;
            pre_constants[8].i = xi * TILE_SIZE_X;
            pre_constants[9].i = std::min(yi * TILE_SIZE_Y, prepadding);
            pre_constants[10].i = channels;
            pre_constants[11].i = in_alpha_tile_gpu.w;
            pre_constants[12].i = in_alpha_tile_gpu.h;

            ncnn::VkMat dispatcher;
            dispatcher.w = in_tile_gpu.w;
            dispatcher.h = in_tile_gpu.h;
            dispatcher.c = channels;
            cmd.record_pipeline(realesrgan_preproc, pre_bindings, pre_constants, dispatcher);

            ncnn::VkMat out_tile_gpu;
            ncnn::Extractor ex = net.create_extractor();
            ex.set_blob_vkallocator(blob_vkallocator);
            ex.set_workspace_vkallocator(blob_vkallocator);
            ex.set_staging_vkallocator(staging_vkallocator);

            ex.input("data", in_tile_gpu);
            ex.extract("output", out_tile_gpu, cmd);

            cmd.submit_and_wait();
            cmd.reset();

            if (is_canceled())
            {
                in_tile_gpu.release();
                out_tile_gpu.release();
                in_alpha_tile_gpu.release();
                out_gpu.release();
                in_gpu.release();
                if (vkdev) ncnn::vkDeviceWaitIdle(vkdev->vkdevice());
                mutable_vkdev->reclaim_blob_allocator(blob_vkallocator);
                mutable_vkdev->reclaim_staging_allocator(staging_vkallocator);
                return -2;
            }

            ncnn::VkMat out_alpha_tile_gpu;
            if (channels == 4)
            {
                if (scale == 2 && bicubic_2x) bicubic_2x->forward(in_alpha_tile_gpu, out_alpha_tile_gpu, cmd, opt);
                else if (scale == 3 && bicubic_3x) bicubic_3x->forward(in_alpha_tile_gpu, out_alpha_tile_gpu, cmd, opt);
                else if (scale == 4 && bicubic_4x) bicubic_4x->forward(in_alpha_tile_gpu, out_alpha_tile_gpu, cmd, opt);
                else out_alpha_tile_gpu = in_alpha_tile_gpu;
            }

            int out_crop_w = std::min(TILE_SIZE_X * scale, out_gpu.w - xi * TILE_SIZE_X * scale);

            std::vector<ncnn::VkMat> post_bindings = { out_tile_gpu, out_alpha_tile_gpu, out_gpu };
            std::vector<ncnn::vk_constant_type> post_constants(13);
            post_constants[0].i = out_tile_gpu.w;
            post_constants[1].i = out_tile_gpu.h;
            post_constants[2].i = out_tile_gpu.cstep;
            post_constants[3].i = out_gpu.w;
            post_constants[4].i = out_gpu.h;
            post_constants[5].i = out_gpu.cstep;
            post_constants[6].i = xi * TILE_SIZE_X * scale;
            post_constants[7].i = out_crop_w;
            post_constants[8].i = prepadding * scale;
            post_constants[9].i = prepadding * scale;
            post_constants[10].i = channels;
            post_constants[11].i = out_alpha_tile_gpu.w;
            post_constants[12].i = out_alpha_tile_gpu.h;

            dispatcher.w = out_crop_w;
            dispatcher.h = out_gpu.h;
            dispatcher.c = channels;
            cmd.record_pipeline(realesrgan_postproc, post_bindings, post_constants, dispatcher);

            cmd.submit_and_wait();
            cmd.reset();

            tile_count++;
            report_progress(tile_count, total_tiles);

            if (is_canceled())
            {
                in_tile_gpu.release();
                out_tile_gpu.release();
                in_alpha_tile_gpu.release();
                out_alpha_tile_gpu.release();
                out_gpu.release();
                in_gpu.release();
                if (vkdev) ncnn::vkDeviceWaitIdle(vkdev->vkdevice());
                mutable_vkdev->reclaim_blob_allocator(blob_vkallocator);
                mutable_vkdev->reclaim_staging_allocator(staging_vkallocator);
                return -2;
            }
        }

        if (is_canceled())
        {
            out_gpu.release();
            in_gpu.release();
            if (vkdev) ncnn::vkDeviceWaitIdle(vkdev->vkdevice());
            mutable_vkdev->reclaim_blob_allocator(blob_vkallocator);
            mutable_vkdev->reclaim_staging_allocator(staging_vkallocator);
            return -2;
        }

        // Download slice to native output
        unsigned char* dst_slice = (unsigned char*)out_native.data + (out_tile_y0 * scale * w * scale * channels);

        if (opt.use_fp16_storage && opt.use_int8_storage)
        {
            ncnn::Mat out_slice(out_gpu.w, out_gpu.h, dst_slice, (size_t)channels, 1);
            cmd.record_clone(out_gpu, out_slice, opt);
            cmd.submit_and_wait();
            cmd.reset();
        }
        else
        {
            ncnn::Mat out_slice;
            cmd.record_clone(out_gpu, out_slice, opt);
            cmd.submit_and_wait();
            cmd.reset();

            if (channels == 3)
            {
                out_slice.to_pixels(dst_slice, ncnn::Mat::PIXEL_RGB);
            }
            else
            {
                out_slice.to_pixels(dst_slice, ncnn::Mat::PIXEL_RGBA);
            }
        }
    }

    if (vkdev) ncnn::vkDeviceWaitIdle(vkdev->vkdevice());
    mutable_vkdev->reclaim_blob_allocator(blob_vkallocator);
    mutable_vkdev->reclaim_staging_allocator(staging_vkallocator);

    if (is_canceled())
    {
        return -2;
    }

    // Check if downscale is needed (e.g. model scale = 4, requested scale = 2 or 3)
    if (target_scale > 0 && target_scale < scale)
    {
        int target_w = w * target_scale;
        int target_h = h * target_scale;
        outimage.create(target_w, target_h, (size_t)channels, 1);

        if (!downscale_box((const unsigned char*)out_native.data, w * scale, h * scale,
                           (unsigned char*)outimage.data, target_w, target_h, channels, cancel_flag))
        {
            return -2;
        }
    }
    else
    {
        if (outimage.data && outimage.data != out_native.data)
        {
            memcpy(outimage.data, out_native.data, (size_t)w * scale * h * scale * channels);
        }
        else
        {
            outimage = out_native;
        }
    }

    return 0;
#else
    return process_cpu(inimage, outimage);
#endif
}

int RealESRGAN::process_cpu(const ncnn::Mat& inimage, ncnn::Mat& outimage) const
{
    hdify::NativeLogger::getInstance().log(hdify::LogLevel::ERROR, "RealESRGAN", "",
        "CPU fallback tidak diaktifkan (PRD Aturan 3)");
    return -101; // HD-HW-101
}
