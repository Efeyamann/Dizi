package com.efeyamann.dizi

import org.json.JSONObject
import java.net.URLEncoder

object Tmdb {
    private const val API = "https://api.themoviedb.org/3"

    private fun get(token: String, path: String) =
        getJson("$API$path", "Authorization" to "Bearer $token", "Accept" to "application/json")

    /** Search results carry the year in the name; details() replaces it when the show is added. */
    fun search(token: String, q: String): List<Show> =
        get(token, "/search/tv?query=" + URLEncoder.encode(q, "UTF-8")).list("results") {
            val year = it.str("first_air_date")?.take(4)?.let { y -> " ($y)" } ?: ""
            Show(it.getInt("id"), title(it) + year, it.str("poster_path"), 0)
        }

    fun details(token: String, id: Int): Show = parseDetails(get(token, "/tv/$id"))

    fun parseDetails(o: JSONObject): Show {
        val next = o.optJSONObject("next_episode_to_air")
        return Show(
            o.getInt("id"), title(o), o.str("poster_path"), o.optInt("number_of_seasons"),
            next?.str("air_date"), next?.optInt("season_number") ?: 0, next?.optInt("episode_number") ?: 0,
            ended = o.str("status") in setOf("Ended", "Canceled"),
        )
    }

    /** The original title, untranslated. Titles in other scripts (Korean, Japanese…) fall back to the English one. */
    private fun title(o: JSONObject): String {
        val original = o.str("original_name")
        return if (original != null && original.all { it.code < 0x250 }) original else o.getString("name")
    }
}
