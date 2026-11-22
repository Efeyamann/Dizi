package com.efeyamann.dizi

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import okhttp3.Request
import java.io.File
import java.io.IOException

/** Updates from this repo's GitHub Releases; tags are "build-<run number>", the same number as versionCode. */
object Update {
    private const val LATEST = "https://api.github.com/repos/Efeyamann/Dizi/releases/latest"

    /** APK link of a release newer than this build, or null. */
    fun newer(): String? {
        val o = getJson(LATEST, "Accept" to "application/vnd.github+json")
        val n = o.getString("tag_name").removePrefix("build-").toIntOrNull() ?: return null
        if (n <= BuildConfig.VERSION_CODE) return null
        return o.list("assets") { it }.firstOrNull { it.getString("name").endsWith(".apk") }?.getString("browser_download_url")
    }

    fun download(ctx: Context, url: String): File {
        val f = File(ctx.cacheDir, "update.apk")
        http.newCall(Request.Builder().url(url).build()).execute().use { r ->
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
            f.outputStream().use { out -> r.body!!.byteStream().copyTo(out) }
        }
        return f
    }

    /** Opens the system installer; it asks once to allow installs from Dizi. */
    fun install(ctx: Context, apk: File) {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", apk)
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
