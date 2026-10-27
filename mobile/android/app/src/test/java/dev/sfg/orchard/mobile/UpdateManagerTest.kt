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

package dev.sfg.orchard.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateManagerTest {

    @Test
    fun parsesMetadataWithReleaseNotes() {
        val json = """
            {
              "version": "1.1.0",
              "codename": "Praise Perceived",
              "versionCode": 2,
              "apkUrl": "https://github.com/jucsp/orchard-audited/releases/latest/download/Orchard-1.1.0.apk",
              "sha256": "dummyhash",
              "publishedAt": "2026-08-07T00:00:00Z",
              "releaseNotes": "## Orchard Mobile 1.1.0\n- Added release notes metadata"
            }
        """.trimIndent()

        val metadata = UpdateManager.parseUpdateMetadata(json)

        assertEquals("1.1.0", metadata.version)
        assertEquals("Praise Perceived", metadata.codename)
        assertEquals(2, metadata.versionCode)
        assertEquals("https://github.com/jucsp/orchard-audited/releases/latest/download/Orchard-1.1.0.apk", metadata.apkUrl)
        assertEquals("dummyhash", metadata.sha256)
        assertEquals("## Orchard Mobile 1.1.0\n- Added release notes metadata", metadata.releaseNotes)
    }

    @Test
    fun comparesVersionsCorrectly() {
        assertEquals(1, UpdateManager.compareVersions("1.1.0", "1.0.0"))
        assertEquals(0, UpdateManager.compareVersions("1.0.0", "1.0.0"))
        assertEquals(-1, UpdateManager.compareVersions("1.0.0", "1.1.0"))
    }

    /**
     * Build-type suffixes used to make the trailing segment parse as 0, so "1.1.1-debug" read as
     * 1.1.0 and every suffixed build believed it was one release behind.
     */
    @Test
    fun ignoresBuildSuffixWhenComparing() {
        assertEquals(0, UpdateManager.compareVersions("1.1.1", "1.1.1-debug"))
        assertEquals(0, UpdateManager.compareVersions("1.1.1", "1.1.1-rc1"))
        assertEquals(1, UpdateManager.compareVersions("1.1.2", "1.1.1-debug"))
        assertEquals(-1, UpdateManager.compareVersions("1.1.0", "1.1.1-debug"))
    }

    @Test
    fun comparesBetaVersionsCorrectly() {
        // Newer beta vs older beta
        assertEquals(1, UpdateManager.compareVersions("1.8.0-beta.2", "1.8.0-beta.1"))
        // Same beta
        assertEquals(0, UpdateManager.compareVersions("1.8.0-beta.1", "1.8.0-beta.1"))
        // Older beta vs newer beta
        assertEquals(-1, UpdateManager.compareVersions("1.8.0-beta.1", "1.8.0-beta.2"))
        // Stable release is newer than its beta
        assertEquals(1, UpdateManager.compareVersions("1.8.0", "1.8.0-beta.1"))
        // Beta is older than its stable release
        assertEquals(-1, UpdateManager.compareVersions("1.8.0-beta.1", "1.8.0"))
        // Older major/minor vs beta
        assertEquals(-1, UpdateManager.compareVersions("1.7.0", "1.8.0-beta.1"))
        // Newer major/minor vs beta
        assertEquals(1, UpdateManager.compareVersions("1.9.0", "1.8.0-beta.1"))
    }

    @Test
    fun parsesGitHubReleasesWithManifestAsset() {
        val releasesJson = """
            [
              {
                "tag_name": "v1.8.0-beta.1",
                "name": "Orchard Mobile 1.8.0 (beta)",
                "prerelease": true,
                "body": "Beta notes",
                "published_at": "2026-08-20T00:00:00Z",
                "assets": [
                  {
                    "name": "latest-android.json",
                    "browser_download_url": "https://github.com/downloads/latest-android.json"
                  },
                  {
                    "name": "Orchard-1.8.0-beta.1.apk",
                    "browser_download_url": "https://github.com/downloads/Orchard-1.8.0-beta.1.apk"
                  }
                ]
              }
            ]
        """.trimIndent()

        val manifestJson = """
            {
              "version": "1.8.0-beta.1",
              "codename": "Beta Codename",
              "versionCode": 10801,
              "apkUrl": "https://github.com/downloads/Orchard-1.8.0-beta.1.apk",
              "sha256": "abc123hash",
              "publishedAt": "2026-08-20T00:00:00Z",
              "releaseNotes": "## Orchard Mobile 1.8.0-beta.1\n- Bug fixes"
            }
        """.trimIndent()

        val metadata = UpdateManager.parseGitHubReleasesMetadata(
            releasesJson = releasesJson,
            fetchManifest = { manifestJson },
        )

        org.junit.Assert.assertNotNull(metadata)
        assertEquals("1.8.0-beta.1", metadata?.version)
        assertEquals("Beta Codename", metadata?.codename)
        assertEquals(10801, metadata?.versionCode)
        assertEquals("https://github.com/downloads/Orchard-1.8.0-beta.1.apk", metadata?.apkUrl)
        assertEquals("abc123hash", metadata?.sha256)
        assertEquals("## Orchard Mobile 1.8.0-beta.1\n- Bug fixes", metadata?.releaseNotes)
    }

    @Test
    fun parsesGitHubReleasesWithApkFallback() {
        val releasesJson = """
            [
              {
                "tag_name": "v1.8.0-beta.2",
                "name": "Orchard Mobile 1.8.0 (beta)",
                "prerelease": true,
                "body": "Fallback beta notes",
                "published_at": "2026-08-21T00:00:00Z",
                "assets": [
                  {
                    "name": "Orchard-1.8.0-beta.2.apk",
                    "browser_download_url": "https://github.com/downloads/Orchard-1.8.0-beta.2.apk"
                  }
                ]
              }
            ]
        """.trimIndent()

        val metadata = UpdateManager.parseGitHubReleasesMetadata(
            releasesJson = releasesJson,
            fetchManifest = { null },
        )

        org.junit.Assert.assertNotNull(metadata)
        assertEquals("1.8.0-beta.2", metadata?.version)
        assertEquals("https://github.com/downloads/Orchard-1.8.0-beta.2.apk", metadata?.apkUrl)
        assertEquals("Fallback beta notes", metadata?.releaseNotes)
        assertEquals("2026-08-21T00:00:00Z", metadata?.publishedAt)
    }

    @Test
    fun parsesGitHubReleasesReturnsNullWhenNoAndroidAssets() {
        val releasesJson = """
            [
              {
                "tag_name": "v1.8.0-beta.1",
                "name": "Orchard Desktop Only (beta)",
                "prerelease": true,
                "body": "Desktop only release",
                "published_at": "2026-08-20T00:00:00Z",
                "assets": [
                  {
                    "name": "Orchard-Setup-1.8.0-beta.1.exe",
                    "browser_download_url": "https://github.com/downloads/Orchard-Setup-1.8.0-beta.1.exe"
                  }
                ]
              }
            ]
        """.trimIndent()

        val metadata = UpdateManager.parseGitHubReleasesMetadata(
            releasesJson = releasesJson,
            fetchManifest = { null },
        )

        org.junit.Assert.assertNull(metadata)
    }

    private fun releaseWith(vararg assets: String, manifest: Boolean = false): String {
        val entries = assets.map { name ->
            """{"name": "$name", "browser_download_url": "https://github.com/downloads/$name"}"""
        } + if (manifest) {
            listOf("""{"name": "latest-android.json", "browser_download_url": "https://github.com/downloads/latest-android.json"}""")
        } else {
            emptyList()
        }
        return """[{"tag_name": "v2.1.0", "body": "", "published_at": "", "assets": [${entries.joinToString(",")}]}]"""
    }

    @Test
    fun phoneTakesThePhoneApkWhenBothArePublished() {
        val metadata = UpdateManager.parseGitHubReleasesMetadata(
            releasesJson = releaseWith(UpdateManager.TV_APK, UpdateManager.PHONE_APK),
            isTv = false,
            fetchManifest = { null },
        )

        assertEquals("https://github.com/downloads/${UpdateManager.PHONE_APK}", metadata?.apkUrl)
    }

    @Test
    fun phoneNeverFallsBackToTheTvApk() {
        val metadata = UpdateManager.parseGitHubReleasesMetadata(
            releasesJson = releaseWith(UpdateManager.TV_APK),
            isTv = false,
            fetchManifest = { null },
        )

        org.junit.Assert.assertNull(metadata)
    }

    @Test
    fun tvTakesTheTvApkWhenBothArePublished() {
        val metadata = UpdateManager.parseGitHubReleasesMetadata(
            releasesJson = releaseWith(UpdateManager.PHONE_APK, UpdateManager.TV_APK),
            isTv = true,
            fetchManifest = { null },
        )

        assertEquals("https://github.com/downloads/${UpdateManager.TV_APK}", metadata?.apkUrl)
    }

    @Test
    fun tvGetsNoOfferWithoutTheTvApk() {
        val metadata = UpdateManager.parseGitHubReleasesMetadata(
            releasesJson = releaseWith(UpdateManager.PHONE_APK, "Orchard-2.1.0.apk"),
            isTv = true,
            fetchManifest = { null },
        )

        org.junit.Assert.assertNull(metadata)
    }

    @Test
    fun tvSkipsAManifestThatPointsAtThePhoneApk() {
        val phoneManifest = """
            {"version": "2.1.0", "apkUrl": "https://github.com/downloads/${UpdateManager.PHONE_APK}", "sha256": "phonehash"}
        """.trimIndent()

        val metadata = UpdateManager.parseGitHubReleasesMetadata(
            releasesJson = releaseWith(UpdateManager.PHONE_APK, UpdateManager.TV_APK, manifest = true),
            isTv = true,
            fetchManifest = { phoneManifest },
        )

        assertEquals("https://github.com/downloads/${UpdateManager.TV_APK}", metadata?.apkUrl)
        assertEquals("", metadata?.sha256)
    }

    @Test
    fun manifestFitsTheDeviceByExactApkName() {
        val tv = MobileUpdateMetadata(version = "2.1.0", apkUrl = "https://x/${UpdateManager.TV_APK}")
        val phone = MobileUpdateMetadata(version = "2.1.0", apkUrl = "https://x/${UpdateManager.PHONE_APK}")
        val blank = MobileUpdateMetadata(version = "2.1.0")

        org.junit.Assert.assertTrue(UpdateManager.manifestFitsDevice(tv, isTv = true))
        org.junit.Assert.assertFalse(UpdateManager.manifestFitsDevice(phone, isTv = true))
        org.junit.Assert.assertFalse(UpdateManager.manifestFitsDevice(blank, isTv = true))
        org.junit.Assert.assertTrue(UpdateManager.manifestFitsDevice(phone, isTv = false))
        org.junit.Assert.assertTrue(UpdateManager.manifestFitsDevice(blank, isTv = false))
    }

    private fun releaseWithManifestAssets(vararg assets: String): String {
        val entries = assets.joinToString(",") { name ->
            """{"name": "$name", "browser_download_url": "https://github.com/downloads/$name"}"""
        }
        return """[{"tag_name": "v2.1.0", "body": "", "published_at": "", "assets": [$entries]}]"""
    }

    @Test
    fun parseGitHubReleasesPrefersTvManifestForTv() {
        val phoneManifest = """{"version": "2.1.0-phone", "apkUrl": "https://github.com/downloads/${UpdateManager.PHONE_APK}", "sha256": "phonehash"}"""
        val tvManifest = """{"version": "2.1.0-tv", "apkUrl": "https://github.com/downloads/${UpdateManager.TV_APK}", "sha256": "tvhash"}"""
        val requested = mutableListOf<String>()

        val metadata = UpdateManager.parseGitHubReleasesMetadata(
            releasesJson = releaseWithManifestAssets("latest-android.json", "latest-android-tv.json"),
            isTv = true,
            fetchManifest = { url ->
                requested += url
                if (url.endsWith("latest-android-tv.json")) tvManifest else phoneManifest
            },
        )

        assertEquals("2.1.0-tv", metadata?.version)
        assertEquals("https://github.com/downloads/${UpdateManager.TV_APK}", metadata?.apkUrl)
        assertEquals("tvhash", metadata?.sha256)
        assertEquals(listOf("https://github.com/downloads/latest-android-tv.json"), requested)
    }

    @Test
    fun parseGitHubReleasesUsesPhoneManifestForPhone() {
        val phoneManifest = """{"version": "2.1.0-phone", "apkUrl": "https://github.com/downloads/${UpdateManager.PHONE_APK}", "sha256": "phonehash"}"""
        val tvManifest = """{"version": "2.1.0-tv", "apkUrl": "https://github.com/downloads/${UpdateManager.TV_APK}", "sha256": "tvhash"}"""
        val requested = mutableListOf<String>()

        val metadata = UpdateManager.parseGitHubReleasesMetadata(
            releasesJson = releaseWithManifestAssets("latest-android.json", "latest-android-tv.json"),
            isTv = false,
            fetchManifest = { url ->
                requested += url
                if (url.endsWith("latest-android-tv.json")) tvManifest else phoneManifest
            },
        )

        assertEquals("2.1.0-phone", metadata?.version)
        assertEquals("https://github.com/downloads/${UpdateManager.PHONE_APK}", metadata?.apkUrl)
        assertEquals("phonehash", metadata?.sha256)
        assertEquals(listOf("https://github.com/downloads/latest-android.json"), requested)
    }

    @Test
    fun parseGitHubReleasesFallsBackToPhoneManifestForTv() {
        // A TV release that only carries the phone manifest still reads it and
        // keeps applying the device-fit check to whatever APK the manifest points at.
        val phoneManifest = """{"version": "2.1.0", "apkUrl": "https://github.com/downloads/${UpdateManager.TV_APK}", "sha256": "tvhash"}"""
        val requested = mutableListOf<String>()

        val metadata = UpdateManager.parseGitHubReleasesMetadata(
            releasesJson = releaseWithManifestAssets("latest-android.json"),
            isTv = true,
            fetchManifest = { url ->
                requested += url
                phoneManifest
            },
        )

        assertEquals("2.1.0", metadata?.version)
        assertEquals("https://github.com/downloads/${UpdateManager.TV_APK}", metadata?.apkUrl)
        assertEquals("tvhash", metadata?.sha256)
        assertEquals(listOf("https://github.com/downloads/latest-android.json"), requested)
    }

    private fun manifest(version: String, versionCode: Int = 0) =
        MobileUpdateMetadata(version = version, versionCode = versionCode)

    @Test
    fun comparesAuditedBuildsByTheirTrailingCounter() {
        assertEquals(1, UpdateManager.compareVersions("2.0.0-beta.9.audited.28", "2.0.0-beta.9.audited.27"))
        assertEquals(0, UpdateManager.compareVersions("2.0.0-beta.9.audited.28", "2.0.0-beta.9.audited.28"))
        assertEquals(-1, UpdateManager.compareVersions("2.0.0-beta.9.audited.27", "2.0.0-beta.9.audited.28"))
        // The audited counter outranks the bare beta it was cut from...
        assertEquals(1, UpdateManager.compareVersions("2.0.0-beta.9.audited.1", "2.0.0-beta.9"))
        // ...but beta.10 is newer than beta.9 whatever the audited counter says.
        assertEquals(1, UpdateManager.compareVersions("2.0.0-beta.10.audited.1", "2.0.0-beta.9.audited.28"))
        // A newer base version still wins over the beta suffix.
        assertEquals(-1, UpdateManager.compareVersions("2.0.0-beta.9.audited.28", "2.0.1"))
    }

    @Test
    fun treatsAHigherVersionCodeAsAnUpdate() {
        assertTrue(
            UpdateManager.isNewerRelease(
                manifest("2.0.0-beta.9.audited.28", 20028), "2.0.0-beta.9.audited.27", 20027,
            ),
        )
        // Same version string, newer code: still an update.
        assertTrue(UpdateManager.isNewerRelease(manifest("1.0.0", 20028), "1.0.0", 20027))
    }

    @Test
    fun doesNotOfferTheSameOrAnOlderBuild() {
        assertFalse(
            UpdateManager.isNewerRelease(
                manifest("2.0.0-beta.9.audited.27", 20027), "2.0.0-beta.9.audited.28", 20028,
            ),
        )
        assertFalse(
            UpdateManager.isNewerRelease(
                manifest("2.0.0-beta.9.audited.28", 20028), "2.0.0-beta.9.audited.28", 20028,
            ),
        )
    }

    @Test
    fun fallsBackToTheVersionStringWhenEitherCodeIsMissing() {
        // Older manifests carry no versionCode and a build with no code of its own has nothing to
        // compare, so the version string decides in both directions.
        assertTrue(
            UpdateManager.isNewerRelease(
                manifest("2.0.0-beta.9.audited.28"), "2.0.0-beta.9.audited.27", 20027,
            ),
        )
        assertFalse(
            UpdateManager.isNewerRelease(
                manifest("2.0.0-beta.9.audited.27"), "2.0.0-beta.9.audited.28", 20028,
            ),
        )
        assertTrue(
            UpdateManager.isNewerRelease(
                manifest("2.0.0-beta.9.audited.28", 20028), "2.0.0-beta.9.audited.27", 0,
            ),
        )
        assertFalse(
            UpdateManager.isNewerRelease(
                manifest("2.0.0-beta.9.audited.27", 20027), "2.0.0-beta.9.audited.28", 0,
            ),
        )
    }

    @Test
    fun parsesAnAuditedManifestWithItsVersionCode() {
        val json = """
            {
              "version": "2.0.0-beta.9.audited.28",
              "versionCode": 20028,
              "apkUrl": "https://github.com/jucsp/orchard-audited/releases/download/v2.0.0-beta.9-audited.28/Orchard-Android-arm64-v8a.apk",
              "sha256": "abc"
            }
        """.trimIndent()

        val metadata = UpdateManager.parseUpdateMetadata(json)

        assertEquals(20028, metadata.versionCode)
        assertTrue(UpdateManager.isNewerRelease(metadata, "2.0.0-beta.9.audited.27", 20027))
        assertFalse(UpdateManager.isNewerRelease(metadata, "2.0.0-beta.9.audited.28", 20028))
    }

    /**
     * GitHub answers `releases/latest/download` with a 302 to release-assets.githubusercontent.com,
     * so the Location header has to be resolved before the body can be read.
     */
    @Test
    fun resolvesRedirectLocations() {
        assertEquals(
            "https://release-assets.githubusercontent.com/github-production-release-asset/1?token=x",
            UpdateManager.resolveRedirectLocation(
                "https://github.com/jucsp/orchard-audited/releases/latest/download/latest-android.json",
                "https://release-assets.githubusercontent.com/github-production-release-asset/1?token=x",
            ),
        )
        assertEquals(
            "https://github.com/other/path.json",
            UpdateManager.resolveRedirectLocation("https://github.com/a/b/c.json", "/other/path.json"),
        )
        assertEquals(
            "https://github.com/a/d.json",
            UpdateManager.resolveRedirectLocation("https://github.com/a/b/c.json", "d.json"),
        )
        assertNull(UpdateManager.resolveRedirectLocation("https://github.com/a/b.json", null))
        assertNull(UpdateManager.resolveRedirectLocation("https://github.com/a/b.json", "   "))
        assertEquals(5, UpdateManager.MAX_REDIRECTS)
    }
}
