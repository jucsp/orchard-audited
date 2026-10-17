// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 SFG545

package dev.sfg.orchard.mobile.cast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class CastDiscoveryTest {

    private fun txt(vararg pairs: Pair<String, String?>): Map<String, ByteArray?> =
        pairs.associate { (k, v) -> k to v?.toByteArray(Charsets.UTF_8) }

    private val nestMini = txt(
        "id" to "8b3f1c2a9d4e5f60718293a4b5c6d7e8",
        "cd" to "4A1C2B3D4E5F60718293A4B5C6D7E8F9",
        "rm" to "",
        "ve" to "05",
        "md" to "Google Nest Mini",
        "ic" to "/setup/icon.png",
        "fn" to "Cocina",
        "ca" to "199172",
        "st" to "0",
        "bs" to "FA8FCA3A1B2C",
        "nf" to "1",
        "rs" to ""
    )

    private val chromecastUltra = txt(
        "id" to "f0e1d2c3b4a5968778695a4b3c2d1e0f",
        "md" to "Chromecast Ultra",
        "fn" to "Living TV",
        "ca" to "201221",
        "rs" to "YouTube"
    )

    @Test
    fun `parses Nest Mini TXT record`() {
        val device = CastTxtParser.parse(nestMini, "192.168.1.40", 8009)
        assertEquals(
            CastDevice(
                id = "8b3f1c2a9d4e5f60718293a4b5c6d7e8",
                name = "Cocina",
                model = "Google Nest Mini",
                host = "192.168.1.40",
                port = 8009,
                capabilities = 199172
            ),
            device
        )
    }

    @Test
    fun `parses Chromecast Ultra TXT record`() {
        val device = CastTxtParser.parse(chromecastUltra, "10.0.0.15", 8009)
        assertNotNull(device)
        assertEquals("f0e1d2c3b4a5968778695a4b3c2d1e0f", device!!.id)
        assertEquals("Living TV", device.name)
        assertEquals("Chromecast Ultra", device.model)
        assertEquals(201221, device.capabilities)
    }

    @Test
    fun `falls back to host when fn is missing or blank`() {
        val noName = txt("id" to "abc", "md" to "Chromecast")
        assertEquals("172.16.5.9", CastTxtParser.parse(noName, "172.16.5.9", 8009)!!.name)
        val blankName = txt("id" to "abc", "fn" to "  ")
        assertEquals("172.16.5.9", CastTxtParser.parse(blankName, "172.16.5.9", 8009)!!.name)
    }

    @Test
    fun `missing model and capabilities use defaults`() {
        val device = CastTxtParser.parse(txt("id" to "abc", "fn" to "TV"), "192.168.0.2", 8009)!!
        assertNull(device.model)
        assertEquals(0, device.capabilities)
    }

    @Test
    fun `rejects missing, null or blank id`() {
        assertNull(CastTxtParser.parse(txt("fn" to "TV"), "192.168.1.40", 8009))
        assertNull(CastTxtParser.parse(txt("id" to null, "fn" to "TV"), "192.168.1.40", 8009))
        assertNull(CastTxtParser.parse(txt("id" to "", "fn" to "TV"), "192.168.1.40", 8009))
        assertNull(CastTxtParser.parse(txt("id" to "   ", "fn" to "TV"), "192.168.1.40", 8009))
    }

    @Test
    fun `rejects hosts outside private ranges`() {
        listOf("8.8.8.8", "100.64.0.1", "127.0.0.1", "224.0.0.251", "192.168.001.40", "chromecast.local", "")
            .forEach { host -> assertNull("expected rejected: $host", CastTxtParser.parse(nestMini, host, 8009)) }
    }

    @Test
    fun `accepts link-local host`() {
        assertNotNull(CastTxtParser.parse(nestMini, "169.254.10.20", 8009))
    }

    @Test
    fun `rejects invalid ports`() {
        listOf(0, -1, 65536).forEach { port ->
            assertNull("expected rejected: $port", CastTxtParser.parse(nestMini, "192.168.1.40", port))
        }
    }

    @Test
    fun `accepts multizone group port`() {
        val group = txt("id" to "group-uuid-1234", "md" to "Google Cast Group", "fn" to "Toda la casa", "ca" to "199204")
        val device = CastTxtParser.parse(group, "192.168.1.40", 32055)!!
        assertEquals(32055, device.port)
        assertEquals("Toda la casa", device.name)
    }

    @Test
    fun `parses capabilities as decimal or hex`() {
        assertEquals(2052, CastTxtParser.parseCapabilities("2052"))
        assertEquals(0x804, CastTxtParser.parseCapabilities("0x804"))
        assertEquals(0x804, CastTxtParser.parseCapabilities("0X804"))
        assertEquals(0x1f, CastTxtParser.parseCapabilities("1f"))
        assertEquals(0, CastTxtParser.parseCapabilities("zz"))
        assertEquals(0, CastTxtParser.parseCapabilities(""))
        assertEquals(0, CastTxtParser.parseCapabilities(null))
    }

    @Test
    fun `matches TXT keys case-insensitively`() {
        val upper = txt("ID" to "abc", "FN" to "TV")
        val device = CastTxtParser.parse(upper, "192.168.1.40", 8009)!!
        assertEquals("abc", device.id)
        assertEquals("TV", device.name)
    }
}
