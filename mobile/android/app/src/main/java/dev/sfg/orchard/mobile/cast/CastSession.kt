/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 SFG545
 *
 * Sovereign CastV2 session: connection, heartbeat, receiver launch, volume
 * control and reconnection. Pure Kotlin/JVM, no android.* imports.
 */
package dev.sfg.orchard.mobile.cast

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

enum class CastSessionState { DISCONNECTED, CONNECTING, CONNECTED, LAUNCHING, READY, CLOSING }

data class CastDeviceVolume(val level: Float, val muted: Boolean)

data class CastApplication(
    val appId: String,
    val sessionId: String,
    val transportId: String,
    val statusText: String?
)

/**
 * Drives a CastV2 session over an already-configured [CastChannel].
 *
 * Heartbeat PINGs are sent every [heartbeatIntervalMs] milliseconds. A receiver
 * that stays silent for more than [heartbeatTimeoutMs] gets one reconnection
 * attempt per outage; a second timeout, an inbound `CLOSE`, the app leaving
 * `RECEIVER_STATUS` or an invalid JSON payload end the session in
 * [CastSessionState.DISCONNECTED]. The owner calls [close] to release the
 * channel and the heartbeat thread; a closed session cannot be reused.
 */
class CastSession(
    val channel: CastChannel,
    private val heartbeatIntervalMs: Long = 5000L,
    private val heartbeatTimeoutMs: Long = 15000L
) : AutoCloseable {

    companion object {
        const val NS_CONNECTION = "urn:x-cast:com.google.cast.tp.connection"
        const val NS_HEARTBEAT = "urn:x-cast:com.google.cast.tp.heartbeat"
        const val NS_RECEIVER = "urn:x-cast:com.google.cast.receiver"
        const val APP_ID_DEFAULT_MEDIA_RECEIVER = "CC1AD845"
        const val SENDER_ID = "sender-0"
        const val RECEIVER_ID = "receiver-0"
    }

    private val launchTimeoutMs: Long = 30000L
    private val stateLock = Any()
    private val stateListeners = CopyOnWriteArrayList<(CastSessionState) -> Unit>()
    private val closing = AtomicBoolean(false)
    private val reconnectAttempt = AtomicInteger(0)
    private val lastInboundAt = AtomicLong(0L)
    private val heartbeatExecutor = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "cast-session-heartbeat").apply { isDaemon = true }
    }

    @Volatile private var heartbeatFuture: ScheduledFuture<*>? = null
    @Volatile private var activeChannel: CastChannel = channel

    @Volatile var state: CastSessionState = CastSessionState.DISCONNECTED
        private set
    @Volatile var sessionId: String? = null
        private set
    @Volatile var transportId: String? = null
        private set
    @Volatile var receiverVolume: CastDeviceVolume? = null
        private set
    @Volatile var applications: List<CastApplication> = emptyList()
        private set

    fun addStateListener(listener: (CastSessionState) -> Unit) {
        stateListeners.add(listener)
    }

    fun removeStateListener(listener: (CastSessionState) -> Unit) {
        stateListeners.remove(listener)
    }

    /** Opens the channel, connects to the receiver and reaches [CastSessionState.READY]. */
    fun connectAndLaunch() {
        if (closing.get()) throw IllegalStateException("CastSession is closed")
        if (state != CastSessionState.DISCONNECTED) return
        try {
            openAndLaunch(activeChannel)
        } catch (e: Exception) {
            terminate()
            throw e
        }
    }

    /** Sets the device volume level (0..1). The receiver rejects `level` and `muted` in one request. */
    fun setDeviceVolume(level: Float) {
        if (level.isNaN()) return
        val clamped = level.coerceIn(0.0f, 1.0f)
        receiverVolume = CastDeviceVolume(clamped, receiverVolume?.muted ?: false)
        sendReceiverCommand("""{"type":"SET_VOLUME","volume":{"level":$clamped}}""")
    }

    fun setDeviceMuted(muted: Boolean) {
        receiverVolume = CastDeviceVolume(receiverVolume?.level ?: 1.0f, muted)
        sendReceiverCommand("""{"type":"SET_VOLUME","volume":{"muted":$muted}}""")
    }

    override fun close() {
        if (!closing.compareAndSet(false, true)) return
        val previous = state
        setState(CastSessionState.CLOSING)
        stopHeartbeat()
        heartbeatExecutor.shutdownNow()
        val ch = activeChannel
        if (previous == CastSessionState.READY ||
            previous == CastSessionState.CONNECTED ||
            previous == CastSessionState.LAUNCHING
        ) {
            try {
                transportId?.let { sendClose(ch, it) }
                sendClose(ch, RECEIVER_ID)
            } catch (_: Exception) {
                // Best-effort CLOSE; the channel teardown below always runs.
            }
        }
        try {
            ch.close()
        } catch (_: Exception) {
        }
        setState(CastSessionState.DISCONNECTED)
    }

    private fun openAndLaunch(ch: CastChannel) {
        setState(CastSessionState.CONNECTING)
        ch.onClose = { handleChannelClosed() }
        ch.addMessageListener(::onInboundBroadcast)
        ch.open()
        lastInboundAt.set(nowMillis())
        setState(CastSessionState.CONNECTED)

        sendConnect(ch, RECEIVER_ID)

        val status = ch.sendRequest(NS_RECEIVER, RECEIVER_ID, payloadJson = """{"type":"GET_STATUS"}""")
            .get(ch.requestTimeoutMs, TimeUnit.MILLISECONDS)
        if (!parseReceiverStatus(status.payloadUtf8 ?: "")) {
            throw CastProtocolException("GET_STATUS answered without RECEIVER_STATUS")
        }

        var app = applications.firstOrNull { it.appId == APP_ID_DEFAULT_MEDIA_RECEIVER }
        if (app == null) {
            setState(CastSessionState.LAUNCHING)
            val launched = ch.sendRequest(
                NS_RECEIVER,
                RECEIVER_ID,
                payloadJson = """{"type":"LAUNCH","appId":"$APP_ID_DEFAULT_MEDIA_RECEIVER"}"""
            ).get(launchTimeoutMs, TimeUnit.MILLISECONDS)
            parseReceiverStatus(launched.payloadUtf8 ?: "")
            app = applications.firstOrNull { it.appId == APP_ID_DEFAULT_MEDIA_RECEIVER }
                ?: throw CastProtocolException("LAUNCH did not activate $APP_ID_DEFAULT_MEDIA_RECEIVER")
        }
        if (app.sessionId.isBlank() || app.transportId.isBlank()) {
            throw CastProtocolException("$APP_ID_DEFAULT_MEDIA_RECEIVER reported without sessionId or transportId")
        }

        sessionId = app.sessionId
        transportId = app.transportId
        sendConnect(ch, app.transportId)
        reconnectAttempt.set(0)
        startHeartbeat()
        // An inbound CLOSE or invalid frame may have ended the session while launching.
        if (!transition(setOf(CastSessionState.CONNECTED, CastSessionState.LAUNCHING), CastSessionState.READY)) {
            throw CastProtocolException("Session ended before reaching READY")
        }
    }

    private fun sendReceiverCommand(payloadJson: String) {
        if (state != CastSessionState.READY) return
        try {
            activeChannel.sendRequest(NS_RECEIVER, RECEIVER_ID, payloadJson = payloadJson)
                .whenComplete { message, _ ->
                    val payload = message?.payloadUtf8 ?: return@whenComplete
                    try {
                        parseReceiverStatus(payload)
                        checkApplicationAlive()
                    } catch (_: CastProtocolException) {
                        terminate()
                    }
                }
        } catch (_: Exception) {
            // Channel-level failures are surfaced through onClose.
        }
    }

    private fun onInboundBroadcast(message: CastMessage) {
        lastInboundAt.set(nowMillis())
        val payload = message.payloadUtf8 ?: return
        val obj = try {
            Json.parse(payload).objOrNull()
        } catch (_: IllegalArgumentException) {
            // CAST-INV-8: invalid JSON closes the channel.
            terminate()
            return
        } ?: return
        val type = obj.field("type")?.asString() ?: return
        when (message.namespace) {
            NS_HEARTBEAT -> if (type == "PING") {
                sendPong(message.sourceId.ifBlank { RECEIVER_ID }, obj.field("requestId")?.asDouble()?.toInt())
            }
            NS_CONNECTION -> if (type == "CLOSE" &&
                (message.sourceId == RECEIVER_ID || message.sourceId == transportId)
            ) {
                terminate()
            }
            NS_RECEIVER -> if (type == "RECEIVER_STATUS") {
                parseReceiverStatus(payload)
                checkApplicationAlive()
            }
        }
    }

    /** Ends a READY session once its app is gone from RECEIVER_STATUS (stopped or replaced). */
    private fun checkApplicationAlive() {
        val current = sessionId ?: return
        if (state == CastSessionState.READY && applications.none { it.sessionId == current }) {
            terminate()
        }
    }

    private fun startHeartbeat() {
        lastInboundAt.set(nowMillis())
        heartbeatFuture = heartbeatExecutor.scheduleAtFixedRate(
            ::heartbeatTick,
            heartbeatIntervalMs,
            heartbeatIntervalMs,
            TimeUnit.MILLISECONDS
        )
    }

    private fun heartbeatTick() {
        if (state != CastSessionState.READY) return
        try {
            val idleFor = nowMillis() - lastInboundAt.get()
            if (idleFor > heartbeatTimeoutMs) {
                handleHeartbeatTimeout()
            } else {
                sendPing()
            }
        } catch (_: Exception) {
            terminate()
        }
    }

    private fun handleHeartbeatTimeout() {
        if (reconnectAttempt.getAndIncrement() == 0) {
            try {
                reconnect()
            } catch (_: Exception) {
                terminate()
            }
        } else {
            terminate()
        }
    }

    private fun reconnect() {
        if (closing.get()) return
        val old = activeChannel
        val next = newChannel()
        old.onClose = null
        try {
            old.close()
        } catch (_: Exception) {
        }
        activeChannel = next
        stopHeartbeat()
        openAndLaunch(next)
    }

    private fun newChannel(): CastChannel = CastChannel(
        host = channel.host,
        port = channel.port,
        deviceId = channel.deviceId,
        trustStore = channel.trustStore,
        socketFactory = channel.socketFactory,
        connectionTimeoutMs = channel.connectionTimeoutMs,
        requestTimeoutMs = channel.requestTimeoutMs
    )

    private fun sendConnect(ch: CastChannel, destinationId: String) {
        ch.send(connectionMessage(destinationId, """{"type":"CONNECT"}"""))
    }

    private fun sendClose(ch: CastChannel, destinationId: String) {
        ch.send(connectionMessage(destinationId, """{"type":"CLOSE"}"""))
    }

    private fun connectionMessage(destinationId: String, payload: String) = CastMessage(
        sourceId = SENDER_ID,
        destinationId = destinationId,
        namespace = NS_CONNECTION,
        payloadType = CastPayloadType.STRING,
        payloadUtf8 = payload
    )

    private fun sendPing() {
        activeChannel.send(
            CastMessage(
                sourceId = SENDER_ID,
                destinationId = RECEIVER_ID,
                namespace = NS_HEARTBEAT,
                payloadType = CastPayloadType.STRING,
                payloadUtf8 = """{"type":"PING"}"""
            )
        )
    }

    private fun sendPong(destinationId: String, requestId: Int?) {
        val payload = if (requestId != null) """{"type":"PONG","requestId":$requestId}""" else """{"type":"PONG"}"""
        try {
            activeChannel.send(
                CastMessage(
                    sourceId = SENDER_ID,
                    destinationId = destinationId,
                    namespace = NS_HEARTBEAT,
                    payloadType = CastPayloadType.STRING,
                    payloadUtf8 = payload
                )
            )
        } catch (_: Exception) {
            // The channel close path reports the failure.
        }
    }

    /**
     * Updates volume and applications from a RECEIVER_STATUS payload. Returns false for
     * other message types; throws [CastProtocolException] on invalid JSON (CAST-INV-8).
     */
    private fun parseReceiverStatus(payload: String): Boolean {
        val root = try {
            Json.parse(payload)
        } catch (e: IllegalArgumentException) {
            throw CastProtocolException("Invalid JSON from receiver: ${e.message}")
        }
        val obj = root.objOrNull() ?: return false
        if (obj.field("type")?.asString() != "RECEIVER_STATUS") return false
        val status = obj.field("status")?.objOrNull() ?: return false
        status.field("volume")?.objOrNull()?.let { volume ->
            val current = receiverVolume
            receiverVolume = CastDeviceVolume(
                level = volume.field("level")?.asDouble()?.toFloat() ?: current?.level ?: 1.0f,
                muted = volume.field("muted")?.asBool() ?: current?.muted ?: false
            )
        }
        applications = status.field("applications")?.asArray()?.items
            ?.mapNotNull { it.objOrNull()?.let(::parseApplication) }
            ?: emptyList()
        return true
    }

    private fun parseApplication(obj: Json.Obj): CastApplication = CastApplication(
        appId = obj.field("appId")?.asString() ?: "",
        sessionId = obj.field("sessionId")?.asString() ?: "",
        transportId = obj.field("transportId")?.asString() ?: "",
        statusText = obj.field("statusText")?.asString()
    )

    private fun handleChannelClosed() {
        stopHeartbeat()
        if (!closing.get()) {
            setState(CastSessionState.DISCONNECTED)
        }
    }

    private fun terminate() {
        if (closing.get()) return
        stopHeartbeat()
        try {
            activeChannel.close()
        } catch (_: Exception) {
        }
        setState(CastSessionState.DISCONNECTED)
    }

    private fun stopHeartbeat() {
        heartbeatFuture?.cancel(false)
        heartbeatFuture = null
    }

    private fun setState(next: CastSessionState) {
        transition(null, next)
    }

    /** Moves to [next] if the current state is in [allowedFrom] (any state when null); listeners run outside the lock. */
    private fun transition(allowedFrom: Set<CastSessionState>?, next: CastSessionState): Boolean {
        synchronized(stateLock) {
            if (state == next) return allowedFrom == null
            if (allowedFrom != null && state !in allowedFrom) return false
            state = next
        }
        stateListeners.forEach { listener ->
            try {
                listener(next)
            } catch (_: Exception) {
            }
        }
        return true
    }

    private fun nowMillis(): Long = System.currentTimeMillis()
}
