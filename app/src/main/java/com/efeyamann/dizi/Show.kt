package com.efeyamann.dizi

import org.json.JSONObject

data class Show(
    val id: Int, // TMDB id (Serializd uses the same ids)
    val name: String,
    val poster: String?,
    val seasons: Int, // 0 = not known yet
    val nextAir: String? = null, // yyyy-MM-dd
    val nextSeason: Int = 0,
    val nextEpisode: Int = 0,
    val startNotified: Int = 0, // highest season we already sent a "starts today" notice for
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("name", name).put("poster", poster ?: JSONObject.NULL)
        .put("seasons", seasons).put("nextAir", nextAir ?: JSONObject.NULL)
        .put("nextSeason", nextSeason).put("nextEpisode", nextEpisode).put("startNotified", startNotified)

    companion object {
        fun fromJson(o: JSONObject) = Show(
            o.getInt("id"), o.getString("name"), o.str("poster"), o.optInt("seasons"),
            o.str("nextAir"), o.optInt("nextSeason"), o.optInt("nextEpisode"), o.optInt("startNotified"),
        )
    }
}

/** Compares the stored show with fresh TMDB data. Returns the notification text (or null) and the show to store. */
fun checkNews(old: Show, fresh: Show, today: String): Pair<String?, Show> {
    val stored = fresh.copy(startNotified = old.startNotified)
    return when {
        fresh.nextEpisode == 1 && fresh.nextAir == today && old.startNotified < fresh.nextSeason ->
            "${fresh.nextSeason}. sezon bugün başlıyor" to stored.copy(startNotified = fresh.nextSeason)
        old.seasons > 0 && fresh.seasons > old.seasons ->
            "${fresh.seasons}. sezon duyuruldu" + (fresh.nextAir?.let { " · ilk bölüm $it" } ?: "") to stored
        else -> null to stored
    }
}
