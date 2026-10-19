#ifndef HDIFY_GPU_INFO_H
#define HDIFY_GPU_INFO_H

#include <string>
#include <cstdint>

namespace hdify {

inline std::string safe_str(const char* s, const char* fallback = "") {
    return s ? std::string(s) : std::string(fallback);
}

int ensure_vulkan_initialized();

struct GpuDeviceInfo {
    int index = 0;
    std::string name;
    std::string type;
    std::string apiVersion;
    std::string driverVersion;
    uint32_t vendorId = 0;
    uint32_t deviceId = 0;
    uint64_t memoryHeapBytes = 0;
    bool supportFp16Storage = false;
    bool supportFp16Arithmetic = false;
    bool supportFp16Packed = false;
};

struct SystemGpuInfo {
    bool vulkanAvailable = false;
    int gpuCount = 0;
    std::string errorMessage;
    GpuDeviceInfo primaryDevice;
};

class GpuManager {
public:
    static GpuManager& getInstance();
    SystemGpuInfo querySystemGpuInfo();

private:
    GpuManager() = default;
    bool initialized_ = false;
    int initStatus_ = -1;
};

} // namespace hdify

#endif // HDIFY_GPU_INFO_H
