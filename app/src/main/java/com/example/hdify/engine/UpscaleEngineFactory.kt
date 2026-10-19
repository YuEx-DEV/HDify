package com.example.hdify.engine

import com.example.hdify.domain.model.ContentType
import com.example.hdify.domain.model.EngineConfig
import com.example.hdify.domain.model.EngineType
import com.example.hdify.domain.model.Preset
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UpscaleEngineFactory @Inject constructor(
    private val realCuganEngine: RealCuganEngine,
    private val realEsrganEngine: RealEsrganEngine
) {
    fun getEngine(engineType: EngineType): UpscaleEngine {
        return when (engineType) {
            EngineType.REAL_CUGAN -> {
                try { realEsrganEngine.release() } catch (_: Exception) {}
                realCuganEngine
            }
            EngineType.REAL_ESRGAN -> {
                try { realCuganEngine.release() } catch (_: Exception) {}
                realEsrganEngine
            }
        }
    }

    fun releaseAll() {
        try { realCuganEngine.release() } catch (_: Exception) {}
        try { realEsrganEngine.release() } catch (_: Exception) {}
    }

    /**
     * Resolves the recommended engine and model configuration based on PRD Section 4.3:
     * - Anime: Real-CUGAN (se for Fast/Balanced, pro for Quality)
     * - Photo: Real-ESRGAN (realesrgan-x4plus / realesrgan-x4plus-anime)
     */
    fun createDefaultConfig(
        contentType: ContentType,
        preset: Preset,
        scale: Int,
        gpuIndex: Int = 0,
        allowCpuFallback: Boolean = false
    ): EngineConfig {
        return when (contentType) {
            ContentType.ANIME -> {
                when (preset) {
                    Preset.FAST -> EngineConfig(
                        engine = EngineType.REAL_CUGAN,
                        modelDirectory = "models-se",
                        scale = scale,
                        denoise = -1, // conservative
                        gpuIndex = gpuIndex,
                        allowCpuFallback = allowCpuFallback
                    )
                    Preset.BALANCED -> EngineConfig(
                        engine = EngineType.REAL_CUGAN,
                        modelDirectory = "models-se",
                        scale = scale,
                        denoise = 0, // no-denoise
                        gpuIndex = gpuIndex,
                        allowCpuFallback = allowCpuFallback
                    )
                    Preset.QUALITY -> {
                        val modelDir = if (scale == 4) "models-se" else "models-pro"
                        val denoiseVal = if (modelDir == "models-pro") 0 else if (scale >= 3) 3 else 1
                        EngineConfig(
                            engine = EngineType.REAL_CUGAN,
                            modelDirectory = modelDir,
                            scale = scale,
                            denoise = denoiseVal,
                            gpuIndex = gpuIndex,
                            allowCpuFallback = allowCpuFallback
                        )
                    }
                }
            }
            ContentType.PHOTO -> {
                when (preset) {
                    Preset.FAST -> EngineConfig(
                        engine = EngineType.REAL_ESRGAN,
                        modelDirectory = "realesrgan-x4plus-anime",
                        scale = scale,
                        denoise = 0,
                        gpuIndex = gpuIndex,
                        allowCpuFallback = allowCpuFallback
                    )
                    Preset.BALANCED, Preset.QUALITY -> EngineConfig(
                        engine = EngineType.REAL_ESRGAN,
                        modelDirectory = "realesrgan-x4plus",
                        scale = scale,
                        denoise = 0,
                        gpuIndex = gpuIndex,
                        allowCpuFallback = allowCpuFallback
                    )
                }
            }
        }
    }
}
