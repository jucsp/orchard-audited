/*
 * Copyright (C) 2026 SFG545
 *
 * This file is part of Orchard.
 *
 * Orchard is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Affero General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option) any
 * later version.
 *
 * Orchard is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU Affero General Public License for more
 * details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Orchard. If not, see <https://www.gnu.org/licenses/>.
 */

package dev.sfg.orchard.mobile.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import dev.sfg.orchard.mobile.model.BrowseDetail
import dev.sfg.orchard.mobile.model.CatalogItem
import dev.sfg.orchard.mobile.model.CatalogKind
import dev.sfg.orchard.mobile.model.CatalogSection
import dev.sfg.orchard.mobile.model.HomeFeed
import dev.sfg.orchard.mobile.model.LibrarySnapshot
import dev.sfg.orchard.mobile.model.SearchResults
import dev.sfg.orchard.mobile.model.Track
import dev.sfg.orchard.mobile.playback.auto.OrchardBrowseSource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

class OrchardMediaLibraryTest {
    /** Builds playable items without touching android.net.Uri or Bundle, so the test stays JVM-pure. */
    private val trackItem: (Track) -> MediaItem = { track ->
        MediaItem.Builder()
            .setMediaId(track.id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(track.title)
                    .setArtist(track.artist)
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .build()
            )
            .build()
    }

    private class FakeSource(
        var session: Boolean = true,
        val snapshot: LibrarySnapshot = LibrarySnapshot(),
        var home: HomeFeed = HomeFeed(),
        val browseDetails: MutableMap<String, BrowseDetail> = mutableMapOf(),
        var searchResults: SearchResults = SearchResults(),
        var refreshResult: Result<Unit> = Result.success(Unit),
    ) : OrchardBrowseSource {
        var refreshCalls = 0
        var browseCalls = 0
        var searchCalls = 0

        override fun hasSession(): Boolean = session
        override fun library(): LibrarySnapshot = snapshot
        override suspend fun refreshLibrary(): Result<Unit> {
            refreshCalls++
            return refreshResult
        }
        override suspend fun likedSongs(): List<Track> = snapshot.likedTracks
        override suspend fun browse(id: String): BrowseDetail {
            browseCalls++
            return browseDetails[id] ?: BrowseDetail(id = id, kind = CatalogKind.PLAYLIST, title = "")
        }
        override suspend fun search(query: String): SearchResults {
            searchCalls++
            return searchResults
        }
        override suspend fun home(): HomeFeed = home
        override suspend fun sectionItems(browseId: String, params: String): List<CatalogItem> = emptyList()
    }

    @Test
    fun rootHasThreeTabs() = runBlocking {
        val library = OrchardMediaLibrary(FakeSource(), trackItem = trackItem)

        val children = library.children(OrchardMediaLibrary.ROOT)

        assertEquals(
            listOf(OrchardMediaLibrary.HOME, OrchardMediaLibrary.LIBRARY, OrchardMediaLibrary.RECENT),
            children.map { it.mediaId },
        )
    }

    @Test
    fun libraryHasFourChildren() = runBlocking {
        val library = OrchardMediaLibrary(FakeSource(), trackItem = trackItem)

        val children = library.children(OrchardMediaLibrary.LIBRARY)

        assertEquals(
            listOf(OrchardMediaLibrary.LIKED, OrchardMediaLibrary.PLAYLISTS, OrchardMediaLibrary.ALBUMS, OrchardMediaLibrary.PODCASTS),
            children.map { it.mediaId },
        )
    }

    @Test
    fun albumAndPodcastResolveChildren() = runBlocking {
        val source = FakeSource(
            browseDetails = mutableMapOf(
                "album-1" to BrowseDetail(id = "album-1", kind = CatalogKind.ALBUM, title = "Album", tracks = listOf(Track("a1", "T1", "Artist"))),
                "podcast-1" to BrowseDetail(id = "podcast-1", kind = CatalogKind.PLAYLIST, title = "Podcast", tracks = listOf(Track("p1", "E1", "Artist"))),
            ),
        )
        val library = OrchardMediaLibrary(source, trackItem = trackItem)

        assertEquals(listOf("a1"), library.children(OrchardMediaLibrary.ALBUM_PREFIX + "album-1").map { it.mediaId })
        assertEquals(listOf("p1"), library.children(OrchardMediaLibrary.PODCAST_PREFIX + "podcast-1").map { it.mediaId })
    }

