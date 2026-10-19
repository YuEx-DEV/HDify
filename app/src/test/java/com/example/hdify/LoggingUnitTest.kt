package com.example.hdify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LoggingUnitTest {

    @Test
    fun testErrorCodeFormat() {
        val validCodes = listOf(
            "HD-HW-101",
            "HD-HW-102",
            "HD-MDL-201",
            "HD-IMG-301",
            "HD-VID-401",
            "HD-ENC-501",
            "HD-STO-601",
            "HD-SYS-901"
        )
        val pattern = Regex("^HD-[A-Z]{2,3}-\\d{3}$")
        for (code in validCodes) {
            assertTrue("Format code $code must match HD-<CATEGORY>-<NNN>", pattern.matches(code))
        }
    }

    @Test
    fun testMemoryHeapMbCalculation() {
        val bytes = 4L * 1024L * 1024L * 1024L // 4 GB
        val mb = bytes / (1024 * 1024)
        assertEquals(4096L, mb)
    }

    @Test
    fun testLogLineFormatDetection() {
        val sampleLine = "2026-10-07 14:03:22.481 I/Upscale [job=42 stage=UPSCALE tile=12/80] backend=vulkan device=Adreno650"
        val parts = sampleLine.split(" ")
        assertTrue(parts.size >= 3)
        assertEquals("I/Upscale", parts[2])
        assertEquals('I', parts[2][0])
    }
}
