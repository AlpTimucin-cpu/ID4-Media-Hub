package tr.id4.mediahub.nativecockpit.data.obd

import org.junit.Assert.*
import org.junit.Test
import tr.id4.mediahub.nativecockpit.domain.model.MebResponseStatus

class MebUdsParserTest {
    @Test fun bmsSocCandidateOnlyFromStrictHeader() {
        val sample = MebUdsParser.parse("22028C","22028C\r17FE007B 04 62 02 8C 55 AA AA AA\r>",100)
        assertEquals(MebResponseStatus.VALID_RAW,sample.status)
        assertEquals(34.0,sample.candidateBmsSocPercent!!,0.001)
        assertEquals("62028C55",sample.payloadHex)
    }
    @Test fun maxTempDoesNotPretendToBeSoH() {
        val sample = MebUdsParser.parse("221E0E","17FE007B07621E0E04680004\r>",100)
        assertEquals(MebResponseStatus.VALID_RAW,sample.status)
        assertEquals(17.625,sample.candidateMaxBatteryTempC!!,0.001)
        assertNull(sample.candidateBmsSocPercent)
    }
    @Test fun currentMultiframeIsCompleteButNotMappedToAmps() {
        val sample=MebUdsParser.parse("221E3D",
            "17FE007B1008621E3D000249\r17FE007B21963AAAAAAAAAAA\r>",100)
        assertEquals(MebResponseStatus.VALID_RAW,sample.status)
        assertEquals("621E3D000249963A",sample.payloadHex)
        assertNull(sample.candidateBmsSocPercent)
        assertNull(sample.candidateMaxBatteryTempC)
    }
    @Test fun negativeAndForeignFramesFailClosed() {
        assertEquals(MebResponseStatus.NEGATIVE,
            MebUdsParser.parse("22028C","17FE007B 03 7F 22 31 AA AA AA AA>",100).status)
        assertEquals(MebResponseStatus.WRONG_ECU,
            MebUdsParser.parse("22028C","17FE0076 04 62 02 8C 55 AA AA AA>",100).status)
        assertEquals(MebResponseStatus.WRONG_IDENTIFIER,
            MebUdsParser.parse("22028C","17FE007B 04 62 1E 0E 55 AA AA AA>",100).status)
    }
    @Test fun truncatedAndErrorRepliesFailClosed() {
        assertEquals(MebResponseStatus.INCOMPLETE,
            MebUdsParser.parse("221E3D","17FE007B1008621E3D000249>",100).status)
        assertEquals(MebResponseStatus.NO_DATA,
            MebUdsParser.parse("22028C","NO DATA>",100).status)
        assertEquals(MebResponseStatus.ADAPTER_ERROR,
            MebUdsParser.parse("22028C","CAN ERROR>",100).status)
        assertEquals(MebResponseStatus.MALFORMED,
            MebUdsParser.parse("22028C","17FE007B 04 62 02 8C FF 00 00 00>",100).status)
    }
    @Test(expected=IllegalArgumentException::class)
    fun disallowArbitraryUdsAndSensitiveCommands() {
        MebUdsParser.parse("22F190","17FE007B 04 62 F1 90 00 00 00 00>",100)
    }
}
