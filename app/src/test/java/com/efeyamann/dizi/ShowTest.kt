package com.efeyamann.dizi

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ShowTest {
    private val base = Show(1, "Dizi", null, 2)

    @Test fun newSeasonCountNotifies() {
        val (msg, stored) = checkNews(base, base.copy(seasons = 3, nextAir = "2027-01-05", nextSeason = 3, nextEpisode = 1), "2026-11-17")
        assertEquals("3. sezon duyuruldu · ilk bölüm 2027-01-05", msg)
        assertEquals(3, stored.seasons)
        assertTrue(stored.newSeason)
    }

    @Test fun unknownSeasonsDoNotNotify() {
        assertNull(checkNews(base.copy(seasons = 0), base.copy(seasons = 5), "2026-11-17").first)
    }

    @Test fun nothingChangedKeepsUserState() {
        val old = base.copy(status = Status.WATCHLIST, newSeason = true)
        val (msg, stored) = checkNews(old, base, "2026-11-17") // fresh TMDB data carries no status
        assertNull(msg)
        assertEquals(old, stored)
    }

    @Test fun premiereTodayNotifiesOnce() {
        val fresh = base.copy(seasons = 3, nextAir = "2026-11-17", nextSeason = 3, nextEpisode = 1)
        val (msg, stored) = checkNews(base.copy(seasons = 3), fresh, "2026-11-17")
        assertEquals("3. sezon bugün başlıyor", msg)
        assertNull(checkNews(stored, fresh, "2026-11-17").first)
    }

    @Test fun midSeasonEpisodeIsQuiet() {
        assertNull(checkNews(base, base.copy(nextAir = "2026-11-17", nextSeason = 2, nextEpisode = 4), "2026-11-17").first)
    }

    @Test fun newSeasonMovesFinishedShowBackToWatching() {
        assertEquals(Status.WATCHING, checkNews(base.copy(status = Status.DONE), base.copy(seasons = 3), "2026-11-17").second.status)
        assertEquals(Status.WATCHLIST, checkNews(base.copy(status = Status.WATCHLIST), base.copy(seasons = 3), "2026-11-17").second.status)
    }

    @Test fun jsonRoundTrip() {
        val s = Show(5, "A", null, 1, null, 0, 0, 2, Status.DONE, ended = true, newSeason = true)
        assertEquals(s, Show.fromJson(JSONObject(s.toJson().toString())))
    }

    @Test fun oldEntriesWithoutStatusAreWatching() {
        val s = Show.fromJson(JSONObject("""{"id": 5, "name": "A", "poster": null, "seasons": 1}"""))
        assertEquals(Status.WATCHING, s.status)
        assertFalse(s.ended)
    }

    @Test fun relativeDays() {
        val today = LocalDate.parse("2026-11-17")
        assertEquals("Bugün", relativeDay("2026-11-17", today))
        assertEquals("Yarın", relativeDay("2026-11-18", today))
        assertEquals("3 gün sonra", relativeDay("2026-11-20", today))
        assertEquals("12 Ara", relativeDay("2026-12-12", today))
        assertEquals("5 Oca 2027", relativeDay("2027-01-05", today))
    }

    @Test fun serializdStatuses() {
        // Trimmed real responses from /api/user/<name>/<list>/1
        val watched = JSONObject("""{"items": [
            {"showId": 93405, "seasonIds": [1, 2, 3], "showName": "Squid Game", "numSeasons": 3},
            {"showId": 97175, "seasonIds": [139705], "showName": "Fate: The Winx Saga", "numSeasons": 2}], "totalPages": 1}""")
        assertEquals(
            listOf(Show(93405, "Squid Game", null, 0, status = Status.DONE), Show(97175, "Fate: The Winx Saga", null, 0)),
            Serializd.parseItems(watched, "watchedpage_v2"),
        )
        val plain = JSONObject("""{"items": [{"showId": 85552, "dateAdded": "2024-05-15T05:45:44Z", "showName": "Euphoria", "bannerImage": "/a.jpg"}], "totalItems": 1, "totalPages": 1}""")
        assertEquals(Status.WATCHING, Serializd.parseItems(plain, "currently_watching_page").single().status)
        assertEquals(Status.WATCHING, Serializd.parseItems(plain, "paused_shows_page").single().status)
        assertEquals(Status.WATCHLIST, Serializd.parseItems(plain, "watchlistpage_v2").single().status)
    }

    @Test fun tmdbDetailsUseOriginalTitle() {
        val o = JSONObject("""{"id": 1, "name": "Money Heist", "original_name": "La casa de papel", "poster_path": "/p.jpg", "number_of_seasons": 3, "status": "Ended", "next_episode_to_air": null}""")
        assertEquals(Show(1, "La casa de papel", "/p.jpg", 3, ended = true), Tmdb.parseDetails(o))
    }

    @Test fun tmdbDetailsFallBackToEnglishForOtherScripts() {
        val o = JSONObject("""{"id": 93405, "name": "Squid Game", "original_name": "오징어 게임", "poster_path": "/p.jpg", "number_of_seasons": 3, "status": "Returning Series", "next_episode_to_air": {"air_date": "2027-01-05", "season_number": 4, "episode_number": 1}}""")
        assertEquals(Show(93405, "Squid Game", "/p.jpg", 3, "2027-01-05", 4, 1), Tmdb.parseDetails(o))
    }
}
