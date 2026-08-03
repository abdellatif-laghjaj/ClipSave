package com.abdellatif.clipsave.download

import android.content.Context

/** Keeps extractors fresh without spending network and battery on every process launch. */
internal object EngineUpdatePolicy {

    fun shouldRefresh(lastSuccessfulUpdate: Long, now: Long): Boolean =
        lastSuccessfulUpdate <= 0L ||
            now < lastSuccessfulUpdate ||
            now - lastSuccessfulUpdate >= AUTO_UPDATE_INTERVAL_MS

    fun shouldRefresh(context: Context, now: Long = System.currentTimeMillis()): Boolean =
        shouldRefresh(preferences(context).getLong(KEY_LAST_SUCCESS, 0L), now)

    fun recordSuccess(context: Context, now: Long = System.currentTimeMillis()) {
        preferences(context).edit().putLong(KEY_LAST_SUCCESS, now).apply()
    }

    private fun preferences(context: Context) = context.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    private const val PREFERENCES_NAME = "download_engine_state"
    private const val KEY_LAST_SUCCESS = "last_successful_update"
    internal const val AUTO_UPDATE_INTERVAL_MS = 24L * 60 * 60 * 1_000
}
