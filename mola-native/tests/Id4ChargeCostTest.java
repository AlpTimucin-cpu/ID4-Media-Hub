package me.aap.fermata.ev;
import java.time.LocalDate;

/** No network or car data required; prices intentionally expire. */
public final class Id4ChargeCostTest {
    private static int n;
    static void ok(boolean yes,String message){
        n++;if(!yes)throw new AssertionError(message);
    }
    public static void main(String[] args){
        LocalDate day=LocalDate.parse("2026-10-22");
        var trugo=Id4ChargeCost.estimate(400,64,"TRUGO Akıllı Şarj",day);
        ok(trugo.socKnown&&trugo.needsStop,"measured SoC needed for model");
        ok(trugo.tariffKnown,"official Trugo network price should map");
        ok(trugo.purchasedLowKwh>38&&trugo.purchasedLowKwh<39,"lower purchased kWh");
        ok(trugo.purchasedHighKwh>70&&trugo.purchasedHighKwh<71,"upper purchased kWh");
        ok(trugo.minTry>500&&trugo.maxTry>1000,"auto cost uses operator tariff");
        var zes=Id4ChargeCost.estimate(400,64,"ZES Dijital",day);
        ok(zes.tariffKnown&&zes.minTry<zes.maxTry,"ZES has two official DC rates");
        var missing=Id4ChargeCost.estimate(400,Double.NaN,"Trugo",day);
        ok(!missing.socKnown&&!missing.tariffKnown&&Double.isNaN(missing.minTry),
            "unknown SoC must never produce fake TL amount");
        var other=Id4ChargeCost.estimate(400,64,"Operator bilinmiyor",day);
        ok(other.socKnown&&!other.tariffKnown&&Double.isNaN(other.maxTry),
            "unknown operator has estimated kWh but not fabricated TRY");
        var expired=Id4ChargeCost.estimate(400,64,"ZES",day.plusDays(20));
        ok(!expired.tariffKnown,"outdated tariff must expire automatically");
        var shortRoad=Id4ChargeCost.estimate(20,90,"Trugo",day);
        ok(!shortRoad.needsStop&&!shortRoad.tariffKnown,
            "no purchased DC energy assumed for feasible short road");
        boolean rejected=false;
        try{Id4ChargeCost.estimate(-1,30,"ZES",day);}
        catch(IllegalArgumentException ok){rejected=true;}
        ok(rejected,"reject nonsensical routing distance");
        System.out.println("PASS "+n+" automatic charging cost checks");
    }
}
