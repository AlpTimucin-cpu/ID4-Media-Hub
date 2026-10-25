package me.aap.fermata.ev;

import java.time.LocalDate;

/** Offline regression: honest charge-stop and market-price scenarios. */
public final class Id4TripChargePlanTest {
    private static void check(boolean ok,String msg){
        if(!ok)throw new AssertionError(msg);
    }
    public static void main(String[] args){
        LocalDate today=LocalDate.parse("2026-10-25");
        var unknown=Id4TripChargePlan.estimate(525,Double.NaN,"",today);
        check(!unknown.measuredSoc&&unknown.startSoc==80,"unknown OBD is 80% scenario");
        check(unknown.minStops==2&&unknown.maxStops==3,
            "525km / 52kWh must require 2-3 model charging stops at 80% start");
        check(unknown.priceEstimateAvailable && !unknown.operatorRate,
            "market reference may be used only as indicative scenario");
        check(unknown.costMinTry>380&&unknown.costMaxTry>1500,
            "52+ kWh purchased and model market cost on 525km");
        var measured=Id4TripChargePlan.estimate(525,40,"",today);
        check(measured.measuredSoc&&measured.startSoc==40,"fresh SoC takes precedence");
        check(measured.minStops==3&&measured.maxStops==4,
            "lower real starting SoC should increase model stop count");
        var quick=Id4TripChargePlan.estimate(12,80,"",today);
        check(quick.minStops==0&&quick.maxStops==0&&
              quick.costMinTry==0&&quick.costMaxTry==0,
              "short trip has no estimated public charge purchase");
        var trugo=Id4TripChargePlan.estimate(525,80,"TRUGO",today);
        check(trugo.operatorRate&&trugo.priceEstimateAvailable&&
              trugo.priceBasis.contains("Trugo"),
              "published known operator should supersede market band");
        var expired=Id4TripChargePlan.estimate(525,Double.NaN,"",LocalDate.parse("2026-12-01"));
        check(!expired.priceEstimateAvailable,
              "dated market band must expire instead of impersonating real prices");
        boolean rejected=false;
        try{Id4TripChargePlan.estimate(-5,80,"",today);}
        catch(IllegalArgumentException e){rejected=true;}
        check(rejected,"invalid road rejected");
        System.out.println("PASS: charge count, measured/unknown SoC, dated market price and operator tariff");
    }
}
