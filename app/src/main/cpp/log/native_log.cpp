#include "native_log.h"
#include <android/log.h>
#include <mutex>
#include <cstdio>
#include <cstdarg>
#include <chrono>
#include <ctime>
#include <fstream>
#include <sstream>
#include <iomanip>

namespace hdify {

static std::mutex g_logMutex;

NativeLogger& NativeLogger::getInstance() {
    static NativeLogger instance;
    return instance;
}

void NativeLogger::setLogFilePath(const std::string& path) {
    std::lock_guard<std::mutex> lock(g_logMutex);
    logFilePath_ = path;
}

void NativeLogger::setVerbose(bool verbose) {
    std::lock_guard<std::mutex> lock(g_logMutex);
    verbose_ = verbose;
}

static android_LogPriority toAndroidPriority(LogLevel level) {
    switch (level) {
        case LogLevel::VERBOSE: return ANDROID_LOG_VERBOSE;
        case LogLevel::DEBUG:   return ANDROID_LOG_DEBUG;
        case LogLevel::INFO:    return ANDROID_LOG_INFO;
        case LogLevel::WARN:    return ANDROID_LOG_WARN;
        case LogLevel::ERROR:   return ANDROID_LOG_ERROR;
        default:                return ANDROID_LOG_INFO;
    }
}

static char toLevelChar(LogLevel level) {
    switch (level) {
        case LogLevel::VERBOSE: return 'V';
        case LogLevel::DEBUG:   return 'D';
        case LogLevel::INFO:    return 'I';
        case LogLevel::WARN:    return 'W';
        case LogLevel::ERROR:   return 'E';
        default:                return 'I';
    }
}

void NativeLogger::log(LogLevel level, const char* tag, const char* context, const char* format, ...) {
    if (!verbose_ && (level == LogLevel::VERBOSE || level == LogLevel::DEBUG)) {
        return;
    }

    char messageBuf[2048];
    va_list args;
    va_start(args, format);
    vsnprintf(messageBuf, sizeof(messageBuf), format, args);
    va_end(args);

    // Format for logcat
    std::string tagStr = (tag && *tag) ? tag : "HDifyNative";
    std::string logcatMsg;
    if (context && *context) {
        logcatMsg = std::string(context) + " " + messageBuf;
    } else {
        logcatMsg = messageBuf;
    }
    __android_log_print(toAndroidPriority(level), tagStr.c_str(), "%s", logcatMsg.c_str());

    // Format for shared log file
    auto now = std::chrono::system_clock::now();
    auto ms = std::chrono::duration_cast<std::chrono::milliseconds>(now.time_since_epoch()) % 1000;
    std::time_t timer = std::chrono::system_clock::to_time_t(now);
    std::tm bt;
    localtime_r(&timer, &bt);

    std::ostringstream oss;
    oss << std::put_time(&bt, "%Y-%m-%d %H:%M:%S")
        << '.' << std::setfill('0') << std::setw(3) << ms.count()
        << ' ' << toLevelChar(level) << '/' << tagStr;

    if (context && *context) {
        oss << ' ' << context;
    }
    oss << ' ' << messageBuf << '\n';

    std::string line = oss.str();

    std::lock_guard<std::mutex> lock(g_logMutex);
    if (!logFilePath_.empty()) {
        std::ofstream file(logFilePath_, std::ios::app);
        if (file.is_open()) {
            file << line;
            file.flush();
        }
    }
}

} // namespace hdify
