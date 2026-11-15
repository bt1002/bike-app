package app.merachride.protocol

import java.util.UUID

/** Bluetooth SIG Fitness Machine Service (FTMS) UUIDs, as exposed by the Merach S29 Aura. */
object FtmsUuids {
    private fun uuid16(id: Int): UUID =
        UUID.fromString("0000%04x-0000-1000-8000-00805f9b34fb".format(id))

    val SERVICE: UUID = uuid16(0x1826)
    val FEATURE: UUID = uuid16(0x2ACC)
    val INDOOR_BIKE_DATA: UUID = uuid16(0x2AD2)
    val TRAINING_STATUS: UUID = uuid16(0x2AD3)
    val RESISTANCE_RANGE: UUID = uuid16(0x2AD6)
    val POWER_RANGE: UUID = uuid16(0x2AD8)
    val CONTROL_POINT: UUID = uuid16(0x2AD9)
    val STATUS: UUID = uuid16(0x2ADA)

    /** Client Characteristic Configuration Descriptor (turns notify/indicate on). */
    val CCCD: UUID = uuid16(0x2902)
}
