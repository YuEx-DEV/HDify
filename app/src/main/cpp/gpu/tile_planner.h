#ifndef TILE_PLANNER_H
#define TILE_PLANNER_H

#include <cstdint>
#include <algorithm>

namespace hdify {

/**
 * Auto-tunes Real-CUGAN tile size based on GPU heap budget and scale factor (FR-HW-06).
 * Based on official realcugan-ncnn-vulkan memory budget thresholds.
 */
inline int plan_tile_size(uint32_t heap_budget_mb, int scale, int requested_tile_size = 0) {
    if (requested_tile_size > 0) {
        return requested_tile_size;
    }

    if (scale == 2) {
        if (heap_budget_mb > 1300) return 400;
        if (heap_budget_mb > 800)  return 300;
        if (heap_budget_mb > 400)  return 200;
        if (heap_budget_mb > 200)  return 100;
        return 64;
    }
    if (scale == 3) {
        if (heap_budget_mb > 3300) return 400;
        if (heap_budget_mb > 1900) return 300;
        if (heap_budget_mb > 950)  return 200;
        if (heap_budget_mb > 320)  return 100;
        return 64;
    }
    if (scale == 4) {
        if (heap_budget_mb > 1690) return 400;
        if (heap_budget_mb > 980)  return 300;
        if (heap_budget_mb > 530)  return 200;
        if (heap_budget_mb > 240)  return 100;
        return 64;
    }
    return 200;
}

/**
 * Auto-tunes Real-ESRGAN tile size based on GPU heap budget and scale factor.
 * Real-ESRGAN (RRDBNet) has 345 convolutional layers, ~20x heavier than Real-CUGAN.
 * On mobile GPUs (Mali / Adreno), tile size must be kept around 64-128 to prevent
 * GPU pipeline starvation, driver hangs, Davey UI stalls, and to ensure cancel
 * responsiveness within 1-2 seconds per tile.
 */
inline int plan_realesrgan_tile_size(uint32_t heap_budget_mb, int scale, int requested_tile_size = 0) {
    if (requested_tile_size > 0) {
        return std::min(requested_tile_size, 200);
    }

    // Aligned with official Nihui RealESRGAN tile size policy:
    // heap_budget > 1900 -> 200
    // heap_budget > 500  -> 100
    // heap_budget > 190  -> 64
    // otherwise          -> 32
    if (heap_budget_mb > 1900) {
        return 200;
    } else if (heap_budget_mb > 500) {
        return 100;
    } else if (heap_budget_mb > 190) {
        return 64;
    }
    return 32;
}

/**
 * Returns default prepadding for Real-CUGAN models.
 */
inline int get_prepadding(int scale) {
    if (scale == 2) return 18;
    if (scale == 3) return 14;
    if (scale == 4) return 19;
    return 18;
}

} // namespace hdify

#endif // TILE_PLANNER_H
