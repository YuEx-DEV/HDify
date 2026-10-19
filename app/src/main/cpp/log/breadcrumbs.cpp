#include "breadcrumbs.h"
#include <unistd.h>
#include <cstring>
#include <ctime>
#include <chrono>

namespace hdify {

Breadcrumbs::Breadcrumbs() {
    std::memset(entries_, 0, sizeof(entries_));
}

Breadcrumbs& Breadcrumbs::getInstance() {
    static Breadcrumbs instance;
    return instance;
}

void Breadcrumbs::add(const char* message) {
    if (!message) return;

    auto now = std::chrono::system_clock::now();
    int64_t ms = std::chrono::duration_cast<std::chrono::milliseconds>(
        now.time_since_epoch()
    ).count();

    uint32_t idx = head_ % MAX_BREADCRUMBS;
    entries_[idx].timestampMs = ms;
    std::strncpy(entries_[idx].message, message, BREADCRUMB_MSG_LEN - 1);
    entries_[idx].message[BREADCRUMB_MSG_LEN - 1] = '\0';

    head_ = (head_ + 1);
    if (count_ < MAX_BREADCRUMBS) {
        count_++;
    }
}

static void writeInt64(int fd, int64_t val) {
    char buf[32];
    int pos = 0;
    if (val == 0) {
        char zero = '0';
        (void)write(fd, &zero, 1);
        return;
    }
    if (val < 0) {
        char minus = '-';
        (void)write(fd, &minus, 1);
        val = -val;
    }
    while (val > 0 && pos < 30) {
        buf[pos++] = '0' + (val % 10);
        val /= 10;
    }
    for (int i = 0; i < pos / 2; ++i) {
        char tmp = buf[i];
        buf[i] = buf[pos - 1 - i];
        buf[pos - 1 - i] = tmp;
    }
    (void)write(fd, buf, pos);
}

void Breadcrumbs::dumpToFd(int fd) {
    const char* header = "\n--- NATIVE BREADCRUMBS DUMP ---\n";
    (void)write(fd, header, std::strlen(header));

    uint32_t total = count_;
    uint32_t startIdx = (head_ >= total) ? (head_ - total) : 0;

    for (uint32_t i = 0; i < total; ++i) {
        uint32_t idx = (startIdx + i) % MAX_BREADCRUMBS;
        const char* prefix = "[BC ";
        (void)write(fd, prefix, std::strlen(prefix));
        writeInt64(fd, entries_[idx].timestampMs);
        const char* mid = "] ";
        (void)write(fd, mid, std::strlen(mid));
        (void)write(fd, entries_[idx].message, std::strlen(entries_[idx].message));
        const char* newline = "\n";
        (void)write(fd, newline, 1);
    }
    const char* footer = "--- END BREADCRUMBS ---\n";
    (void)write(fd, footer, std::strlen(footer));
}

} // namespace hdify
