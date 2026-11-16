package com.efeyamann.dizi

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShowTest {
    private val base = Show(1, "Dizi", null, 2)

    @Test fun newSeasonCountNotifies() {
        val (msg, stored) = checkNews(base, base.copy(seasons = 3, nextAir = "2027-01-05", nextSeason = 3, nextEpisode = 1), "2026-11-17")
        assertEquals("3. sezon duyuruldu · ilk bölüm 2027-01-05", msg)
        assertEquals(3, stored.seasons)
    }

    @Test fun unknownSeasonsDoNotNotify() {
        assertNull(checkNews(base.copy(seasons = 0), base.copy(seasons = 5), "2026-11-17").first)
    }

    @Test fun nothingChanged() {
        assertNull(checkNews(base, base, "2026-11-17").first)
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

    @Test fun jsonRoundTripKeepsNulls() {
        val s = Show(5, "A", null, 1, null, 0, 0, 2)
        assertEquals(s, Show.fromJson(JSONObject(s.toJson().toString())))
    }

    @Test fun parsesSerializdPage() {
        // Trimmed real response from /api/user/<name>/watchedpage_v2/1
        val o = JSONObject("""{"items": [{"showId": 93405, "seasonIds": [131977], "dateAdded": "2024-05-19T05:59:52Z", "showName": "Squid Game", "bannerImage": "/1QdXdRYfktUSONkl1oD5gc6Be0s.jpg", "numSeasons": 3, "numEpisodes": 22, "premiereDate": "2021-09-17"}], "totalPages": 1, "numberOfShows": 1, "numberOfSeasons": 1}""")
        assertEquals(listOf(Show(93405, "Squid Game", null, 0)), Serializd.parseItems(o))
    }

    @Test fun parsesTmdbDetails() {
        val o = JSONObject("""{"id": 93405, "name": "Squid Game", "poster_path": "/p.jpg", "number_of_seasons": 3, "next_episode_to_air": {"air_date": "2027-01-05", "season_number": 4, "episode_number": 1}}""")
        assertEquals(Show(93405, "Squid Game", "/p.jpg", 3, "2027-01-05", 4, 1), Tmdb.parseDetails(o))
        o.put("next_episode_to_air", JSONObject.NULL)
        assertEquals(Show(93405, "Squid Game", "/p.jpg", 3), Tmdb.parseDetails(o))
    }
}
