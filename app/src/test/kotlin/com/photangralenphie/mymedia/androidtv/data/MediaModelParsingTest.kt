package com.photangralenphie.mymedia.androidtv.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaModelParsingTest {
    @Test
    fun `movie fixture maps nullable fields credits and description fallback`() {
        val movie = MediaDetail.fromJson(
            JSONObject(
                """
                {
                  "id": "movie-1",
                  "title": "Arrival",
                  "year": 2016,
                  "genre": ["Drama", "Science Fiction"],
                  "durationMinutes": 116,
                  "progressMinutes": 23,
                  "isWatched": false,
                  "isFavorite": true,
                  "isPinned": false,
                  "artworkURL": "/api/v1/artwork/movie-1",
                  "longDescription": null,
                  "shortDescription": "First contact changes everything.",
                  "releaseDate": "2016-09-01T00:00:00Z",
                  "studio": null,
                  "hdVideoQuality": "4k",
                  "rating": "mpaa|PG-13|",
                  "languages": ["English"],
                  "credits": {
                    "cast": ["Amy Adams"],
                    "directors": ["Denis Villeneuve"],
                    "coDirectors": [],
                    "screenwriters": ["Eric Heisserer"],
                    "producers": [],
                    "executiveProducers": [],
                    "composer": "Jóhann Jóhannsson"
                  }
                }
                """.trimIndent(),
            ),
            kindHint = "movie",
        )

        assertEquals("movie", movie.kind)
        assertEquals("First contact changes everything.", movie.description)
        assertEquals(listOf("Drama", "Science Fiction"), movie.genres)
        assertEquals("Denis Villeneuve", movie.credits.directors.single())
        assertEquals("Jóhann Jóhannsson", movie.credits.composer)
        assertTrue(movie.isFavorite)
        assertFalse(movie.isWatched)
        assertNull(movie.studio)
    }

    @Test
    fun `tv show and episode fixtures preserve children and parent relationship`() {
        val show = MediaDetail.fromJson(
            JSONObject(
                """
                {
                  "id": "show-1",
                  "title": "Example Show",
                  "year": 2024,
                  "genre": ["Drama"],
                  "showDescription": "A serialized story.",
                  "isFavorite": false,
                  "isPinned": true,
                  "isWatched": false,
                  "networks": ["Example Network"],
                  "durationMinutes": 45,
                  "episodes": [
                    {"kind": "episode", "id": "episode-1", "name": "Pilot", "year": 2024, "season": 1, "episode": 1}
                  ]
                }
                """.trimIndent(),
            ),
            kindHint = "tvShow",
        )
        val episode = MediaDetail.fromJson(
            JSONObject(
                """
                {
                  "id": "episode-1",
                  "title": "Pilot",
                  "year": 2024,
                  "season": 1,
                  "episode": 1,
                  "durationMinutes": 45,
                  "progressMinutes": 0,
                  "isWatched": false,
                  "isFavorite": false,
                  "isPinned": false,
                  "episodeLongDescription": "The story begins.",
                  "releaseDate": "2024-01-01T00:00:00Z",
                  "languages": ["English"],
                  "credits": {"cast": [], "directors": [], "coDirectors": [], "screenwriters": [], "producers": [], "executiveProducers": [], "composer": null},
                  "tvShow": {"kind": "tvShow", "id": "show-1", "name": "Example Show", "year": 2024}
                }
                """.trimIndent(),
            ),
            kindHint = "episode",
        )

        assertEquals("episode-1", show.children.single().id)
        assertEquals(listOf("Example Network"), show.networks)
        assertEquals("show-1", episode.parentShow?.id)
        assertEquals("The story begins.", episode.description)
        assertEquals(episode, MediaDetail.fromJson(episode.toJson(), "episode"))
    }

    @Test
    fun `person fixture maps role-specific media lists`() {
        val person = PersonDetail.fromJson(
            JSONObject(
                """
                {
                  "name": "Example Person",
                  "roles": ["Actor", "Director"],
                  "creditedMovies": [{"kind": "movie", "id": "movie-1", "name": "Movie"}],
                  "creditedEpisodes": [],
                  "credits": {
                    "cast": [{"kind": "movie", "id": "movie-1", "name": "Movie"}],
                    "directors": [{"kind": "episode", "id": "episode-1", "name": "Episode", "season": 1, "episode": 2}],
                    "coDirectors": [],
                    "screenwriters": [],
                    "producers": [],
                    "executiveProducers": [],
                    "composer": []
                  }
                }
                """.trimIndent(),
            ),
        )

        assertEquals(listOf("Actor", "Director"), person.roles)
        assertEquals("movie-1", person.creditedMovies.single().id)
        assertEquals("episode-1", person.credits.directors.single().id)
    }
}
