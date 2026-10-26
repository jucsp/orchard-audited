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

package dev.sfg.orchard.mobile.playback.auto

import dev.sfg.orchard.mobile.OrchardGraph
import dev.sfg.orchard.mobile.model.BrowseDetail
import dev.sfg.orchard.mobile.model.CatalogItem
import dev.sfg.orchard.mobile.model.HomeFeed
import dev.sfg.orchard.mobile.model.LibrarySnapshot
import dev.sfg.orchard.mobile.model.SearchResults
import dev.sfg.orchard.mobile.model.Track

/**
 * Read-only view of the signed-in catalog and library that the browse tree needs.
 *
 * The concrete graph touches Android and QuickJS; the interface keeps [dev.sfg.orchard.mobile.playback.OrchardMediaLibrary]
 * unit-testable against a fake.
 */
interface OrchardBrowseSource {
    fun hasSession(): Boolean
    fun library(): LibrarySnapshot
    suspend fun refreshLibrary(): Result<Unit>
    suspend fun likedSongs(): List<Track>
    suspend fun browse(id: String): BrowseDetail
    suspend fun search(query: String): SearchResults
    suspend fun home(): HomeFeed
    suspend fun sectionItems(browseId: String, params: String): List<CatalogItem>
}

/** Adapts [OrchardGraph] to [OrchardBrowseSource] without pulling the graph into the browse tree. */
class GraphBrowseSource(private val graph: OrchardGraph) : OrchardBrowseSource {
    override fun hasSession(): Boolean = graph.auth.session() != null
    override fun library(): LibrarySnapshot = graph.library.library.value
    override suspend fun refreshLibrary(): Result<Unit> = graph.library.refreshSignedInLibrary()
    override suspend fun likedSongs(): List<Track> = graph.catalog.likedSongs().tracks
    override suspend fun browse(id: String): BrowseDetail = graph.catalog.browse(id)
    override suspend fun search(query: String): SearchResults = graph.catalog.search(query)
    override suspend fun home(): HomeFeed = graph.catalog.home()
    override suspend fun sectionItems(browseId: String, params: String): List<CatalogItem> =
        graph.catalog.sectionItems(browseId, params)
}
