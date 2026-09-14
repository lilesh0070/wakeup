package com.wakeupbuddy

import android.app.Activity
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * In-app updater. Checks the latest GitHub release, downloads the APK and hands it
 * to the package installer — no browser needed. Network is used ONLY here.
 */
object Updater {

    private const val API =
        "https://api.github.com/repos/lilesh0070/wakeup/releases/latest"

    private val main = Handler(Looper.getMainLooper())

    interface Callback {
        fun onStatus(msg: String)
        fun onProgress(percent: Int) // -1 = indeterminate
        fun onUpToDate(latest: String)
        fun onReadyToInstall(file: File)
        fun onError(msg: String)
    }

    fun run(activity: Activity, currentVersionName: String, cb: Callback) {
        Thread {
            try {
                main.post { cb.onProgress(-1); cb.onStatus("Checking for updates…") }
                val (latestTag, apkUrl) = fetchLatest()
                val current = "v$currentVersionName"
                if (latestTag != null && latestTag.equals(current, ignoreCase = true)) {
                    main.post { cb.onUpToDate(latestTag) }
                    return@Thread
                }
                main.post { cb.onStatus("Downloading " + (latestTag ?: "update") + "…") }
                val dir = File(activity.cacheDir, "updates").apply { mkdirs() }
                val out = File(dir, Config.APK_ASSET)
                download(apkUrl ?: Config.UPDATE_DOWNLOAD_URL, out) { p -> main.post { cb.onProgress(p) } }
                main.post { cb.onReadyToInstall(out) }
            } catch (e: Exception) {
                main.post { cb.onError(e.message ?: "Update failed") }
            }
        }.start()
    }

    private fun fetchLatest(): Pair<String?, String?> = try {
        val conn = (URL(API).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("User-Agent", "WakeUpBuddy")
            setRequestProperty("Accept", "application/vnd.github+json")
            connectTimeout = 15000
            readTimeout = 15000
        }
        conn.inputStream.bufferedReader().use { r ->
            val json = JSONObject(r.readText())
            val tag = if (json.has("tag_name")) json.getString("tag_name") else null
            var apk: String? = null
            json.optJSONArray("assets")?.let { assets ->
                for (i in 0 until assets.length()) {
                    val a = assets.getJSONObject(i)
                    if (a.optString("name") == Config.APK_ASSET) {
                        apk = a.optString("browser_download_url"); break
                    }
                }
            }
            Pair(tag, apk)
        }
    } catch (e: Exception) {
        Pair(null, null) // fall back to the direct /latest/download URL
    }

    private fun download(startUrl: String, out: File, onPct: (Int) -> Unit) {
        var url = startUrl
        var redirects = 0
        while (true) {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", "WakeUpBuddy")
                connectTimeout = 20000
                readTimeout = 30000
            }
            val code = conn.responseCode
            if (code in 300..399) {
                val loc = conn.getHeaderField("Location") ?: throw Exception("Redirect without location")
                conn.disconnect()
                if (++redirects > 5) throw Exception("Too many redirects")
                url = loc
                continue
            }
            if (code !in 200..299) throw Exception("HTTP $code")
            val total = conn.contentLengthLong
            conn.inputStream.use { input ->
                out.outputStream().use { output ->
                    val buf = ByteArray(8192)
                    var downloaded = 0L
                    var read = input.read(buf)
                    while (read != -1) {
                        output.write(buf, 0, read)
                        downloaded += read
                        if (total > 0) onPct(((downloaded * 100) / total).toInt())
                        read = input.read(buf)
                    }
                }
            }
            conn.disconnect()
            return
        }
    }

    fun install(activity: Activity, file: File) {
        val uri = FileProvider.getUriForFile(activity, activity.packageName + ".fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        activity.startActivity(intent)
    }
}