    @Test
    fun playlistChildrenAreServedFromCacheOnSecondPageRequest() = runBlocking {
        val source = FakeSource(
            browseDetails = mutableMapOf(
                "pl-1" to BrowseDetail(
                    id = "pl-1",
                    kind = CatalogKind.PLAYLIST,
                    title = "Playlist",
                    tracks = (1..150).map { Track("t$it", "T$it", "Artist") },
                ),
            ),
        )
        val library = OrchardMediaLibrary(source, trackItem = trackItem)

        library.children(OrchardMediaLibrary.PLAYLIST_PREFIX + "pl-1")
        library.children(OrchardMediaLibrary.PLAYLIST_PREFIX + "pl-1")

        assertEquals(1, source.browseCalls)
    }

    @Test
    fun missingSessionThrowsAuthenticationRequired() = runBlocking {
        val library = OrchardMediaLibrary(FakeSource(session = false), trackItem = trackItem)

        try {
            library.children(OrchardMediaLibrary.LIKED)
            fail("Expected BrowseFailure.AuthRequired")
        } catch (_: BrowseFailure.AuthRequired) {
            // Expected: nodes that need a signed-in session surface an actionable error.
        }
    }

    @Test
    fun queueForReturnsFullPlaylistForATappedTrack() = runBlocking {
        val source = FakeSource(
            browseDetails = mutableMapOf(
                "pl-1" to BrowseDetail(
                    id = "pl-1",
                    kind = CatalogKind.PLAYLIST,
                    title = "Playlist",
                    tracks = listOf(Track("t1", "First", "Artist"), Track("t2", "Second", "Artist")),
                ),
            ),
        )
        val library = OrchardMediaLibrary(source, trackItem = trackItem)
        library.children(OrchardMediaLibrary.PLAYLIST_PREFIX + "pl-1")

        val (queue, index) = library.queueFor("t2")!!

        assertEquals(listOf("t1", "t2"), queue.map { it.mediaId })
        assertEquals(1, index)
    }

    @Test
    fun blankSearchFallsBackToLikedTracks() = runBlocking {
        val liked = listOf(Track("liked-1", "Liked", "Artist"))
        val library = OrchardMediaLibrary(FakeSource(snapshot = LibrarySnapshot(likedTracks = liked)), trackItem = trackItem)

        assertEquals(listOf("liked-1"), library.search("").map { it.mediaId })
    }

    @Test
    fun nonBlankSearchUsesProvider() = runBlocking {
        val source = FakeSource(searchResults = SearchResults(tracks = listOf(Track("s1", "Song", "Artist"))))
        val library = OrchardMediaLibrary(source, trackItem = trackItem)

        assertEquals(listOf("s1"), library.search("orchard").map { it.mediaId })
        assertEquals(1, source.searchCalls)
    }

    @Test
    fun homeListsSections() = runBlocking {
        val source = FakeSource(
            home = HomeFeed(sections = listOf(CatalogSection(id = "s1", title = "Sección", items = emptyList()))),
        )
        val library = OrchardMediaLibrary(source, trackItem = trackItem)

        assertEquals(listOf(OrchardMediaLibrary.HOME_PREFIX + "s1"), library.children(OrchardMediaLibrary.HOME).map { it.mediaId })
    }

    @Test
    fun recentNodeServesRecentlyPlayed() = runBlocking {
        val library = OrchardMediaLibrary(
            FakeSource(snapshot = LibrarySnapshot(recentlyPlayed = listOf(Track("r1", "Recent", "Artist")))),
            trackItem = trackItem,
        )

        assertEquals(listOf("r1"), library.children(OrchardMediaLibrary.RECENT).map { it.mediaId })
    }

    @Test
    fun unknownParentReturnsEmptyList() = runBlocking {
        val library = OrchardMediaLibrary(FakeSource(), trackItem = trackItem)

        assertEquals(emptyList<String>(), library.children("orchard://unknown").map { it.mediaId })
    }

    @Test
    fun itemResolvesUnknownIdToNull() = runBlocking {
        val library = OrchardMediaLibrary(FakeSource(), trackItem = trackItem)

        assertNull(library.item("missing"))
    }
}
