package com.example.hdify.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_settings")

@Singleton
class UserPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        val KEY_THEME = stringPreferencesKey("theme_mode")
        val KEY_LANGUAGE = stringPreferencesKey("app_language")
        val KEY_VERBOSE_LOG = booleanPreferencesKey("verbose_logging")
        val KEY_ALLOW_CPU = booleanPreferencesKey("allow_cpu_fallback")
    }

    val themeModeFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_THEME] ?: "system"
    }

    val languageFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_LANGUAGE] ?: "system"
    }

    val verboseLoggingFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_VERBOSE_LOG] ?: false
    }

    val allowCpuFallbackFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_ALLOW_CPU] ?: true
    }

    suspend fun getThemeMode(): String {
        return try {
            themeModeFlow.first()
        } catch (_: Exception) {
            "system"
        }
    }

    suspend fun setThemeMode(mode: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_THEME] = mode
        }
    }

    suspend fun getLanguage(): String {
        return try {
            languageFlow.first()
        } catch (_: Exception) {
            "system"
        }
    }

    suspend fun setLanguage(lang: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_LANGUAGE] = lang
        }
    }

    suspend fun setVerboseLogging(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_VERBOSE_LOG] = enabled
        }
    }

    suspend fun setAllowCpuFallback(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_ALLOW_CPU] = enabled
        }
    }
}
