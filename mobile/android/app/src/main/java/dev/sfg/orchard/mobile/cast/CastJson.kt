/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 SFG545
 *
 * Minimal dependency-free JSON parser for CastV2 control payloads.
 */
package dev.sfg.orchard.mobile.cast

internal sealed class Json {
    object Null : Json()
    data class Bool(val value: Boolean) : Json()
    data class Num(val value: Double) : Json()
    data class Str(val value: String) : Json()
    data class Arr(val items: MutableList<Json> = mutableListOf()) : Json()
    data class Obj(val fields: LinkedHashMap<String, Json> = LinkedHashMap()) : Json()

    companion object {
        // Receiver payloads nest a few levels; the cap keeps a hostile frame from overflowing the stack.
        const val MAX_DEPTH = 32

        /** Parses [text]; throws [IllegalArgumentException] on malformed or too deeply nested input. */
        fun parse(text: String): Json {
            val p = Parser(text)
            p.ws()
            val v = p.value(0)
            p.ws()
            if (!p.eof()) p.fail("trailing")
            return v
        }
    }

    private class Parser(val t: String) {
        var i = 0
        fun eof() = i >= t.length
        fun ws() { while (i < t.length && t[i] in " \t\n\r") i++ }
        fun fail(m: String): Nothing = throw IllegalArgumentException("JSON@$i: $m")

        fun value(depth: Int): Json {
            if (depth > MAX_DEPTH) fail("depth")
            ws()
            if (eof()) fail("eof")
            return when (t[i]) {
                '{' -> obj(depth)
                '[' -> arr(depth)
                '"' -> Str(str())
                't' -> lit("true", Bool(true))
                'f' -> lit("false", Bool(false))
                'n' -> lit("null", Null)
                else -> if (t[i] == '-' || t[i] in '0'..'9') num() else fail("char ${t[i]}")
            }
        }

        fun lit(l: String, v: Json): Json {
            if (!t.startsWith(l, i)) fail("lit")
            i += l.length
            return v
        }

        fun obj(depth: Int): Json {
            i++
            val o = Obj()
            ws()
            if (!eof() && t[i] == '}') { i++; return o }
            while (true) {
                ws()
                if (eof() || t[i] != '"') fail("key")
                val k = str()
                ws()
                if (eof() || t[i] != ':') fail("colon")
                i++
                o.fields[k] = value(depth + 1)
                ws()
                if (!eof() && t[i] == ',') { i++; continue }
                if (!eof() && t[i] == '}') { i++; return o }
                fail("obj")
            }
        }

        fun arr(depth: Int): Json {
            i++
            val a = Arr()
            ws()
            if (!eof() && t[i] == ']') { i++; return a }
            while (true) {
                a.items.add(value(depth + 1))
                ws()
                if (!eof() && t[i] == ',') { i++; continue }
                if (!eof() && t[i] == ']') { i++; return a }
                fail("arr")
            }
        }

        fun str(): String {
            i++
            val sb = StringBuilder()
            while (true) {
                if (eof()) fail("str")
                val c = t[i]
                if (c == '"') { i++; return sb.toString() }
                if (c == '\\') {
                    i++
                    if (eof()) fail("esc")
                    when (t[i]) {
                        '"' -> sb.append('"')
                        '\\' -> sb.append('\\')
                        '/' -> sb.append('/')
                        'b' -> sb.append('\b')
                        'f' -> sb.append('\u000C')
                        'n' -> sb.append('\n')
                        'r' -> sb.append('\r')
                        't' -> sb.append('\t')
                        'u' -> {
                            if (i + 4 >= t.length) fail("u")
                            sb.append(t.substring(i + 1, i + 5).toInt(16).toChar())
                            i += 4
                        }
                        else -> fail("esc")
                    }
                } else sb.append(c)
                i++
            }
        }

        fun num(): Json {
            val s = i
            if (!eof() && t[i] == '-') i++
            while (!eof() && t[i] in '0'..'9') i++
            if (!eof() && t[i] == '.') { i++; while (!eof() && t[i] in '0'..'9') i++ }
            if (!eof() && (t[i] == 'e' || t[i] == 'E')) {
                i++
                if (!eof() && (t[i] == '+' || t[i] == '-')) i++
                while (!eof() && t[i] in '0'..'9') i++
            }
            return Num(t.substring(s, i).toDouble())
        }
    }
}

internal fun Json.objOrNull(): Json.Obj? = this as? Json.Obj
internal fun Json.asString(): String = (this as? Json.Str)?.value ?: ""
internal fun Json.asDouble(): Double = (this as? Json.Num)?.value ?: 0.0
internal fun Json.asBool(): Boolean = (this as? Json.Bool)?.value ?: false
internal fun Json.field(name: String): Json? = (this as? Json.Obj)?.fields?.get(name)
internal fun Json.asArray(): Json.Arr? = this as? Json.Arr
