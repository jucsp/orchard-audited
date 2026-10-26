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

import androidx.media3.session.SessionError
import dev.sfg.orchard.mobile.model.Track
import dev.sfg.orchard.mobile.playback.auto.AutoControllerPolicy
import dev.sfg.orchard.mobile.playback.auto.AutoSearchPaging
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The callback itself needs a PlaybackStateStore (and therefore a Context), so its pure dispatch
 * seams are exercised here instead of Robolectric: error mapping, log redaction, the resume track
 * selection, search paging and controller authorization.
 */
class OrchardSessionCallbackAutoTest {

    @Test
    fun authFailureMapsToAuthenticationExpired() {
        assertEquals(
            SessionError.ERROR_SESSION_AUTHENTICATION_EXPIRED,
            BrowseFailure.AuthRequired().toBrowseSessionError().code,
        )
    }

    @Test
    fun networkFailureMapsToIo() {
        assertEquals(SessionError.ERROR_IO, BrowseFailure.Unavailable().toBrowseSessionError().code)
        assertEquals(SessionError.ERROR_IO, RuntimeException("boom").toBrowseSessionError().code)
    }

    @Test
    fun redactNodeHidesSearchQueriesOnly() {
        assertEquals("search", redactNode(OrchardMediaLibrary.SEARCH_PREFIX + "secret query"))
        assertEquals(OrchardMediaLibrary.LIKED, redactNode(OrchardMediaLibrary.LIKED))
    }

    @Test
    fun resumableTrackPrefersCurrentIndexThenFirst() {
        val tracks = listOf(Track("a", "A", "Artist"), Track("b", "B", "Artist"))

        assertEquals("b", RestoredPlayback(queue = tracks, currentIndex = 1).resumableTrack()?.id)
        assertEquals("a", RestoredPlayback(queue = tracks, currentIndex = -1).resumableTrack()?.id)
        assertNull(RestoredPlayback().resumableTrack())
    }

    @Test
    fun searchPagingCapsPageSizeAndPaginates() {
        val items = (1..250).toList()

        assertEquals(100, AutoSearchPaging.page(items, 0, 100).size)
        assertEquals((101..200).toList(), AutoSearchPaging.page(items, 1, 100))
        assertEquals((201..250).toList(), AutoSearchPaging.page(items, 2, 100))
        assertEquals(100, AutoSearchPaging.page(items, 0, 500).size)
    }

    @Test
    fun sanitizeQueryCapsAtTwoHundred() {
        assertEquals(200, AutoSearchPaging.sanitizeQuery("q".repeat(250)).length)
        assertEquals("hello", AutoSearchPaging.sanitizeQuery("hello"))
    }

    @Test
    fun controllerAuthorizationAllowsOnlyKnownBrowsers() {
        val appPackage = "dev.sfg.orchard.mobile"

        assertTrue(AutoControllerPolicy.canBrowse(appPackage, appPackage, isTrusted = false))
        assertTrue(AutoControllerPolicy.canBrowse(appPackage, "com.google.android.projection.gearhead", isTrusted = false))
        assertFalse(AutoControllerPolicy.canBrowse(appPackage, "com.example.random", isTrusted = false))
    }
}
