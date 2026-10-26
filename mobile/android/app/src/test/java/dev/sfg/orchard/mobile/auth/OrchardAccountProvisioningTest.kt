// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 SFG545

package dev.sfg.orchard.mobile.auth

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class OrchardAccountProvisioningTest {

    private class RecordingTransport : AccountTransport {
        data class Request(val method: String, val path: String, val body: JSONObject?, val bearer: String?)

        val requests = mutableListOf<Request>()
        var handler: ((Request) -> JSONObject)? = null

        override fun request(method: String, path: String, body: JSONObject?, bearer: String?): JSONObject {
            val request = Request(method, path, body, bearer)
            requests += request
            return handler?.invoke(request)
                ?: throw AccountServiceException(500, "server_error", "no handler configured")
        }
    }

    private fun binding(): ByteArray = ByteArray(32) { 7 }

    @Test
    fun computeProvisionBindingIsDeterministicAnd32Bytes() {
        val th1 = ByteArray(32) { 1 }
        val th2 = ByteArray(32) { 2 }

        val b1 = OrchardAccountProvisioning.computeProvisionBinding(th1)
        val b2 = OrchardAccountProvisioning.computeProvisionBinding(th2)

        assertEquals(32, b1.size)
        assertEquals(32, b2.size)
        assertFalse(b1.contentEquals(b2))
        assertTrue(b1.contentEquals(OrchardAccountProvisioning.computeProvisionBinding(th1)))
    }

    @Test
    fun requestProvisionCodeSendsBindingAndReturnsCode() {
        val transport = RecordingTransport()
        transport.handler = { request ->
            assertEquals("POST", request.method)
            assertEquals("/devices/provision", request.path)
            assertEquals(OrchardAccountProvisioning.encode(binding()), request.body?.optString("binding"))
            assertEquals("access-token", request.bearer)
            JSONObject().put("code", "c".repeat(43)).put("expires_in", 120)
        }

        val result = OrchardAccountProvisioning(transport).requestProvisionCode("access-token", binding())

        assertEquals(43, result.code.length)
        assertEquals(120, result.expiresInSeconds)
        assertEquals(1, transport.requests.size)
    }

    @Test
    fun requestProvisionCodeMapsUnauthorizedAndRateLimitAsNonRetriable() {
        listOf(401, 429).forEach { status ->
            val transport = RecordingTransport()
            transport.handler = { throw AccountServiceException(status, if (status == 401) "invalid_token" else "rate_limited", "nope") }
            try {
                OrchardAccountProvisioning(transport).requestProvisionCode("bad", binding())
                fail("expected ProvisionException for $status")
            } catch (expected: ProvisionException) {
                assertFalse(expected.retriable)
            }
        }
    }

    @Test
    fun requestProvisionCodeMapsServerErrorAsRetriable() {
        val transport = RecordingTransport()
        transport.handler = { throw AccountServiceException(500, "server_error", "boom") }
        try {
            OrchardAccountProvisioning(transport).requestProvisionCode("token", binding())
            fail("expected ProvisionException")
        } catch (expected: ProvisionException) {
            assertTrue(expected.retriable)
        }
    }

    @Test
    fun redeemProvisionBuildsGrantBodyAndReturnsTokens() {
        val transport = RecordingTransport()
        transport.handler = { request ->
            assertEquals("POST", request.method)
            assertEquals("/auth/token", request.path)
            assertEquals("urn:orchard:grant-type:provision", request.body?.optString("grant_type"))
            assertEquals(OrchardAccountProvisioning.encode(binding()), request.body?.optString("binding"))
            assertEquals("Living Room", request.body?.getJSONObject("device")?.optString("name"))
            assertEquals("android", request.body?.getJSONObject("device")?.optString("platform"))
            assertEquals(null, request.bearer)
            JSONObject().put("access_token", "at").put("refresh_token", "rt").put("expires_in", 3600)
        }

        val result = OrchardAccountProvisioning(transport).redeemProvision("c".repeat(43), binding(), "Living Room")

        assertEquals("at", result.optString("access_token"))
        assertEquals("rt", result.optString("refresh_token"))
    }

    @Test
    fun redeemProvisionMapsInvalidGrantAsNonRetriable() {
        val transport = RecordingTransport()
        transport.handler = { throw AccountServiceException(400, "invalid_grant", "used") }
        try {
            OrchardAccountProvisioning(transport).redeemProvision("c".repeat(43), binding(), "TV")
            fail("expected ProvisionException")
        } catch (expected: ProvisionException) {
            assertFalse(expected.retriable)
        }
    }
}
