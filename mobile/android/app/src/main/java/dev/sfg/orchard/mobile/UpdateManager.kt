// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 SFG545

package dev.sfg.orchard.mobile

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInfo
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import dev.sfg.orchard.connect.BuildConfig
import dev.sfg.orchard.mobile.ui.tv.isTvDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlin.concurrent.thread

data class MobileUpdateMetadata(
    val version: String,
    val codename: String = "",
    val versionCode: Int = 0,
    val apkUrl: String = "",
    val sha256: String = "",
    val publishedAt: String = "",
    val releaseNotes: String = "",
)

/** What the update flow is currently doing, for the in-app prompt to render. */
sealed interface UpdateState {
    data object Idle : UpdateState
    data class Available(val metadata: MobileUpdateMetadata) : UpdateState
    data class Downloading(val version: String) : UpdateState
    data class Failed(val version: String, val reason: String) : UpdateState
    data class ReadyToInstall(val version: String) : UpdateState
}

class UpdateManager(
    private val context: Context,
    private val isTv: Boolean = isTvDevice(context),
    private val isBetaEnabled: () -> Boolean = { false },
) {

    private val baseUrl = "https://github.com/jucsp/orchard-audited/releases/latest/download"
    private val phoneManifestUrl = "$baseUrl/latest-android.json"
    private val tvManifestUrl = "$baseUrl/latest-android-tv.json"
    private val githubReleasesUrl = "https://api.github.com/repos/jucsp/orchard-audited/releases"

    private val mutableState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = mutableState.asStateFlow()

    fun checkForUpdates() {
        // Debug and canary builds carry their own versionNameSuffix and applicationId; the
        // published release APK is neither an upgrade nor installable over them.
        if (!BuildConfig.UPDATER_ENABLED) return

        thread {
            try {
                val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                val currentVersion = packageInfo.versionName
                val currentCode = packageInfo.currentVersionCode()

                val metadata: MobileUpdateMetadata? = if (isBetaEnabled()) {
                    fetchBetaUpdateMetadata()
                } else {
                    fetchStableUpdateMetadata()
                }

                if (metadata != null && isNewerRelease(metadata, currentVersion, currentCode)) {
                    // Surface it and let the user decide; downloading and firing the system
                    // installer unprompted is what made this feel like a random intrusion.
                    mutableState.value = UpdateState.Available(metadata)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Update check failed: ${e.message}")
            }
        }
    }

    /**
     * The code the installed APK was built with. Reads the long form where the platform has it,
     * because a fork that ships often can exhaust the 32-bit range.
     */
    private fun PackageInfo.currentVersionCode(): Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        longVersionCode.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    } else {
        @Suppress("DEPRECATION")
        versionCode
    }

    private fun fetchStableUpdateMetadata(): MobileUpdateMetadata? =
        fetchStableManifestMetadata() ?: fetchGitHubReleasesMetadata()

    /**
     * A TV reads the dedicated TV manifest first and falls back to the phone manifest when it has
     * not been published yet. Either way the manifest still has to fit this device.
     */
    private fun fetchStableManifestMetadata(): MobileUpdateMetadata? {
        val urls = if (isTv) listOf(tvManifestUrl, phoneManifestUrl) else listOf(phoneManifestUrl)
        for (url in urls) {
            val metadata = fetchManifestAt(url)?.let(::parseUpdateMetadata)
            if (metadata != null && manifestFitsDevice(metadata, isTv)) return metadata
        }
        return null
    }

    private fun fetchManifestAt(url: String): String? = fetchFollowingRedirects(url)

    /**
     * The releases listing is what a stable check falls back to when no manifest can be read: the
     * `releases/latest/download` alias lags behind the newest release. It never calls the manifest
     * or beta paths back, so the fallback cannot recurse.
     */
    private fun fetchGitHubReleasesMetadata(): MobileUpdateMetadata? = runCatching {
        val response = fetchFollowingRedirects(githubReleasesUrl, accept = "application/vnd.github+json")
            ?: return@runCatching null
        parseGitHubReleasesMetadata(response, isTv)
    }.getOrNull()

    private fun fetchBetaUpdateMetadata(): MobileUpdateMetadata? =
        fetchGitHubReleasesMetadata() ?: fetchStableManifestMetadata()

    fun dismiss() {
        mutableState.value = UpdateState.Idle
    }

    companion object {
        private const val TAG = "UpdateManager"
        const val PHONE_APK = "Orchard-Android-arm64-v8a.apk"
        const val TV_APK = "Orchard-AndroidTV-arm.apk"
        const val PHONE_MANIFEST = "latest-android.json"
        const val TV_MANIFEST = "latest-android-tv.json"

        /** Redirect hops followed before giving up on a URL. */
        const val MAX_REDIRECTS = 5

        private val REDIRECT_STATUS_CODES = setOf(301, 302, 303, 307, 308)

        /**
         * Absolute URL for a redirect [location], or null when there is nothing usable to follow.
         * Relative locations are resolved against [currentUrl], as the RFC requires.
         */
        fun resolveRedirectLocation(currentUrl: String, location: String?): String? {
            val header = location?.trim().orEmpty()
            if (header.isEmpty()) return null
            return runCatching { URL(URL(currentUrl), header).toString() }.getOrNull()
        }

        /** One request's outcome: a body, or the redirect that has to be followed next. */
        private class FetchAttempt(val body: String?, val redirect: String?)

        /**
         * GETs [url] and follows up to [MAX_REDIRECTS] redirects. GitHub answers the
         * `releases/latest/download` alias with a 302 to release-assets.githubusercontent.com, so a
         * single-shot request read the redirect instead of the manifest and reported "no update".
         */
        private fun fetchFollowingRedirects(url: String, accept: String? = null): String? {
            var target = url
            for (hop in 0..MAX_REDIRECTS) {
                val attempt = runCatching { requestOnce(target, accept) }.getOrNull() ?: return null
                attempt.body?.let { return it }
                val next = attempt.redirect ?: return null
                if (hop == MAX_REDIRECTS) return null
                target = next
            }
            return null
        }

        private fun requestOnce(url: String, accept: String?): FetchAttempt {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            // Followed by hand: the default follower hides that the asset host changes.
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.setRequestProperty("User-Agent", "Orchard-Android")
            if (accept != null) connection.setRequestProperty("Accept", accept)
            try {
                val code = connection.responseCode
                if (code == HttpURLConnection.HTTP_OK) {
                    return FetchAttempt(connection.inputStream.bufferedReader().use { it.readText() }, null)
                }
                val redirect = if (code in REDIRECT_STATUS_CODES) {
                    resolveRedirectLocation(url, connection.getHeaderField("Location"))
                } else {
                    null
                }
                return FetchAttempt(null, redirect)
            } finally {
                connection.disconnect()
            }
        }

        /**
         * A release is newer when its versionCode advances, or when the version string does and the
         * codes cannot answer. Manifests written before the code was published carry 0, and a build
         * with no code of its own has nothing to compare, so the string stays authoritative there.
         */
        fun isNewerRelease(
            metadata: MobileUpdateMetadata,
            currentVersion: String?,
            currentVersionCode: Int,
        ): Boolean {
            val newerCode = metadata.versionCode > 0 &&
                currentVersionCode > 0 &&
                metadata.versionCode > currentVersionCode
            return newerCode || compareVersions(metadata.version, currentVersion) > 0
        }

        /** A manifest names one APK; a TV only takes it when that APK is the TV build. */
        fun manifestFitsDevice(metadata: MobileUpdateMetadata, isTv: Boolean): Boolean =
            !isTv || metadata.apkUrl.substringAfterLast('/') == TV_APK

        /**
         * The APK asset for this kind of device. A TV takes only [TV_APK]; a phone prefers
         * [PHONE_APK] and otherwise the first APK that is not the TV build.
         */
        fun selectApkUrl(assets: List<Pair<String, String>>, isTv: Boolean): String? {
            val apks = assets.filter { (name, _) -> name.endsWith(".apk", ignoreCase = true) }
            return if (isTv) {
                apks.firstOrNull { (name, _) -> name == TV_APK }?.second
            } else {
                (apks.firstOrNull { (name, _) -> name == PHONE_APK } ?: apks.firstOrNull { (name, _) -> name != TV_APK })
                    ?.second
            }
        }

        fun parseUpdateMetadata(jsonString: String): MobileUpdateMetadata {
            val json = JSONObject(jsonString)
            return MobileUpdateMetadata(
                version = json.getString("version"),
                codename = json.optString("codename", ""),
                versionCode = json.optInt("versionCode", 0),
                apkUrl = json.optString("apkUrl", ""),
                sha256 = json.optString("sha256", ""),
                publishedAt = json.optString("publishedAt", ""),
                releaseNotes = json.optString("releaseNotes", ""),
            )
        }

        fun parseGitHubReleasesMetadata(
            releasesJson: String,
            isTv: Boolean = false,
            fetchManifest: (String) -> String? = { url -> fetchFollowingRedirects(url) },
        ): MobileUpdateMetadata? {
            val array = JSONArray(releasesJson)
            for (i in 0 until array.length()) {
                val releaseObj = array.getJSONObject(i)
                val tagName = releaseObj.optString("tag_name", "").removePrefix("v").removePrefix("V").trim()
                if (tagName.isEmpty()) continue

                val assets = releaseObj.optJSONArray("assets") ?: JSONArray()
                var phoneManifestUrl: String? = null
                var tvManifestUrl: String? = null
                val named = mutableListOf<Pair<String, String>>()

                for (j in 0 until assets.length()) {
                    val asset = assets.getJSONObject(j)
                    val name = asset.optString("name", "")
                    val downloadUrl = asset.optString("browser_download_url", "")
                    when {
                        name.equals(PHONE_MANIFEST, ignoreCase = true) -> phoneManifestUrl = downloadUrl
                        name.equals(TV_MANIFEST, ignoreCase = true) -> tvManifestUrl = downloadUrl
                        else -> named += name to downloadUrl
                    }
                }
                val apkUrl = selectApkUrl(named, isTv)
                // A TV prefers the dedicated TV manifest and falls back to the phone manifest.
                val manifestUrl = if (isTv) tvManifestUrl ?: phoneManifestUrl else phoneManifestUrl

                if (manifestUrl != null) {
                    val manifestContent = fetchManifest(manifestUrl)
                    if (manifestContent != null) {
                        val manifest = parseUpdateMetadata(manifestContent)
                        if (manifestFitsDevice(manifest, isTv)) return manifest
                    }
                }

                if (apkUrl != null) {
                    val body = releaseObj.optString("body", "")
                    val publishedAt = releaseObj.optString("published_at", "")
                    return MobileUpdateMetadata(
                        version = tagName,
                        apkUrl = apkUrl,
                        releaseNotes = body,
                        publishedAt = publishedAt,
                    )
                }
            }
            return null
        }

        fun compareVersions(latest: String, current: String?): Int {
            if (current == null) return 1

            fun parseSemver(value: String): Pair<List<Int>, String?> {
                val clean = value.trim().removePrefix("v").removePrefix("V")
                val dashIndex = clean.indexOf('-')
                val (base, pre) = if (dashIndex >= 0) {
                    clean.substring(0, dashIndex) to clean.substring(dashIndex + 1)
                } else {
                    clean to null
                }
                val numbers = base.split(".").map { segment ->
                    segment.takeWhile(Char::isDigit).toIntOrNull() ?: 0
                }
                return numbers to pre
            }

            val (lParts, lPre) = parseSemver(latest)
            val (cParts, cPre) = parseSemver(current)

            val length = maxOf(lParts.size, cParts.size)
            for (i in 0 until length) {
                val l = lParts.getOrElse(i) { 0 }
                val c = cParts.getOrElse(i) { 0 }
                if (l > c) return 1
                if (l < c) return -1
            }

            if (lPre == null && cPre == null) return 0
            if (lPre == null && cPre in listOf("debug", "canary", "rc1")) return 0
            if (cPre == null && lPre in listOf("debug", "canary", "rc1")) return 0

            if (lPre == null && cPre != null) return 1
            if (lPre != null && cPre == null) return -1

            if (lPre != null && cPre != null) {
                if (lPre == cPre) return 0
                fun preParts(s: String) = s.split(".").map { seg ->
                    seg.toIntOrNull()?.let { Pair(true, it) } ?: Pair(false, seg)
                }
                val lp = preParts(lPre)
                val cp = preParts(cPre)
                val pLen = maxOf(lp.size, cp.size)
                for (i in 0 until pLen) {
                    val lSeg = lp.getOrNull(i)
                    val cSeg = cp.getOrNull(i)
                    if (lSeg == null) return -1
                    if (cSeg == null) return 1
                    if (lSeg.first && cSeg.first) {
                        val lNum = lSeg.second as Int
                        val cNum = cSeg.second as Int
                        if (lNum != cNum) return lNum.compareTo(cNum)
                    } else {
                        val lStr = lSeg.second.toString()
                        val cStr = cSeg.second.toString()
                        if (lStr != cStr) return lStr.compareTo(cStr)
                    }
                }
            }

            return 0
        }
    }

    fun downloadAndInstallUpdate(metadata: MobileUpdateMetadata) {
        val version = metadata.version
        // The published asset is not always named the way we would guess (it is "Orchard-x.y.z.apk",
        // not "orchard-x.y.z.apk"), so honour the URL the manifest gives us. Guessing produced a 404
        // whose HTML body got saved as the .apk, which is why installs failed to parse.
        val apkUrl = metadata.apkUrl.ifBlank { "$baseUrl/Orchard-$version.apk" }
        val fileName = "Orchard-$version.apk"
        val destination = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)

        if (destination.exists()) destination.delete()

        mutableState.value = UpdateState.Downloading(version)

        val request = DownloadManager.Request(apkUrl.toUri())
            .setTitle("Orchard Update")
            .setDescription("Downloading version $version")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationUri(Uri.fromFile(destination))
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)

        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val downloadId = downloadManager.enqueue(request)

        val onComplete = object : BroadcastReceiver() {
            override fun onReceive(ctxt: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id != downloadId) return
                context.unregisterReceiver(this)

                // ACTION_DOWNLOAD_COMPLETE fires for failures too. Handing a failed download to
                // the installer is what produced "problem parsing the package".
                val status = downloadManager.query(DownloadManager.Query().setFilterById(downloadId))
                    ?.use { cursor -> if (cursor.moveToFirst()) cursor.statusOf() else null }

                if (status != DownloadManager.STATUS_SUCCESSFUL) {
                    Log.w(TAG, "Update download failed for $version (status=$status)")
                    destination.delete()
                    mutableState.value = UpdateState.Failed(version, "The download did not complete.")
                    return
                }

                if (metadata.sha256.isNotBlank()) {
                    val actual = sha256Of(destination)
                    if (!actual.equals(metadata.sha256, ignoreCase = true)) {
                        Log.w(TAG, "Update checksum mismatch for $version: expected ${metadata.sha256}, got $actual")
                        destination.delete()
                        mutableState.value =
                            UpdateState.Failed(version, "The downloaded file did not match its checksum.")
                        return
                    }
                }

                mutableState.value = UpdateState.ReadyToInstall(version)
                installApk(destination)
            }
        }

        ContextCompat.registerReceiver(
            context, onComplete, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_EXPORTED,
        )
    }

    private fun Cursor.statusOf(): Int =
        getInt(getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))

    private fun sha256Of(file: File): String = runCatching {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { stream ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = stream.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }.getOrDefault("")

    private fun installApk(apkFile: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", apkFile)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
