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

    @Test fun newSeasonKeepsFinishedShowInDoneWithBadge() {
        val old = base.copy(status = Status.DONE, watched = setOf(1, 2))
        val (_, stored) = checkNews(old, base.copy(seasons = 3, nextAir = "2027-02-10", nextSeason = 3, nextEpisode = 1), "2026-11-17")
        assertEquals(Status.DONE, stored.status)
        assertEquals(setOf(1, 2), stored.watched)
        assertTrue(stored.newSeason)
        assertEquals(3, stored.resumeAt)
    }

    @Test fun seasonTapMeansWatchedUpToIt() {
        val s = Show(1, "A", null, 8)
        val six = s.toggleSeason(6)
        assertEquals((1..6).toSet(), six.watched)
        assertEquals(Status.WATCHING, six.status)
        assertEquals(7, six.resumeAt)
        assertEquals((1..5).toSet(), six.toggleSeason(6).watched) // tapping the last one steps back
        assertEquals((1..3).toSet(), six.toggleSeason(3).watched)
        val all = six.toggleSeason(8)
        assertEquals(Status.DONE, all.status)
        assertNull(all.resumeAt)
        assertEquals(Status.WATCHING, all.toggleSeason(8).status)
        assertEquals(Status.WATCHING, s.copy(status = Status.WATCHLIST).toggleSeason(1).status) // started watching
    }

    @Test fun episodeLabelsAndDates() {
        val today = LocalDate.parse("2026-11-17")
        assertEquals("3/16. bölüm", Show(1, "A", null, 2, nextEpisode = 3, nextTotal = 16).nextEpisodeLabel())
        assertEquals("3. bölüm", Show(1, "A", null, 2, nextEpisode = 3).nextEpisodeLabel())
        assertEquals("20 Kasım", longDate("2026-11-20", today))
        assertEquals("5 Şubat 2027", longDate("2027-02-05", today))
        assertTrue(withinWeek("2026-11-23", today))
        assertFalse(withinWeek("2026-11-24", today))
    }

    @Test fun tmdbNextSeasonEpisodeCount() {
        val o = JSONObject("""{"id": 1, "name": "A", "original_name": "A", "poster_path": null, "number_of_seasons": 2, "status": "Returning Series",
            "next_episode_to_air": {"air_date": "2026-11-20", "season_number": 2, "episode_number": 3},
            "seasons": [{"id": 9, "season_number": 0, "episode_count": 4}, {"id": 10, "season_number": 1, "episode_count": 8}, {"id": 11, "season_number": 2, "episode_count": 16}]}""")
        assertEquals(16, Tmdb.parseDetails(o).nextTotal)
    }

    @Test fun doneButtonMarksAllSeasons() {
        val s = Show(1, "A", null, 3, watched = setOf(1)).markDone()
        assertEquals(Status.DONE, s.status)
        assertEquals(setOf(1, 2, 3), s.watched)
    }

    @Test fun jsonRoundTrip() {
        val s = Show(5, "A", null, 1, null, 0, 0, 2, Status.DONE, ended = true, newSeason = true, watched = setOf(1))
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
            listOf(
                Show(93405, "Squid Game", null, 0, status = Status.DONE) to listOf(1, 2, 3),
                Show(97175, "Fate: The Winx Saga", null, 0) to listOf(139705),
            ),
            Serializd.parseItems(watched, "watchedpage_v2"),
        )
        val plain = JSONObject("""{"items": [{"showId": 85552, "dateAdded": "2024-05-15T05:45:44Z", "showName": "Euphoria", "bannerImage": "/a.jpg"}], "totalItems": 1, "totalPages": 1}""")
        assertEquals(Status.WATCHING, Serializd.parseItems(plain, "currently_watching_page").single().first.status)
        assertEquals(Status.WATCHING, Serializd.parseItems(plain, "paused_shows_page").single().first.status)
        assertEquals(Status.WATCHLIST, Serializd.parseItems(plain, "watchlistpage_v2").single().first.status)
    }

    @Test fun tmdbSeasonNumbersSkipSpecials() {
        val o = JSONObject("""{"seasons": [{"id": 10, "season_number": 0}, {"id": 11, "season_number": 1}, {"id": 12, "season_number": 2}]}""")
        assertEquals(mapOf(11 to 1, 12 to 2), Tmdb.seasonNumbers(o))
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
