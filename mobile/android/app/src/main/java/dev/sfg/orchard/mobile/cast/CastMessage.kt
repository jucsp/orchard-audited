/*
 * Copyright (C) 2026 SFG545
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package dev.sfg.orchard.mobile.cast

import java.io.ByteArrayOutputStream

/** Cast V2 payload types. STRING carries JSON; BINARY is parsed for completeness and ignored by callers. */
enum class CastPayloadType(val value: Int) {
    STRING(0),
    BINARY(1);

    companion object {
        fun fromValue(value: Int): CastPayloadType? = values().firstOrNull { it.value == value }
    }
}

/** Protocol-level failure for malformed or oversized CastMessage bytes. */
class CastProtocolException(message: String) : Exception(message)

/**
 * A proto2 CastMessage encoded by hand so the protocol stays free of android.*
 * and protobuf-lite. The three id/namespace strings and the two required fields are
 * always serialized, even when their value is zero or empty.
 */
data class CastMessage(
    val protocolVersion: Int = CastMessageCodec.PROTOCOL_VERSION,
    val sourceId: String,
    val destinationId: String,
    val namespace: String,
    val payloadType: CastPayloadType = CastPayloadType.STRING,
    val payloadUtf8: String? = null,
    val payloadBinary: ByteArray? = null,
)

/** Hand-rolled proto2 wire codec and 4-byte big-endian frame framing. */
object CastMessageCodec {
    const val PROTOCOL_VERSION = 0
    const val MAX_MESSAGE_SIZE = 65536
    const val SENDER_ID = "sender-0"
    const val RECEIVER_ID = "receiver-0"
    const val NAMESPACE_CONNECTION = "urn:x-cast:com.google.cast.tp.connection"
    const val NAMESPACE_HEARTBEAT = "urn:x-cast:com.google.cast.tp.heartbeat"

    fun encode(message: CastMessage): ByteArray {
        val out = ByteArrayOutputStream()
        writeEnum(1, message.protocolVersion, out)
        writeString(2, message.sourceId, out)
        writeString(3, message.destinationId, out)
        writeString(4, message.namespace, out)
        writeEnum(5, message.payloadType.value, out)
        message.payloadUtf8?.let { writeString(6, it, out) }
        message.payloadBinary?.let { writeBytes(7, it, out) }
        return out.toByteArray()
    }

    fun decode(bytes: ByteArray): CastMessage {
        val reader = CastMessageReader(bytes)
        var protocolVersion = PROTOCOL_VERSION
        var sourceId = ""
        var destinationId = ""
        var namespace = ""
        var payloadType = CastPayloadType.STRING
        var payloadUtf8: String? = null
        var payloadBinary: ByteArray? = null

        while (!reader.isAtEnd) {
            val tag = reader.readFieldTag()
            when (tag.number) {
                1 -> if (tag.wireType == WIRE_VARINT) {
                    protocolVersion = reader.readVarintField().toInt()
                } else {
                    reader.skipField(tag)
                }
                2 -> if (tag.wireType == WIRE_LENGTH_DELIMITED) {
                    sourceId = reader.readLengthDelimited().toString(Charsets.UTF_8)
                } else {
                    reader.skipField(tag)
                }
                3 -> if (tag.wireType == WIRE_LENGTH_DELIMITED) {
                    destinationId = reader.readLengthDelimited().toString(Charsets.UTF_8)
                } else {
                    reader.skipField(tag)
                }
                4 -> if (tag.wireType == WIRE_LENGTH_DELIMITED) {
                    namespace = reader.readLengthDelimited().toString(Charsets.UTF_8)
                } else {
                    reader.skipField(tag)
                }
                5 -> if (tag.wireType == WIRE_VARINT) {
                    val value = reader.readVarintField().toInt()
                    payloadType = CastPayloadType.fromValue(value)
                        ?: throw CastProtocolException("unknown payload_type: $value")
                } else {
                    reader.skipField(tag)
                }
                6 -> if (tag.wireType == WIRE_LENGTH_DELIMITED) {
                    payloadUtf8 = reader.readLengthDelimited().toString(Charsets.UTF_8)
                } else {
                    reader.skipField(tag)
                }
                7 -> if (tag.wireType == WIRE_LENGTH_DELIMITED) {
                    payloadBinary = reader.readLengthDelimited()
                } else {
                    reader.skipField(tag)
                }
                else -> reader.skipField(tag)
            }
        }

        return CastMessage(
            protocolVersion = protocolVersion,
            sourceId = sourceId,
            destinationId = destinationId,
            namespace = namespace,
            payloadType = payloadType,
            payloadUtf8 = payloadUtf8,
            payloadBinary = payloadBinary,
        )
    }

    fun encodeFrame(message: CastMessage): ByteArray {
        val body = encode(message)
        if (body.size > MAX_MESSAGE_SIZE) {
            throw CastProtocolException("CastMessage of ${body.size} bytes exceeds $MAX_MESSAGE_SIZE")
        }
        val out = ByteArrayOutputStream(body.size + 4)
        writeFrameLength(body.size, out)
        out.write(body)
        return out.toByteArray()
    }

