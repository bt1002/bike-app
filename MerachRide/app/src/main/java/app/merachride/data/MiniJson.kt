package app.merachride.data

/**
 * Minimal JSON reader and writer for the app's own save files. Pure Kotlin with no dependencies,
 * so the save formats can be unit-tested on the JVM (Android's org.json is only a stub there).
 *
 * Values map to: object → Map<String, Any?>, array → List<Any?>, string → String,
 * number → Long (whole numbers) or Double, true/false → Boolean, null → null.
 */
object MiniJson {

    fun write(value: Any?): String = StringBuilder().also { writeTo(it, value) }.toString()

    /** Parses [text]. Throws [IllegalArgumentException] if it isn't valid JSON. */
    fun parse(text: String): Any? {
        val p = Parser(text)
        val v = p.value()
        p.skipWhitespace()
        if (!p.atEnd()) p.fail("unexpected trailing content")
        return v
    }

    private fun writeTo(sb: StringBuilder, v: Any?) {
        when (v) {
            null -> sb.append("null")
            is String -> writeString(sb, v)
            is Boolean -> sb.append(v)
            is Int, is Long, is Short, is Byte -> sb.append(v)
            is Number -> {
                val d = v.toDouble()
                require(d.isFinite()) { "JSON can't hold $d" }
                sb.append(d)
            }
            is Map<*, *> -> {
                sb.append('{')
                var first = true
                for ((k, x) in v) {
                    if (!first) sb.append(',')
                    first = false
                    writeString(sb, k as? String ?: throw IllegalArgumentException("JSON keys must be strings"))
                    sb.append(':')
                    writeTo(sb, x)
                }
                sb.append('}')
            }
            is List<*> -> {
                sb.append('[')
                v.forEachIndexed { i, x ->
                    if (i > 0) sb.append(',')
                    writeTo(sb, x)
                }
                sb.append(']')
            }
            else -> throw IllegalArgumentException("Can't write ${v::class.simpleName} as JSON")
        }
    }

    private fun writeString(sb: StringBuilder, s: String) {
        sb.append('"')
        for (c in s) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> if (c < ' ') sb.append("\\u%04x".format(c.code)) else sb.append(c)
            }
        }
        sb.append('"')
    }

    private class Parser(private val s: String) {
        private var i = 0

        fun atEnd() = i >= s.length

        fun fail(msg: String): Nothing = throw IllegalArgumentException("Bad JSON at $i: $msg")

        fun skipWhitespace() {
            while (i < s.length && s[i].isWhitespace()) i++
        }

        fun value(): Any? {
            skipWhitespace()
            if (atEnd()) fail("unexpected end")
            return when (s[i]) {
                '{' -> obj()
                '[' -> array()
                '"' -> string()
                't' -> literal("true", true)
                'f' -> literal("false", false)
                'n' -> literal("null", null)
                else -> number()
            }
        }

        private fun expect(c: Char) {
            skipWhitespace()
            if (atEnd() || s[i] != c) fail("expected '$c'")
            i++
        }

        /** Consumes [c] if it's next (after whitespace). */
        private fun take(c: Char): Boolean {
            skipWhitespace()
            if (!atEnd() && s[i] == c) {
                i++
                return true
            }
            return false
        }

        private fun obj(): Map<String, Any?> {
            expect('{')
            val m = LinkedHashMap<String, Any?>()
            if (take('}')) return m
            do {
                skipWhitespace()
                if (atEnd() || s[i] != '"') fail("expected a key")
                val k = string()
                expect(':')
                m[k] = value()
            } while (take(','))
            expect('}')
            return m
        }

        private fun array(): List<Any?> {
            expect('[')
            val list = ArrayList<Any?>()
            if (take(']')) return list
            do {
                list.add(value())
            } while (take(','))
            expect(']')
            return list
        }

        private fun string(): String {
            expect('"')
            val sb = StringBuilder()
            while (true) {
                if (atEnd()) fail("unterminated string")
                val c = s[i++]
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        if (atEnd()) fail("unterminated escape")
                        when (val e = s[i++]) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> {
                                if (i + 4 > s.length) fail("short \\u escape")
                                val code = s.substring(i, i + 4).toIntOrNull(16) ?: fail("bad \\u escape")
                                sb.append(code.toChar())
                                i += 4
                            }
                            else -> fail("bad escape '\\$e'")
                        }
                    }
                    else -> sb.append(c)
                }
            }
        }

        private fun literal(word: String, v: Any?): Any? {
            if (!s.startsWith(word, i)) fail("expected $word")
            i += word.length
            return v
        }

        private fun number(): Number {
            val start = i
            while (i < s.length && (s[i].isDigit() || s[i] in "+-.eE")) i++
            val text = s.substring(start, i)
            if (text.isEmpty()) fail("unexpected '${s[start]}'")
            return text.toLongOrNull() ?: text.toDoubleOrNull() ?: fail("bad number '$text'")
        }
    }
}

/** Typed helpers for reading parsed JSON. They throw if the value is missing or the wrong type. */
fun Map<String, Any?>.str(key: String): String = this[key] as? String ?: throw IllegalArgumentException("missing $key")
fun Map<String, Any?>.strOrNull(key: String): String? = this[key] as? String
fun Map<String, Any?>.int(key: String): Int = (this[key] as? Number)?.toInt() ?: throw IllegalArgumentException("missing $key")
fun Map<String, Any?>.intOrNull(key: String): Int? = (this[key] as? Number)?.toInt()
fun Map<String, Any?>.long(key: String): Long = (this[key] as? Number)?.toLong() ?: throw IllegalArgumentException("missing $key")
fun Map<String, Any?>.doubleOrNull(key: String): Double? = (this[key] as? Number)?.toDouble()

@Suppress("UNCHECKED_CAST")
fun Map<String, Any?>.objects(key: String): List<Map<String, Any?>> =
    (this[key] as? List<*>)?.filterIsInstance<Map<*, *>>()?.map { it as Map<String, Any?> } ?: emptyList()

@Suppress("UNCHECKED_CAST")
fun Any?.asObject(): Map<String, Any?> = this as? Map<String, Any?> ?: throw IllegalArgumentException("not an object")
