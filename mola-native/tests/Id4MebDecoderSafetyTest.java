import me.aap.fermata.ev.Id4MebDecoder;

/** Pure-Java regression; never substitute canned MEB values for vehicle validation. */
public final class Id4MebDecoderSafetyTest {
    public static void main(String[] args){
        if(Id4MebDecoder.isVehicleProfileValidated())
            throw new AssertionError("Vehicle calibration must not be enabled without VIN/Car Scanner verification");
        String positive="7E8 04 62 02 8C 96 >";
        if(!Id4MebDecoder.hasPositiveReply("028C",positive))
            throw new AssertionError("DID acknowledgement must remain inspectable");
        if(Id4MebDecoder.hasPositiveReply("028C","NO DATA >"))
            throw new AssertionError("NO DATA is not a positive reply");
        if(Id4MebDecoder.hasPositiveReply("028C","7F 22 31 >"))
            throw new AssertionError("UDS negative is not a positive reply");
        if(Id4MebDecoder.bmsSoc(positive)!=null)
            throw new AssertionError("Unvalidated SoC must never enter trip planner");
        if(Id4MebDecoder.chargeModeCode("62 74 48 04")!=null)
            throw new AssertionError("Unvalidated charge mode must not create sessions");
        for(Id4MebDecoder.Sensor sensor:Id4MebDecoder.SENSORS){
            if(Id4MebDecoder.decode(sensor,"62 "+sensor.did+" FF FF FF FF")!=null)
                throw new AssertionError("Unvalidated telemetry shown: "+sensor.did);
        }
        System.out.println("PASS: ELM327 raw handshake is diagnostic only; unvalidated values blocked");
    }
}
