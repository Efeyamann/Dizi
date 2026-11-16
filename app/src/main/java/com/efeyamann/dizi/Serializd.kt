package com.efeyamann.dizi

import org.json.JSONObject
import java.net.URLEncoder

/** Unofficial: the API serializd.com's own site calls. serializd.com sits behind Cloudflare, the Render backend does not. */
object Serializd {
    private const val API = "https://serializd.onrender.com/api/user"

    // Order is the priority when a show is in several lists: watching > watched > watchlist. Dropped shows are skipped.
    private val LISTS = listOf("currently_watching_page", "paused_shows_page", "watchedpage_v2", "watchlistpage_v2")

    /** Shows of a public profile with their status. Seasons and posters stay empty until TMDB fills them in. */
    fun fetch(user: String): List<Show> = LISTS.flatMap { pages(user, it) }.distinctBy { it.id }

    private fun pages(user: String, list: String): List<Show> {
        val out = mutableListOf<Show>()
        var page = 1
        do {
            val o = getJson(
                "$API/${URLEncoder.encode(user, "UTF-8")}/$list/$page?sort_by=date_added_desc",
                "X-Requested-With" to "serializd_vercel",
            )
            out += parseItems(o, list)
        } while (++page <= o.optInt("totalPages"))
        return out
    }

    fun parseItems(o: JSONObject, list: String): List<Show> = o.list("items") {
        val status = when (list) {
            "watchlistpage_v2" -> Status.WATCHLIST
            // "Watched" lists every show with at least one watched season; it is finished only if all are.
            "watchedpage_v2" ->
                if ((it.optJSONArray("seasonIds")?.length() ?: 0) >= it.optInt("numSeasons", 1)) Status.DONE else Status.WATCHING
            else -> Status.WATCHING
        }
        Show(it.getInt("showId"), it.getString("showName"), null, 0, status = status)
    }
}
