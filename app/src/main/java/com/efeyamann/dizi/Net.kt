package com.efeyamann.dizi

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

// Long read timeout: the Serializd backend on Render cold-starts slowly.
val http: OkHttpClient = OkHttpClient.Builder().readTimeout(90, TimeUnit.SECONDS).build()

fun getJson(url: String, vararg headers: Pair<String, String>): JSONObject {
    val req = Request.Builder().url(url).apply { headers.forEach { header(it.first, it.second) } }.build()
    http.newCall(req).execute().use { r ->
        val body = r.body?.string().orEmpty()
        if (!r.isSuccessful) throw IOException("HTTP ${r.code}: ${body.take(200)}")
        return JSONObject(body)
    }
}

/** org.json's optString turns JSON null into "null"; this returns real null. */
fun JSONObject.str(key: String): String? = if (isNull(key)) null else optString(key)

fun <T> JSONObject.list(key: String, map: (JSONObject) -> T): List<T> =
    getJSONArray(key).let { a -> (0 until a.length()).map { map(a.getJSONObject(it)) } }
