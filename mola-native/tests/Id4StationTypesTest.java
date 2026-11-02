package me.aap.fermata.ev;

/** EPDK TYPE2/CCS/CHAdeMO and Turkish search; no platform dependency. */
public final class Id4StationTypesTest {
    private static void yes(boolean value) { if(!value) throw new AssertionError("Expected true"); }
    private static void no(boolean value) { if(value) throw new AssertionError("Expected false"); }
    public static void main(String[] args) {
        yes(Id4StationTypes.ac("Type 2", "AC"));
        yes(Id4StationTypes.ac("TYPE2", ""));
        yes(Id4StationTypes.dc("CCS 2", "DC"));
        yes(Id4StationTypes.dc("CHAdeMO", "DC"));
        yes(Id4StationTypes.dc("Combo Type 2","DC"));
        no(Id4StationTypes.ac("Combo Type 2", "DC"));
        no(Id4StationTypes.dc("Type 2","AC"));
        no(Id4StationTypes.ac("",""));
        no(Id4StationTypes.dc("",""));
        yes(Id4StationTypes.searchable("NİĞDE").equals("nigde"));
        yes(Id4StationTypes.searchable("Çeşme Şarj İstasyonu").equals("cesme sarj istasyonu"));
        System.out.println("PASS EPDK AC/DC classifier, CCS precedence and Turkish search");
    }
}
