package cn.ntit.crps_compose.ble

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

/** One characteristic's bounded UTF-8 JSON stream; decode only after all bytes arrive. */
internal class JsonFrameBuffer {
    private val bytes = ByteArray(1024)
    private var size = 0
    private var depth = 0
    private var inString = false
    private var escaped = false
    private var startedAt = 0L

    @Synchronized
    fun clear() {
        size = 0
        depth = 0
        inString = false
        escaped = false
    }

    @Synchronized
    fun consume(chunk: ByteArray, now: Long, onFrame: (String) -> Unit) {
        if (chunk.size > bytes.size) {
            clear()
            return
        }
        if (size > 0 && now - startedAt >= 2000) clear()
        for (value in chunk) {
            if (size == bytes.size) clear()
            val c = value.toInt() and 0xFF
            if (size == 0) {
                if (c != '{'.code) continue
                startedAt = now
            }
            bytes[size++] = value
            if (inString) {
                when {
                    escaped -> escaped = false
                    c == '\\'.code -> escaped = true
                    c == '"'.code -> inString = false
                }
            } else {
                when (c) {
                    '"'.code -> inString = true
                    '{'.code -> depth++
                    '}'.code -> depth--
                }
            }
            if (depth == 0) {
                val frame = try {
                    StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                        .decode(ByteBuffer.wrap(bytes, 0, size)).toString()
                } catch (_: java.nio.charset.CharacterCodingException) {
                    null
                }
                clear()
                if (frame != null) onFrame(frame)
            }
        }
    }
}
