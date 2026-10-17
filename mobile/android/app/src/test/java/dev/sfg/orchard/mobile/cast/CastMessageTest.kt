/*
 * Copyright (C) 2026 SFG545
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package dev.sfg.orchard.mobile.cast

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.File

class CastMessageTest {

    @Test
    fun encodesConnectMessageToGoldenBytes() {
        val message = connectMessage()
        assertEquals(CONNECT_BODY_HEX, CastMessageCodec.encode(message).toHex())
        assertEquals(CONNECT_FRAME_HEX, CastMessageCodec.encodeFrame(message).toHex())
    }

    @Test
    fun encodesPingMessageToGoldenBytes() {
        val message = pingMessage()
        assertEquals(PING_BODY_HEX, CastMessageCodec.encode(message).toHex())
        assertEquals(PING_FRAME_HEX, CastMessageCodec.encodeFrame(message).toHex())
    }

    @Test
    fun decodesConnectFrameGoldenBytes() {
        val decoded = CastMessageCodec.decodeFrame(hexToBytes(CONNECT_FRAME_HEX))
        assertEquals(0, decoded.protocolVersion)
        assertEquals(CastMessageCodec.SENDER_ID, decoded.sourceId)
        assertEquals(CastMessageCodec.RECEIVER_ID, decoded.destinationId)
        assertEquals(CastMessageCodec.NAMESPACE_CONNECTION, decoded.namespace)
        assertEquals(CastPayloadType.STRING, decoded.payloadType)
        assertEquals("""{"type":"CONNECT"}""", decoded.payloadUtf8)
        assertNull(decoded.payloadBinary)
    }

    @Test
    fun decodesPingFrameGoldenBytes() {
        val decoded = CastMessageCodec.decodeFrame(hexToBytes(PING_FRAME_HEX))
        assertEquals(0, decoded.protocolVersion)
        assertEquals(CastMessageCodec.SENDER_ID, decoded.sourceId)
        assertEquals(CastMessageCodec.RECEIVER_ID, decoded.destinationId)
        assertEquals(CastMessageCodec.NAMESPACE_HEARTBEAT, decoded.namespace)
        assertEquals(CastPayloadType.STRING, decoded.payloadType)
        assertEquals("""{"type":"PING"}""", decoded.payloadUtf8)
    }

    @Test
    fun fixturesMatchEncodedFrames() {
        assertEquals(fixtureHex("connect_receiver0.hex"), CastMessageCodec.encodeFrame(connectMessage()).toHex())
        assertEquals(fixtureHex("ping_receiver0.hex"), CastMessageCodec.encodeFrame(pingMessage()).toHex())
    }

    @Test
    fun binaryPayloadRoundTrips() {
        val binary = byteArrayOf(0x00, 0x01, 0x7f, 0x80.toByte(), 0xff.toByte())
        val message = CastMessage(
            sourceId = CastMessageCodec.SENDER_ID,
            destinationId = CastMessageCodec.RECEIVER_ID,
            namespace = "urn:x-cast:com.google.cast.media",
            payloadType = CastPayloadType.BINARY,
            payloadBinary = binary,
        )
        val decoded = CastMessageCodec.decode(CastMessageCodec.encode(message))
        assertEquals(CastPayloadType.BINARY, decoded.payloadType)
        assertArrayEquals(binary, decoded.payloadBinary)
        assertNull(decoded.payloadUtf8)
    }

    @Test
    fun frameHeaderCarriesBigEndianBodyLength() {
        val message = connectMessage()
        val frame = CastMessageCodec.encodeFrame(message)
        val body = CastMessageCodec.encode(message)
        val length = ((frame[0].toInt() and 0xff) shl 24) or
            ((frame[1].toInt() and 0xff) shl 16) or
            ((frame[2].toInt() and 0xff) shl 8) or
            (frame[3].toInt() and 0xff)
        assertEquals(body.size, length)
        assertEquals(4 + body.size, frame.size)
    }

    @Test
    fun frameRejectsLengthAboveLimit() {
        val oversized = byteArrayOf(0x00, 0x01, 0x00, 0x01)
        assertThrows(CastProtocolException::class.java) {
            CastMessageCodec.decodeFrame(oversized)
        }
    }

    @Test
    fun frameRejectsTruncatedBody() {
        val truncated = hexToBytes("0000000a0800")
        assertThrows(CastProtocolException::class.java) {
            CastMessageCodec.decodeFrame(truncated)
        }
    }

    @Test
    fun frameRejectsOversizedMessage() {
        val message = connectMessage().copy(
            payloadUtf8 = "x".repeat(CastMessageCodec.MAX_MESSAGE_SIZE + 1),
        )
        assertThrows(CastProtocolException::class.java) {
            CastMessageCodec.encodeFrame(message)
        }
    }

    @Test
    fun frameAtExactLimitRoundTrips() {
        val base = CastMessageCodec.encode(connectMessage().copy(payloadUtf8 = "")).size
        // base counts a two-byte empty field 6; at this size its length varint takes three bytes.
        val payload = "x".repeat(CastMessageCodec.MAX_MESSAGE_SIZE - base - 2)
        val message = connectMessage().copy(payloadUtf8 = payload)
        val frame = CastMessageCodec.encodeFrame(message)
        assertEquals(4 + CastMessageCodec.MAX_MESSAGE_SIZE, frame.size)
        assertEquals(payload, CastMessageCodec.decodeFrame(frame).payloadUtf8)
    }

    @Test
    fun frameRejectsTrailingBytes() {
        val frame = hexToBytes(CONNECT_FRAME_HEX) + byteArrayOf(0x00)
        assertThrows(CastProtocolException::class.java) {
            CastMessageCodec.decodeFrame(frame)
        }
    }

    @Test
    fun deeplyNestedGroupsFail() {
        val bytes = hexToBytes("7b".repeat(10_000))
        assertThrows(CastProtocolException::class.java) {
            CastMessageCodec.decode(bytes)
        }
    }

    @Test
    fun shallowNestedGroupsAreSkipped() {
        val decoded = CastMessageCodec.decode(hexToBytes("7b7b7c7c" + CONNECT_BODY_HEX))
        assertEquals(CastMessageCodec.NAMESPACE_CONNECTION, decoded.namespace)
    }

    @Test
    fun lengthVarintLongerThanFiveBytesFails() {
        val bytes = hexToBytes("22808080808001")
        assertThrows(CastProtocolException::class.java) {
            CastMessageCodec.decode(bytes)
        }
    }

    @Test
    fun unknownFieldsAreSkippedByWireType() {
        val unknown = hexToBytes(
            "7801" +
                "81010102030405060708" +
                "8a01026162" +
                "53080154" +
                "9501deadbeef",
        )
        val decoded = CastMessageCodec.decode(unknown + hexToBytes(CONNECT_BODY_HEX))
        assertEquals(CastMessageCodec.NAMESPACE_CONNECTION, decoded.namespace)
        assertEquals("""{"type":"CONNECT"}""", decoded.payloadUtf8)
    }

    @Test
    fun invalidPayloadTypeFails() {
        val bytes = hexToBytes("0800120873656e6465722d301a0a72656365697665722d30220028023200")
        assertThrows(CastProtocolException::class.java) {
            CastMessageCodec.decode(bytes)
        }
    }

    @Test
    fun zeroTagFails() {
        assertThrows(CastProtocolException::class.java) {
            CastMessageCodec.decode(hexToBytes("00"))
        }
    }

    private fun connectMessage() = CastMessage(
        sourceId = CastMessageCodec.SENDER_ID,
        destinationId = CastMessageCodec.RECEIVER_ID,
        namespace = CastMessageCodec.NAMESPACE_CONNECTION,
        payloadUtf8 = """{"type":"CONNECT"}""",
    )

    private fun pingMessage() = CastMessage(
        sourceId = CastMessageCodec.SENDER_ID,
        destinationId = CastMessageCodec.RECEIVER_ID,
        namespace = CastMessageCodec.NAMESPACE_HEARTBEAT,
        payloadUtf8 = """{"type":"PING"}""",
    )

    private companion object {
        const val CONNECT_BODY_HEX = "0800120873656e6465722d301a0a72656365697665722d302228" +
            "75726e3a782d636173743a636f6d2e676f6f676c652e636173742e74702e636f6e6e656374696f6e" +
            "280032127b2274797065223a22434f4e4e454354227d"
        const val CONNECT_FRAME_HEX = "00000058" + CONNECT_BODY_HEX
        const val PING_BODY_HEX = "0800120873656e6465722d301a0a72656365697665722d302227" +
            "75726e3a782d636173743a636f6d2e676f6f676c652e636173742e74702e686561727462656174" +
            "2800320f7b2274797065223a2250494e47227d"
        const val PING_FRAME_HEX = "00000054" + PING_BODY_HEX
    }
}

private fun fixtureHex(name: String): String {
    var dir: File? = File(System.getProperty("user.dir")).absoluteFile
    while (dir != null) {
        val candidate = File(dir, "tests/fixtures/castv2/$name")
        if (candidate.isFile) return candidate.readText().trim()
        dir = dir.parentFile
    }
    error("fixture tests/fixtures/castv2/$name not found")
}

private fun ByteArray.toHex(): String =
    joinToString("") { ((it.toInt() and 0xff).toString(16)).padStart(2, '0') }

private fun hexToBytes(hex: String): ByteArray {
    val clean = hex.filterNot { it.isWhitespace() }
    require(clean.length % 2 == 0) { "odd hex length" }
    return ByteArray(clean.length / 2) { index ->
        clean.substring(index * 2, index * 2 + 2).toInt(16).toByte()
    }
}
