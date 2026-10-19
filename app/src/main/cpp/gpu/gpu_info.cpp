#include "gpu_info.h"
#include <ncnn/gpu.h>
#include <sstream>
#include <iomanip>

#include <mutex>

namespace hdify {

int ensure_vulkan_initialized() {
    static std::mutex g_vk_mutex;
    static bool g_vk_inited = false;
    static int g_vk_status = -1;
    std::lock_guard<std::mutex> lock(g_vk_mutex);
    if (!g_vk_inited) {
        g_vk_status = ncnn::create_gpu_instance();
        g_vk_inited = true;
    }
    return g_vk_status;
}

GpuManager& GpuManager::getInstance() {
    static GpuManager instance;
    return instance;
}

static std::string formatVkVersion(uint32_t version) {
    uint32_t major = (version >> 22) & 0x7F;
    uint32_t minor = (version >> 12) & 0x3FF;
    uint32_t patch = version & 0xFFF;
    std::ostringstream oss;
    oss << major << "." << minor << "." << patch;
    return oss.str();
}

static std::string formatDeviceType(VkPhysicalDeviceType type) {
    switch (type) {
        case VK_PHYSICAL_DEVICE_TYPE_INTEGRATED_GPU:
            return "Integrated GPU";
        case VK_PHYSICAL_DEVICE_TYPE_DISCRETE_GPU:
            return "Discrete GPU";
        case VK_PHYSICAL_DEVICE_TYPE_VIRTUAL_GPU:
            return "Virtual GPU";
        case VK_PHYSICAL_DEVICE_TYPE_CPU:
            return "CPU (Software)";
        default:
            return "Other / Unknown";
    }
}

SystemGpuInfo GpuManager::querySystemGpuInfo() {
    SystemGpuInfo sysInfo;

    initStatus_ = ensure_vulkan_initialized();

    if (initStatus_ != 0) {
        sysInfo.vulkanAvailable = false;
        sysInfo.gpuCount = 0;
        sysInfo.errorMessage = "Failed to initialize Vulkan instance (code " + std::to_string(initStatus_) + ")";
        return sysInfo;
    }

    int count = ncnn::get_gpu_count();
    sysInfo.gpuCount = count;

    if (count <= 0) {
        sysInfo.vulkanAvailable = false;
        sysInfo.errorMessage = "No compatible Vulkan GPU found";
        return sysInfo;
    }

    sysInfo.vulkanAvailable = true;

    // Read device 0 (primary GPU)
    const ncnn::GpuInfo& info = ncnn::get_gpu_info(0);
    GpuDeviceInfo dev;
    dev.index = 0;
    dev.name = safe_str(info.device_name(), "Unknown GPU");
    dev.vendorId = info.vendor_id();
    dev.deviceId = info.device_id();
    dev.apiVersion = formatVkVersion(info.api_version());
    dev.driverVersion = formatVkVersion(info.driver_version());

    const VkPhysicalDeviceProperties& props = info.physicalDeviceProperties();
    dev.type = formatDeviceType(props.deviceType);

    const VkPhysicalDeviceMemoryProperties& memProps = info.physicalDeviceMemoryProperties();
    uint64_t totalHeapBytes = 0;
    for (uint32_t i = 0; i < memProps.memoryHeapCount; ++i) {
        if (memProps.memoryHeaps[i].flags & VK_MEMORY_HEAP_DEVICE_LOCAL_BIT) {
            totalHeapBytes += memProps.memoryHeaps[i].size;
        }
    }
    dev.memoryHeapBytes = totalHeapBytes;

    dev.supportFp16Storage = info.support_fp16_storage();
    dev.supportFp16Arithmetic = info.support_fp16_arithmetic();
    dev.supportFp16Packed = info.support_fp16_packed();

    sysInfo.primaryDevice = dev;
    return sysInfo;
}

} // namespace hdify
