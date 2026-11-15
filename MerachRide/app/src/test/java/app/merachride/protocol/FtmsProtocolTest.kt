package app.merachride.protocol

import app.merachride.protocol.FtmsProtocol.ResultCode
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Byte values here are the ones verified by hand on the S29 Aura in nRF Connect. */
class FtmsProtocolTest {

    private fun hex(vararg b: Int) = ByteArray(b.size) { b[it].toByte() }

    @Test
    fun handshakeCommands() {
        assertArrayEquals(hex(0x00), FtmsProtocol.requestControl())
        assertArrayEquals(hex(0x07), FtmsProtocol.startOrResume())
    }

    @Test
    fun resistanceLevelIsSentInTenths() {
        assertArrayEquals(hex(0x04, 0x0A), FtmsProtocol.setResistanceLevel(1))
        assertArrayEquals(hex(0x04, 0x50), FtmsProtocol.setResistanceLevel(8))
        assertArrayEquals(hex(0x04, 0xA0), FtmsProtocol.setResistanceLevel(16))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsLevelAboveMax() {
        FtmsProtocol.setResistanceLevel(17)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsLevelBelowMin() {
        FtmsProtocol.setResistanceLevel(0)
    }

    @Test
    fun parsesSuccessResponse() {
        val r = FtmsProtocol.parseControlPointResponse(hex(0x80, 0x00, 0x01))
        assertEquals(FtmsProtocol.ControlPointResponse(0x00, ResultCode.SUCCESS), r)
    }

    @Test
    fun parsesErrorResponses() {
        assertEquals(ResultCode.INVALID_PARAMETER, FtmsProtocol.parseControlPointResponse(hex(0x80, 0x04, 0x03))?.result)
        assertEquals(ResultCode.CONTROL_NOT_PERMITTED, FtmsProtocol.parseControlPointResponse(hex(0x80, 0x04, 0x05))?.result)
    }

    @Test
    fun ignoresNonResponses() {
        assertNull(FtmsProtocol.parseControlPointResponse(hex(0x04, 0x50)))
        assertNull(FtmsProtocol.parseControlPointResponse(hex(0x80, 0x00)))
    }
}
