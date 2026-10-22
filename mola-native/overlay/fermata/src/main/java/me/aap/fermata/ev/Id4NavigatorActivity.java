package me.aap.fermata.ev;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.ActivityInfo;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.webkit.WebViewAssetLoader;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Locale;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Navigation-first, same-app route preview for ID.4.
 * Beta routing via OSRM demo; NOT a validated live-traffic, offline or safety-grade navigator.
 * The map uses OSM tiles. Never launches ABRP/Maps/other apps.
 */
public final class Id4NavigatorActivity extends Activity implements LocationListener {
    private static final String PAGE="https://appassets.androidplatform.net/assets/id4/navigator.html";
    private final ExecutorService jobs=Executors.newSingleThreadExecutor();
    // Station requests must not hold back route or weather processing.
    private final ExecutorService stationJobs=Executors.newSingleThreadExecutor();
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final ArrayList<Step> steps=new ArrayList<>();
    private WebView web;
    private Id4LiveTelemetry telemetry;
    private Id4StationCatalog stationCatalog;
    private volatile int stationRequestId;
    private Id4LiveTelemetry.Data latestObd=new Id4LiveTelemetry.Data();
    private LocationManager locationManager;
    private TextToSpeech tts;
    private Location position;
    private boolean running, voice=true, destroyed, gpsTrackingRequested, resumeRouteAfterPause;
    private volatile String activeRouteProvider="OSRM demo";
    private int generation, nextStep;
    private volatile JSONArray cachedAlternatives;
    private volatile boolean cachedRouteGps, cachedHasStop;
    private long lastSpeech;
    private static final class Step {
        final double lat, lon;
        final String instruction;
        Step(double lat,double lon,String instruction){this.lat=lat;this.lon=lon;this.instruction=instruction;}
    }

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        // Enter the requested car-style cockpit at launch, not the tall legacy sheet.
        // Store a manual portrait choice so that a configuration recreation cannot
        // silently lock the user back into landscape.
        boolean wide=getSharedPreferences("id4_presentation",MODE_PRIVATE)
            .getBoolean("landscape",true);
        setRequestedOrientation(wide?ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE:
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        getWindow().setStatusBarColor(0xff061e2b);
        getWindow().setNavigationBarColor(0xff061e2b);
        locationManager=(LocationManager)getSystemService(LOCATION_SERVICE);
        stationCatalog=new Id4StationCatalog(this);
        telemetry=new Id4LiveTelemetry(this,data->{
            latestObd=data;
            send("molaTelemetry",obdPacket(data));
        });
        tts=new TextToSpeech(this,status->{
            if(status==TextToSpeech.SUCCESS)tts.setLanguage(new Locale("tr","TR"));
        });
        WebViewAssetLoader loader=new WebViewAssetLoader.Builder()
            .addPathHandler("/assets/",new WebViewAssetLoader.AssetsPathHandler(this)).build();
        web=new WebView(this);
        web.setBackgroundColor(0xffedf2ed);
        setContentView(web,new ViewGroup.LayoutParams(-1,-1));
        WebSettings st=web.getSettings();
        st.setJavaScriptEnabled(true);
        // OSMF tile policy: identify the application instead of sending only the
        // generic embedded browser UA. Leave the Android WebView capabilities
        // intact; do not spoof an unrelated application.
        String standardWebViewAgent=st.getUserAgentString();
        st.setUserAgentString(standardWebViewAgent+
            " ID4YolArkadasim/0.5 (+https://github.com/AlpTimucin-cpu/ID4-Media-Hub)");
        st.setCacheMode(WebSettings.LOAD_DEFAULT); // respect server HTTP caching

        st.setDomStorageEnabled(false);
        st.setAllowFileAccess(false);
        st.setAllowContentAccess(false);
        st.setJavaScriptCanOpenWindowsAutomatically(false);
        web.addJavascriptInterface(new Bridge(),"MolaNative");
        web.setWebViewClient(new WebViewClient(){
            @Override public WebResourceResponse shouldInterceptRequest(WebView v,WebResourceRequest r){
                return loader.shouldInterceptRequest(r.getUrl());
            }
            @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){
                return !PAGE.equals(r.getUrl().toString());
            }
            @Override public void onPageFinished(WebView v,String url){
                if(!PAGE.equals(url) || destroyed)return;
                send("molaTelemetry",obdPacket(latestObd));
                Intent source=getIntent();
                if(source==null || !source.hasExtra("toLat") || !source.hasExtra("toLon"))return;
                double lat=source.getDoubleExtra("toLat",Double.NaN);
                double lon=source.getDoubleExtra("toLon",Double.NaN);
                if(!valid(lat,lon))return;
                JSONObject station=new JSONObject();
                try{
                    station.put("lat",lat);station.put("lon",lon);
                    station.put("label",source.getStringExtra("toName"));
                    send("molaDestination",station);
                }catch(Exception ignored){}
            }
        });
        web.loadUrl(PAGE);
    }
    private void send(String function, JSONObject data){
        ui.post(()->{if(!destroyed && web!=null)
            web.evaluateJavascript("window."+function+"("+data.toString()+")",null);
        });
    }
    private JSONObject record(String status,String detail) {
        JSONObject j=new JSONObject();
        try{j.put("status",status);j.put("detail",detail);}catch(Exception ignored){}
        return j;
    }
    private final class Bridge {
        @JavascriptInterface public void location() {ui.post(()->requestGps());}
        @JavascriptInterface public void landscape(boolean enabled){
            ui.post(()->{
                if(destroyed || isFinishing())return;
                getSharedPreferences("id4_presentation",MODE_PRIVATE).edit()
                    .putBoolean("landscape",enabled).apply();
                setRequestedOrientation(enabled?ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE:
                    ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                send("molaStatus",record("display",
                    enabled?"Yatay tam ekran açılıyor.":"Ekran yönü serbest bırakıldı."));
            });
        }
        @JavascriptInterface public void selectObd() {ui.post(()->selectObdAdapter());}
        @JavascriptInterface public void visibleStations(
              double south,double west,double north,double east){
            requestVisibleStations(south,west,north,east);
        }
        @JavascriptInterface public void saveStation(String title,String operator){
            if(title!=null && title.length()<=160 && operator!=null && operator.length()<=120)
                Id4EnergyData.saveStation(Id4NavigatorActivity.this,title,operator);
        }
        @JavascriptInterface public void back() {ui.post(()->finish());}
        @JavascriptInterface public void stations() {
            ui.post(()->startActivity(new Intent(Id4NavigatorActivity.this,Id4EvActivity.class)));
        }
        @JavascriptInterface public void sensors() {
            ui.post(()->startActivity(new Intent(Id4NavigatorActivity.this,Id4ObdActivity.class)));
        }
        @JavascriptInterface public void voice(boolean on) {
            voice=on;if(!on && tts!=null)tts.stop();
        }
        @JavascriptInterface public void stop() {
            running=false;generation++;stationRequestId++;
            steps.clear();nextStep=0;
        }
        @JavascriptInterface public void suggest(String field,String query,int requestId){
            suggestPlaces(field,query,requestId);
        }
        @JavascriptInterface public void route(double fromLat,double fromLon,
              double toLat,double toLon,boolean useGps){
            plan(fromLat,fromLon,toLat,toLon,useGps);
        }
        @JavascriptInterface public void chooseAlternative(int index){
            ui.post(()->{
                JSONArray available=cachedAlternatives;
                if(available==null||index<0||index>=available.length()){
                    send("molaStatus",record("route","Alternatif rota güncel değil."));
                    return;
                }
                int selectionGeneration=++generation;
                stationRequestId++;
                final boolean gps=cachedRouteGps,stop=cachedHasStop;
                jobs.execute(()->{
                    try{renderRouteOption(available,index,gps,stop,selectionGeneration);}
                    catch(Exception e){
                        send("molaStatus",record("error",
                            "Alternatif rota açılamadı: "+e.getClass().getSimpleName()));
                    }
                });
            });
        }
        @JavascriptInterface public void routeVia(double fromLat,double fromLon,
              double viaLat,double viaLon,double toLat,double toLon,boolean useGps){
            if(!valid(viaLat,viaLon)){
                send("molaStatus",record("error","Şarj molası konumu geçersiz."));return;
            }
            planWithStop(fromLat,fromLon,toLat,toLon,useGps,viaLat,viaLon);
        }
    }
    /** Only known, freshly read ECU measurements reach the map HUD. */
    private JSONObject obdPacket(Id4LiveTelemetry.Data d) {
        JSONObject p=new JSONObject();
        Id4LiveTelemetry.Data shown=d.displayAt(System.currentTimeMillis());
        boolean fresh=d.connected && d.updatedAt>0 &&
            System.currentTimeMillis()-d.updatedAt<25000;
        try{
            p.put("fresh",fresh);
            p.put("soc",fresh?shown.soc:"—");
            p.put("voltage",fresh?shown.voltage:"—");
            p.put("temperature",fresh?shown.batteryTemp:"—");
            p.put("chargeMode",fresh?shown.chargeMode:"—");
            p.put("status",d.status);
        }catch(Exception ignored){}
        return p;
    }
    /** Explicit first selection; only paired adapters, no background scan. */
    @SuppressWarnings("deprecation")
    private void selectObdAdapter() {
        if(Build.VERSION.SDK_INT>=31 &&
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT},72);return;
        }
        try{
            BluetoothAdapter adapter=BluetoothAdapter.getDefaultAdapter();
            if(adapter==null || !adapter.isEnabled()){
                send("molaTelemetry",obdStatus("Önce Bluetooth'u açıp ELM327'yi eşleştir."));return;
            }
            Set<BluetoothDevice> paired=adapter.getBondedDevices();
            if(paired==null || paired.isEmpty()){
                send("molaTelemetry",obdStatus("Eşleştirilmiş OBD cihazı bulunamadı."));return;
            }
            List<BluetoothDevice> devices=new ArrayList<>(paired);
            String[] labels=new String[devices.size()];
            for(int i=0;i<devices.size();i++){
                String name=devices.get(i).getName();
                labels[i]=name==null?"Eşleştirilmiş cihaz "+(i+1):name;
            }
            new AlertDialog.Builder(this)
                .setTitle("OBD adaptörünü seç")
                .setItems(labels,(dialog,index)->{
                    BluetoothDevice d=devices.get(index);
                    getSharedPreferences("id4_obd_pairing_v1",MODE_PRIVATE).edit()
                        .putString("paired_mac",d.getAddress()).apply();
                    telemetry.start(d);
                })
                .setNegativeButton("Vazgeç",null).show();
        }catch(SecurityException e){
            send("molaTelemetry",obdStatus("Bluetooth erişim izni gerekli."));
        }
    }
    private JSONObject obdStatus(String status){
        JSONObject j=obdPacket(new Id4LiveTelemetry.Data());
        try{j.put("status",status);}catch(Exception ignored){}
        return j;
    }
    @SuppressWarnings("deprecation")
    private void autoConnectObd(){
        if(Build.VERSION.SDK_INT>=31 &&
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED)return;
        String address=getSharedPreferences("id4_obd_pairing_v1",MODE_PRIVATE)
            .getString("paired_mac",null);
        if(address==null)return;
        try{
            BluetoothAdapter adapter=BluetoothAdapter.getDefaultAdapter();
            if(adapter==null || !adapter.isEnabled())return;
            for(BluetoothDevice d:adapter.getBondedDevices()){
                if(address.equals(d.getAddress())){telemetry.start(d);return;}
            }
        }catch(SecurityException ignored){}
    }

    private void requestVisibleStations(double south,double west,double north,double east){
        final int request=++stationRequestId;
        stationJobs.execute(()->{
            try{
                JSONObject packet=stationCatalog.inBounds(south,west,north,east);
                if(!destroyed && request==stationRequestId)send("molaStations",packet);
            }catch(Exception e){
                if(!destroyed && request==stationRequestId)
                    send("molaStationError",record("stations",
                        "EPDK istasyon verisi alınamadı: "+e.getClass().getSimpleName()));
            }
        });
    }
    private void loadRouteStations(JSONObject route,int routeGeneration,int stationRequest){
        try{
            JSONArray coords=route.getJSONObject("geometry").getJSONArray("coordinates");
            ArrayList<Id4StationCatalog.Point> points=new ArrayList<>();
            int stride=Math.max(1,coords.length()/2400);
            for(int i=0;i<coords.length();i+=stride){
                JSONArray coord=coords.getJSONArray(i);
                points.add(new Id4StationCatalog.Point(coord.getDouble(1),coord.getDouble(0)));
            }
            if(coords.length()>1){
                JSONArray finalPt=coords.getJSONArray(coords.length()-1);
                points.add(new Id4StationCatalog.Point(finalPt.getDouble(1),finalPt.getDouble(0)));
            }
            Id4EnergyData.Snapshot snapshot=Id4EnergyData.load(this,System.currentTimeMillis());
            double soc=snapshot.hasSoc()?snapshot.soc:Double.NaN;
            double roadKm=route.getDouble("distance")/1000.0;
            JSONObject packet=stationCatalog.alongRoute(points,roadKm,soc);
            if(route.getJSONArray("legs").length()>1){
                packet.remove("suggested"); // already travelling via a stop; avoid second suggestion
                packet.put("stopIncluded",true);
            }
            if(!destroyed && generation==routeGeneration && stationRequestId==stationRequest)
                send("molaStations",packet);
        }catch(Exception e){
            if(!destroyed && generation==routeGeneration && stationRequestId==stationRequest)
                send("molaStationError",record("stations",
                    "Rota çevresi EPDK verisi yüklenemedi: "+e.getClass().getSimpleName()));
        }
    }
    private void requestGps() {
        if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION},41);return;
        }
        try{
            gpsTrackingRequested=true; // User explicitly requested GPS at least once.
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,1500,3,this);
            Location loc=locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if(loc!=null && System.currentTimeMillis()-loc.getTime()<90000)onLocationChanged(loc);
            send("molaStatus",record("gps","Konum izni verildi. GPS aranıyor..."));
        }catch(Exception e){send("molaStatus",record("error","GPS açılamadı"));}
    }
    @Override public void onRequestPermissionsResult(int code,String[] permissions,int[] results){
        super.onRequestPermissionsResult(code,permissions,results);
        if(code==72){
            if(results.length>0 && results[0]==PackageManager.PERMISSION_GRANTED)selectObdAdapter();
            else send("molaTelemetry",obdStatus("Bluetooth izni verilmedi."));
            return;
        }
        if(code==41 && results.length>0 && results[0]==PackageManager.PERMISSION_GRANTED)requestGps();
        else if(code==41)send("molaStatus",record("error","Konum izni olmadan canlı yönlendirme yapılamaz"));
    }
    @Override public void onLocationChanged(Location location) {
        if(destroyed)return;
        position=location;
        JSONObject j=new JSONObject();
        try{j.put("lat",location.getLatitude());j.put("lon",location.getLongitude());
            j.put("accuracy",location.getAccuracy());
            j.put("timeMs",location.getTime());}catch(Exception ignored){}
        send("molaPosition",j);
        long fixAge=System.currentTimeMillis()-location.getTime();
        if(!running || location.getAccuracy()>60 || fixAge<0 || fixAge>30000 ||
            nextStep>=steps.size())return;
        Step step=steps.get(nextStep);
        float[] meters=new float[1];
        Location.distanceBetween(location.getLatitude(),location.getLongitude(),step.lat,step.lon,meters);
        JSONObject maneuver=new JSONObject();
        try{
            maneuver.put("instruction",step.instruction);
            maneuver.put("straightLineMeters",Math.round(meters[0]));
            maneuver.put("fixTimeMs",location.getTime());
            maneuver.put("accuracy",location.getAccuracy());
            send("molaManeuver",maneuver);
        }catch(Exception ignored){}
        if(meters[0]<90 && System.currentTimeMillis()-lastSpeech>6000){
            announce(step.instruction);lastSpeech=System.currentTimeMillis();nextStep++;
        }
    }
    private void announce(String instruction){
        if(voice && tts!=null && !instruction.isEmpty())
            ui.post(()->tts.speak(instruction,TextToSpeech.QUEUE_FLUSH,null,"mola-direction"));
    }
    // Both departure and arrival use the same autocomplete lookup.
    // Queries only leave the phone after the user types 3+ characters.
    private void suggestPlaces(String field,String raw,int requestId){
        if(!"from".equals(field) && !"to".equals(field))return;
        final String query=raw==null?"":raw.trim();
        if(query.length()<3||query.length()>100)return;
        jobs.execute(()->{
            JSONObject packet=new JSONObject();
            try{
                packet.put("field",field);
                packet.put("requestId",requestId);
                // The public Photon demo usually indexes English/local names, not 'tr'.
                // Invalid lang=tr previously caused 400, reported as 'no internet'.
                String u="https://photon.komoot.io/api/?limit=7&lang=en&q="+
                    URLEncoder.encode(query,"UTF-8");
                JSONArray found=new JSONObject(fetch(u,500000)).getJSONArray("features");
                JSONArray items=new JSONArray();
                for(int i=0;i<found.length() && items.length()<7;i++){
                    JSONObject feature=found.optJSONObject(i);
                    if(feature==null)continue;
                    JSONObject geom=feature.optJSONObject("geometry");
                    if(geom==null)continue;
                    JSONArray coords=geom.optJSONArray("coordinates");
                    if(coords==null || coords.length()<2)continue;
                    double lon=coords.optDouble(0,Double.NaN);
                    double lat=coords.optDouble(1,Double.NaN);
                    if(!valid(lat,lon))continue;
                    JSONObject props=feature.optJSONObject("properties");
                    if(props==null)continue;
                    String name=props.optString("name","");
                    String city=props.optString("city","");
                    String district=props.optString("district","");
                    String state=props.optString("state","");
                    String country=props.optString("country","");
                    StringBuilder label=new StringBuilder();
                    for(String part:new String[]{name,district,city,state,country}){
                        if(part.isEmpty())continue;
                        if(label.toString().contains(part))continue;
                        if(label.length()>0)label.append(", ");
                        label.append(part);
                    }
                    if(label.length()==0)continue;
                    JSONObject item=new JSONObject();
                    item.put("label",label.toString());
                    item.put("lat",lat);item.put("lon",lon);
                    items.put(item);
                }
                packet.put("items",items);
                if(items.length()==0)packet.put("error","Türkiye içinde eşleşen konum bulunamadı.");
            }catch(Exception e){
                try{
                    packet.put("items",new JSONArray());
                    packet.put("error","Photon ayrıntılı adres servisi şu an yanıt vermiyor; çevrimdışı iller kullanılabilir.");
                }catch(Exception ignored){}
            }
            send("molaSuggestions",packet);
        });
    }
    private void plan(double fromLat,double fromLon,
                      double toLat,double toLon,boolean useGps) {
        planWithStop(fromLat,fromLon,toLat,toLon,useGps,Double.NaN,Double.NaN);
    }
    private void planWithStop(double fromLat,double fromLon,
                      double toLat,double toLon,boolean useGps,
                      double stopLat,double stopLon) {
        if(!valid(fromLat,fromLon)||!valid(toLat,toLon)){
            send("molaStatus",record("error","Başlangıç ve varış için listeden konum seç."));return;
        }
        boolean verifiedGps=useGps && position!=null && position.getAccuracy()<=60 &&
            System.currentTimeMillis()-position.getTime()<=30000;
        final int myGeneration=++generation;
        stationRequestId++; // Invalidate the previous map/route charger batch.
        jobs.execute(()->{
            try{
                String a=String.format(Locale.US,"%.6f,%.6f",fromLon,fromLat);
                String b=String.format(Locale.US,"%.6f,%.6f",toLon,toLat);
                boolean hasStop=valid(stopLat,stopLon);
                String via=hasStop?
                    String.format(Locale.US,"%.6f,%.6f",stopLon,stopLat):"";
                String query="/route/v1/driving/"+a+(hasStop?";"+via:"")+";"+b+
                    "?overview=full&geometries=geojson&steps=true&alternatives=true";
                JSONObject out=null;
                StringBuilder problems=new StringBuilder();
                String[] hosts={"https://router.project-osrm.org",
                                "https://routing.openstreetmap.de/routed-car"};
                for(int attempt=0;attempt<hosts.length && out==null;attempt++){
                    try{
                        JSONObject candidate=new JSONObject(fetch(hosts[attempt]+query,3000000));
                        if(!"Ok".equals(candidate.optString("code"))){
                            throw new IllegalStateException("OSRM "+candidate.optString("code"));
                        }
                        JSONArray matches=candidate.optJSONArray("routes");
                        if(matches==null || matches.length()==0)throw new IllegalStateException("Boş güzergâh");
                        out=candidate;
                        activeRouteProvider=hosts[attempt];
                    }catch(Exception e){
                        if(problems.length()>0)problems.append(" | ");
                        problems.append("Sağlayıcı ").append(attempt+1).append(": ")
                            .append(e.getClass().getSimpleName());
                    }
                }
                if(out==null)throw new IllegalStateException(problems.toString());
                JSONArray routes=out.getJSONArray("routes");
                cachedAlternatives=routes;
                cachedRouteGps=verifiedGps;
                cachedHasStop=hasStop;
                renderRouteOption(routes,0,verifiedGps,hasStop,myGeneration);
            }catch(Exception e){send("molaStatus",record("error",
                "Rota çizilemedi: "+e.getMessage()+". Ağ veya rota servisini kontrol et."));}
        });
    }
    private void renderRouteOption(JSONArray routes,int optionIndex,boolean verifiedGps,
                                   boolean hasStop,int myGeneration)throws Exception{
                JSONObject selected=routes.getJSONObject(optionIndex);
                JSONObject packet=new JSONObject();
                packet.put("coordinates",selected.getJSONObject("geometry").getJSONArray("coordinates"));
                packet.put("distanceKm",Math.round(selected.getDouble("distance")/100.0)/10.0);
                packet.put("durationMin",Math.round(selected.getDouble("duration")/60.0));
                packet.put("alternatives",routes.length());
                packet.put("routeIndex",optionIndex);
                packet.put("provider",activeRouteProvider);
                packet.put("trafficAvailable",false);
                packet.put("navigationActive",verifiedGps);
                packet.put("stopIncluded",hasStop);
                // EV profile model, not an ECU claim. No manual entry.
                // A recent measured SoC is optional; assumptions are explicit in the UI.
                Id4EnergyData.Snapshot facts=Id4EnergyData.load(this,System.currentTimeMillis());
                Id4EnergyModel.Result energy=Id4EnergyModel.estimate(
                    packet.getDouble("distanceKm"),facts.hasSoc()?facts.soc:Double.NaN);
                if(energy!=null){
                    packet.put("energyLowKwh",energy.lowRouteKwh);
                    packet.put("energyHighKwh",energy.highRouteKwh);
                    packet.put("energyProfile","ID.4 Pure profil tahmini: 16–24 kWh/100 km");
                    // On a route with a charger waypoint we do not know how
                    // many kWh will be added, so a terminal SoC would be fictitious.
                    if(energy.hasSoc && !hasStop){
                        packet.put("socMeasuredAt",facts.socAt);
                        double arrivalLow=facts.soc-
                            (100.0*energy.highRouteKwh/Id4EnergyModel.ASSUMED_USABLE_KWH);
                        double arrivalHigh=facts.soc-
                            (100.0*energy.lowRouteKwh/Id4EnergyModel.ASSUMED_USABLE_KWH);
                        // Negative SoC cannot physically arrive; do not render fake 0% arrival.
                        packet.put("chargingNeededByModel",
                            arrivalLow<Id4EnergyModel.ASSUMED_RESERVE_PERCENT);
                        if(arrivalLow>=0 && arrivalHigh>=0){
                            packet.put("arrivalSocLower",arrivalLow);
                            packet.put("arrivalSocUpper",arrivalHigh);
                        }
                    }
                }
                JSONArray legs=selected.getJSONArray("legs");
                ArrayList<Step> parsed=new ArrayList<>();
                JSONArray labels=new JSONArray();
                for(int leg=0;leg<legs.length();leg++){
                    if(leg==1 && hasStop){
                        labels.put("Şarj molasından sonra hedefe devam et");
                    }
                    JSONArray roadSteps=legs.getJSONObject(leg).getJSONArray("steps");
                    for(int i=0;i<roadSteps.length() && parsed.size()<180;i++){
                        JSONObject row=roadSteps.getJSONObject(i);
                        JSONObject m=row.getJSONObject("maneuver");
                        JSONArray coord=m.getJSONArray("location");
                        String instruction=spoken(m.optString("type"),m.optString("modifier"),
                            row.optString("name"));
                        if(!instruction.isEmpty()){
                            parsed.add(new Step(coord.getDouble(1),coord.getDouble(0),instruction));
                            labels.put(instruction);
                        }
                    }
                }
                packet.put("steps",labels);
                ui.post(()->{
                    if(myGeneration!=generation||destroyed)return;
                    steps.clear();steps.addAll(parsed);nextStep=0;
                    running=verifiedGps;
                    Id4EnergyData.saveRoute(this,
                        packet.optDouble("distanceKm",Double.NaN),
                        packet.optDouble("durationMin",Double.NaN),
                        System.currentTimeMillis());
                    send("molaRoute",packet);
                    // Weather is a separate best-effort request after the drawn route.
                    final JSONObject routeData=selected;
                    jobs.execute(()->loadRouteWeather(routeData,myGeneration));
                    // Read-only EPDK snapshot route corridor; never block route rendering.
                    final int stationTicket=++stationRequestId;
                    stationJobs.execute(()->loadRouteStations(routeData,myGeneration,stationTicket));
                    stationJobs.execute(()->loadAlternativeCards(routes,myGeneration,stationTicket,
                        optionIndex,hasStop));
                    if(verifiedGps)announce("Rota hazır. "+packet.optDouble("distanceKm")+
                        " kilometre. Deneysel sesli yönlendirme başlıyor.");
                });
    }

    /**
     * Open-Meteo 15-minute MODEL forecast, evaluated at predicted positions
     * along the route for +1,+5,+10,+15 min. Not minute-by-minute observations.
     * Separate network request so a weather outage cannot erase the route.
     */
    private void loadRouteWeather(JSONObject route,int routeGeneration){
        final int[] minutes={1,5,10,15};
        try{
            JSONArray coords=route.getJSONObject("geometry").getJSONArray("coordinates");
            if(coords.length()<2)throw new IllegalStateException("Rota geometrisi boş");
            double durationSec=route.getDouble("duration");
            double[] lengths=new double[coords.length()];
            double total=0;
            for(int i=1;i<coords.length();i++){
                JSONArray previous=coords.getJSONArray(i-1),current=coords.getJSONArray(i);
                float[] distance=new float[1];
                Location.distanceBetween(previous.getDouble(1),previous.getDouble(0),
                    current.getDouble(1),current.getDouble(0),distance);
                total+=distance[0];lengths[i]=total;
            }
            if(total<10)throw new IllegalStateException("Rota mesafesi hatalı");
            StringBuilder lat=new StringBuilder(),lon=new StringBuilder();
            for(int minute:minutes){
                double fraction=Math.min(1.0,minute*60.0/Math.max(1.0,durationSec));
                double target=total*fraction;int idx=1;
                while(idx<lengths.length-1 && lengths[idx]<target)idx++;
                JSONArray p=coords.getJSONArray(idx-1),q=coords.getJSONArray(idx);
                double ratio=(target-lengths[idx-1])/
                    Math.max(0.0001,lengths[idx]-lengths[idx-1]);
                ratio=Math.max(0,Math.min(1,ratio));
                double plat=p.getDouble(1)+(q.getDouble(1)-p.getDouble(1))*ratio;
                double plon=p.getDouble(0)+(q.getDouble(0)-p.getDouble(0))*ratio;
                if(lat.length()>0){lat.append(',');lon.append(',');}
                lat.append(String.format(Locale.US,"%.5f",plat));
                lon.append(String.format(Locale.US,"%.5f",plon));
            }
            String u="https://api.open-meteo.com/v1/forecast?latitude="+lat+
                "&longitude="+lon+
                "&minutely_15=temperature_2m,precipitation,wind_speed_10m,weather_code"+
                "&hourly=temperature_2m,precipitation,wind_speed_10m,weather_code"+
                "&forecast_minutely_15=8&forecast_hours=4"+
                "&timeformat=unixtime&timezone=GMT";
            JSONArray locations=new JSONArray(fetch(u,350000));
            if(locations.length()<4)throw new IllegalStateException("Tahmin bölgeleri eksik");
            JSONArray cards=new JSONArray();
            long now=System.currentTimeMillis()/1000L;
            for(int i=0;i<4;i++){
                JSONObject item=locations.getJSONObject(i);
                JSONObject series=item.optJSONObject("minutely_15");
                String resolution="15dk model";
                if(series==null || series.optJSONArray("time")==null ||
                    series.optJSONArray("time").length()==0){
                    series=item.optJSONObject("hourly");resolution="saatlik model";
                }
                if(series==null)throw new IllegalStateException("Tahmin serisi yok");
                JSONArray times=series.optJSONArray("time");
                JSONArray temp=series.optJSONArray("temperature_2m");
                JSONArray precip=series.optJSONArray("precipitation");
                JSONArray wind=series.optJSONArray("wind_speed_10m");
                JSONArray code=series.optJSONArray("weather_code");
                if(times==null || temp==null || precip==null || wind==null ||
                    times.length()==0)throw new IllegalStateException("Hava alanları eksik");
                long target=now+minutes[i]*60L;
                int closest=0;long diff=Long.MAX_VALUE;
                for(int j=0;j<times.length();j++){
                    long d=Math.abs(times.optLong(j)-target);
                    if(d<diff){closest=j;diff=d;}
                }
                if(diff>4000)throw new IllegalStateException("Güncel tahmin zamanı bulunamadı");
                double temperature=temp.optDouble(closest,Double.NaN);
                double rainfall=precip.optDouble(closest,Double.NaN);
                double windKmh=wind.optDouble(closest,Double.NaN);
                if(!Double.isFinite(temperature)||!Double.isFinite(rainfall)||
                    !Double.isFinite(windKmh))throw new IllegalStateException("Eksik hava ölçümü");
                JSONObject card=new JSONObject();
                card.put("minutes",minutes[i]);
                card.put("temperature",temperature);
                card.put("precipitation",rainfall);
                card.put("wind",windKmh);
                card.put("weatherCode",code==null?-1:code.optInt(closest,-1));
                card.put("resolution",resolution);
                card.put("forecastTime",times.optLong(closest));
                cards.put(card);
            }
            JSONObject packet=new JSONObject();
            packet.put("cards",cards);
            packet.put("source","Open-Meteo");
            packet.put("updatedAt",System.currentTimeMillis());
            if(!destroyed && routeGeneration==generation)send("molaWeather",packet);
        }catch(Exception e){
            if(!destroyed && routeGeneration==generation)
                send("molaWeatherError",record("weather",
                    "Hava tahmini alınamadı ("+e.getClass().getSimpleName()+")."));
        }
    }

    private static String spoken(String type,String modifier,String street){
        String road=street==null||street.trim().isEmpty()?"":(" "+street.trim());
        switch(type){
            case "depart":return "Yola çıkın"+(road.isEmpty()?"":", "+road.trim()+" yolunu takip edin");
            case "arrive":return "Hedefinize ulaştınız";
            case "roundabout":case "rotary":return "Döner kavşağa girin";
            case "turn":case "continue":case "fork":case "new name":
                String m=modifier==null?"":modifier;
                if(m.contains("left"))return "Sola dönün"+road;
                if(m.contains("right"))return "Sağa dönün"+road;
                return "Yola devam edin"+road;
            case "merge":return "Yola katılın"+road;
            case "on ramp":return "Bağlantı yoluna girin"+road;
            case "off ramp":return "Çıkışı kullanın"+road;
            default:return "";
        }
    }
    private static boolean valid(double lat,double lon){
        return Double.isFinite(lat)&&Double.isFinite(lon)&&lat>=35&&lat<=43&&lon>=25&&lon<=46;
    }
    private static String fetch(String address,int max)throws Exception{
        HttpURLConnection c=(HttpURLConnection)new URL(address).openConnection();
        c.setConnectTimeout(7500);c.setReadTimeout(10000);c.setInstanceFollowRedirects(false);
        c.setRequestProperty("User-Agent","Mola-ID4-Navigation-Preview/0.2");
        c.setRequestProperty("Accept-Language","en");
        try{
            if(c.getResponseCode()!=200)throw new IllegalStateException("HTTP "+c.getResponseCode());
            ByteArrayOutputStream b=new ByteArrayOutputStream();
            try(InputStream in=c.getInputStream()){
                byte[] data=new byte[4096];int n;
                while((n=in.read(data))!=-1){
                    if(b.size()+n>max)throw new IllegalStateException("Yanıt çok büyük");
                    b.write(data,0,n);
                }
            }
            return b.toString("UTF-8");
        }finally{c.disconnect();}
    }
    // Never start GPS merely because the user opened a manual route preview.
    // Location is requested explicitly via the "Konumumu başlangıç yap" button.
    private void updateImmersiveCockpit(){
        // Only our PHONE/embedded Activity uses immersive mode in landscape.
        // This does not bypass or claim Android Auto host/display support.
        boolean wide=getResources().getConfiguration().orientation==
            Configuration.ORIENTATION_LANDSCAPE;
        if(Build.VERSION.SDK_INT>=30){
            WindowInsetsController controller=getWindow().getInsetsController();
            if(controller!=null){
                controller.setSystemBarsBehavior(
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                if(wide)controller.hide(WindowInsets.Type.systemBars());
                else controller.show(WindowInsets.Type.systemBars());
            }
        }else{
            int flags=wide?(View.SYSTEM_UI_FLAG_FULLSCREEN |
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN):0;
            getWindow().getDecorView().setSystemUiVisibility(flags);
        }
    }
    @Override public void onConfigurationChanged(Configuration configuration){
        super.onConfigurationChanged(configuration);
        updateImmersiveCockpit();
        // Landscape switch is user-initiated; retain this WebView's OBD and route state.
        if(web!=null)web.post(()->{
            if(web!=null&&!destroyed)
                web.evaluateJavascript("window.molaLayout&&window.molaLayout()",null);
        });
    }
    @Override public void onWindowFocusChanged(boolean hasFocus){
        super.onWindowFocusChanged(hasFocus);
        if(hasFocus&&!destroyed)updateImmersiveCockpit();
    }
    @Override protected void onResume(){
        super.onResume();updateImmersiveCockpit();
        if(telemetry!=null)autoConnectObd();
        // Restore only GPS monitoring that the user previously opted into.
        if(gpsTrackingRequested &&
           checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==
           PackageManager.PERMISSION_GRANTED)requestGps();
        else if(gpsTrackingRequested)
            send("molaStatus",record("gps","GPS izni yok; canlı takip durduruldu."));
        running=resumeRouteAfterPause && gpsTrackingRequested &&
            checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==
            PackageManager.PERMISSION_GRANTED;
        resumeRouteAfterPause=false;
    }
    @Override protected void onPause(){
        resumeRouteAfterPause=running;
        try{locationManager.removeUpdates(this);}catch(Exception ignored){}
        running=false; if(tts!=null)tts.stop();
        if(telemetry!=null)telemetry.stop();super.onPause();
    }
    @Override protected void onDestroy(){
        destroyed=true;running=false;generation++;stationRequestId++;
        try{locationManager.removeUpdates(this);}catch(Exception ignored){}
        jobs.shutdownNow();stationJobs.shutdownNow();if(tts!=null){tts.stop();tts.shutdown();}
        if(telemetry!=null){telemetry.close();telemetry=null;}
        if(web!=null){web.removeJavascriptInterface("MolaNative");web.destroy();web=null;}
        super.onDestroy();
    }
}
