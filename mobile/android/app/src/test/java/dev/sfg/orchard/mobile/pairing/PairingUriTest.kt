// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 SFG545

package dev.sfg.orchard.mobile.pairing

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class PairingUriTest {
    private val token = PairingCrypto.randomBytes(16)
    private val fp = PairingCrypto.randomBytes(32)

    private fun link(host: String = "192.168.1.20", port: Int = 40123, name: String = "Orchard TV 65PUD8507"): String =
        PairingUri.build(host, port, token, fp, name)

    private fun raw(h: String = "192.168.1.20", p: String = "40123", t: String = PairingCodec.encode(token), f: String = PairingCodec.encode(fp), extra: String = ""): String =
        "orchard://pair?v=1&h=$h&p=$p&t=$t&f=$f&n=TV$extra"

    @Test
    fun roundTripsAValidLink() {
        val info = PairingUri.parse(link(name = "Orchard TV Philips ñ/&"))
        assertNotNull(info)
        info!!
        assertEquals("192.168.1.20", info.host)
        assertEquals(40123, info.port)
        assertArrayEquals(token, info.token)
        assertArrayEquals(fp, info.fingerprint)
        assertEquals("Orchard TV Philips ñ/&", info.tvName)
    }

    @Test
    fun acceptsEveryPrivateRange() {
        listOf("10.0.0.2", "172.16.4.1", "172.31.255.254", "192.168.0.10", "169.254.3.3").forEach {
            assertNotNull(it, PairingUri.parse(raw(h = it)))
        }
    }

    @Test
    fun rejectsNonPrivateHosts() {
        listOf(
            "8.8.8.8", "127.0.0.1", "0.0.0.0", "224.0.0.251", "255.255.255.255", "100.64.0.1", "172.32.0.1",
            "tv.local", "example.com", "192.168.001.002", "%5B::1%5D", "fe80::1", "192.168.1",
        ).forEach { assertNull(it, PairingUri.parse(raw(h = it))) }
    }

    @Test
    fun rejectsBadPortsAndLengths() {
        listOf("0", "80", "1023", "65536", "-1", "abc").forEach { assertNull(it, PairingUri.parse(raw(p = it))) }
        assertNull(PairingUri.parse(raw(t = PairingCodec.encode(ByteArray(15)))))
        assertNull(PairingUri.parse(raw(t = PairingCodec.encode(ByteArray(17)))))
        assertNull(PairingUri.parse(raw(f = PairingCodec.encode(ByteArray(31)))))
        assertNull(PairingUri.parse(raw(f = "not*base64")))
        assertNull(PairingUri.parse("orchard://pair?v=1&h=192.168.1.20&p=40123&t=${PairingCodec.encode(token)}&f=${PairingCodec.encode(fp)}&n=${"x".repeat(41)}"))
    }

    @Test
    fun rejectsWrongSchemeHostVersionAndExtraParams() {
        val good = raw()
        assertNotNull(PairingUri.parse(good))
        assertNull(PairingUri.parse(good.replace("orchard://", "https://")))
        assertNull(PairingUri.parse(good.replace("://pair", "://pairing")))
        assertNull(PairingUri.parse(good.replace("v=1", "v=2")))
        assertNull(PairingUri.parse(raw(extra = "&k=secret")))
        assertNull(PairingUri.parse(raw(extra = "&h=10.0.0.1")))
        assertNull(PairingUri.parse("orchard://pair"))
        assertNull(PairingUri.parse("::not a uri::"))
    }

    @Test
    fun linkNeverContainsSessionKeyMaterial() {
        val uri = link()
        assertFalse(uri.contains("cookie") || uri.contains("key"))
        assertEquals(setOf("v", "h", "p", "t", "f", "n"), uri.substringAfter('?').split('&').map { it.substringBefore('=') }.toSet())
    }

    @Test
    fun qrMatrixKeepsAFourModuleQuietZone() {
        val matrix = ZxingQrMatrixEncoder().encode(link(host = "192.168.100.200", port = 65000, name = "x".repeat(40)))
        // The longest link (158 bytes) needs version 9 at level M: 53 modules plus 4 per side.
        assertTrue("size ${matrix.size}", matrix.size in 29..61)
        for (i in 0 until matrix.size) {
            for (margin in 0 until 4) {
                assertFalse(matrix.isDark(i, margin) || matrix.isDark(margin, i))
                assertFalse(matrix.isDark(i, matrix.size - 1 - margin) || matrix.isDark(matrix.size - 1 - margin, i))
            }
        }
        // Top-left finder pattern starts right after the quiet zone.
        assertTrue(matrix.isDark(4, 4))
    }

    @Test
    fun buildRefusesInvalidInput() {
        expectIllegalArgument { PairingUri.build("8.8.8.8", 40000, token, fp, "TV") }
        expectIllegalArgument { PairingUri.build("192.168.1.2", 80, token, fp, "TV") }
        expectIllegalArgument { PairingUri.build("192.168.1.2", 40000, ByteArray(8), fp, "TV") }
        expectIllegalArgument { PairingUri.build("192.168.1.2", 40000, token, fp, "x".repeat(41)) }
    }

    private inline fun expectIllegalArgument(block: () -> Unit) {
        try {
            block()
            fail("expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
            // expected
        }
    }
}
