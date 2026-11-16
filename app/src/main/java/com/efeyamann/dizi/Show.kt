package com.efeyamann.dizi

import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

enum class Status { WATCHING, DONE, WATCHLIST }

data class Show(
    val id: Int, // TMDB id (Serializd uses the same ids)
    val name: String,
    val poster: String?,
    val seasons: Int, // 0 = not known yet
    val nextAir: String? = null, // yyyy-MM-dd
    val nextSeason: Int = 0,
    val nextEpisode: Int = 0,
    val startNotified: Int = 0, // highest season we already sent a "starts today" notice for
    val status: Status = Status.WATCHING,
    val ended: Boolean = false, // the show itself is over (TMDB: Ended/Canceled)
    val newSeason: Boolean = false, // badge until the user opens the show
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("name", name).put("poster", poster ?: JSONObject.NULL)
        .put("seasons", seasons).put("nextAir", nextAir ?: JSONObject.NULL)
        .put("nextSeason", nextSeason).put("nextEpisode", nextEpisode).put("startNotified", startNotified)
        .put("status", status.name).put("ended", ended).put("newSeason", newSeason)

    companion object {
        fun fromJson(o: JSONObject) = Show(
            o.getInt("id"), o.getString("name"), o.str("poster"), o.optInt("seasons"),
            o.str("nextAir"), o.optInt("nextSeason"), o.optInt("nextEpisode"), o.optInt("startNotified"),
            // v0.1 entries have no status: they land in WATCHING.
            Status.entries.find { it.name == o.str("status") } ?: Status.WATCHING,
            o.optBoolean("ended"), o.optBoolean("newSeason"),
        )
    }
}

/** Compares the stored show with fresh TMDB data. Returns the notification text (or null) and the show to store. */
fun checkNews(old: Show, fresh: Show, today: String): Pair<String?, Show> {
    val stored = fresh.copy(startNotified = old.startNotified, status = old.status, newSeason = old.newSeason)
    // A new season means there is something to watch again: finished shows move back to WATCHING.
    val flagged = stored.copy(newSeason = true, status = if (old.status == Status.DONE) Status.WATCHING else old.status)
    return when {
        fresh.nextEpisode == 1 && fresh.nextAir == today && old.startNotified < fresh.nextSeason ->
            "${fresh.nextSeason}. sezon bugün başlıyor" to flagged.copy(startNotified = fresh.nextSeason)
        old.seasons > 0 && fresh.seasons > old.seasons ->
            "${fresh.seasons}. sezon duyuruldu" + (fresh.nextAir?.let { " · ilk bölüm $it" } ?: "") to flagged
        else -> null to stored
    }
}

/** "Bugün", "Yarın", "3 gün sonra", then a short date. */
fun relativeDay(date: String, today: LocalDate): String {
    val d = LocalDate.parse(date)
    return when (val n = ChronoUnit.DAYS.between(today, d)) {
        0L -> "Bugün"
        1L -> "Yarın"
        in 2L..6L -> "$n gün sonra"
        else -> d.format(DateTimeFormatter.ofPattern(if (d.year == today.year) "d MMM" else "d MMM yyyy", Locale.forLanguageTag("tr")))
    }
}
