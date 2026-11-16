package com.efeyamann.dizi

import org.json.JSONObject
import java.net.URLEncoder

/** Unofficial: the API serializd.com's own site calls. serializd.com sits behind Cloudflare, the Render backend does not. */
object Serializd {
    private const val API = "https://serializd.onrender.com/api/user"

    /** Watched + currently watching shows of a public profile. Seasons stay 0 until TMDB fills them in. */
    fun watched(user: String): List<Show> =
        listOf("watchedpage_v2", "currently_watching_page").flatMap { pages(user, it) }.distinctBy { it.id }

    private fun pages(user: String, kind: String): List<Show> {
        val out = mutableListOf<Show>()
        var page = 1
        do {
            val o = getJson(
                "$API/${URLEncoder.encode(user, "UTF-8")}/$kind/$page?sort_by=date_added_desc",
                "X-Requested-With" to "serializd_vercel",
            )
            out += parseItems(o)
        } while (++page <= o.optInt("totalPages"))
        return out
    }

    fun parseItems(o: JSONObject): List<Show> =
        o.list("items") { Show(it.getInt("showId"), it.getString("showName"), null, 0) }
}
