package com.example.videotrimmer

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import android.widget.TextView
import androidx.preference.PreferenceManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.arthenica.ffmpegkit.FFmpegKit
import java.io.File

class MainActivity : AppCompatActivity() {
    private var selectedUri: Uri? = null
    private var audioUri: Uri? = null
    private var subtitleUri: Uri? = null
    private lateinit var logFile: File

    private val pickVideo = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        selectedUri = uri
        findViewById<TextView>(R.id.statusText).text = uri?.let { getFileName(it) } ?: "Seçim iptal"
    }

    private val pickAudio = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        audioUri = uri
        findViewById<TextView>(R.id.audioPath).text = uri?.let { getFileName(it) } ?: "Ses seçilmedi"
    }

    private val pickSubtitle = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        subtitleUri = uri
        findViewById<TextView>(R.id.subtitlePath).text = uri?.let { getFileName(it) } ?: "Altyazı seçilmedi"
    }

    private val requestPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val granted = perms.values.any { it }
        if (granted) {
            pickVideo.launch("video/*")
        } else {
            findViewById<TextView>(R.id.statusText).text = "Gerekli izinler reddedildi"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val premiumSwitch = findViewById<Switch>(R.id.premiumSwitch)
        val checkBtn = findViewById<Button>(R.id.checkPremiumBtn)
        val activateBtn = findViewById<Button>(R.id.activateBtn)
        val logPathView = findViewById<TextView>(R.id.logPath)
        val premiumSaved = prefs.getBoolean("is_premium", false)
        premiumSwitch.isChecked = premiumSaved

        // Ensure device key exists
        var deviceKey = prefs.getString("device_key", null)
        if (deviceKey == null) {
            deviceKey = java.util.UUID.randomUUID().toString()
            prefs.edit().putString("device_key", deviceKey).apply()
        }

        // Show log path placeholder
        val logFile = File(getExternalFilesDir(null), "ffmpeg_log.txt")
        logPathView.text = "Log: ${logFile.absolutePath}"

        premiumSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("is_premium", isChecked).apply()
        }

        checkBtn.setOnClickListener {
            checkPremiumFromServer(deviceKey) { isPremium ->
                prefs.edit().putBoolean("is_premium", isPremium).apply()
                runOnUiThread {
                    premiumSwitch.isChecked = isPremium
                    findViewById<TextView>(R.id.statusText).text = if (isPremium) "Premium" else "Normal"
                }
            }
        }

        activateBtn.setOnClickListener {
            activateOnServer(deviceKey) { ok ->
                runOnUiThread {
                    if (ok) {
                        findViewById<TextView>(R.id.statusText).text = "Aktivasyon başarılı"
                        checkPremiumFromServer(deviceKey) { isPremium ->
                            prefs.edit().putBoolean("is_premium", isPremium).apply()
                            runOnUiThread { premiumSwitch.isChecked = isPremium }
                        }
                    } else {
                        findViewById<TextView>(R.id.statusText).text = "Aktivasyon başarısız"
                    }
                }
            }
        }

        findViewById<Button>(R.id.selectBtn).setOnClickListener {
            if (hasVideoPermission()) pickVideo.launch("video/*") else requestVideoPermission()
        }

        findViewById<Button>(R.id.trimBtn).setOnClickListener {
            val start = findViewById<EditText>(R.id.startInput).text.toString()
            val end = findViewById<EditText>(R.id.endInput).text.toString()
            if (selectedUri == null) {
                findViewById<TextView>(R.id.statusText).text = "Lütfen önce video seçin"
                return@setOnClickListener
            }
            trimVideo(selectedUri!!, start, end)
        }
    }

    private fun trimVideo(uri: Uri, start: String, end: String) {
        // Copy URI to a temp file in cache
        val inputPath = File.createTempFile("input_video", null, cacheDir).apply { deleteOnExit() }
        try {
            contentResolver.openInputStream(uri)?.use { input ->
                inputPath.outputStream().use { out -> input.copyTo(out) }
            }
        } catch (e: Exception) {
            findViewById<TextView>(R.id.statusText).text = "Dosya kopyalama hatası: ${e.message}"
            return
        }

        val outputFile = File(getExternalFilesDir(null), "trimmed_${System.currentTimeMillis()}.mp4")

        val cmd = "-y -i ${inputPath.absolutePath} -ss $start -to $end -c copy ${outputFile.absolutePath}"

        findViewById<TextView>(R.id.statusText).text = "İşleniyor..."

        // Execute FFmpeg with log callback that appends to file
        com.arthenica.ffmpegkit.FFmpegKit.executeAsync(cmd,
            { session ->
                val returnCode = session.returnCode
                runOnUiThread {
                    if (returnCode != null && returnCode.isValueSuccess) {
                        findViewById<TextView>(R.id.statusText).text = "Tamamlandı: ${outputFile.absolutePath}"
                    } else {
                        findViewById<TextView>(R.id.statusText).text = "Hata: ${session.returnCode}"
                    }
                }
            },
            { log ->
                // LogCallback: append message to file
                try {
                    logFile.appendText("${log.level}: ${log.message}\n")
                } catch (_: Exception) {}
            },
            null
        )
    }

    private fun getFileName(uri: Uri): String {
        var name = ""
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIndex >= 0) name = cursor.getString(nameIndex)
        }
        return name.ifEmpty { uri.path ?: "Seçilmiş video" }
    }

    private fun java.io.OutputStream.copyFrom(input: java.io.InputStream) {
        val buf = ByteArray(8192)
        var len: Int
        while (input.read(buf).also { len = it } > 0) write(buf, 0, len)
    }

    private fun checkPremiumFromServer(key: String, callback: (Boolean) -> Unit) {
        Thread {
            try {
                val url = java.net.URL("http://10.0.2.2:8000/validate?key=${java.net.URLEncoder.encode(key, "utf-8")}")
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                val code = conn.responseCode
                Log.d("VideoTrimmer", "Server response code: $code")
                if (code == 200) {
                    val text = conn.inputStream.bufferedReader().use { it.readText() }
                    val isPremium = text.contains("\"premium\": true")
                    callback(isPremium)
                } else {
                    callback(false)
                }
            } catch (e: Exception) {
                Log.d("VideoTrimmer", "Premium check failed: ${e.message}")
                callback(false)
            }
        }.start()
    }

    private fun activateOnServer(key: String, callback: (Boolean) -> Unit) {
        Thread {
            try {
                val url = java.net.URL("http://10.0.2.2:8000/activate?key=${java.net.URLEncoder.encode(key, "utf-8")}")
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                conn.outputStream.use { }
                val code = conn.responseCode
                callback(code == 200)
            } catch (e: Exception) {
                Log.d("VideoTrimmer", "Activation failed: ${e.message}")
                callback(false)
            }
        }.start()
    }

    private fun openAppSettings() {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            val uri = Uri.fromParts("package", packageName, null)
            intent.data = uri
            startActivity(intent)
        } catch (e: Exception) {
            Log.d("VideoTrimmer", "Cannot open settings: ${e.message}")
        }
    }

    private fun hasVideoPermission(): Boolean {
        val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_VIDEO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestVideoPermission() {
        val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        requestPermissions.launch(perms)
    }
}
