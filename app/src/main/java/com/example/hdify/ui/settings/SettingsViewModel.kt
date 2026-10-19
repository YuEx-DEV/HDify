package com.example.hdify.ui.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hdify.data.preferences.UserPreferencesRepository
import com.example.hdify.logging.HdLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefsRepo: UserPreferencesRepository
) : ViewModel() {

    val themeMode: StateFlow<String> = prefsRepo.themeModeFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        "system"
    )

    val languageMode: StateFlow<String> = prefsRepo.languageFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        "system"
    )

    val verboseLogging: StateFlow<Boolean> = prefsRepo.verboseLoggingFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        false
    )

    val allowCpuFallback: StateFlow<Boolean> = prefsRepo.allowCpuFallbackFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        false
    )

    fun setTheme(mode: String) {
        viewModelScope.launch {
            prefsRepo.setThemeMode(mode)
            applyTheme(mode)
        }
    }

    fun setLanguage(lang: String) {
        viewModelScope.launch {
            prefsRepo.setLanguage(lang)
            applyLanguage(lang)
            HdLogger.i("Settings", null, "Bahasa aplikasi diubah: $lang")
        }
    }

    fun setVerboseLogging(enabled: Boolean) {
        viewModelScope.launch {
            prefsRepo.setVerboseLogging(enabled)
            HdLogger.setVerbose(enabled)
            HdLogger.i("Settings", null, "Verbose logging diubah: $enabled")
        }
    }

    fun setAllowCpuFallback(enabled: Boolean) {
        viewModelScope.launch {
            prefsRepo.setAllowCpuFallback(enabled)
            HdLogger.i("Settings", null, "Allow CPU fallback diubah: $enabled")
        }
    }

    private fun applyTheme(mode: String) {
        val targetNightMode = when (mode) {
            "light" -> AppCompatDelegate.MODE_NIGHT_NO
            "dark" -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        if (AppCompatDelegate.getDefaultNightMode() != targetNightMode) {
            AppCompatDelegate.setDefaultNightMode(targetNightMode)
        }
    }

    private fun applyLanguage(lang: String) {
        val appLocales = when (lang) {
            "in" -> LocaleListCompat.forLanguageTags("in")
            "en" -> LocaleListCompat.forLanguageTags("en")
            else -> LocaleListCompat.getEmptyLocaleList()
        }
        AppCompatDelegate.setApplicationLocales(appLocales)
    }
}
