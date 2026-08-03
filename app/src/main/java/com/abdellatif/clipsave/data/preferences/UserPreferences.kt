package com.abdellatif.clipsave.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class AccentColor { AMBER, BLUE, PURPLE, RED, GREEN, ORANGE }
enum class AccessMode { NORMAL, ACCESSIBILITY, SHIZUKU, ROOT }
enum class NetworkPolicy { ANY, UNMETERED_ONLY }

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "clipsave_prefs")

data class Settings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val accentColor: AccentColor = AccentColor.AMBER,
    val accessMode: AccessMode = AccessMode.NORMAL,
    val networkPolicy: NetworkPolicy = NetworkPolicy.ANY,
    val onboardingDone: Boolean = false
)

class UserPreferences(private val context: Context) {

    private object Keys {
        val THEME = stringPreferencesKey("theme_mode")
        val ACCENT_COLOR = stringPreferencesKey("accent_color")
        val ACCESS = stringPreferencesKey("access_mode")
        val NETWORK_POLICY = stringPreferencesKey("network_policy")
        val ONBOARDING = booleanPreferencesKey("onboarding_done")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        Settings(
            themeMode = runCatching { ThemeMode.valueOf(p[Keys.THEME] ?: "SYSTEM") }.getOrDefault(
                ThemeMode.SYSTEM
            ),
            accentColor = runCatching {
                AccentColor.valueOf(p[Keys.ACCENT_COLOR] ?: "AMBER")
            }.getOrDefault(AccentColor.AMBER),
            accessMode = runCatching {
                AccessMode.valueOf(
                    p[Keys.ACCESS] ?: "NORMAL"
                )
            }.getOrDefault(AccessMode.NORMAL),
            networkPolicy = runCatching {
                NetworkPolicy.valueOf(p[Keys.NETWORK_POLICY] ?: "ANY")
            }.getOrDefault(NetworkPolicy.ANY),
            onboardingDone = p[Keys.ONBOARDING] ?: false
        )
    }

    suspend fun setTheme(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.THEME] = mode.name }
    }

    suspend fun setAccentColor(color: AccentColor) {
        context.dataStore.edit { it[Keys.ACCENT_COLOR] = color.name }
    }

    suspend fun setAccessMode(mode: AccessMode) {
        context.dataStore.edit { it[Keys.ACCESS] = mode.name }
    }

    suspend fun setNetworkPolicy(policy: NetworkPolicy) {
        context.dataStore.edit { it[Keys.NETWORK_POLICY] = policy.name }
    }

    suspend fun setOnboardingDone(done: Boolean) {
        context.dataStore.edit { it[Keys.ONBOARDING] = done }
    }
}
