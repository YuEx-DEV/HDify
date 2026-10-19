#include <jni.h>
#include <string>
#include <memory>
#include <atomic>
#include <vector>
#include <cmath>
#include <algorithm>
#include <cstring>
#include <mutex>
#include <unordered_map>
#include <android/asset_manager.h>
#include <android/asset_manager_jni.h>
#include <android/bitmap.h>

#include "gpu/gpu_info.h"
#include "gpu/gpu_selftest.h"
#include "gpu/gpu_benchmark.h"
#include "gpu/tile_planner.h"
#include "log/native_log.h"
#include "log/breadcrumbs.h"
#include "log/crash_handler.h"
#include "engines/realcugan/realcugan.h"
#include "engines/realesrgan/realesrgan.h"
#include "engines/rife/rife.h"

namespace hdify {

struct NativeUpscaleEngineContext {
    int engine_type = 0;
    std::unique_ptr<RealCUGAN> realcugan;
    std::unique_ptr<RealESRGAN> realesrgan;
    std::atomic<bool> canceled{false};
    int scale = 2;
    std::string backend = "vulkan";
    std::string device_name = "Unknown";
    bool fp16_active = true;
    int tile_size = 200;
    std::mutex exec_mutex;
};

struct NativeInterpolationEngineContext {
    std::unique_ptr<RIFE> rife;
    std::atomic<bool> canceled{false};
    std::string backend = "vulkan";
    std::string device_name = "Unknown";
    bool fp16_active = true;
    std::mutex exec_mutex;
};

class EngineRegistry {
public:
    static EngineRegistry& getInstance() {
        static EngineRegistry instance;
        return instance;
    }

    int64_t registerUpscale(std::shared_ptr<NativeUpscaleEngineContext> ctx) {
        std::lock_guard<std::mutex> lock(mutex_);
        int64_t handle = next_handle_++;
        upscale_map_[handle] = std::move(ctx);
        return handle;
    }

    std::shared_ptr<NativeUpscaleEngineContext> getUpscale(int64_t handle) {
        std::lock_guard<std::mutex> lock(mutex_);
        auto it = upscale_map_.find(handle);
        return (it != upscale_map_.end()) ? it->second : nullptr;
    }

    void releaseUpscale(int64_t handle) {
        std::shared_ptr<NativeUpscaleEngineContext> ctx;
        {
            std::lock_guard<std::mutex> lock(mutex_);
            auto it = upscale_map_.find(handle);
            if (it != upscale_map_.end()) {
                ctx = it->second;
                upscale_map_.erase(it);
            }
        }
        if (ctx) {
            ctx->canceled.store(true, std::memory_order_seq_cst);
            std::lock_guard<std::mutex> exec_lock(ctx->exec_mutex);
            ctx->realcugan.reset();
            ctx->realesrgan.reset();
        }
    }

    int64_t registerRife(std::shared_ptr<NativeInterpolationEngineContext> ctx) {
        std::lock_guard<std::mutex> lock(mutex_);
        int64_t handle = next_handle_++;
        rife_map_[handle] = std::move(ctx);
        return handle;
    }

    std::shared_ptr<NativeInterpolationEngineContext> getRife(int64_t handle) {
        std::lock_guard<std::mutex> lock(mutex_);
        auto it = rife_map_.find(handle);
        return (it != rife_map_.end()) ? it->second : nullptr;
    }

    void releaseRife(int64_t handle) {
        std::shared_ptr<NativeInterpolationEngineContext> ctx;
        {
            std::lock_guard<std::mutex> lock(mutex_);
            auto it = rife_map_.find(handle);
            if (it != rife_map_.end()) {
                ctx = it->second;
                rife_map_.erase(it);
            }
        }
        if (ctx) {
            ctx->canceled.store(true, std::memory_order_seq_cst);
            std::lock_guard<std::mutex> exec_lock(ctx->exec_mutex);
            ctx->rife.reset();
        }
    }

private:
    std::mutex mutex_;
    int64_t next_handle_{1};
    std::unordered_map<int64_t, std::shared_ptr<NativeUpscaleEngineContext>> upscale_map_;
    std::unordered_map<int64_t, std::shared_ptr<NativeInterpolationEngineContext>> rife_map_;
};

} // namespace hdify

