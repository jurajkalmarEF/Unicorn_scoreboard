package com.example.scorebuddystats

import android.content.Context
import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ResultsStore {

    private const val TAG = "ResultsStore"
    private val client = OkHttpClient()

    fun resultsDir(context: Context): File {
        val dir = File(context.getExternalFilesDir(null), "results")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun dumpsDir(context: Context): File {
        val dir = File(context.getExternalFilesDir(null), "dumps")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /** Appends the leg result to a local CSV (always works, no network needed) and
     *  uploads it to the Supabase table configured in settings. */
    fun saveLegResult(context: Context, legResult: List<PlayerResult>, legKey: String) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(Date())

        // 1) local CSV, always written, on the calling thread (fast, no I/O contention risk here)
        val csv = File(resultsDir(context), "leg_results.csv")
        val isNew = !csv.exists()
        csv.appendText(buildString {
            if (isNew) append("timestamp,player,placement,points\n")
            legResult.forEach { r ->
                append("$timestamp,${r.playerName.replace(",", " ")},${r.placement},${r.points}\n")
            }
        })
        Log.i(TAG, "Saved leg result locally: $legResult")

        // 2) Supabase upload - run on a background thread, network calls are not
        // allowed on the main thread (this is called from the accessibility
        // service's main-looper handler).
        val supabaseUrl = Settings.getSupabaseUrl(context)
        val supabaseKey = Settings.getSupabaseKey(context)
        if (supabaseUrl.isNotBlank() && supabaseKey.isNotBlank()) {
            Thread {
                uploadToSupabase(supabaseUrl, supabaseKey, legKey, legResult)
            }.start()
        }
    }

    private fun uploadToSupabase(
        baseUrl: String,
        apiKey: String,
        legKey: String,
        legResult: List<PlayerResult>
    ) {
        try {
            val endpoint = baseUrl.trimEnd('/') + "/leg_results"

            val rowsArray = JSONArray()
            legResult.forEach { r ->
                rowsArray.put(JSONObject().apply {
                    put("leg_key", legKey)
                    put("player_name", r.playerName)
                    put("placement", r.placement)
                    put("points", r.points)
                })
            }

            val request = Request.Builder()
                .url(endpoint)
                .addHeader("apikey", apiKey)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Prefer", "return=minimal")
                .post(rowsArray.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Log.i(TAG, "Supabase upload OK: ${response.code}")
                } else {
                    Log.e(TAG, "Supabase upload failed: ${response.code} ${response.body?.string()}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Supabase upload failed, result is still saved locally", e)
        }
    }

    /** Writes a raw accessibility-tree text dump, used during the capture phase
     *  to figure out the exact screen layout of the leg-result screen. */
    fun saveDump(context: Context, content: String) {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(dumpsDir(context), "dump_$timestamp.txt")
        file.writeText(content)
        Log.i(TAG, "Saved screen dump to ${file.absolutePath}")
    }
}
