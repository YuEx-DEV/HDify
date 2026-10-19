#include "crash_handler.h"
#include "breadcrumbs.h"
#include <signal.h>
#include <unistd.h>
#include <fcntl.h>
#include <cstring>
#include <sys/syscall.h>
#include <ctime>

namespace hdify {

static constexpr size_t CRASH_PATH_MAX = 512;
static char g_crashDir[CRASH_PATH_MAX] = {0};
static stack_t g_signalStack;
static char g_altStackMem[SIGSTKSZ * 2];
static struct sigaction g_oldActions[32];

static const int MONITORED_SIGNALS[] = {
    SIGSEGV, SIGABRT, SIGBUS, SIGFPE, SIGILL
};

static pid_t getThreadId() {
    return (pid_t)syscall(__NR_gettid);
}

static void safeWrite(int fd, const char* str) {
    if (str && fd >= 0) {
        (void)write(fd, str, std::strlen(str));
    }
}

static void safeWriteHex(int fd, uintptr_t val) {
    char buf[32];
    int pos = 0;
    buf[pos++] = '0';
    buf[pos++] = 'x';
    if (val == 0) {
        buf[pos++] = '0';
        buf[pos] = '\0';
        safeWrite(fd, buf);
        return;
    }
    char hexDigits[] = "0123456789abcdef";
    char rev[20];
    int revPos = 0;
    while (val > 0 && revPos < 18) {
        rev[revPos++] = hexDigits[val & 0xF];
        val >>= 4;
    }
    while (revPos > 0) {
        buf[pos++] = rev[--revPos];
    }
    buf[pos] = '\0';
    safeWrite(fd, buf);
}

static void safeWriteInt(int fd, int64_t val) {
    char buf[32];
    int pos = 0;
    if (val == 0) {
        buf[pos++] = '0';
        buf[pos] = '\0';
        safeWrite(fd, buf);
        return;
    }
    if (val < 0) {
        buf[pos++] = '-';
        val = -val;
    }
    char rev[30];
    int revPos = 0;
    while (val > 0 && revPos < 28) {
        rev[revPos++] = '0' + (val % 10);
        val /= 10;
    }
    while (revPos > 0) {
        buf[pos++] = rev[--revPos];
    }
    buf[pos] = '\0';
    safeWrite(fd, buf);
}

static const char* getSignalName(int sig) {
    switch (sig) {
        case SIGSEGV: return "SIGSEGV (Segmentation violation)";
        case SIGABRT: return "SIGABRT (Abort)";
        case SIGBUS:  return "SIGBUS (Bus error)";
        case SIGFPE:  return "SIGFPE (Floating point exception)";
        case SIGILL:  return "SIGILL (Illegal instruction)";
        default:      return "UNKNOWN SIGNAL";
    }
}

static void signalHandler(int sig, siginfo_t* info, void* ucontext) {
    // Generate filename based on timestamp
    time_t now = time(nullptr);
    char filepath[CRASH_PATH_MAX + 64];
    std::strncpy(filepath, g_crashDir, CRASH_PATH_MAX);
    size_t len = std::strlen(filepath);
    if (len > 0 && filepath[len - 1] != '/') {
        filepath[len++] = '/';
        filepath[len] = '\0';
    }

    const char* prefix = "crash_native_";
    std::strcat(filepath, prefix);
    char timeBuf[32];
    int tPos = 0;
    time_t tVal = now;
    if (tVal == 0) timeBuf[tPos++] = '0';
    char tRev[24]; int tRevPos = 0;
    while (tVal > 0 && tRevPos < 20) {
        tRev[tRevPos++] = '0' + (tVal % 10);
        tVal /= 10;
    }
    while (tRevPos > 0) timeBuf[tPos++] = tRev[--tRevPos];
    timeBuf[tPos] = '\0';
    std::strcat(filepath, timeBuf);
    std::strcat(filepath, ".txt");

    int fd = open(filepath, O_WRONLY | O_CREAT | O_TRUNC, 0644);
    if (fd >= 0) {
        safeWrite(fd, "=== HDIFY NATIVE CRASH REPORT ===\n");
        safeWrite(fd, "Signal: ");
        safeWrite(fd, getSignalName(sig));
        safeWrite(fd, " (code ");
        safeWriteInt(fd, sig);
        safeWrite(fd, ")\n");

        safeWrite(fd, "Thread ID: ");
        safeWriteInt(fd, getThreadId());
        safeWrite(fd, "\n");

        safeWrite(fd, "Fault Address: ");
        safeWriteHex(fd, (uintptr_t)info->si_addr);
        safeWrite(fd, "\n");

        safeWrite(fd, "Timestamp: ");
        safeWriteInt(fd, now);
        safeWrite(fd, "\n");

        // Dump native breadcrumbs
        Breadcrumbs::getInstance().dumpToFd(fd);

        safeWrite(fd, "=== END NATIVE CRASH REPORT ===\n");
        fsync(fd);
        close(fd);
    }

    // Call previous handler or re-raise
    if (sig < 32 && g_oldActions[sig].sa_sigaction) {
        g_oldActions[sig].sa_sigaction(sig, info, ucontext);
    } else {
        signal(sig, SIG_DFL);
        raise(sig);
    }
}

NativeCrashHandler& NativeCrashHandler::getInstance() {
    static NativeCrashHandler instance;
    return instance;
}

bool NativeCrashHandler::install(const std::string& crashDir) {
    if (installed_) return true;

    std::strncpy(g_crashDir, crashDir.c_str(), CRASH_PATH_MAX - 1);
    g_crashDir[CRASH_PATH_MAX - 1] = '\0';

    // Set alternate stack
    g_signalStack.ss_sp = g_altStackMem;
    g_signalStack.ss_size = sizeof(g_altStackMem);
    g_signalStack.ss_flags = 0;
    if (sigaltstack(&g_signalStack, nullptr) != 0) {
        return false;
    }

    struct sigaction sa;
    std::memset(&sa, 0, sizeof(sa));
    sa.sa_sigaction = signalHandler;
    sa.sa_flags = SA_SIGINFO | SA_ONSTACK;
    sigemptyset(&sa.sa_mask);

    for (int sig : MONITORED_SIGNALS) {
        if (sig < 32) {
            sigaction(sig, &sa, &g_oldActions[sig]);
        }
    }

    installed_ = true;
    return true;
}

void NativeCrashHandler::uninstall() {
    if (!installed_) return;
    for (int sig : MONITORED_SIGNALS) {
        if (sig < 32) {
            sigaction(sig, &g_oldActions[sig], nullptr);
        }
    }
    installed_ = false;
}

void NativeCrashHandler::triggerCrashForTesting() {
    // Deliberate SIGSEGV write to null pointer for debug testing
    volatile int* ptr = nullptr;
    *ptr = 42;
}

} // namespace hdify
