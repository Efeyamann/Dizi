package com.efeyamann.dizi

import org.json.JSONObject
import java.net.URLEncoder

/** Unofficial: the API serializd.com's own site calls. serializd.com sits behind Cloudflare, the Render backend does not. */
object Serializd {
    private const val API = "https://serializd.onrender.com/api/user"

    // Order is the priority when a show is in several lists: watching > watched > watchlist. Dropped shows are skipped.
    private val LISTS = listOf("currently_watching_page", "paused_shows_page", "watchedpage_v2", "watchlistpage_v2")

    /**
     * Shows of a public profile with their status, paired with the TMDB season ids watched on Serializd
     * (merged from all lists). Seasons and posters stay empty until TMDB fills them in.
     */
    fun fetch(user: String): List<Pair<Show, List<Int>>> =
        LISTS.flatMap { pages(user, it) }.groupBy { it.first.id }.map { (_, xs) -> xs.first().first to xs.flatMap { it.second } }

    private fun pages(user: String, list: String): List<Pair<Show, List<Int>>> {
        val out = mutableListOf<Pair<Show, List<Int>>>()
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

    fun parseItems(o: JSONObject, list: String): List<Pair<Show, List<Int>>> = o.list("items") {
        val ids = it.optJSONArray("seasonIds")?.let { a -> (0 until a.length()).map(a::getInt) }.orEmpty()
        val status = when (list) {
            "watchlistpage_v2" -> Status.WATCHLIST
            // "Watched" lists every show with at least one watched season; it is finished only if all are.
            "watchedpage_v2" -> if (ids.size >= it.optInt("numSeasons", 1)) Status.DONE else Status.WATCHING
            else -> Status.WATCHING
        }
        // Only the watched list's season ids mean "watched"; the watchlist's mean "want to watch".
        Show(it.getInt("showId"), it.getString("showName"), null, 0, status = status) to (if (list == "watchlistpage_v2") emptyList() else ids)
    }
}
