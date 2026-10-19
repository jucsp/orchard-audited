// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 SFG545

package dev.sfg.orchard.mobile.pairing

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import dev.sfg.orchard.mobile.cast.isPrivateIp
import java.net.Inet4Address

data class PairingTv(val name: String, val host: String, val port: Int)

/**
 * mDNS discovery of TVs advertising `_orchard-pair._tcp` (SDD §5.2). Opt-in like [CastDiscovery]:
 * nothing touches the network until [startDiscovery], and the search stops by itself.
 */
class PairingDiscovery(context: Context) : AutoCloseable {
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
    private var onUpdate: ((List<PairingTv>) -> Unit)? = null
    private val tvsByService = LinkedHashMap<String, PairingTv>()
    private val pendingResolves = ArrayDeque<NsdServiceInfo>()
    private var resolving = false
    private val timeoutRunnable = Runnable { stopDiscovery() }

    val isDiscovering: Boolean
        get() = synchronized(lock) { discoveryListener != null }

    fun startDiscovery(timeoutMs: Long = DEFAULT_TIMEOUT_MS, onDevicesUpdated: (List<PairingTv>) -> Unit) {
        stopDiscovery()
        val listener: NsdManager.DiscoveryListener
        synchronized(lock) {
            val gen = ++generation
            tvsByService.clear()
            pendingResolves.clear()
            resolving = false
            onUpdate = onDevicesUpdated
            listener = DiscoveryCallback(gen)
            discoveryListener = listener
        }
        try {
            multicastLock.acquire()
            nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (error: RuntimeException) {
            stopDiscovery()
            throw error
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
        if (listener != null) runCatching { nsdManager.stopServiceDiscovery(listener) }
        runCatching { if (multicastLock.isHeld) multicastLock.release() }
    }

    override fun close() = stopDiscovery()

    private fun publish(tvs: List<PairingTv>) {
        val callback = synchronized(lock) { onUpdate } ?: return
        mainHandler.post { callback(tvs) }
    }

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
        } catch (error: RuntimeException) {
            synchronized(lock) { if (gen == generation) resolving = false }
            resolveNext(gen)
        }
    }

    private fun onResolved(gen: Int, info: NsdServiceInfo) {
        val tv = ipv4Of(info)?.let { host ->
            if (isPrivateIp(host) && info.port in 1..65535) {
                PairingTv(info.serviceName, host, info.port)
            } else {
                null
            }
        }
        val tvs = synchronized(lock) {
            if (gen != generation) return
            resolving = false
            if (tv == null) null else {
                tvsByService[info.serviceName] = tv
                tvsByService.values.toList()
            }
        }
        tvs?.let(::publish)
        resolveNext(gen)
    }

    private fun onResolveFailed(gen: Int) {
        synchronized(lock) {
            if (gen != generation) return
            resolving = false
        }
        resolveNext(gen)
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

        override fun onServiceLost(serviceInfo: NsdServiceInfo) {
            val tvs = synchronized(lock) {
                if (gen != generation) return
                pendingResolves.removeAll { it.serviceName == serviceInfo.serviceName }
                if (tvsByService.remove(serviceInfo.serviceName) == null) null else tvsByService.values.toList()
            }
            tvs?.let(::publish)
        }

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
        const val SERVICE_TYPE = PairingAdvertiser.SERVICE_TYPE
        const val DEFAULT_TIMEOUT_MS = 15_000L
        private const val LOCK_TAG = "orchard-pair-discovery"
    }
}
