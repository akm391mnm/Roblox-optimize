package com.kob.frameboost

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object UpdateChecker {
    // repo ที่ Actions build แล้วปล่อย release tag "latest"
    const val REPO = "akm391mnm/Roblox-optimize"

    data class Info(val versionCode: Int, val apkUrl: String)

    fun fetchLatest(): Info? = try {
        val conn = URL("https://api.github.com/repos/$REPO/releases/tags/latest")
            .openConnection() as HttpURLConnection
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        if (conn.responseCode != 200) {
            null
        } else {
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val code = Regex("versionCode=(\\d+)")
                .find(json.optString("body"))?.groupValues?.get(1)?.toIntOrNull() ?: 0
            val assets = json.getJSONArray("assets")
            var url: String? = null
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.getString("name").endsWith(".apk")) {
                    url = a.getString("browser_download_url")
                    break
                }
            }
            if (url == null) null else Info(code, url)
        }
    } catch (e: Exception) {
        null
    }

    fun download(ctx: Context, url: String, onProgress: (Int) -> Unit): File? = try {
        val dir = File(ctx.cacheDir, "updates").apply { mkdirs() }
        val out = File(dir, "FrameBoost.apk")
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10000
        conn.readTimeout = 20000
        val total = conn.contentLengthLong
        conn.inputStream.use { input ->
            out.outputStream().use { o ->
                val buf = ByteArray(32 * 1024)
                var read: Int
                var sum = 0L
                var last = -1
                while (input.read(buf).also { read = it } != -1) {
                    o.write(buf, 0, read)
                    sum += read
                    if (total > 0) {
                        val pct = (sum * 100 / total).toInt()
                        if (pct != last) {
                            last = pct
                            onProgress(pct)
                        }
                    }
                }
            }
        }
        out
    } catch (e: Exception) {
        null
    }

    fun install(ctx: Context, apk: File) {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", apk)
        val i = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        ctx.startActivity(i)
    }
}
