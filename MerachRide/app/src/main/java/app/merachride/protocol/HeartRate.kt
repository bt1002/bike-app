package app.merachride.protocol

import java.util.UUID

/** Bluetooth SIG Heart Rate Service, used by chest straps and by watch apps that broadcast heart rate. */
object HeartRateUuids {
    val SERVICE: UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
    /** Heart Rate Measurement (notify). */
    val MEASUREMENT: UUID = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
}

/**
 * Parser for Heart Rate Measurement (0x2A37) notifications.
 *
 * Byte 0 is a flags field. Bit 0 says whether the heart rate is a uint8 (0) or a little-endian
 * uint16 (1); it follows the flags. The other fields (energy, RR intervals) aren't needed here.
 */
object HeartRateParser {
    /** Beats per minute, or null if the packet is too short or reports 0 (no reading). */
    fun parse(bytes: ByteArray): Int? {
        if (bytes.isEmpty()) return null
        val flags = bytes[0].toInt() and 0xFF
        val bpm = if ((flags and 0x01) != 0) {
            if (bytes.size < 3) return null
            (bytes[1].toInt() and 0xFF) or ((bytes[2].toInt() and 0xFF) shl 8)
        } else {
            if (bytes.size < 2) return null
            bytes[1].toInt() and 0xFF
        }
        return bpm.takeIf { it > 0 }
    }
}
