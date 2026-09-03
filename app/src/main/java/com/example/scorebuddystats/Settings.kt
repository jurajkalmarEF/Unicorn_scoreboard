package com.example.scorebuddystats

import android.content.Context

object Settings {
    private const val PREFS = "scorebuddy_stats_prefs"
    private const val KEY_WEBHOOK_URL = "webhook_url"

    fun getWebhookUrl(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_WEBHOOK_URL, null)

    fun setWebhookUrl(context: Context, url: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_WEBHOOK_URL, url)
            .apply()
    }
}
