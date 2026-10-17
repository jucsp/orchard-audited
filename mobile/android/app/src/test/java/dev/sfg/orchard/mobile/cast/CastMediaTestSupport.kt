/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 SFG545
 *
 * Shared helpers for the CastMediaChannel JVM tests.
 */
package dev.sfg.orchard.mobile.cast

import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

internal fun runReceiverScript(receiver: FakeCastReceiver, body: () -> Unit): Thread =
    Thread {
        try {
            body()
        } catch (t: Throwable) {
            receiver.error = t
        }
    }.apply { start() }

internal fun mediaTestSession(
    receiver: FakeCastReceiver,
    heartbeatIntervalMs: Long = 5000,
    heartbeatTimeoutMs: Long = 15000
): CastSession {
    val channel = CastChannel(
        "10.0.0.8",
        receiver.port,
        "test-device",
        InMemoryCastTrustStore(),
        receiver.connectedSocketFactory()
    )
    return CastSession(channel, heartbeatIntervalMs = heartbeatIntervalMs, heartbeatTimeoutMs = heartbeatTimeoutMs)
}

internal fun <T> assertFutureFails(future: CompletableFuture<T>, expected: Class<out Throwable>) {
    try {
        future.get(2, TimeUnit.SECONDS)
        fail("Expected failure with ${expected.simpleName}")
    } catch (e: ExecutionException) {
        val cause = e.cause
        assertTrue(
            "Expected ${expected.simpleName} but got ${cause?.javaClass?.simpleName}",
            expected.isInstance(cause)
        )
    } catch (e: TimeoutException) {
        fail("Future did not complete within 2s")
    }
}

internal fun mediaStatus(
    requestId: Int,
    mediaSessionId: Long,
    playerState: String,
    currentTime: Double = 0.0,
    duration: Double? = null,
    idleReason: String? = null
): String {
    val media = duration?.let { ",\"media\":{\"duration\":$it}" } ?: ""
    val idle = idleReason?.let { ",\"idleReason\":\"$it\"" } ?: ""
    return "{\"type\":\"MEDIA_STATUS\",\"requestId\":$requestId,\"status\":[{\"mediaSessionId\":$mediaSessionId,\"playerState\":\"$playerState\",\"currentTime\":$currentTime,\"playbackRate\":1.0$media$idle}]}"
}

internal fun mediaSessionIdOf(payload: String): Long =
    Regex(""""mediaSessionId"\s*:\s*(\d+)""").find(payload)?.groupValues?.get(1)?.toLong() ?: -1L
