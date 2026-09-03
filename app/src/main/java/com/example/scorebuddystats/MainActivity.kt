package com.example.scorebuddystats

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings as AndroidSettings
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import java.io.File

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val editWebhookUrl = findViewById<EditText>(R.id.editWebhookUrl)
        editWebhookUrl.setText(Settings.getWebhookUrl(this).orEmpty())

        findViewById<Button>(R.id.btnOpenSettings).setOnClickListener {
            try {
                startActivity(Intent(AndroidSettings.ACTION_ACCESSIBILITY_SETTINGS))
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(this, "Nepodarilo sa otvoriť nastavenia", Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<Button>(R.id.btnSaveWebhook).setOnClickListener {
            Settings.setWebhookUrl(this, editWebhookUrl.text.toString().trim())
            Toast.makeText(this, "Uložené", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btnShareLatestDump).setOnClickListener {
            val dumpsDir = ResultsStore.dumpsDir(this)
            val latest = dumpsDir.listFiles()?.maxByOrNull { it.lastModified() }
            if (latest == null) {
                Toast.makeText(this, "Zatiaľ žiadny dump. Otvor Scorebuddy a odohraj leg.", Toast.LENGTH_LONG).show()
            } else {
                shareFile(latest)
            }
        }

        findViewById<Button>(R.id.btnShareResultsCsv).setOnClickListener {
            val csv = File(ResultsStore.resultsDir(this), "leg_results.csv")
            if (!csv.exists()) {
                Toast.makeText(this, "Zatiaľ žiadne výsledky.", Toast.LENGTH_LONG).show()
            } else {
                shareFile(csv)
            }
        }
    }

    private fun shareFile(file: File) {
        val uri: Uri = FileProvider.getUriForFile(
            this, "com.example.scorebuddystats.fileprovider", file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Zdieľať"))
    }
}
