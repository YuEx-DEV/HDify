#ifndef HDIFY_NATIVE_LOG_H
#define HDIFY_NATIVE_LOG_H

#include <string>

namespace hdify {

enum class LogLevel {
    VERBOSE,
    DEBUG,
    INFO,
    WARN,
    ERROR
};

class NativeLogger {
public:
    static NativeLogger& getInstance();

    void setLogFilePath(const std::string& path);
    void setVerbose(bool verbose);

    void log(LogLevel level, const char* tag, const char* context, const char* format, ...);

private:
    NativeLogger() = default;
    std::string logFilePath_;
    bool verbose_ = false;
};

#define HD_LOGV(tag, ctx, fmt, ...) hdify::NativeLogger::getInstance().log(hdify::LogLevel::VERBOSE, tag, ctx, fmt, ##__VA_ARGS__)
#define HD_LOGD(tag, ctx, fmt, ...) hdify::NativeLogger::getInstance().log(hdify::LogLevel::DEBUG, tag, ctx, fmt, ##__VA_ARGS__)
#define HD_LOGI(tag, ctx, fmt, ...) hdify::NativeLogger::getInstance().log(hdify::LogLevel::INFO, tag, ctx, fmt, ##__VA_ARGS__)
#define HD_LOGW(tag, ctx, fmt, ...) hdify::NativeLogger::getInstance().log(hdify::LogLevel::WARN, tag, ctx, fmt, ##__VA_ARGS__)
#define HD_LOGE(tag, ctx, fmt, ...) hdify::NativeLogger::getInstance().log(hdify::LogLevel::ERROR, tag, ctx, fmt, ##__VA_ARGS__)

} // namespace hdify

#endif // HDIFY_NATIVE_LOG_H
