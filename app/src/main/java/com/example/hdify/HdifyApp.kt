package com.example.hdify

import android.app.Application
import android.os.StrictMode
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.example.hdify.data.preferences.UserPreferencesRepository
import com.example.hdify.engine.UpscaleEngineFactory
import com.example.hdify.logging.BreadcrumbManager
import com.example.hdify.logging.CrashHandler
import com.example.hdify.logging.HdLogger
import com.example.hdify.logging.StartupCrashDetector
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class HdifyApp : Application() {

    @Inject
    lateinit var engineFactory: UpscaleEngineFactory

    @Inject
    lateinit var prefsRepo: UserPreferencesRepository

    override fun onCreate() {
        super.onCreate()

        // 1. Initialize Logging & Crash Handling
        BreadcrumbManager.init(this)
        HdLogger.init(this, verbose = BuildConfig.DEBUG)
        CrashHandler.init(this)

        // Initialize Notification Channels
        com.example.hdify.service.UpscaleForegroundService.createNotificationChannels(this)

        HdLogger.i("HdifyApp", null, "Aplikasi dimulai. Versi: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}), Build: ${BuildConfig.BUILD_TYPE}")
        BreadcrumbManager.add("App onCreate")

        // Restore user's saved theme mode & language on IO without blocking Main thread
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val mode = prefsRepo.getThemeMode()
                val lang = prefsRepo.getLanguage()
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    val targetNightMode = when (mode) {
                        "light" -> AppCompatDelegate.MODE_NIGHT_NO
                        "dark" -> AppCompatDelegate.MODE_NIGHT_YES
                        else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                    }
                    if (AppCompatDelegate.getDefaultNightMode() != targetNightMode) {
                        AppCompatDelegate.setDefaultNightMode(targetNightMode)
                    }

                    // Restore user's saved language preference
                    val targetLocales = when (lang) {
                        "in" -> LocaleListCompat.forLanguageTags("in")
                        "en" -> LocaleListCompat.forLanguageTags("en")
                        else -> LocaleListCompat.getEmptyLocaleList()
                    }
                    AppCompatDelegate.setApplicationLocales(targetLocales)
                }
            } catch (e: Exception) {
                HdLogger.e("HdifyApp", "Gagal memulihkan preferensi tema / bahasa: ${e.message}", e)
            }
        }

        // 2. StrictMode in Debug (FR-LOG-14)
        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectDiskReads()
                    .detectDiskWrites()
                    .detectNetwork()
                    .penaltyLog()
                    .build()
            )
            StrictMode.setVmPolicy(
                StrictMode.VmPolicy.Builder()
                    .detectLeakedSqlLiteObjects()
                    .detectLeakedClosableObjects()
                    .penaltyLog()
                    .build()
            )
            HdLogger.d("HdifyApp", null, "StrictMode aktif untuk build debug")
        }

        // 3. Mark session active for abnormal exit tracking
        StartupCrashDetector.markSessionActive(this)
    }

    @Suppress("DEPRECATION")
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_MODERATE) {
            HdLogger.w("HdifyApp", null, "TrimMemory level=$level, releasing native engines to free VRAM")
            try {
                engineFactory.releaseAll()
            } catch (e: Exception) {
                HdLogger.e("HdifyApp", "Failed to release engines on onTrimMemory: ${e.message}", e)
            }
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        HdLogger.w("HdifyApp", null, "LowMemory signal received, releasing all native engines")
        try {
            engineFactory.releaseAll()
        } catch (e: Exception) {
            HdLogger.e("HdifyApp", "Failed to release engines on onLowMemory: ${e.message}", e)
        }
    }
}
