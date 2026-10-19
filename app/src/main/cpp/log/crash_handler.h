#ifndef HDIFY_CRASH_HANDLER_H
#define HDIFY_CRASH_HANDLER_H

#include <string>

namespace hdify {

class NativeCrashHandler {
public:
    static NativeCrashHandler& getInstance();

    bool install(const std::string& crashDir);
    void uninstall();

    static void triggerCrashForTesting();

private:
    NativeCrashHandler() = default;
    bool installed_ = false;
};

} // namespace hdify

#endif // HDIFY_CRASH_HANDLER_H
