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

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import dev.sfg.orchard.mobile.model.CatalogItem
import dev.sfg.orchard.mobile.model.Track
import dev.sfg.orchard.mobile.playback.auto.OrchardBrowseSource
import java.util.concurrent.ConcurrentHashMap

/** A browse node that cannot be served; [OrchardSessionCallback] maps these to actionable errors. */
sealed class BrowseFailure(message: String) : Exception(message) {
    class AuthRequired : BrowseFailure("No YouTube session is signed in on this phone.")
    class Unavailable(message: String = "The catalog could not be loaded.") : BrowseFailure(message)
}

/**
 * The browse tree served to Android Auto and any other media browser.
 *
 * Browsers hand back only a media id when the driver taps a row, so every list served here is
 * retained: [queueFor] turns that id back into the list it came from, and a tap plays the whole
 * playlist from that point instead of one orphaned song.
 */
class OrchardMediaLibrary(
    private val source: OrchardBrowseSource,
    private val trackItem: (Track) -> MediaItem = MediaItemMapper::toMediaItem,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    // Parent id -> the tracks last served for it, and the reverse lookup for expansion. Both are
    // read from the session callback and written from the loader coroutine.
    private val servedLists = java.util.concurrent.ConcurrentHashMap<String, List<Track>>()
    private val parentOfTrack = java.util.concurrent.ConcurrentHashMap<String, String>()
    // Every browsable row ever served, so onGetItem can answer by id without re-fetching.
    private val browsableById = java.util.concurrent.ConcurrentHashMap<String, MediaItem>()
    private val nodeCache = ConcurrentHashMap<String, NodeCache>()

    private data class NodeCache(val items: List<MediaItem>, val expiresAtMs: Long)

    fun root(): MediaItem = browsable(ROOT, "Orchard")

    suspend fun children(parentId: String): List<MediaItem> {
        if (parentId.startsWith(SEARCH_PREFIX)) return search(parentId.removePrefix(SEARCH_PREFIX))
        nodeCache[parentId]?.takeIf { it.expiresAtMs > clock() }?.let { return it.items }
        val loaded = loadChildren(parentId)
        nodeCache[parentId] = NodeCache(loaded, clock() + NODE_TTL_MS)
        return loaded
    }

    private suspend fun loadChildren(parentId: String): List<MediaItem> = when (parentId) {
        ROOT -> listOf(
            browsable(HOME, "Inicio", mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED),
            browsable(LIBRARY, "Biblioteca", mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED),
            browsable(RECENT, "Recientes", mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED),
        )
        LIBRARY -> listOf(
            browsable(LIKED, "Me gusta", mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED),
            browsable(PLAYLISTS, "Playlists", mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS),
            browsable(ALBUMS, "Álbumes", mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS),
            browsable(PODCASTS, "Pódcasts", mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_PODCASTS),
        )
        HOME -> requireSession { homeSections() }
        LIKED -> requireSession { likedChildren() }
        PLAYLISTS -> requireSession { playlists() }
        ALBUMS -> requireSession { albums() }
        PODCASTS -> requireSession { podcasts() }
        RECENT -> tracks(RECENT) { source.library().recentlyPlayed }
        else -> when {
            parentId.startsWith(HOME_PREFIX) -> requireSession { homeSectionChildren(parentId) }
            parentId.startsWith(PLAYLIST_PREFIX) -> requireSession { playlistChildren(parentId) }
            parentId.startsWith(ALBUM_PREFIX) -> requireSession { albumChildren(parentId) }
            parentId.startsWith(PODCAST_PREFIX) -> requireSession { podcastChildren(parentId) }
            parentId.startsWith(ARTIST_PREFIX) -> requireSession { artistChildren(parentId) }
            parentId.startsWith(CATEGORY_PREFIX) -> requireSession { categoryChildren(parentId) }
            else -> emptyList()
        }
    }

    /**
     * Tracks for a spoken or typed query, retained like any other list so the result can be played
     * as a queue. A blank query is a request for liked songs with no other preference.
     */
    suspend fun search(query: String): List<MediaItem> {
        val id = searchId(query)
        nodeCache[id]?.takeIf { it.expiresAtMs > clock() }?.let { return it.items }
        val loaded = tracks(id) {
            if (query.isBlank()) source.library().likedTracks
            else source.search(query).tracks
        }
        nodeCache[id] = NodeCache(loaded, clock() + NODE_TTL_MS)
        return loaded
    }

    /** Resolves a media id already served through [children]; browsers re-request items by id. */
    fun item(mediaId: String): MediaItem? {
        trackFor(mediaId)?.let { return trackItem(it) }
        return browsableById[mediaId]
    }

    /**
     * The list [mediaId] was served in, paired with its position, so a tapped row starts the whole
     * list. Null when the id is not a track from a browse result.
     */
    fun queueFor(mediaId: String): Pair<List<MediaItem>, Int>? {
        val parent = parentOfTrack[mediaId] ?: return null
        val list = servedLists[parent] ?: return null
        val index = list.indexOfFirst { it.id == mediaId }
        if (index < 0) return null
        return list.map(trackItem) to index
    }

    private fun trackFor(mediaId: String): Track? =
        parentOfTrack[mediaId]?.let { servedLists[it] }?.firstOrNull { it.id == mediaId }

    private suspend fun requireSession(load: suspend () -> List<MediaItem>): List<MediaItem> {
        if (!source.hasSession()) throw BrowseFailure.AuthRequired()
        return load()
    }

    private suspend fun homeSections(): List<MediaItem> = source.home().sections.map { section ->
        browsable(homeSectionId(section.id), section.title.ifBlank { "Sección" })
    }

    private suspend fun homeSectionChildren(parentId: String): List<MediaItem> {
        val sectionId = parentId.removePrefix(HOME_PREFIX)
        val section = source.home().sections.firstOrNull { it.id == sectionId } ?: return emptyList()
        return catalogItems(parentId, section.items)
    }

    private suspend fun likedChildren(): List<MediaItem> {
        val cached = source.library().likedTracks
        if (cached.isNotEmpty()) return tracks(LIKED) { cached }
        source.refreshLibrary()
        return tracks(LIKED) { source.library().likedTracks.ifEmpty { source.likedSongs() } }
    }

    private suspend fun playlists(): List<MediaItem> = signedInCollection(
        current = source.library().savedPlaylists,
        read = { source.library().savedPlaylists },
        map = { playlist -> browsable(playlistId(playlist.id), playlist.title, playlist.author, playlist.artworkUrl, MediaMetadata.MEDIA_TYPE_PLAYLIST) },
    )

    private suspend fun albums(): List<MediaItem> = signedInCollection(
        current = source.library().savedAlbums,
        read = { source.library().savedAlbums },
        map = { album -> browsable(albumId(album.id), album.title, album.artist, album.artworkUrl, MediaMetadata.MEDIA_TYPE_ALBUM) },
    )

    private suspend fun podcasts(): List<MediaItem> = signedInCollection(
        current = source.library().savedPodcasts,
        read = { source.library().savedPodcasts },
        map = { podcast -> browsable(podcastId(podcast.id), podcast.title, podcast.author, podcast.artworkUrl, MediaMetadata.MEDIA_TYPE_PODCAST) },
    )

    private suspend fun <T> signedInCollection(
        current: List<T>,
        read: () -> List<T>,
        map: (T) -> MediaItem,
    ): List<MediaItem> {
        if (current.isNotEmpty()) return current.map(map)
        val refreshed = source.refreshLibrary()
        val next = read()
        if (next.isNotEmpty()) return next.map(map)
        if (refreshed.isFailure) throw BrowseFailure.Unavailable()
        return emptyList()
    }

    private suspend fun playlistChildren(parentId: String): List<MediaItem> =
        tracks(parentId) { source.browse(parentId.removePrefix(PLAYLIST_PREFIX)).tracks }

    private suspend fun albumChildren(parentId: String): List<MediaItem> =
        tracks(parentId) { source.browse(parentId.removePrefix(ALBUM_PREFIX)).tracks }

    private suspend fun podcastChildren(parentId: String): List<MediaItem> =
        tracks(parentId) { source.browse(parentId.removePrefix(PODCAST_PREFIX)).tracks }

    private suspend fun artistChildren(parentId: String): List<MediaItem> {
        val detail = source.browse(parentId.removePrefix(ARTIST_PREFIX))
        val items = detail.sections.flatMap { it.items }.ifEmpty { detail.related }
        return catalogItems(parentId, items)
    }

    private suspend fun categoryChildren(parentId: String): List<MediaItem> {
        val target = parentId.removePrefix(CATEGORY_PREFIX)
        val id = target.substringBefore(":")
        val params = target.substringAfter(":", "")
        return catalogItems(parentId, source.sectionItems(id, params))
    }

    private fun catalogItems(parentId: String, items: List<CatalogItem>): List<MediaItem> {
        val songs = items.filterIsInstance<CatalogItem.Song>().map { it.track }
        servedLists[parentId] = songs
        songs.forEach { parentOfTrack[it.id] = parentId }
        return items.map { item ->
            when (item) {
                is CatalogItem.Song -> trackItem(item.track)
                is CatalogItem.Record -> browsable(albumId(item.album.id), item.album.title, item.album.artist, item.album.artworkUrl, MediaMetadata.MEDIA_TYPE_ALBUM)
                is CatalogItem.Collection -> browsable(playlistId(item.playlist.id), item.playlist.title, item.playlist.author, item.playlist.artworkUrl, MediaMetadata.MEDIA_TYPE_PLAYLIST)
                is CatalogItem.Performer -> browsable(artistId(item.artist.id), item.artist.name, item.artist.subtitle, item.artist.artworkUrl, MediaMetadata.MEDIA_TYPE_ARTIST)
                is CatalogItem.Category -> browsable(categoryId(item.stableId), item.title)
            }
        }
    }

    private suspend fun tracks(parentId: String, load: suspend () -> List<Track>): List<MediaItem> {
        val loaded = load()
        servedLists[parentId] = loaded
        loaded.forEach { parentOfTrack[it.id] = parentId }
        return loaded.map(trackItem)
    }

    private fun browsable(
        id: String,
        title: String,
        subtitle: String = "",
        artworkUrl: String = "",
        mediaType: Int = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED,
    ): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setSubtitle(subtitle.takeIf(String::isNotBlank))
            .setArtworkUri(artworkUrl.takeIf(String::isNotBlank)?.let(Uri::parse))
            .setIsBrowsable(true)
            .setIsPlayable(false)
            .setMediaType(mediaType)
            .build()
        val item = MediaItem.Builder().setMediaId(id).setMediaMetadata(metadata).build()
        browsableById[id] = item
        return item
    }

    private fun playlistId(id: String): String = "$PLAYLIST_PREFIX$id"
    private fun albumId(id: String): String = "$ALBUM_PREFIX$id"
    private fun podcastId(id: String): String = "$PODCAST_PREFIX$id"
    private fun artistId(id: String): String = "$ARTIST_PREFIX$id"
    private fun categoryId(id: String): String = "$CATEGORY_PREFIX$id"
    private fun homeSectionId(id: String): String = "$HOME_PREFIX$id"
    private fun searchId(query: String): String = "$SEARCH_PREFIX$query"

    companion object {
        const val ROOT = "orchard://root"
        const val HOME = "orchard://home"
        const val LIBRARY = "orchard://library"
        const val LIKED = "orchard://liked"
        const val PLAYLISTS = "orchard://playlists"
        const val ALBUMS = "orchard://albums"
        const val PODCASTS = "orchard://podcasts"
        const val RECENT = "orchard://recent"
        const val RECENT_ROOT = "orchard://recent-root"
        const val PLAYLIST_PREFIX = "orchard://playlist/"
        const val ALBUM_PREFIX = "orchard://album/"
        const val PODCAST_PREFIX = "orchard://podcast/"
        const val ARTIST_PREFIX = "orchard://artist/"
        const val CATEGORY_PREFIX = "orchard://category/"
        const val HOME_PREFIX = "orchard://home/"
        const val SEARCH_PREFIX = "orchard://search/"
        private const val NODE_TTL_MS = 5 * 60 * 1000L

        /**
         * Android Auto reads these off the root to pick its layout: playlists as a grid of covers,
         * songs as a list. Without them every level falls back to the same dense list.
         */
        fun rootExtras(): Bundle = Bundle().apply {
            putInt(CONTENT_STYLE_BROWSABLE_HINT, CONTENT_STYLE_GRID)
            putInt(CONTENT_STYLE_PLAYABLE_HINT, CONTENT_STYLE_LIST)
            putBoolean(CONTENT_STYLE_SUPPORTED, true)
        }

        /** The reanudación root served when the system asks for the most recent playback. */
        fun recentRootItem(): MediaItem {
            val metadata = MediaMetadata.Builder()
                .setTitle("Recientes")
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                .build()
            return MediaItem.Builder().setMediaId(RECENT_ROOT).setMediaMetadata(metadata).build()
        }

        private const val CONTENT_STYLE_SUPPORTED = "android.media.browse.CONTENT_STYLE_SUPPORTED"
        private const val CONTENT_STYLE_BROWSABLE_HINT =
            "android.media.browse.CONTENT_STYLE_BROWSABLE_HINT"
        private const val CONTENT_STYLE_PLAYABLE_HINT =
            "android.media.browse.CONTENT_STYLE_PLAYABLE_HINT"
        private const val CONTENT_STYLE_LIST = 1
        private const val CONTENT_STYLE_GRID = 2
    }
}
