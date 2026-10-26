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

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoControllerPolicyTest {
    private val appPackage = "dev.sfg.orchard.mobile"

    @Test
    fun ownAppCanBrowse() {
        assertTrue(AutoControllerPolicy.canBrowse(appPackage, appPackage, isTrusted = false))
    }

    @Test
    fun trustedControllerCanBrowse() {
        assertTrue(AutoControllerPolicy.canBrowse(appPackage, "com.example.random", isTrusted = true))
    }

    @Test
    fun androidAutoCanBrowse() {
        assertTrue(
            AutoControllerPolicy.canBrowse(appPackage, "com.google.android.projection.gearhead", isTrusted = false),
        )
    }

    @Test
    fun systemUiCanBrowse() {
        assertTrue(AutoControllerPolicy.canBrowse(appPackage, "com.android.systemui", isTrusted = false))
    }

    @Test
    fun carAssistantCanBrowse() {
        assertTrue(AutoControllerPolicy.canBrowse(appPackage, "com.google.android.carassistant", isTrusted = false))
    }

    @Test
    fun arbitraryPackageCannotBrowse() {
        assertFalse(AutoControllerPolicy.canBrowse(appPackage, "com.example.random", isTrusted = false))
    }

    @Test
    fun blankPackageCannotBrowse() {
        assertFalse(AutoControllerPolicy.canBrowse(appPackage, "", isTrusted = false))
        assertFalse(AutoControllerPolicy.canBrowse(appPackage, null, isTrusted = false))
    }
}