extern "C" {

JNIEXPORT jobject JNICALL
Java_com_example_hdify_engine_NativeBridge_getGpuInfo(JNIEnv* env, jobject /* this */) {
    hdify::SystemGpuInfo info = hdify::GpuManager::getInstance().querySystemGpuInfo();

    jclass cls = env->FindClass("com/example/hdify/domain/model/GpuInfo");
    if (!cls) return nullptr;

    jmethodID ctor = env->GetMethodID(cls, "<init>", "(ZILjava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;JZZZ)V");
    if (!ctor) return nullptr;

    jstring errStr = env->NewStringUTF(info.errorMessage.c_str());
    jstring nameStr = env->NewStringUTF(info.primaryDevice.name.c_str());
    jstring typeStr = env->NewStringUTF(info.primaryDevice.type.c_str());
    jstring apiStr = env->NewStringUTF(info.primaryDevice.apiVersion.c_str());
    jstring driverStr = env->NewStringUTF(info.primaryDevice.driverVersion.c_str());

    jobject obj = env->NewObject(
        cls, ctor,
        (jboolean)info.vulkanAvailable,
        (jint)info.gpuCount,
        errStr,
        nameStr,
        typeStr,
        apiStr,
        driverStr,
        (jlong)info.primaryDevice.memoryHeapBytes,
        (jboolean)info.primaryDevice.supportFp16Storage,
        (jboolean)info.primaryDevice.supportFp16Arithmetic,
        (jboolean)info.primaryDevice.supportFp16Packed
    );

    return obj;
}

JNIEXPORT void JNICALL
Java_com_example_hdify_engine_NativeBridge_initNativeLogger(JNIEnv* env, jobject /* this */, jstring logFilePath, jboolean verbose) {
    if (logFilePath) {
        const char* pathStr = env->GetStringUTFChars(logFilePath, nullptr);
        hdify::NativeLogger::getInstance().setLogFilePath(pathStr);
        env->ReleaseStringUTFChars(logFilePath, pathStr);
    }
    hdify::NativeLogger::getInstance().setVerbose(verbose);
}

JNIEXPORT void JNICALL
Java_com_example_hdify_engine_NativeBridge_logNative(JNIEnv* env, jobject /* this */, jint level, jstring tag, jstring context, jstring message) {
    const char* tagStr = tag ? env->GetStringUTFChars(tag, nullptr) : "Native";
    const char* ctxStr = context ? env->GetStringUTFChars(context, nullptr) : "";
    const char* msgStr = message ? env->GetStringUTFChars(message, nullptr) : "";

    hdify::LogLevel nativeLevel = hdify::LogLevel::INFO;
    switch (level) {
        case 0: nativeLevel = hdify::LogLevel::VERBOSE; break;
        case 1: nativeLevel = hdify::LogLevel::DEBUG; break;
        case 2: nativeLevel = hdify::LogLevel::INFO; break;
        case 3: nativeLevel = hdify::LogLevel::WARN; break;
        case 4: nativeLevel = hdify::LogLevel::ERROR; break;
    }

    hdify::NativeLogger::getInstance().log(nativeLevel, tagStr, ctxStr, "%s", msgStr);

    if (tag) env->ReleaseStringUTFChars(tag, tagStr);
    if (context) env->ReleaseStringUTFChars(context, ctxStr);
    if (message) env->ReleaseStringUTFChars(message, msgStr);
}

JNIEXPORT void JNICALL
Java_com_example_hdify_engine_NativeBridge_addBreadcrumb(JNIEnv* env, jobject /* this */, jstring message) {
    if (message) {
        const char* msg = env->GetStringUTFChars(message, nullptr);
        hdify::Breadcrumbs::getInstance().add(msg);
        env->ReleaseStringUTFChars(message, msg);
    }
}

JNIEXPORT jboolean JNICALL
Java_com_example_hdify_engine_NativeBridge_installCrashHandler(JNIEnv* env, jobject /* this */, jstring crashDir) {
    if (!crashDir) return JNI_FALSE;
    const char* dir = env->GetStringUTFChars(crashDir, nullptr);
    bool ok = hdify::NativeCrashHandler::getInstance().install(dir);
    env->ReleaseStringUTFChars(crashDir, dir);
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_example_hdify_engine_NativeBridge_triggerNativeCrash(JNIEnv* /* env */, jobject /* this */) {
    hdify::NativeCrashHandler::triggerCrashForTesting();
}

JNIEXPORT jobject JNICALL
Java_com_example_hdify_engine_NativeBridge_runGpuSelfTest(JNIEnv* env, jobject /* this */, jobject assetManager, jint gpuIndex) {
    AAssetManager* mgr = AAssetManager_fromJava(env, assetManager);
    hdify::SelfTestResult res = hdify::run_gpu_self_test(mgr, gpuIndex);

    jclass cls = env->FindClass("com/example/hdify/domain/model/SelfTestResult");
    if (!cls) return nullptr;

    jmethodID ctor = env->GetMethodID(cls, "<init>", "(ZZZLjava/lang/String;Ljava/lang/String;Ljava/lang/String;J)V");
    if (!ctor) return nullptr;

    jstring devStr = env->NewStringUTF(res.device_name.c_str());
    jstring msgStr = env->NewStringUTF(res.message.c_str());
    jstring errStr = env->NewStringUTF(res.error_code.c_str());

    return env->NewObject(cls, ctor,
                          (jboolean)res.passed,
                          (jboolean)res.fp16_passed,
                          (jboolean)res.fp32_fallback_used,
                          devStr,
                          msgStr,
                          errStr,
                          (jlong)res.duration_ms);
}

JNIEXPORT jobject JNICALL
Java_com_example_hdify_engine_NativeBridge_runBenchmark(
    JNIEnv* env, jobject /* this */,
    jobject assetManager,
    jint engineType,
    jint scale,
    jint gpuIndex
) {
    AAssetManager* mgr = AAssetManager_fromJava(env, assetManager);
    hdify::BenchmarkResult res = hdify::run_gpu_benchmark(mgr, engineType, scale, gpuIndex);

    jclass cls = env->FindClass("com/example/hdify/domain/model/BenchmarkResult");
    if (!cls) return nullptr;

    jmethodID ctor = env->GetMethodID(cls, "<init>", "(ZLjava/lang/String;IJDDDLjava/lang/String;)V");
    if (!ctor) return nullptr;

    jstring engStr = env->NewStringUTF(res.engine_name.c_str());
    jstring errStr = env->NewStringUTF(res.error_message.c_str());

    return env->NewObject(
        cls, ctor,
        (jboolean)res.passed,
        engStr,
        (jint)res.scale,
        (jlong)res.ms_per_tile,
        (jdouble)res.est_1080p_sec,
        (jdouble)res.est_720p_video_sec,
        (jdouble)res.fps_720p,
        errStr
    );
}

JNIEXPORT jlong JNICALL
Java_com_example_hdify_engine_NativeBridge_createEngine(JNIEnv* /* env */, jobject /* this */) {
    auto ctx = std::make_shared<hdify::NativeUpscaleEngineContext>();
    return hdify::EngineRegistry::getInstance().registerUpscale(ctx);
}

static bool check_asset_exists(AAssetManager* mgr, const std::string& path) {
    if (!mgr || path.empty()) return false;
    AAsset* asset = AAssetManager_open(mgr, path.c_str(), AASSET_MODE_UNKNOWN);
    if (asset) {
        AAsset_close(asset);
        return true;
    }
    return false;
}

static std::string sanitize_model_name(const std::string& input, const std::string& prefix_to_strip = "") {
    std::string s = input;
    while (!s.empty() && (s.front() == ' ' || s.front() == '\t' || s.front() == '\r' || s.front() == '\n')) {
        s.erase(s.begin());
    }
    while (!s.empty() && (s.back() == ' ' || s.back() == '\t' || s.back() == '\r' || s.back() == '\n')) {
        s.pop_back();
    }
    if (!prefix_to_strip.empty() && s.rfind(prefix_to_strip, 0) == 0) {
        s = s.substr(prefix_to_strip.length());
    }
    while (!s.empty() && (s.front() == '/' || s.front() == '\\')) {
        s.erase(s.begin());
    }
    while (!s.empty() && (s.back() == '/' || s.back() == '\\')) {
        s.pop_back();
    }
    if (s.length() >= 6 && s.compare(s.length() - 6, 6, ".param") == 0) {
        s = s.substr(0, s.length() - 6);
    } else if (s.length() >= 4 && s.compare(s.length() - 4, 4, ".bin") == 0) {
        s = s.substr(0, s.length() - 4);
    }
    return s;
}

JNIEXPORT jobject JNICALL
Java_com_example_hdify_engine_NativeBridge_initEngine(
    JNIEnv* env, jobject /* this */,
    jlong handle,
    jobject assetManager,
    jint engineType,
    jstring modelDir,
    jint scale,
    jint denoise,
    jint tileSize,
    jboolean useFp16,
    jint gpuIndex,
    jboolean allowCpuFallback
) {
    auto ctx = hdify::EngineRegistry::getInstance().getUpscale(handle);
    if (!ctx) return nullptr;
    std::lock_guard<std::mutex> exec_lock(ctx->exec_mutex);

    AAssetManager* mgr = AAssetManager_fromJava(env, assetManager);
    if (!mgr) {
        hdify::NativeLogger::getInstance().log(hdify::LogLevel::ERROR, "Engine", "",
            "HD-SYS-901 Failed to acquire AAssetManager from Java");
        return nullptr;
    }

    const char* modelDirStr = env->GetStringUTFChars(modelDir, nullptr);
    std::string modelDirectory(modelDirStr ? modelDirStr : "models-se");
    if (modelDirStr) env->ReleaseStringUTFChars(modelDir, modelDirStr);

    ctx->scale = scale;
    ctx->canceled.store(false);

    hdify::ensure_vulkan_initialized();

    // Hardware selection logic
    int actualGpuId = gpuIndex;
    int availableGpus = ncnn::get_gpu_count();

    if (actualGpuId >= availableGpus || actualGpuId < 0) {
        if (!allowCpuFallback) {
            hdify::NativeLogger::getInstance().log(hdify::LogLevel::ERROR, "Engine", "",
                "HD-HW-101 Vulkan GPU device %d not available and CPU fallback is OFF", actualGpuId);
            return nullptr;
        }
        actualGpuId = -1; // Fallback to CPU
        ctx->backend = "cpu";
        ctx->device_name = "CPU (lambat)";
    } else {
        ncnn::VulkanDevice* vkdev = ncnn::get_gpu_device(actualGpuId);
        if (!vkdev) {
            if (!allowCpuFallback) {
                hdify::NativeLogger::getInstance().log(hdify::LogLevel::ERROR, "Engine", "",
                    "HD-HW-101 Vulkan device %d initialization failed and CPU fallback is OFF", actualGpuId);
                return nullptr;
            }
            actualGpuId = -1;
            ctx->backend = "cpu";
            ctx->device_name = "CPU (lambat)";
        } else {
            ctx->backend = "vulkan";
            ctx->device_name = hdify::safe_str(vkdev->info.device_name(), "Vulkan Device");
        }
    }

    // Determine tile size
    uint32_t heap_budget_mb = 1024;
    if (actualGpuId >= 0) {
        auto* dev = ncnn::get_gpu_device(actualGpuId);
        if (dev) {
            heap_budget_mb = dev->get_heap_budget();
        }
    }
    if (engineType == 1) {
        ctx->tile_size = hdify::plan_realesrgan_tile_size(heap_budget_mb, scale, tileSize);
    } else {
        ctx->tile_size = hdify::plan_tile_size(heap_budget_mb, scale, tileSize);
    }
    ctx->fp16_active = (actualGpuId >= 0) && useFp16;

    ctx->engine_type = engineType;
    ctx->scale = scale;
    ctx->canceled.store(false);

    if (engineType == 1) {
        // Real-ESRGAN
        std::string model_name = sanitize_model_name(modelDirectory, "models/realesrgan/");
        if (model_name.empty() || model_name == "models-se" || model_name == "models-pro") {
            model_name = "realesrgan-x4plus";
        }

        // Map generic "realesr-animevideov3" to scale-specific model
        if (model_name == "realesr-animevideov3") {
            int target_scale = scale;
            if (target_scale < 2) target_scale = 2;
            if (target_scale > 4) target_scale = 4;
            model_name = "realesr-animevideov3-x" + std::to_string(target_scale);
        }

        std::string param_path = "models/realesrgan/" + model_name + ".param";
        std::string bin_path = "models/realesrgan/" + model_name + ".bin";

        // Asset existence verification & fallback to default model if missing
        if (!check_asset_exists(mgr, param_path)) {
            hdify::NativeLogger::getInstance().log(hdify::LogLevel::WARN, "Engine", "",
                "Model param %s not found in assets. Falling back to default realesrgan-x4plus", param_path.c_str());
            model_name = "realesrgan-x4plus";
            param_path = "models/realesrgan/" + model_name + ".param";
            bin_path = "models/realesrgan/" + model_name + ".bin";
        }

        ctx->realesrgan = std::make_unique<RealESRGAN>(actualGpuId, false, 1);
        ctx->realesrgan->target_scale = scale;
        ctx->realesrgan->tilesize = ctx->tile_size;
        ctx->realesrgan->prepadding = 10;
        ctx->realesrgan->use_fp16 = ctx->fp16_active;
        ctx->realesrgan->set_cancel_flag(&ctx->canceled);

        hdify::NativeLogger::getInstance().log(hdify::LogLevel::INFO, "Upscale", "",
            "backend=%s device=%s fp16=%s tile=%d model=%s scale=%d",
            ctx->backend.c_str(), ctx->device_name.c_str(),
            ctx->fp16_active ? "true" : "false", ctx->tile_size, model_name.c_str(), scale);

        int load_res = ctx->realesrgan->load(mgr, param_path, bin_path);
        if (load_res != 0) {
            if (model_name != "realesrgan-x4plus") {
                hdify::NativeLogger::getInstance().log(hdify::LogLevel::WARN, "Engine", "",
                    "Failed to load %s, attempting fallback load of realesrgan-x4plus", model_name.c_str());
                model_name = "realesrgan-x4plus";
                param_path = "models/realesrgan/" + model_name + ".param";
                bin_path = "models/realesrgan/" + model_name + ".bin";
                load_res = ctx->realesrgan->load(mgr, param_path, bin_path);
            }
            if (load_res != 0) {
                hdify::NativeLogger::getInstance().log(hdify::LogLevel::ERROR, "Engine", "",
                    "HD-MDL-201 Failed to load Real-ESRGAN model: %s", param_path.c_str());
                ctx->realesrgan.reset();
                return nullptr;
            }
        }
    } else {
        // Construct model paths
        std::string param_file;
        std::string bin_file;
        char param_buf[128];
        char bin_buf[128];

        int effective_scale = scale;
        if (effective_scale < 2) effective_scale = 2;
        if (effective_scale > 4) effective_scale = 4;

        std::string effective_model_dir = sanitize_model_name(modelDirectory, "models/realcugan/");
        if (effective_model_dir.empty()) {
            effective_model_dir = "models-se";
        }
        int effective_denoise = denoise;

        if (effective_model_dir == "models-pro") {
            // models-pro has no 4x model in Real-CUGAN; fallback to models-se
            if (effective_scale == 4) {
                effective_model_dir = "models-se";
            }
            // models-pro only supports conservative (-1), no-denoise (0), and denoise3x (3)
            if (effective_denoise == 1 || effective_denoise == 2) {
                effective_denoise = 0;
            }
        }

        if (effective_scale == 3 || effective_scale == 4) {
            // Models for 3x and 4x only provide: conservative (-1), no-denoise (0), denoise3x (3)
            if (effective_denoise > 0) {
                effective_denoise = 3;
            }
        }

        if (effective_denoise == -1) {
            snprintf(param_buf, sizeof(param_buf), "up%dx-conservative.param", effective_scale);
            snprintf(bin_buf, sizeof(bin_buf), "up%dx-conservative.bin", effective_scale);
        } else if (effective_denoise == 0) {
            snprintf(param_buf, sizeof(param_buf), "up%dx-no-denoise.param", effective_scale);
            snprintf(bin_buf, sizeof(bin_buf), "up%dx-no-denoise.bin", effective_scale);
        } else {
            snprintf(param_buf, sizeof(param_buf), "up%dx-denoise%dx.param", effective_scale, effective_denoise);
            snprintf(bin_buf, sizeof(bin_buf), "up%dx-denoise%dx.bin", effective_scale, effective_denoise);
        }

        std::string param_path = "models/realcugan/" + effective_model_dir + "/" + param_buf;
        std::string bin_path = "models/realcugan/" + effective_model_dir + "/" + bin_buf;

        if (!check_asset_exists(mgr, param_path)) {
            hdify::NativeLogger::getInstance().log(hdify::LogLevel::WARN, "Engine", "",
                "Real-CUGAN param %s not found in assets, falling back to models-se up%dx-no-denoise", param_path.c_str(), effective_scale);
            effective_model_dir = "models-se";
            effective_denoise = 0;
            snprintf(param_buf, sizeof(param_buf), "up%dx-no-denoise.param", effective_scale);
            snprintf(bin_buf, sizeof(bin_buf), "up%dx-no-denoise.bin", effective_scale);
            param_path = "models/realcugan/" + effective_model_dir + "/" + param_buf;
            bin_path = "models/realcugan/" + effective_model_dir + "/" + bin_buf;
        }

        // Instantiate RealCUGAN
        ctx->realcugan = std::make_unique<RealCUGAN>(actualGpuId, false, 1);
        ctx->realcugan->scale = effective_scale;
        ctx->realcugan->noise = effective_denoise;
        ctx->realcugan->tilesize = ctx->tile_size;
        ctx->realcugan->prepadding = hdify::get_prepadding(scale);
        ctx->realcugan->syncgap = 0;
        ctx->realcugan->use_fp16 = ctx->fp16_active;
        ctx->realcugan->set_cancel_flag(&ctx->canceled);

        hdify::NativeLogger::getInstance().log(hdify::LogLevel::INFO, "Upscale", "",
            "backend=%s device=%s fp16=%s tile=%d model=%s",
            ctx->backend.c_str(), ctx->device_name.c_str(),
            ctx->fp16_active ? "true" : "false", ctx->tile_size, param_buf);

        int load_res = ctx->realcugan->load(mgr, param_path, bin_path);
        if (load_res != 0) {
            if (effective_model_dir != "models-se" || effective_denoise != 0) {
                hdify::NativeLogger::getInstance().log(hdify::LogLevel::WARN, "Engine", "",
                    "Failed to load %s, attempting fallback to models-se no-denoise", param_path.c_str());
                effective_model_dir = "models-se";
                effective_denoise = 0;
                snprintf(param_buf, sizeof(param_buf), "up%dx-no-denoise.param", effective_scale);
                snprintf(bin_buf, sizeof(bin_buf), "up%dx-no-denoise.bin", effective_scale);
                param_path = "models/realcugan/" + effective_model_dir + "/" + param_buf;
                bin_path = "models/realcugan/" + effective_model_dir + "/" + bin_buf;
                ctx->realcugan->noise = effective_denoise;
                load_res = ctx->realcugan->load(mgr, param_path, bin_path);
            }
            if (load_res != 0) {
                hdify::NativeLogger::getInstance().log(hdify::LogLevel::ERROR, "Engine", "",
                    "HD-MDL-201 Failed to load Real-CUGAN model: %s", param_path.c_str());
                ctx->realcugan.reset();
                return nullptr;
            }
        }
    }

    // Return EngineInfo object
    jclass cls = env->FindClass("com/example/hdify/domain/model/EngineInfo");
    if (!cls) return nullptr;

    jmethodID ctor = env->GetMethodID(cls, "<init>", "(Ljava/lang/String;Ljava/lang/String;ZI)V");
    if (!ctor) return nullptr;

    jstring backendStr = env->NewStringUTF(ctx->backend.c_str());
    jstring devStr = env->NewStringUTF(ctx->device_name.c_str());

    return env->NewObject(cls, ctor, backendStr, devStr, (jboolean)ctx->fp16_active, (jint)ctx->tile_size);
}

// Adaptive edge acutance filter to make upscaling scales (2x, 3x, 4x) visually distinctive and razor-sharp
static void apply_adaptive_acutance(ncnn::Mat& mat, int target_scale) {
    if (!mat.data || mat.w <= 2 || mat.h <= 2) return;

    // Acutance strength scales with magnification
    // 2x: gentle boost (0.20f) for clean crispness
    // 3x: distinct definition (0.36f)
    // 4x: ultra-definition punch (0.50f) for razor-sharp micro-textures
    float amount = 0.20f;
    if (target_scale == 3) amount = 0.36f;
    else if (target_scale >= 4) amount = 0.50f;

    int w = mat.w;
    int h = mat.h;
    int channels = (mat.elempack > 1) ? mat.elempack : (mat.elemsize == 4 ? 4 : 3);
    if (channels < 3) return;

    std::vector<uint8_t> orig_row(w * channels);
    std::vector<int> prev_blur(w * 3);
    std::vector<int> curr_blur(w * 3);
    std::vector<int> next_blur(w * 3);

    auto compute_row_blur = [&](int y, std::vector<int>& out_blur) {
        y = std::clamp(y, 0, h - 1);
        const uint8_t* row = (const uint8_t*)mat.data + y * w * channels;
        for (int x = 0; x < w; x++) {
            int x_prev = std::max(0, x - 1);
            int x_next = std::min(w - 1, x + 1);
            const uint8_t* p0 = row + x_prev * channels;
            const uint8_t* p1 = row + x * channels;
            const uint8_t* p2 = row + x_next * channels;
            for (int c = 0; c < 3; c++) {
                // Horizontal 1-2-1 kernel
                out_blur[x * 3 + c] = (int)p0[c] + ((int)p1[c] << 1) + (int)p2[c];
            }
        }
    };

    compute_row_blur(0, curr_blur);
    compute_row_blur(1, next_blur);
    prev_blur = curr_blur;

    for (int y = 0; y < h; y++) {
        if (y + 1 < h) {
            compute_row_blur(y + 1, next_blur);
        } else {
            next_blur = curr_blur;
        }

        uint8_t* row_data = (uint8_t*)mat.data + y * w * channels;
        memcpy(orig_row.data(), row_data, w * channels);

        for (int x = 0; x < w; x++) {
            for (int c = 0; c < 3; c++) {
                // Vertical 1-2-1 combination of horizontal blur = 2D Gaussian 3x3 filter (divisor 16)
                int blur_val = (prev_blur[x * 3 + c] + (curr_blur[x * 3 + c] << 1) + next_blur[x * 3 + c] + 8) >> 4;
                int orig_val = orig_row[x * channels + c];
                int high_pass = orig_val - blur_val;

                // Clamp high_pass to avoid overshoot/ringing artifacts on high contrast borders
                high_pass = std::clamp(high_pass, -32, 32);

                int enhanced = orig_val + (int)std::round(amount * high_pass);
                row_data[x * channels + c] = (uint8_t)std::clamp(enhanced, 0, 255);
            }
        }

        prev_blur = curr_blur;
        curr_blur = next_blur;
    }
}

JNIEXPORT jint JNICALL
Java_com_example_hdify_engine_NativeBridge_upscaleBitmap(
    JNIEnv* env, jobject /* this */,
    jlong handle,
    jobject inBitmap,
    jobject outBitmap,
    jobject progressCallback
) {
    auto ctx = hdify::EngineRegistry::getInstance().getUpscale(handle);
    if (!ctx) {
        return -1; // HD-SYS-901
    }
    std::unique_lock<std::mutex> exec_lock(ctx->exec_mutex);
    if (ctx->canceled.load()) return -2;
    if (ctx->engine_type == 1 && !ctx->realesrgan) return -1;
    if (ctx->engine_type == 0 && !ctx->realcugan) return -1;

    ctx->canceled.store(false);

    AndroidBitmapInfo inInfo{};
    AndroidBitmapInfo outInfo{};

    if (AndroidBitmap_getInfo(env, inBitmap, &inInfo) < 0 ||
        AndroidBitmap_getInfo(env, outBitmap, &outInfo) < 0) {
        return -301; // HD-IMG-301
    }

    if (inInfo.format != ANDROID_BITMAP_FORMAT_RGBA_8888 ||
        outInfo.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
        return -302; // HD-IMG-302 Invalid pixel format
    }

    if (outInfo.width != inInfo.width * ctx->scale ||
        outInfo.height != inInfo.height * ctx->scale) {
        return -303; // HD-IMG-303 Dimension mismatch
    }

    void* inPixels = nullptr;
    void* outPixels = nullptr;

    if (AndroidBitmap_lockPixels(env, inBitmap, &inPixels) < 0 || !inPixels) {
        return -301;
    }

    if (AndroidBitmap_lockPixels(env, outBitmap, &outPixels) < 0 || !outPixels) {
        AndroidBitmap_unlockPixels(env, inBitmap);
        return -301;
    }

    // Check stride and prepare contiguous buffers if needed
    bool inNeedsContiguous = (inInfo.stride != inInfo.width * 4);
    std::vector<uint8_t> inPackedBuf;
    void* inDataPtr = inPixels;
    if (inNeedsContiguous) {
        inPackedBuf.resize((size_t)inInfo.width * inInfo.height * 4);
        for (uint32_t y = 0; y < inInfo.height; ++y) {
            memcpy(inPackedBuf.data() + y * inInfo.width * 4,
                   (const uint8_t*)inPixels + y * inInfo.stride,
                   inInfo.width * 4);
        }
        inDataPtr = inPackedBuf.data();
    }

    bool outNeedsContiguous = (outInfo.stride != outInfo.width * 4);
    std::vector<uint8_t> outPackedBuf;
    void* outDataPtr = outPixels;
    if (outNeedsContiguous) {
        outPackedBuf.resize((size_t)outInfo.width * outInfo.height * 4);
        outDataPtr = outPackedBuf.data();
    }

    // Configure progress callback
    jclass cbClass = nullptr;
    jmethodID onProgressMethod = nullptr;
    if (progressCallback) {
        cbClass = env->GetObjectClass(progressCallback);
        if (cbClass) {
            onProgressMethod = env->GetMethodID(cbClass, "onProgress", "(II)V");
        }
    }

    // Wrap bitmaps in ncnn::Mat (channels = 4, RGBA: elemsize = 4u, elempack = 4)
    ncnn::Mat in_mat = ncnn::Mat(inInfo.width, inInfo.height, inDataPtr, (size_t)4u, 4);
    ncnn::Mat out_mat = ncnn::Mat(outInfo.width, outInfo.height, outDataPtr, (size_t)4u, 4);

    int ret = 0;
    if (ctx->engine_type == 1) {
        if (onProgressMethod) {
            ctx->realesrgan->set_progress_callback([&](int current, int total) {
                env->CallVoidMethod(progressCallback, onProgressMethod, (jint)current, (jint)total);
            });
        } else {
            ctx->realesrgan->set_progress_callback(nullptr);
        }

        if (ctx->backend == "cpu") {
            ret = ctx->realesrgan->process_cpu(in_mat, out_mat);
        } else {
            ret = ctx->realesrgan->process(in_mat, out_mat);
        }
    } else {
        if (onProgressMethod) {
            ctx->realcugan->set_progress_callback([&](int current, int total) {
                env->CallVoidMethod(progressCallback, onProgressMethod, (jint)current, (jint)total);
            });
        } else {
            ctx->realcugan->set_progress_callback(nullptr);
        }

        if (ctx->backend == "cpu") {
            ret = ctx->realcugan->process_cpu(in_mat, out_mat);
        } else {
            ret = ctx->realcugan->process(in_mat, out_mat);
        }
    }

    // Write back result to outPixels safely only if successful and not canceled
    if (ret == 0 && !ctx->canceled.load()) {
        apply_adaptive_acutance(out_mat, ctx->scale);
        const uint8_t* resultSrc = (out_mat.data && out_mat.data != outDataPtr)
                                   ? (const uint8_t*)out_mat.data
                                   : (const uint8_t*)outDataPtr;
        if (outNeedsContiguous) {
            for (uint32_t y = 0; y < outInfo.height; ++y) {
                memcpy((uint8_t*)outPixels + y * outInfo.stride,
                       resultSrc + y * outInfo.width * 4,
                       outInfo.width * 4);
            }
        } else if (resultSrc != outPixels) {
            memcpy(outPixels, resultSrc, (size_t)outInfo.width * outInfo.height * 4);
        }
    }

    AndroidBitmap_unlockPixels(env, outBitmap);
    AndroidBitmap_unlockPixels(env, inBitmap);

    if (ctx->canceled.load() || ret == -2) {
        return -2; // Canceled
    }

    return ret;
}

JNIEXPORT void JNICALL
Java_com_example_hdify_engine_NativeBridge_cancelEngine(JNIEnv* /* env */, jobject /* this */, jlong handle) {
    auto ctx = hdify::EngineRegistry::getInstance().getUpscale(handle);
    if (ctx) {
        ctx->canceled.store(true, std::memory_order_seq_cst);
    }
}

JNIEXPORT void JNICALL
Java_com_example_hdify_engine_NativeBridge_releaseEngine(JNIEnv* /* env */, jobject /* this */, jlong handle) {
    hdify::EngineRegistry::getInstance().releaseUpscale(handle);
}

// -------------------------------------------------------------------------------------------------
// RIFE Frame Interpolation (Fase 4)
// -------------------------------------------------------------------------------------------------

JNIEXPORT jlong JNICALL
Java_com_example_hdify_engine_NativeBridge_createRifeEngine(JNIEnv* /* env */, jobject /* this */) {
    auto ctx = std::make_shared<hdify::NativeInterpolationEngineContext>();
    return hdify::EngineRegistry::getInstance().registerRife(ctx);
}

JNIEXPORT jobject JNICALL
Java_com_example_hdify_engine_NativeBridge_initRifeEngine(
    JNIEnv* env, jobject /* this */,
    jlong handle,
    jobject assetManager,
    jstring modelDir,
    jint gpuIndex,
    jboolean useFp16,
    jboolean uhdMode,
    jboolean allowCpuFallback
) {
    auto ctx = hdify::EngineRegistry::getInstance().getRife(handle);
    if (!ctx) return nullptr;
    std::lock_guard<std::mutex> exec_lock(ctx->exec_mutex);

    const char* modelDirChars = env->GetStringUTFChars(modelDir, nullptr);
    std::string rawModelDir = modelDirChars ? modelDirChars : "models/rife/rife-v4.6";
    if (modelDirChars) env->ReleaseStringUTFChars(modelDir, modelDirChars);

    std::string modelDirectory = rawModelDir;
    while (!modelDirectory.empty() && (modelDirectory.front() == ' ' || modelDirectory.front() == '\t')) {
        modelDirectory.erase(modelDirectory.begin());
    }
    while (!modelDirectory.empty() && (modelDirectory.back() == ' ' || modelDirectory.back() == '\t')) {
        modelDirectory.pop_back();
    }
    while (!modelDirectory.empty() && (modelDirectory.front() == '/' || modelDirectory.front() == '\\')) {
        modelDirectory.erase(modelDirectory.begin());
    }
    while (!modelDirectory.empty() && (modelDirectory.back() == '/' || modelDirectory.back() == '\\')) {
        modelDirectory.pop_back();
    }
    if (modelDirectory.length() >= 14 && modelDirectory.compare(modelDirectory.length() - 14, 14, "/flownet.param") == 0) {
        modelDirectory = modelDirectory.substr(0, modelDirectory.length() - 14);
    } else if (modelDirectory.length() >= 12 && modelDirectory.compare(modelDirectory.length() - 12, 12, "/flownet.bin") == 0) {
        modelDirectory = modelDirectory.substr(0, modelDirectory.length() - 12);
    }

    if (modelDirectory.empty()) {
        modelDirectory = "models/rife/rife-v4.6";
    } else if (modelDirectory.rfind("models/rife/", 0) != 0) {
        modelDirectory = "models/rife/" + modelDirectory;
    }

    AAssetManager* mgr = AAssetManager_fromJava(env, assetManager);
    if (!mgr) return nullptr;

    if (!check_asset_exists(mgr, modelDirectory + "/flownet.param")) {
        hdify::NativeLogger::getInstance().log(hdify::LogLevel::WARN, "Engine", "",
            "RIFE model %s/flownet.param not found in assets, falling back to models/rife/rife-v4.6", modelDirectory.c_str());
        modelDirectory = "models/rife/rife-v4.6";
    }

    hdify::ensure_vulkan_initialized();

    // Check Vulkan availability
    int gpu_count = ncnn::get_gpu_count();
    int actualGpuId = -1;

    if (gpu_count > 0 && gpuIndex >= 0 && gpuIndex < gpu_count) {
        const auto* dev = ncnn::get_gpu_device(gpuIndex);
        if (dev) {
            actualGpuId = gpuIndex;
            ctx->backend = "vulkan";
            ctx->device_name = hdify::safe_str(dev->info.device_name(), "Vulkan GPU");
        }
    }

    if (actualGpuId < 0) {
        if (!allowCpuFallback) {
            hdify::NativeLogger::getInstance().log(hdify::LogLevel::ERROR, "Engine", "",
                "HD-HW-101 Vulkan unavailable and CPU fallback disallowed for RIFE");
            return nullptr;
        }
        ctx->backend = "cpu";
        ctx->device_name = "CPU (Fallback)";
    }

    ctx->fp16_active = (actualGpuId >= 0) && useFp16;
    ctx->canceled.store(false);

    // Instantiate RIFE with rife_v4 = true
    ctx->rife = std::make_unique<RIFE>(actualGpuId, false, false, (bool)uhdMode, 1, false, true);
    ctx->rife->set_cancel_flag(&ctx->canceled);
    ctx->rife->set_fp16(ctx->fp16_active);

    hdify::NativeLogger::getInstance().log(hdify::LogLevel::INFO, "Interpolation", "",
        "backend=%s device=%s fp16=%s model=%s uhd=%s",
        ctx->backend.c_str(), ctx->device_name.c_str(),
        ctx->fp16_active ? "true" : "false", modelDirectory.c_str(),
        uhdMode ? "true" : "false");

    int load_res = ctx->rife->load(mgr, modelDirectory);
    if (load_res != 0) {
        hdify::NativeLogger::getInstance().log(hdify::LogLevel::ERROR, "Engine", "",
            "HD-MDL-201 Failed to load RIFE model: %s", modelDirectory.c_str());
        ctx->rife.reset();
        return nullptr;
    }

    jclass cls = env->FindClass("com/example/hdify/domain/model/EngineInfo");
    if (!cls) return nullptr;

    jmethodID ctor = env->GetMethodID(cls, "<init>", "(Ljava/lang/String;Ljava/lang/String;ZI)V");
    if (!ctor) return nullptr;

    jstring backendStr = env->NewStringUTF(ctx->backend.c_str());
    jstring devStr = env->NewStringUTF(ctx->device_name.c_str());

    return env->NewObject(cls, ctor, backendStr, devStr, (jboolean)ctx->fp16_active, (jint)0);
}

JNIEXPORT jint JNICALL
Java_com_example_hdify_engine_NativeBridge_interpolateFrames(
    JNIEnv* env, jobject /* this */,
    jlong handle,
    jobject in0Bitmap,
    jobject in1Bitmap,
    jfloat timestep,
    jobject outBitmap
) {
    auto ctx = hdify::EngineRegistry::getInstance().getRife(handle);
    if (!ctx || !ctx->rife) return -1;
    std::unique_lock<std::mutex> exec_lock(ctx->exec_mutex);
    if (ctx->canceled.load()) return -2;

    AndroidBitmapInfo in0Info{};
    AndroidBitmapInfo in1Info{};
    AndroidBitmapInfo outInfo{};

    if (AndroidBitmap_getInfo(env, in0Bitmap, &in0Info) < 0 ||
        AndroidBitmap_getInfo(env, in1Bitmap, &in1Info) < 0 ||
        AndroidBitmap_getInfo(env, outBitmap, &outInfo) < 0) {
        return -301;
    }

    if (in0Info.format != ANDROID_BITMAP_FORMAT_RGBA_8888 ||
        in1Info.format != ANDROID_BITMAP_FORMAT_RGBA_8888 ||
        outInfo.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
        return -302;
    }

    int w = in0Info.width;
    int h = in0Info.height;

    if (in1Info.width != w || in1Info.height != h || outInfo.width != w || outInfo.height != h) {
        return -303;
    }

    void* p0 = nullptr;
    void* p1 = nullptr;
    void* pOut = nullptr;

    if (AndroidBitmap_lockPixels(env, in0Bitmap, &p0) < 0 || !p0) return -301;
    if (AndroidBitmap_lockPixels(env, in1Bitmap, &p1) < 0 || !p1) {
        AndroidBitmap_unlockPixels(env, in0Bitmap);
        return -301;
    }
    if (AndroidBitmap_lockPixels(env, outBitmap, &pOut) < 0 || !pOut) {
        AndroidBitmap_unlockPixels(env, in1Bitmap);
        AndroidBitmap_unlockPixels(env, in0Bitmap);
        return -301;
    }

    // RIFE expects in0image and in1image to contain packed RGB pixels (elemsize=3, elempack=1)
    ncnn::Mat mat0(w, h, (size_t)3u, 1);
    ncnn::Mat mat1(w, h, (size_t)3u, 1);
    if (!mat0.data || !mat1.data) {
        AndroidBitmap_unlockPixels(env, outBitmap);
        AndroidBitmap_unlockPixels(env, in1Bitmap);
        AndroidBitmap_unlockPixels(env, in0Bitmap);
        return -301;
    }

    const auto* src0_base = static_cast<const uint8_t*>(p0);
    const auto* src1_base = static_cast<const uint8_t*>(p1);
    auto* dst0 = static_cast<uint8_t*>(mat0.data);
    auto* dst1 = static_cast<uint8_t*>(mat1.data);

    for (int y = 0; y < h; ++y) {
        const auto* row0 = src0_base + y * in0Info.stride;
        const auto* row1 = src1_base + y * in1Info.stride;
        auto* out0 = dst0 + y * w * 3;
        auto* out1 = dst1 + y * w * 3;
        for (int x = 0; x < w; ++x) {
            out0[x * 3 + 0] = row0[x * 4 + 0];
            out0[x * 3 + 1] = row0[x * 4 + 1];
            out0[x * 3 + 2] = row0[x * 4 + 2];

            out1[x * 3 + 0] = row1[x * 4 + 0];
            out1[x * 3 + 1] = row1[x * 4 + 1];
            out1[x * 3 + 2] = row1[x * 4 + 2];
        }
    }

    ncnn::Mat matOut(w, h, (size_t)3u, 1);
    if (!matOut.data) {
        AndroidBitmap_unlockPixels(env, outBitmap);
        AndroidBitmap_unlockPixels(env, in1Bitmap);
        AndroidBitmap_unlockPixels(env, in0Bitmap);
        return -301;
    }

    int ret = ctx->rife->process(mat0, mat1, static_cast<float>(timestep), matOut);

    if (ret == 0 && matOut.data) {
        const auto* rgb_base = static_cast<const uint8_t*>(matOut.data);
        auto* rgba_base = static_cast<uint8_t*>(pOut);
        for (int y = 0; y < h; ++y) {
            const auto* rgb_row = rgb_base + y * w * 3;
            auto* rgba_row = rgba_base + y * outInfo.stride;
            for (int x = 0; x < w; ++x) {
                rgba_row[x * 4 + 0] = rgb_row[x * 3 + 0];
                rgba_row[x * 4 + 1] = rgb_row[x * 3 + 1];
                rgba_row[x * 4 + 2] = rgb_row[x * 3 + 2];
                rgba_row[x * 4 + 3] = 255;
            }
        }
    }

    AndroidBitmap_unlockPixels(env, outBitmap);
    AndroidBitmap_unlockPixels(env, in1Bitmap);
    AndroidBitmap_unlockPixels(env, in0Bitmap);

    if (ctx->canceled.load()) return -2;
    return ret;
}

JNIEXPORT jboolean JNICALL
Java_com_example_hdify_engine_NativeBridge_detectSceneCut(
    JNIEnv* env, jobject /* this */,
    jobject in0Bitmap,
    jobject in1Bitmap,
    jfloat threshold
) {
    AndroidBitmapInfo info0{};
    AndroidBitmapInfo info1{};
    if (AndroidBitmap_getInfo(env, in0Bitmap, &info0) < 0 ||
        AndroidBitmap_getInfo(env, in1Bitmap, &info1) < 0) {
        return JNI_FALSE;
    }
    if (info0.width != info1.width || info0.height != info1.height) {
        return JNI_TRUE;
    }

    void* p0 = nullptr;
    void* p1 = nullptr;
    if (AndroidBitmap_lockPixels(env, in0Bitmap, &p0) < 0 || !p0) return JNI_FALSE;
    if (AndroidBitmap_lockPixels(env, in1Bitmap, &p1) < 0 || !p1) {
        AndroidBitmap_unlockPixels(env, in0Bitmap);
        return JNI_FALSE;
    }

    const auto* u0 = static_cast<const uint32_t*>(p0);
    const auto* u1 = static_cast<const uint32_t*>(p1);
    int total_pixels = info0.width * info0.height;

    int step = 4;
    int sampled_count = 0;
    double total_diff = 0.0;

    for (int i = 0; i < total_pixels; i += step) {
        uint32_t c0 = u0[i];
        uint32_t c1 = u1[i];

        int r0 = (c0) & 0xFF;
        int g0 = (c0 >> 8) & 0xFF;
        int b0 = (c0 >> 16) & 0xFF;

        int r1 = (c1) & 0xFF;
        int g1 = (c1 >> 8) & 0xFF;
        int b1 = (c1 >> 16) & 0xFF;

        int diff = std::abs(r0 - r1) + std::abs(g0 - g1) + std::abs(b0 - b1);
        total_diff += diff / (3.0 * 255.0);
        sampled_count++;
    }

    AndroidBitmap_unlockPixels(env, in1Bitmap);
    AndroidBitmap_unlockPixels(env, in0Bitmap);

    double avg_diff = (sampled_count > 0) ? (total_diff / sampled_count) : 0.0;
    return (avg_diff >= threshold) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_example_hdify_engine_NativeBridge_cancelRifeEngine(JNIEnv* /* env */, jobject /* this */, jlong handle) {
    auto ctx = hdify::EngineRegistry::getInstance().getRife(handle);
    if (ctx) {
        ctx->canceled.store(true, std::memory_order_seq_cst);
    }
}

JNIEXPORT void JNICALL
Java_com_example_hdify_engine_NativeBridge_releaseRifeEngine(JNIEnv* /* env */, jobject /* this */, jlong handle) {
    hdify::EngineRegistry::getInstance().releaseRife(handle);
}

} // extern "C"
