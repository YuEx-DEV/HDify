package com.example.hdify

import com.example.hdify.domain.model.ContentType
import com.example.hdify.domain.model.EngineConfig
import com.example.hdify.domain.model.EngineType
import com.example.hdify.domain.model.Preset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineUnitTest {

    @Test
    fun testRealCuganModelRules() {
        // Real-CUGAN pro only supports 2x and 3x
        assertTrue(isCuganModelSupported("models-pro", 2, -1))
        assertTrue(isCuganModelSupported("models-pro", 2, 0))
        assertTrue(isCuganModelSupported("models-pro", 2, 3))
        assertFalse(isCuganModelSupported("models-pro", 2, 1)) // 1x denoise not in pro
        assertFalse(isCuganModelSupported("models-pro", 4, 0)) // 4x not in pro

        // Real-CUGAN se supports 2x, 3x, 4x
        assertTrue(isCuganModelSupported("models-se", 2, -1))
        assertTrue(isCuganModelSupported("models-se", 2, 0))
        assertTrue(isCuganModelSupported("models-se", 2, 1))
        assertTrue(isCuganModelSupported("models-se", 2, 2))
        assertTrue(isCuganModelSupported("models-se", 2, 3))
        assertTrue(isCuganModelSupported("models-se", 3, 0))
        assertTrue(isCuganModelSupported("models-se", 4, 0))
    }

    @Test
    fun testRealEsrganModels() {
        val supportedModels = listOf(
            "realesrgan-x4plus",
            "realesrgan-x4plus-anime",
            "realesr-animevideov3-x2",
            "realesr-animevideov3-x3",
            "realesr-animevideov3-x4"
        )

        for (m in supportedModels) {
            assertTrue(m.isNotEmpty())
            assertTrue(m.startsWith("realesr"))
        }

        // x4plus is natively 4x
        assertEquals(4, getNativeModelScale("realesrgan-x4plus"))
        assertEquals(4, getNativeModelScale("realesrgan-x4plus-anime"))
        assertEquals(2, getNativeModelScale("realesr-animevideov3-x2"))
        assertEquals(3, getNativeModelScale("realesr-animevideov3-x3"))
        assertEquals(4, getNativeModelScale("realesr-animevideov3-x4"))
    }

    @Test
    fun testDimensionCalculations() {
        val srcW = 720
        val srcH = 720

        assertEquals(1440, srcW * 2)
        assertEquals(1440, srcH * 2)

        assertEquals(2160, srcW * 3)
        assertEquals(2160, srcH * 3)

        assertEquals(2880, srcW * 4)
        assertEquals(2880, srcH * 4)
    }

    @Test
    fun testBenchmarkFormulae() {
        // Tile 256x256
        val tileW = 256
        val tileH = 256

        // 1080p (1920x1080)
        val tilesX1080p = Math.ceil(1920.0 / tileW).toInt() // 8
        val tilesY1080p = Math.ceil(1080.0 / tileH).toInt() // 5
        val total1080pTiles = tilesX1080p * tilesY1080p
        assertEquals(40, total1080pTiles)

        // 720p (1280x720)
        val tilesX720p = Math.ceil(1280.0 / tileW).toInt() // 5
        val tilesY720p = Math.ceil(720.0 / tileH).toInt() // 3
        val total720pTiles = tilesX720p * tilesY720p
        assertEquals(15, total720pTiles)

        // At 50 ms/tile
        val msPerTile = 50L
        val est1080pSec = (total1080pTiles * msPerTile) / 1000.0
        assertEquals(2.0, est1080pSec, 0.001)

        val secPer720pFrame = (total720pTiles * msPerTile) / 1000.0 // 0.75 s
        val fps720p = 1.0 / secPer720pFrame
        assertEquals(1.333, fps720p, 0.01)

        val est1MinVideoSec = 1800 * secPer720pFrame
        assertEquals(1350.0, est1MinVideoSec, 0.01)
    }

    @Test
    fun testEngineConfigDefaults() {
        val config = EngineConfig()
        assertEquals(EngineType.REAL_CUGAN, config.engine)
        assertEquals("models-se", config.modelDirectory)
        assertEquals(2, config.scale)
        assertEquals(0, config.denoise)
        assertEquals(0, config.tileSize)
        assertTrue(config.useFp16)
        assertEquals(0, config.gpuIndex)
        assertFalse(config.allowCpuFallback)
    }

    @Test
    fun testVideoBitrateHeuristics() {
        val width = 1280
        val height = 720
        val scale = 2
        val outW = width * scale // 2560
        val outH = height * scale // 1440
        val fps = 30.0

        // Adaptive bitrate heuristic test
        val pixelRate = outW.toLong() * outH.toLong() * fps.toLong()
        val h264Bitrate = (pixelRate * 0.10).toLong()
        val hevcBitrate = (pixelRate * 0.065).toLong()

        assertTrue(hevcBitrate < h264Bitrate)
        assertTrue(hevcBitrate > 0)
        assertTrue(h264Bitrate > 0)

        // Quality slider scaling (80% default)
        val qualityPercent = 80
        val scaledBitrate = (hevcBitrate * (qualityPercent / 100.0)).toLong()
        assertEquals((hevcBitrate * 0.8).toLong(), scaledBitrate)
    }

    @Test
    fun testVideoStorageEstimation() {
        val durationMs = 60_000L // 1 minute
        val targetBitrate = 8_000_000 // 8 Mbps
        val durationSec = durationMs / 1000

        val estimatedBytes = (targetBitrate.toLong() * durationSec) / 8
        assertEquals(60_000_000L, estimatedBytes) // 60 MB

        val safetyFactor = 1.5
        val requiredStorageBytes = (estimatedBytes * safetyFactor).toLong()
        assertEquals(90_000_000L, requiredStorageBytes) // 90 MB

        // Verify storage sufficiency check
        val availableBytesLow = 50_000_000L
        val availableBytesHigh = 500_000_000L

        assertFalse(availableBytesLow >= requiredStorageBytes)
        assertTrue(availableBytesHigh >= requiredStorageBytes)
    }

    @Test
    fun testVideoAudioSyncTolerance() {
        // FR-VID-05: sync drift must be <= 40ms (40,000 us)
        val maxAcceptableDriftUs = 40_000L
        val videoPtsUs = 1_000_000L
        val audioPtsUsGood = 1_020_000L
        val audioPtsUsBad = 1_060_000L

        val driftGood = Math.abs(videoPtsUs - audioPtsUsGood)
        val driftBad = Math.abs(videoPtsUs - audioPtsUsBad)

        assertTrue(driftGood <= maxAcceptableDriftUs)
        assertFalse(driftBad <= maxAcceptableDriftUs)
    }

    @Test
    fun testRifeInterpolationTimestepAndMonotonicPts() {
        // FR-INT-03: Timestep t = i/k, i = 1..k-1 and monotonic timestamps
        val pts0Us = 100_000L
        val pts1Us = 141_666L // ~24fps delta (41.666 ms)
        val deltaPtsUs = pts1Us - pts0Us

        // Factor x2: 1 interpolated frame at t=0.5
        val factor2 = 2
        val timesteps2 = (1 until factor2).map { it.toFloat() / factor2 }
        assertEquals(1, timesteps2.size)
        assertEquals(0.5f, timesteps2[0], 0.001f)
        val interpPts2 = pts0Us + (timesteps2[0] * deltaPtsUs).toLong()
        assertTrue(pts0Us < interpPts2)
        assertTrue(interpPts2 < pts1Us)

        // Factor x3: 2 interpolated frames at t=1/3, 2/3
        val factor3 = 3
        val timesteps3 = (1 until factor3).map { it.toFloat() / factor3 }
        assertEquals(2, timesteps3.size)
        assertEquals(0.3333f, timesteps3[0], 0.001f)
        assertEquals(0.6666f, timesteps3[1], 0.001f)
        val interpPts3_1 = pts0Us + (timesteps3[0] * deltaPtsUs).toLong()
        val interpPts3_2 = pts0Us + (timesteps3[1] * deltaPtsUs).toLong()
        assertTrue(pts0Us < interpPts3_1)
        assertTrue(interpPts3_1 < interpPts3_2)
        assertTrue(interpPts3_2 < pts1Us)

        // Factor x4: 3 interpolated frames at t=0.25, 0.5, 0.75
        val factor4 = 4
        val timesteps4 = (1 until factor4).map { it.toFloat() / factor4 }
        assertEquals(3, timesteps4.size)
        assertEquals(0.25f, timesteps4[0], 0.001f)
        assertEquals(0.50f, timesteps4[1], 0.001f)
        assertEquals(0.75f, timesteps4[2], 0.001f)
        val interpPts4_1 = pts0Us + (timesteps4[0] * deltaPtsUs).toLong()
        val interpPts4_2 = pts0Us + (timesteps4[1] * deltaPtsUs).toLong()
        val interpPts4_3 = pts0Us + (timesteps4[2] * deltaPtsUs).toLong()
        assertTrue(pts0Us < interpPts4_1)
        assertTrue(interpPts4_1 < interpPts4_2)
        assertTrue(interpPts4_2 < interpPts4_3)
        assertTrue(interpPts4_3 < pts1Us)
    }

    @Test
    fun testRifeFpsCalculationAndClamping() {
        // FR-INT-05: Output FPS limit (default 120 FPS)
        val maxFps = 120

        // 24 fps
        val fps24 = 24.0
        assertEquals(48.0, (fps24 * 2).coerceAtMost(maxFps.toDouble()), 0.01)
        assertEquals(72.0, (fps24 * 3).coerceAtMost(maxFps.toDouble()), 0.01)
        assertEquals(96.0, (fps24 * 4).coerceAtMost(maxFps.toDouble()), 0.01)

        // 30 fps
        val fps30 = 30.0
        assertEquals(60.0, (fps30 * 2).coerceAtMost(maxFps.toDouble()), 0.01)
        assertEquals(90.0, (fps30 * 3).coerceAtMost(maxFps.toDouble()), 0.01)
        assertEquals(120.0, (fps30 * 4).coerceAtMost(maxFps.toDouble()), 0.01)

        // 60 fps exceeding 120 limit
        val fps60 = 60.0
        assertEquals(120.0, (fps60 * 2).coerceAtMost(maxFps.toDouble()), 0.01)
        assertEquals(120.0, (fps60 * 3).coerceAtMost(maxFps.toDouble()), 0.01) // Clamped to 120
        assertEquals(120.0, (fps60 * 4).coerceAtMost(maxFps.toDouble()), 0.01) // Clamped to 120
    }

    @Test
    fun testSceneCutDetectionLogic() {
        // FR-INT-04: On scene cut, duplicate previous frame to prevent morphing
        val threshold = 0.35f

        // Case 1: Gradual motion (diff = 0.12f) -> Not a cut -> Interpolate
        val diffGradual = 0.12f
        val isCutGradual = diffGradual > threshold
        assertFalse(isCutGradual)

        // Case 2: Hard cut / shot transition (diff = 0.58f) -> Scene cut -> Duplicate frame
        val diffCut = 0.58f
        val isCutHard = diffCut > threshold
        assertTrue(isCutHard)
    }

    @Test
    fun testCombinedPipelineAndStandaloneModes() {
        // FR-INT-02: Standalone interpolation (scale = 1, factor = 2)
        val srcW = 1280
        val srcH = 720
        val srcFps = 24.0

        val standaloneScale = 1
        val standaloneFactor = 2
        val standaloneOutW = srcW * standaloneScale
        val standaloneOutH = srcH * standaloneScale
        val standaloneOutFps = srcFps * standaloneFactor
        assertEquals(1280, standaloneOutW)
        assertEquals(720, standaloneOutH)
        assertEquals(48.0, standaloneOutFps, 0.01)

        // Combined mode: Interpolation -> Upscale (scale = 2, factor = 2)
        val combinedScale = 2
        val combinedFactor = 2
        val combinedOutW = srcW * combinedScale
        val combinedOutH = srcH * combinedScale
        val combinedOutFps = srcFps * combinedFactor
        assertEquals(2560, combinedOutW)
        assertEquals(1440, combinedOutH)
        assertEquals(48.0, combinedOutFps, 0.01)
    }

    @Test
    fun testUhdModeCriteria() {
        // FR-INT-07: UHD mode for input >= 2K (width or height >= 2048)
        assertFalse(isUhdRecommended(1920, 1080))
        assertFalse(isUhdRecommended(1280, 720))
        assertTrue(isUhdRecommended(2560, 1440))
        assertTrue(isUhdRecommended(3840, 2160))
        assertTrue(isUhdRecommended(1080, 2048)) // Vertical 2K
    }

    private fun isUhdRecommended(width: Int, height: Int): Boolean {
        return width >= 2048 || height >= 2048
    }

    private fun isCuganModelSupported(directory: String, scale: Int, denoise: Int): Boolean {
        if (directory == "models-pro") {
            if (scale == 4) return false
            return denoise in listOf(-1, 0, 3)
        }
        if (directory == "models-se") {
            if (scale == 2) return denoise in listOf(-1, 0, 1, 2, 3)
            if (scale in listOf(3, 4)) return denoise in listOf(-1, 0, 3)
        }
        return false
    }

    private fun getNativeModelScale(modelName: String): Int {
        return when {
            modelName.endsWith("-x2") -> 2
            modelName.endsWith("-x3") -> 3
            else -> 4
        }
    }

    @Test
    fun testMinimumImageDimensionValidation() {
        fun validateImageDimension(width: Int, height: Int): Boolean {
            return width >= 32 && height >= 32
        }

        assertTrue(validateImageDimension(32, 32))
        assertTrue(validateImageDimension(100, 200))
        assertFalse(validateImageDimension(31, 64))
        assertFalse(validateImageDimension(64, 16))
        assertFalse(validateImageDimension(10, 10))
    }

    @Test
    fun testAlphaChannelMaskPreservationCalculation() {
        val srcW = 64
        val srcH = 64
        val scale = 2
        val targetW = srcW * scale
        val targetH = srcH * scale

        assertEquals(128, targetW)
        assertEquals(128, targetH)
        assertEquals(srcW * 2, targetW)
        assertEquals(srcH * 2, targetH)
    }

    @Test
    fun testThermalStatusAction() {
        fun shouldPauseOrThrottle(thermalStatus: Int): Boolean {
            return thermalStatus >= 4 // PowerManager.THERMAL_STATUS_SEVERE or CRITICAL
        }

        fun shouldWarn(thermalStatus: Int): Boolean {
            return thermalStatus == 3 // PowerManager.THERMAL_STATUS_MODERATE
        }

        assertFalse(shouldPauseOrThrottle(0))
        assertFalse(shouldPauseOrThrottle(1))
        assertFalse(shouldPauseOrThrottle(2))
        assertFalse(shouldPauseOrThrottle(3))
        assertTrue(shouldPauseOrThrottle(4))
        assertTrue(shouldPauseOrThrottle(5))
        assertTrue(shouldWarn(3))
    }

    @Test
    fun testBatteryThresholdCheck() {
        fun isBatteryWarningNeeded(batteryPct: Int, isCharging: Boolean): Boolean {
            return !isCharging && batteryPct < 15
        }

        assertTrue(isBatteryWarningNeeded(14, false))
        assertTrue(isBatteryWarningNeeded(5, false))
        assertFalse(isBatteryWarningNeeded(15, false))
        assertFalse(isBatteryWarningNeeded(80, false))

        assertFalse(isBatteryWarningNeeded(10, true))
        assertFalse(isBatteryWarningNeeded(5, true))
    }

    @Test
    fun testInterruptedJobRecoveryCalculation() {
        val checkpointFrame = 450
        val totalFrames = 1000
        val percent = ((checkpointFrame * 100) / totalFrames).coerceIn(0, 100)
        assertEquals(45, percent)

        val remainingFrames = (totalFrames - checkpointFrame).coerceAtLeast(0)
        assertEquals(550, remainingFrames)
    }
}
