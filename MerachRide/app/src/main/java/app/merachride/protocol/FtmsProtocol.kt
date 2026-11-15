package app.merachride.protocol

/**
 * FTMS Control Point (0x2AD9) commands and responses.
 *
 * Verified by hand on the S29 Aura with nRF Connect (2026-11-14):
 *   00     -> 80 00 01  (Request Control, success)
 *   07     -> 80 07 01  (Start/Resume, success)
 *   04 50  -> bike shows level 8
 *   04 A0  -> level 16,  04 0A -> level 1
 * i.e. Set Target Resistance takes a uint8 in 0.1 units: level N is sent as N * 10.
 */
object FtmsProtocol {
    const val MIN_LEVEL = 1
    const val MAX_LEVEL = 16

    const val OP_REQUEST_CONTROL = 0x00
    const val OP_RESET = 0x01
    const val OP_SET_TARGET_RESISTANCE = 0x04
    const val OP_START_RESUME = 0x07
    const val OP_STOP_PAUSE = 0x08
    const val OP_RESPONSE = 0x80

    fun requestControl(): ByteArray = byteArrayOf(OP_REQUEST_CONTROL.toByte())

    fun startOrResume(): ByteArray = byteArrayOf(OP_START_RESUME.toByte())

    fun stop(): ByteArray = byteArrayOf(OP_STOP_PAUSE.toByte(), 0x01)

    fun pause(): ByteArray = byteArrayOf(OP_STOP_PAUSE.toByte(), 0x02)

    fun setResistanceLevel(level: Int): ByteArray {
        require(level in MIN_LEVEL..MAX_LEVEL) { "level $level outside $MIN_LEVEL..$MAX_LEVEL" }
        // 160 doesn't fit a signed Kotlin Byte, but toByte() keeps the bit pattern (0xA0), which is what we want.
        return byteArrayOf(OP_SET_TARGET_RESISTANCE.toByte(), (level * 10).toByte())
    }

    enum class ResultCode {
        SUCCESS, NOT_SUPPORTED, INVALID_PARAMETER, OPERATION_FAILED, CONTROL_NOT_PERMITTED,
        /** Response code we don't recognise. */
        UNKNOWN,
        /** App-side: the GATT write itself failed. */
        WRITE_FAILED,
        /** App-side: no indication arrived in time. */
        TIMEOUT;

        companion object {
            fun fromByte(code: Int): ResultCode = when (code) {
                0x01 -> SUCCESS
                0x02 -> NOT_SUPPORTED
                0x03 -> INVALID_PARAMETER
                0x04 -> OPERATION_FAILED
                0x05 -> CONTROL_NOT_PERMITTED
                else -> UNKNOWN
            }
        }
    }

    data class ControlPointResponse(val requestOpCode: Int, val result: ResultCode)

    /** Parses an indication from 0x2AD9: `80 <request op> <result>`. Returns null if it isn't a response. */
    fun parseControlPointResponse(bytes: ByteArray): ControlPointResponse? {
        if (bytes.size < 3) return null
        if ((bytes[0].toInt() and 0xFF) != OP_RESPONSE) return null
        return ControlPointResponse(
            requestOpCode = bytes[1].toInt() and 0xFF,
            result = ResultCode.fromByte(bytes[2].toInt() and 0xFF),
        )
    }

    /** Human-readable summary of a Fitness Machine Status (0x2ADA) notification, or null to ignore it. */
    fun describeStatus(bytes: ByteArray): String? {
        if (bytes.isEmpty()) return null
        return when (bytes[0].toInt() and 0xFF) {
            0x01 -> "Bike reset"
            0x02 -> "Stopped/paused on the bike"
            0x04 -> "Started/resumed on the bike"
            0x07 -> if (bytes.size >= 2) "Bike target resistance now ${(bytes[1].toInt() and 0xFF) / 10}" else null
            0xFF -> "Control permission lost"
            else -> null
        }
    }

    const val STATUS_CONTROL_LOST = 0xFF
}
