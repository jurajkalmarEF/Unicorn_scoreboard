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
     *  fires a webhook POST if a URL is configured in settings. */
    fun saveLegResult(context: Context, legResult: List<PlayerResult>) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(Date())

        // 1) local CSV, always written
        val csv = File(resultsDir(context), "leg_results.csv")
        val isNew = !csv.exists()
        csv.appendText(buildString {
            if (isNew) append("timestamp,player,placement,points\n")
            legResult.forEach { r ->
                append("$timestamp,${r.playerName.replace(",", " ")},${r.placement},${r.points}\n")
            }
        })
        Log.i(TAG, "Saved leg result locally: $legResult")

        // 2) optional webhook upload
        val webhookUrl = Settings.getWebhookUrl(context)
        if (!webhookUrl.isNullOrBlank()) {
            uploadToWebhook(webhookUrl, timestamp, legResult)
        }
    }

    private fun uploadToWebhook(url: String, timestamp: String, legResult: List<PlayerResult>) {
        try {
            val playersArray = JSONArray()
            legResult.forEach { r ->
                playersArray.put(JSONObject().apply {
                    put("player", r.playerName)
                    put("placement", r.placement)
                    put("points", r.points)
                })
            }
            val body = JSONObject().apply {
                put("timestamp", timestamp)
                put("results", playersArray)
            }

            val request = Request.Builder()
                .url(url)
                .post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                Log.i(TAG, "Webhook upload response: ${response.code}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Webhook upload failed, result is still saved locally", e)
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
