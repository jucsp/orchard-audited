// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 SFG545

package dev.sfg.orchard.mobile.pairing

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import dev.sfg.orchard.mobile.cast.isPrivateIp
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Locale

/**
 * Announces `_orchard-pair._tcp` while the TV pairing screen is visible (PAIR-INV-4). The TXT
 * record carries only `v=1`; the token and fingerprint never leave through mDNS (SDD §5.2).
 */
class PairingAdvertiser(context: Context) : AutoCloseable {
    private val appContext = context.applicationContext
    private val nsdManager by lazy { appContext.getSystemService(Context.NSD_SERVICE) as NsdManager }
    private var registrationListener: NsdManager.RegistrationListener? = null

    val isAdvertising: Boolean get() = registrationListener != null

    fun start(port: Int): Boolean {
        stop()
        val info = NsdServiceInfo().apply {
            serviceType = SERVICE_TYPE
            serviceName = serviceName()
            setPort(port)
            setAttribute("v", "1")
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) = Unit
            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit
            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) = Unit
            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit
        }
        registrationListener = listener
        return try {
            nsdManager.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener)
            true
        } catch (error: RuntimeException) {
            registrationListener = null
            false
        }
    }

    fun stop() {
        val listener = registrationListener ?: return
        registrationListener = null
        runCatching { nsdManager.unregisterService(listener) }
    }

    override fun close() = stop()

    private fun serviceName(): String {
        val model = Build.MODEL.ifBlank { "TV" }
        val suffix = PairingCrypto.randomBytes(2).joinToString("") { "%02x".format(it) }.uppercase(Locale.US)
        return "Orchard TV $model $suffix".take(63)
    }

    companion object {
        const val SERVICE_TYPE = "_orchard-pair._tcp."
    }
}

/**
 * The TV's active private IPv4 (RFC 1918 or link-local), skipping VPNs. Phones refuse any other
 * address (PAIR-INV-1), so a public or CGNAT address is treated as no LAN at all.
 */
fun localTvIpv4(context: Context): Inet4Address? {
    val connectivity = context.getSystemService(ConnectivityManager::class.java)
    val active = connectivity?.activeNetwork
    if (active != null) {
        val capabilities = connectivity.getNetworkCapabilities(active)
        if (capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) != true) {
            connectivity.getLinkProperties(active)?.linkAddresses?.asSequence()
                ?.map { it.address }
                ?.filterIsInstance<Inet4Address>()
                ?.firstOrNull { isPrivateIp(it.hostAddress.orEmpty()) }
                ?.let { return it }
        }
    }
    return NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
        .asSequence()
        .filter { it.isUp && !it.isLoopback }
        .flatMap { it.inetAddresses.toList().asSequence() }
        .filterIsInstance<Inet4Address>()
        .firstOrNull { isPrivateIp(it.hostAddress.orEmpty()) }
}