    fun decodeFrame(frame: ByteArray): CastMessage {
        if (frame.size < 4) throw CastProtocolException("truncated frame header")
        val length = ((frame[0].toLong() and 0xffL) shl 24) or
            ((frame[1].toLong() and 0xffL) shl 16) or
            ((frame[2].toLong() and 0xffL) shl 8) or
            (frame[3].toLong() and 0xffL)
        if (length > MAX_MESSAGE_SIZE.toLong()) {
            throw CastProtocolException("frame length $length exceeds $MAX_MESSAGE_SIZE")
        }
        val bodyLength = length.toInt()
        if (frame.size - 4 < bodyLength) throw CastProtocolException("truncated frame body")
        if (frame.size - 4 > bodyLength) throw CastProtocolException("trailing bytes after frame body")
        return decode(frame.copyOfRange(4, 4 + bodyLength))
    }
}

private const val WIRE_VARINT = 0
private const val WIRE_FIXED64 = 1
private const val WIRE_LENGTH_DELIMITED = 2
private const val WIRE_START_GROUP = 3
private const val WIRE_END_GROUP = 4
private const val WIRE_FIXED32 = 5

// Bounds recursion on nested unknown groups so a crafted frame cannot overflow the stack.
private const val MAX_GROUP_DEPTH = 32

private data class FieldTag(val number: Int, val wireType: Int)

private class CastMessageReader(private val bytes: ByteArray) {
    private var position = 0

    val isAtEnd: Boolean
        get() = position == bytes.size

    private fun readByte(): Int {
        if (position >= bytes.size) throw CastProtocolException("truncated CastMessage")
        return bytes[position++].toInt() and 0xff
    }

    private fun skip(count: Int) {
        if (count < 0 || position + count > bytes.size) {
            throw CastProtocolException("truncated CastMessage")
        }
        position += count
    }

    private fun readVarint(maxBytes: Int): Long {
        var result = 0L
        var shift = 0
        repeat(maxBytes) {
            val next = readByte()
            result = result or ((next and 0x7f).toLong() shl shift)
            if (next and 0x80 == 0) return result
            shift += 7
        }
        throw CastProtocolException("varint exceeds $maxBytes bytes")
    }

    fun readFieldTag(): FieldTag {
        val tag = readVarint(5)
        if (tag == 0L) throw CastProtocolException("zero tag")
        val number = tag ushr 3
        if (number <= 0L || number > 0x1fffffffL) {
            throw CastProtocolException("invalid field number: $number")
        }
        return FieldTag(number.toInt(), (tag and 0x7L).toInt())
    }

    fun readLengthDelimited(): ByteArray {
        val length = readVarint(5)
        if (length > CastMessageCodec.MAX_MESSAGE_SIZE.toLong()) {
            throw CastProtocolException(
                "length-delimited field exceeds ${CastMessageCodec.MAX_MESSAGE_SIZE} bytes",
            )
        }
        val size = length.toInt()
        if (size > bytes.size - position) {
            throw CastProtocolException("truncated length-delimited field")
        }
        val result = bytes.copyOfRange(position, position + size)
        position += size
        return result
    }

    fun readVarintField(): Long = readVarint(10)

    fun skipField(tag: FieldTag, depth: Int = 0) {
        when (tag.wireType) {
            WIRE_VARINT -> readVarint(10)
            WIRE_FIXED64 -> skip(8)
            WIRE_LENGTH_DELIMITED -> readLengthDelimited()
            WIRE_START_GROUP -> skipGroup(tag.number, depth + 1)
            WIRE_END_GROUP -> throw CastProtocolException("unexpected end group")
            WIRE_FIXED32 -> skip(4)
            else -> throw CastProtocolException("unsupported wire type ${tag.wireType}")
        }
    }

    private fun skipGroup(fieldNumber: Int, depth: Int) {
        if (depth > MAX_GROUP_DEPTH) throw CastProtocolException("groups nested deeper than $MAX_GROUP_DEPTH")
        while (true) {
            val tag = readFieldTag()
            if (tag.wireType == WIRE_END_GROUP) {
                if (tag.number != fieldNumber) {
                    throw CastProtocolException("mismatched end group for field $fieldNumber")
                }
                return
            }
            skipField(tag, depth)
        }
    }
}

private fun writeVarint(value: Long, out: ByteArrayOutputStream) {
    require(value >= 0) { "varint value must be non-negative" }
    var remaining = value
    do {
        val next = (remaining and 0x7fL).toInt()
        remaining = remaining ushr 7
        out.write(if (remaining != 0L) next or 0x80 else next)
    } while (remaining != 0L)
}

private fun writeTag(fieldNumber: Int, wireType: Int, out: ByteArrayOutputStream) {
    writeVarint(((fieldNumber shl 3) or wireType).toLong(), out)
}

private fun writeBytes(fieldNumber: Int, value: ByteArray, out: ByteArrayOutputStream) {
    writeTag(fieldNumber, WIRE_LENGTH_DELIMITED, out)
    writeVarint(value.size.toLong(), out)
    out.write(value)
}

private fun writeString(fieldNumber: Int, value: String, out: ByteArrayOutputStream) {
    writeBytes(fieldNumber, value.toByteArray(Charsets.UTF_8), out)
}

private fun writeEnum(fieldNumber: Int, value: Int, out: ByteArrayOutputStream) {
    writeTag(fieldNumber, WIRE_VARINT, out)
    writeVarint(value.toLong(), out)
}

private fun writeFrameLength(length: Int, out: ByteArrayOutputStream) {
    out.write((length ushr 24) and 0xff)
    out.write((length ushr 16) and 0xff)
    out.write((length ushr 8) and 0xff)
    out.write(length and 0xff)
}
