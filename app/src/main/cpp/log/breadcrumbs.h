#ifndef HDIFY_BREADCRUMBS_H
#define HDIFY_BREADCRUMBS_H

#include <cstdint>
#include <cstddef>

namespace hdify {

constexpr size_t MAX_BREADCRUMBS = 64;
constexpr size_t BREADCRUMB_MSG_LEN = 256;

struct BreadcrumbEntry {
    int64_t timestampMs;
    char message[BREADCRUMB_MSG_LEN];
};

class Breadcrumbs {
public:
    static Breadcrumbs& getInstance();

    void add(const char* message);
    void dumpToFd(int fd); // Async-signal-safe dump for crash handler

private:
    Breadcrumbs();
    BreadcrumbEntry entries_[MAX_BREADCRUMBS];
    volatile uint32_t head_ = 0;
    volatile uint32_t count_ = 0;
};

} // namespace hdify

#endif // HDIFY_BREADCRUMBS_H
