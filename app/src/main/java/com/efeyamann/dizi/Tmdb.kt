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

    fun details(token: String, id: Int): Show = details(token, detailsJson(token, id))

    fun details(token: String, o: JSONObject): Show = parseDetails(o, seasonGroup(token, o))

    fun detailsJson(token: String, id: Int): JSONObject = get(token, "/tv/$id?append_to_response=episode_groups")

    /**
     * TMDB keeps many anime (Jujutsu Kaisen, Frieren…) as one long season 1; their "Production" (type 6)
     * episode group splits it into the real seasons. Null when the show has real seasons or no such group.
     */
    fun seasonGroup(token: String, o: JSONObject): JSONObject? {
        if (o.optInt("number_of_seasons") != 1) return null
        val a = o.optJSONObject("episode_groups")?.optJSONArray("results") ?: return null
        val g = (0 until a.length()).map(a::getJSONObject)
            .filter { it.optInt("type") == 6 && it.optInt("group_count") > 1 }
            .maxByOrNull { it.optInt("episode_count") } ?: return null
        return get(token, "/tv/episode_group/" + g.getString("id"))
    }

    /** The group's seasons in order, each as its season-1 episode numbers; specials (season 0) left out. */
    fun groupSeasons(group: JSONObject): List<List<Int>> =
        group.list("groups") { it }.sortedBy { it.optInt("order") }
            .map { it.list("episodes") { e -> e }.filter { e -> e.optInt("season_number") == 1 }.map { e -> e.getInt("episode_number") } }
            .filter { it.isNotEmpty() }

    /** TMDB season id → season number, specials (season 0) left out. */
    fun seasonNumbers(o: JSONObject): Map<Int, Int> =
        o.optJSONArray("seasons")?.let { a -> (0 until a.length()).map(a::getJSONObject) }.orEmpty()
            .filter { it.optInt("season_number") > 0 }.associate { it.getInt("id") to it.getInt("season_number") }

    fun parseDetails(o: JSONObject, group: JSONObject? = null): Show {
        val next = o.optJSONObject("next_episode_to_air")
        var seasons = o.optInt("number_of_seasons")
        var nextSeason = next?.optInt("season_number") ?: 0
        var nextEpisode = next?.optInt("episode_number") ?: 0
        var total = o.optJSONArray("seasons")?.let { a -> (0 until a.length()).map(a::getJSONObject) }
            ?.find { it.optInt("season_number") == nextSeason }?.optInt("episode_count") ?: 0
        val split = group?.let(::groupSeasons).orEmpty()
        if (split.size > 1) {
            seasons = split.size
            if (next != null) {
                val i = split.indexOfFirst { nextEpisode in it }
                if (i >= 0) {
                    total = split[i].size
                    nextSeason = i + 1
                    nextEpisode = split[i].indexOf(nextEpisode) + 1
                } else {
                    // shortcut: an episode the group does not list yet is counted into the last season; fix if groups lag a new season.
                    total = 0
                    nextSeason = split.size
                    nextEpisode -= split.dropLast(1).sumOf { it.size }
                }
            }
        }
        return Show(
            o.getInt("id"), title(o), o.str("poster_path"), seasons,
            next?.str("air_date"), nextSeason, nextEpisode,
            ended = o.str("status") in setOf("Ended", "Canceled"),
            nextTotal = if (next != null) total else 0,
        )
    }

    /** The original title, untranslated. Titles in other scripts (Korean, Japanese…) fall back to the English one. */
    private fun title(o: JSONObject): String {
        val original = o.str("original_name")
        return if (original != null && original.all { it.code < 0x250 }) original else o.getString("name")
    }
}
