package com.example.scorebuddystats

import android.content.Context

object Settings {
    private const val PREFS = "scorebuddy_stats_prefs"
    private const val KEY_SUPABASE_URL = "supabase_url"
    private const val KEY_SUPABASE_KEY = "supabase_key"

    // Pre-filled with the project the user set up. Editable in-app if it ever changes.
    private const val DEFAULT_SUPABASE_URL = "https://wxurldyudrwbnntckfvc.supabase.co/rest/v1/"
    private const val DEFAULT_SUPABASE_KEY = "sb_publishable_bG3QUWFav4wxCo7xK8EFVQ_wobfFL0S"

    fun getSupabaseUrl(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_SUPABASE_URL, DEFAULT_SUPABASE_URL) ?: DEFAULT_SUPABASE_URL

    fun setSupabaseUrl(context: Context, url: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SUPABASE_URL, url)
            .apply()
    }

    fun getSupabaseKey(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_SUPABASE_KEY, DEFAULT_SUPABASE_KEY) ?: DEFAULT_SUPABASE_KEY

    fun setSupabaseKey(context: Context, key: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SUPABASE_KEY, key)
            .apply()
    }
}
