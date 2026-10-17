// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 SFG545

package dev.sfg.orchard.mobile.cast

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import java.net.Inet4Address

data class CastDevice(
    val id: String,
    val name: String,
    val model: String?,
    val host: String,
    val port: Int,
    val capabilities: Int = 0
)

/** Builds a [CastDevice] from a `_googlecast._tcp` TXT record. Pure JVM, no Android types. */
object CastTxtParser {
    fun parse(attributes: Map<String, ByteArray?>, host: String, port: Int): CastDevice? {
        val id = text(attributes, "id")?.takeIf { it.isNotBlank() } ?: return null
        if (port !in 1..65535) return null
        if (!isPrivateIp(host)) return null
        return CastDevice(
            id = id,
            name = text(attributes, "fn")?.takeIf { it.isNotBlank() } ?: host,
            model = text(attributes, "md")?.takeIf { it.isNotBlank() },
            host = host,
            port = port,
            capabilities = parseCapabilities(text(attributes, "ca"))
        )
    }

    internal fun parseCapabilities(raw: String?): Int {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty()) return 0
        if (value.startsWith("0x", ignoreCase = true)) {
            return value.substring(2).toLongOrNull(16)?.toInt() ?: 0
        }
        return value.toIntOrNull() ?: value.toLongOrNull(16)?.toInt() ?: 0
    }

    // TXT keys are case-insensitive (RFC 6763 §6.4).
    private fun text(attributes: Map<String, ByteArray?>, key: String): String? {
        val bytes = attributes[key]
            ?: attributes.entries.firstOrNull { it.key.equals(key, ignoreCase = true) }?.value
            ?: return null
        return String(bytes, Charsets.UTF_8).trim()
    }
}

/**
 * mDNS discovery of Cast receivers (SDD §4.1). Nothing touches the network until
 * [startDiscovery] is called, and the search stops by itself after the timeout (CAST-INV-3).
 * Resolutions run one at a time because `resolveService` fails with
 * `FAILURE_ALREADY_ACTIVE` on concurrent calls before API 34.
 */
class CastDiscovery(context: Context) : AutoCloseable {
    private val appContext = context.applicationContext
    private val nsdManager by lazy { appContext.getSystemService(Context.NSD_SERVICE) as NsdManager }
    private val multicastLock by lazy {
        val wifi = appContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        wifi.createMulticastLock(LOCK_TAG).apply { setReferenceCounted(false) }
    }
    private val mainHandler = Handler(Looper.getMainLooper())
    private val lock = Any()

    private var generation = 0
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var onUpdate: ((List<CastDevice>) -> Unit)? = null
    private val devicesByService = LinkedHashMap<String, CastDevice>()
    private val pendingResolves = ArrayDeque<NsdServiceInfo>()
    private var resolving = false
    private val timeoutRunnable = Runnable { stopDiscovery() }

    val isDiscovering: Boolean
        get() = synchronized(lock) { discoveryListener != null }

    fun startDiscovery(timeoutMs: Long = DEFAULT_TIMEOUT_MS, onDevicesUpdated: (List<CastDevice>) -> Unit) {
        stopDiscovery()
        val listener: NsdManager.DiscoveryListener
        synchronized(lock) {
            val gen = ++generation
            devicesByService.clear()
            pendingResolves.clear()
            resolving = false
            onUpdate = onDevicesUpdated
            listener = DiscoveryCallback(gen)
            discoveryListener = listener
        }
        try {
            multicastLock.acquire()
            nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (e: RuntimeException) {
            stopDiscovery()
            throw e
        }
        mainHandler.postDelayed(timeoutRunnable, timeoutMs)
        publish(emptyList())
    }

    fun stopDiscovery() {
        mainHandler.removeCallbacks(timeoutRunnable)
        val listener = synchronized(lock) {
            generation++
            pendingResolves.clear()
            resolving = false
            onUpdate = null
            discoveryListener.also { discoveryListener = null }
        }
        if (listener != null) {
            // Throws IllegalArgumentException if discovery never started or already stopped.
            runCatching { nsdManager.stopServiceDiscovery(listener) }
        }
        runCatching { if (multicastLock.isHeld) multicastLock.release() }
    }

    override fun close() = stopDiscovery()

    private fun publish(devices: List<CastDevice>) {
        val callback = synchronized(lock) { onUpdate } ?: return
        mainHandler.post { callback(devices) }
    }

    private fun snapshot(): List<CastDevice> = devicesByService.values.distinctBy { it.id }

    private fun enqueueResolve(gen: Int, info: NsdServiceInfo) {
        synchronized(lock) {
            if (gen != generation) return
            if (pendingResolves.none { it.serviceName == info.serviceName }) pendingResolves.addLast(info)
        }
        resolveNext(gen)
    }

    @Suppress("DEPRECATION")
    private fun resolveNext(gen: Int) {
        val next = synchronized(lock) {
            if (gen != generation || resolving) return
            pendingResolves.removeFirstOrNull()?.also { resolving = true } ?: return
        }
        try {
            nsdManager.resolveService(next, ResolveCallback(gen))
        } catch (e: RuntimeException) {
            synchronized(lock) { if (gen == generation) resolving = false }
            resolveNext(gen)
        }
    }

    private fun onResolved(gen: Int, info: NsdServiceInfo) {
        val device = ipv4Of(info)?.let { CastTxtParser.parse(info.attributes, it, info.port) }
        val devices = synchronized(lock) {
            if (gen != generation) return
            resolving = false
            if (device == null) null else {
                devicesByService[info.serviceName] = device
                snapshot()
            }
        }
        devices?.let(::publish)
        resolveNext(gen)
    }

    private fun onResolveFailed(gen: Int) {
        synchronized(lock) {
            if (gen != generation) return
            resolving = false
        }
        resolveNext(gen)
    }

    private fun onLost(gen: Int, serviceName: String) {
        val devices = synchronized(lock) {
            if (gen != generation) return
            pendingResolves.removeAll { it.serviceName == serviceName }
            if (devicesByService.remove(serviceName) == null) null else snapshot()
        }
        devices?.let(::publish)
    }

    @Suppress("DEPRECATION")
    private fun ipv4Of(info: NsdServiceInfo): String? {
        val addresses = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            info.hostAddresses
        } else {
            listOfNotNull(info.host)
        }
        return addresses.filterIsInstance<Inet4Address>().firstOrNull()?.hostAddress
    }

    private inner class DiscoveryCallback(private val gen: Int) : NsdManager.DiscoveryListener {
        override fun onDiscoveryStarted(serviceType: String) = Unit
        override fun onDiscoveryStopped(serviceType: String) = Unit

        override fun onServiceFound(serviceInfo: NsdServiceInfo) = enqueueResolve(gen, serviceInfo)

        override fun onServiceLost(serviceInfo: NsdServiceInfo) = onLost(gen, serviceInfo.serviceName)

        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
            if (synchronized(lock) { gen == generation }) mainHandler.post { stopDiscovery() }
        }

        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
    }

    private inner class ResolveCallback(private val gen: Int) : NsdManager.ResolveListener {
        override fun onServiceResolved(serviceInfo: NsdServiceInfo) = onResolved(gen, serviceInfo)
        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = onResolveFailed(gen)
    }

    companion object {
        const val SERVICE_TYPE = "_googlecast._tcp."
        const val DEFAULT_TIMEOUT_MS = 30_000L
        private const val LOCK_TAG = "orchard-cast-discovery"
    }
}
