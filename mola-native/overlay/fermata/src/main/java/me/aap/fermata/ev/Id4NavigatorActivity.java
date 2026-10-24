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
import android.text.InputType;
import android.widget.EditText;
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
    // Current Open-Meteo readings are MODEL values, never actual station sensors.
    private final ExecutorService currentWeatherJobs=Executors.newSingleThreadExecutor();
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final ArrayList<Step> steps=new ArrayList<>();
    private WebView web;
    private Id4LiveTelemetry telemetry;
    private final Id4TripJournal tripJournal=new Id4TripJournal();
    private Id4StationCatalog stationCatalog;
    private volatile int stationRequestId;
    private volatile int routeStationRequestId;
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
    private volatile long lastCurrentWeatherAt;
    private volatile double lastCurrentWeatherLat=Double.NaN,
                            lastCurrentWeatherLon=Double.NaN;
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
                applyLocalTomTomKey();
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
        @JavascriptInterface public void refreshWeather(){
            ui.post(()->{
                Location loc=position;
                if(loc!=null && System.currentTimeMillis()-loc.getTime()<=30000
                   && loc.getAccuracy()<=60){
                    fetchCurrentWeather(loc.getLatitude(),loc.getLongitude(),true,true);
                }else if(cachedAlternatives!=null && cachedAlternatives.length()>0){
                    try{
                        JSONArray pts=cachedAlternatives.getJSONObject(0)
                            .getJSONObject("geometry").getJSONArray("coordinates");
                        JSONArray first=pts.getJSONArray(0);
                        fetchCurrentWeather(first.getDouble(1),first.getDouble(0),
                            false,true);
                    }catch(Exception ignored){send("molaCurrentWeatherError",
                        record("weather","Rota başlangıcı okunamadı."));}
                }else send("molaCurrentWeatherError",
                    record("weather","Hava için GPS konumu veya rota gerekiyor."));
            });
        }
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
        @JavascriptInterface public void disconnectObd(){
            ui.post(()->{
                getSharedPreferences("id4_obd_pairing_v1",MODE_PRIVATE).edit()
                    .putBoolean("manual_disconnect",true).apply();
                telemetry.stop();
                send("molaStatus",record("obd",
                    "OBD bağlantısı bırakıldı. Car Scanner artık adaptöre bağlanabilir."));
            });
        }
        @JavascriptInterface public void visibleStations(
              double south,double west,double north,double east){
            requestVisibleStations(south,west,north,east,false);
        }
        @JavascriptInterface public void visibleStationsFiltered(
              double south,double west,double north,double east,boolean onlyCcs){
            requestVisibleStations(south,west,north,east,onlyCcs);
        }
        @JavascriptInterface public void saveStation(String title,String operator){
            if(title!=null && title.length()<=160 && operator!=null && operator.length()<=120)
                Id4EnergyData.saveStation(Id4NavigatorActivity.this,title,operator);
        }
        @JavascriptInterface public void back() {
            ui.post(()->{
                if(isTaskRoot()){
                    startActivity(new Intent(Id4NavigatorActivity.this,
                        me.aap.fermata.ui.activity.MainActivity.class));
                }
                finish();
            });
        }
        @JavascriptInterface public void configureTomTom() {
            ui.post(()->showTomTomKeyDialog());
        }
        @JavascriptInterface public void stations() {
            ui.post(()->startActivity(new Intent(Id4NavigatorActivity.this,Id4EvActivity.class)));
        }
        @JavascriptInterface public void tripJournal() {ui.post(()->showTripJournal());}
        @JavascriptInterface public void sensors() {
            ui.post(()->startActivity(new Intent(Id4NavigatorActivity.this,Id4ObdActivity.class)));
        }
        @JavascriptInterface public void voice(boolean on) {
            voice=on;if(!on && tts!=null)tts.stop();
        }
        @JavascriptInterface public void stop() {
            running=false;generation++;stationRequestId++;routeStationRequestId++;
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
                routeStationRequestId++;
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
    /** The TomTom browser token stays in app-private storage (Android backup
     * is disabled). Browser keys are recoverable in principle: use a narrow
     * TomTom API restriction and spending cap; never log the token. */
    private void showTomTomKeyDialog(){
        if(destroyed||isFinishing())return;
        EditText field=new EditText(this);
        field.setSingleLine(true);
        field.setTextSize(15);
        field.setInputType(InputType.TYPE_CLASS_TEXT|
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        String current=getSharedPreferences("id4_traffic_keys",MODE_PRIVATE)
            .getString("tomtom","");
        field.setText(current);field.selectAll();
        new AlertDialog.Builder(this)
            .setTitle("TomTom Orbis API anahtarı")
            .setMessage("TomTom Developer hesabından Traffic API anahtarı al. "+
                "Kota/harcama limitini ayarla. Anahtar yalnız uygulamanın özel "+
                "ayarında tutulur, APK dosyasına yazılmaz.")
            .setView(field)
            .setNegativeButton("Vazgeç",null)
            .setNeutralButton("Sil",(dialog,which)->{
                getSharedPreferences("id4_traffic_keys",MODE_PRIVATE).edit()
                    .remove("tomtom").apply();
                if(web!=null&&!destroyed)web.evaluateJavascript(
                    "window.Id4TrafficProviders&&window.Id4TrafficProviders.setTomTomKey('');",null);
                send("molaStatus",record("traffic","TomTom anahtarı temizlendi."));
            })
            .setPositiveButton("Kaydet",(dialog,which)->{
                String key=field.getText().toString().trim();
                if(!key.matches("[A-Za-z0-9_-]{15,200}")){
                    send("molaStatus",record("traffic","TomTom anahtarı geçersiz (15-200 karakter)."));
                    return;
                }
                getSharedPreferences("id4_traffic_keys",MODE_PRIVATE).edit()
                    .putString("tomtom",key).apply();
                applyLocalTomTomKey();
                send("molaStatus",record("traffic","TomTom anahtarı kaydedildi. Trafik katmanını seç."));
            }).show();
    }
    private void applyLocalTomTomKey(){
        if(web==null||destroyed)return;
        String key=getSharedPreferences("id4_traffic_keys",MODE_PRIVATE)
            .getString("tomtom","");
        if(key!=null&&!key.isEmpty()&&key.matches("[A-Za-z0-9_-]{15,200}"))
            web.evaluateJavascript("window.Id4TrafficProviders&&window.Id4TrafficProviders.setTomTomKey("+
                JSONObject.quote(key)+");",null);
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
                send("molaTelemetry",obdStatus("Önce Bluetooth'u açıp ELM327'yi eşleştir. Car Scanner bağlantısını kapat."));return;
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
                        .putString("paired_mac",d.getAddress())
                        .putBoolean("manual_disconnect",false).apply();
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
        if(getSharedPreferences("id4_obd_pairing_v1",MODE_PRIVATE)
            .getBoolean("manual_disconnect",false))return;
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

    private void requestVisibleStations(double south,double west,double north,double east,
                                        boolean onlyCcs){
        final int request=++stationRequestId;
        stationJobs.execute(()->{
            try{
                JSONObject packet=stationCatalog.inBounds(south,west,north,east,onlyCcs);
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
            if(!destroyed && generation==routeGeneration && routeStationRequestId==stationRequest)
                send("molaStations",packet);
        }catch(Exception e){
            if(!destroyed && generation==routeGeneration && routeStationRequestId==stationRequest)
                send("molaStationError",record("stations",
                    "Rota çevresi EPDK verisi yüklenemedi: "+e.getClass().getSimpleName()));
        }
    }
    private void loadAlternativeCards(JSONArray alternatives,int routeGeneration,
                                      int stationTicket,int selectedIndex,boolean hasStop){
        JSONArray rows=new JSONArray();
        double fastest=Double.POSITIVE_INFINITY,shortest=Double.POSITIVE_INFINITY;
        int fastIndex=0,shortIndex=0,chargeIndex=-1;
        double chargeScore=Double.NEGATIVE_INFINITY;
        Id4EnergyData.Snapshot facts=Id4EnergyData.load(this,System.currentTimeMillis());
        double soc=facts.hasSoc()?facts.soc:Double.NaN;
        int count=Math.min(12,alternatives.length());
        try{
            for(int k=0;k<count;k++){
                JSONObject route=alternatives.getJSONObject(k);
                double roadKm=route.getDouble("distance")/1000.0;
                double minutes=route.getDouble("duration")/60.0;
                if(roadKm<=0||roadKm>5000||minutes<=0)continue;
                if(minutes<fastest){fastest=minutes;fastIndex=k;}
                if(roadKm<shortest){shortest=roadKm;shortIndex=k;}
                JSONObject item=new JSONObject();
                item.put("index",k).put("km",Math.round(roadKm*10)/10.0)
                    .put("minutes",Math.round(minutes))
                    .put("energyLowKwh",Math.round(roadKm*.16*10)/10.0)
                    .put("energyHighKwh",Math.round(roadKm*.24*10)/10.0)
                    .put("socKnown",Double.isFinite(soc)).put("ccsCount",0)
                    .put("reachableCcsModel",false);
                Id4ChargeCost.Estimate cost=Id4ChargeCost.estimate(
                    roadKm,soc,"",java.time.LocalDate.now());
                item.put("chargeNeededModel",cost.needsStop);
                if(cost.socKnown) {
                    item.put("chargeLowKwh",Math.round(cost.purchasedLowKwh*10)/10.0);
                    item.put("chargeHighKwh",Math.round(cost.purchasedHighKwh*10)/10.0);
                }
                try{
                    JSONArray coords=route.getJSONObject("geometry").getJSONArray("coordinates");
                    ArrayList<Id4StationCatalog.Point> line=new ArrayList<>();
                    int stride=Math.max(1,coords.length()/2000);
                    for(int j=0;j<coords.length();j+=stride){
                        JSONArray c=coords.getJSONArray(j);
                        line.add(new Id4StationCatalog.Point(c.getDouble(1),c.getDouble(0)));
                    }
                    if(coords.length()>1){
                        JSONArray last=coords.getJSONArray(coords.length()-1);
                        line.add(new Id4StationCatalog.Point(last.getDouble(1),last.getDouble(0)));
                    }
                    JSONObject stationData=stationCatalog.alongRoute(line,roadKm,soc);
                    item.put("stationsKnown",true);
                    item.put("ccsCount",stationData.optInt("total",0));
                    JSONObject suggested=stationData.optJSONObject("suggested");
                    if(suggested!=null&&!hasStop){
                        item.put("reachableCcsModel",true);
                        item.put("firstCcs",suggested.optString("title"));
                        item.put("firstCcsOperator",suggested.optString("operator"));
                        item.put("firstCcsKm",suggested.optDouble("routeProgressKm",0));
                        double score=1000.0-Math.abs(roadKm-shortest)*.05-
                            minutes*.18+Math.min(100,stationData.optInt("total",0))*.25;
                        if(score>chargeScore){chargeScore=score;chargeIndex=k;}
                        cost=Id4ChargeCost.estimate(
                            roadKm,soc,suggested.optString("operator"),
                            java.time.LocalDate.now());
                        if(cost.tariffKnown){
                            item.put("costMinTry",cost.minTry)
                                .put("costMaxTry",cost.maxTry)
                                .put("tariffOperator",cost.operator)
                                .put("tariffSource",cost.tariffSource)
                                .put("tariffChecked","2026-10-22");
                        }
                    }
                }catch(Exception unavailable){
                    item.put("stationsKnown",false);
                }
                rows.put(item);
            }
            JSONObject response=new JSONObject().put("options",rows)
                .put("selectedIndex",selectedIndex)
                .put("fastestIndex",fastIndex).put("shortestIndex",shortIndex)
                .put("chargeAwareIndex",chargeIndex)
                .put("livePrices",false).put("liveAvailability",false)
                .put("modelNote","OSRM alternatifleri, 16–24 kWh/100 km, "
                   +"52 kWh batarya ve %15 rezerv. CCS yakınlığı yol erişimi değildir.");
            if(!destroyed && generation==routeGeneration && routeStationRequestId==stationTicket)
                send("molaAlternatives",response);
        }catch(Exception e){
            if(!destroyed && generation==routeGeneration && routeStationRequestId==stationTicket)
                send("molaAlternativeError",record("alternatives",
                    "Alternatiflerin şarj analizi yüklenemedi."));
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
        tripJournal.accept(location,System.currentTimeMillis());
        position=location;
        JSONObject j=new JSONObject();
        try{j.put("lat",location.getLatitude());j.put("lon",location.getLongitude());
            j.put("accuracy",location.getAccuracy());
            j.put("timeMs",location.getTime());}catch(Exception ignored){}
        send("molaPosition",j);
        long liveFixAge=System.currentTimeMillis()-location.getTime();
        if(liveFixAge>=0 && liveFixAge<=30000 && location.getAccuracy()<=60)
            fetchCurrentWeather(location.getLatitude(),location.getLongitude(),true,false);
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

    /** Explicitly opt-in GPS-only trip summary, no storage of GPS track. */
    private void showTripJournal(){
        if(destroyed||isFinishing())return;
        JSONArray past=Id4TripJournal.list(this);
        StringBuilder info=new StringBuilder();
        if(tripJournal.recording())
            info.append("● Kayıt etkin · ").append(tripJournal.distanceKm()).append(" km\\n\\n");
        else info.append("GPS kaydı kapalı\\n\\n");
        for(int i=0;i<Math.min(8,past.length());i++){
            JSONObject row=past.optJSONObject(i);
            if(row==null)continue;
            java.text.SimpleDateFormat fmt=new java.text.SimpleDateFormat(
                "dd.MM.yyyy HH:mm",new Locale("tr","TR"));
            info.append(fmt.format(new java.util.Date(row.optLong("startedAt"))))
                .append(" · ").append(row.optDouble("distanceKm"))
                .append(" km · ").append(row.optLong("durationMin"))
                .append(" dk\\n");
        }
        if(past.length()==0)info.append("Henüz GPS yolculuğu kaydedilmedi.\\n");
        info.append("\\nKonum ve güzergâh saklanmaz. Enerji ve maliyet ölçülmediyse boş bırakılır.");
        new AlertDialog.Builder(this).setTitle("ID.4 · Yolculuk Geçmişi")
            .setMessage(info.toString())
            .setPositiveButton(tripJournal.recording()?"Kaydı durdur":"GPS kaydını başlat",
                (dialog,which)->{
                    if(tripJournal.recording()){
                        boolean saved=tripJournal.finish(this,System.currentTimeMillis());
                        send("molaStatus",record("trip",saved?
                          "GPS yolculuğu kaydedildi.":"GPS mesafesi/süresi yetersiz; kayıt yok."));
                    }else if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=
                             PackageManager.PERMISSION_GRANTED){
                        requestGps();
                        send("molaStatus",record("trip","GPS iznini verdikten sonra yeniden başlat."));
                    }else{
                        tripJournal.start(System.currentTimeMillis());
                        requestGps();
                        send("molaStatus",record("trip","GPS yolculuk kaydı başladı."));
                    }
                })
            .setNeutralButton("Geçmişi temizle",(dialog,which)->
                new AlertDialog.Builder(this).setTitle("Yolculuk geçmişini sil?")
                    .setMessage("Yerel yolculuk özetleri silinir.")
                    .setNegativeButton("İptal",null)
                    .setPositiveButton("Sil",(d,w)->Id4TripJournal.erase(this)).show())
            .setNegativeButton("Kapat",null).show();
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
        routeStationRequestId++; // Invalidate the previous ROUTE charger batch only.
        jobs.execute(()->{
            try{
                String a=String.format(Locale.US,"%.6f,%.6f",fromLon,fromLat);
                String b=String.format(Locale.US,"%.6f,%.6f",toLon,toLat);
                boolean hasStop=valid(stopLat,stopLon);
                String via=hasStop?
                    String.format(Locale.US,"%.6f,%.6f",stopLon,stopLat):"";
                String query="/route/v1/driving/"+a+(hasStop?";"+via:"")+";"+b+
                    "?overview=full&geometries=geojson&steps=true&alternatives=6";
                JSONObject out=null;
                StringBuilder problems=new StringBuilder();
                String trafficKey=getSharedPreferences("id4_traffic_keys",MODE_PRIVATE)
                    .getString("tomtom","");
                if(trafficKey!=null&&trafficKey.matches("[A-Za-z0-9_-]{15,200}")){
                    try{
                        out=Id4TomTomRouting.request(trafficKey,fromLat,fromLon,
                            toLat,toLon,stopLat,stopLon);
                        activeRouteProvider="TomTom Orbis v3 canlı trafik";
                    }catch(Exception e){
                        // Never put keys, full request URLs, or coordinates in logs.
                        problems.append("TomTom erişimi başarısız; OSRM yedek rota. ");
                    }
                }
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

    /** Every genuine OSRM route, at most 12 for untrusted response protection.
     * Sample geometries to bound phone/WebView memory use; never make up roads. */
    private static JSONArray routePreviewGeometries(JSONArray routes)throws Exception{
        JSONArray result=new JSONArray();
        int count=Math.min(routes.length(),12);
        for(int i=0;i<count;i++){
            JSONObject route=routes.optJSONObject(i);
            JSONObject geo=route==null?null:route.optJSONObject("geometry");
            JSONArray coords=geo==null?null:geo.optJSONArray("coordinates");
            if(coords==null||coords.length()<2)continue;
            JSONArray shape=new JSONArray();
            int stride=Math.max(1,(int)Math.ceil(coords.length()/450.0));
            for(int j=0;j<coords.length();j+=stride){
                JSONArray c=coords.getJSONArray(j);
                shape.put(new JSONArray().put(c.optDouble(1)).put(c.optDouble(0)));
            }
            JSONArray last=coords.getJSONArray(coords.length()-1);
            shape.put(new JSONArray().put(last.optDouble(1)).put(last.optDouble(0)));
            result.put(new JSONObject().put("index",i).put("coordinates",shape)
                .put("km",Math.round(route.optDouble("distance")/100.0)/10.0)
                .put("minutes",Math.round(route.optDouble("duration")/60.0)));
        }
        return result;
    }
    private void renderRouteOption(JSONArray routes,int optionIndex,boolean verifiedGps,
                                   boolean hasStop,int myGeneration)throws Exception{
                JSONObject selected=routes.getJSONObject(optionIndex);
                JSONObject packet=new JSONObject();
                packet.put("coordinates",selected.getJSONObject("geometry").getJSONArray("coordinates"));
                packet.put("distanceKm",Math.round(selected.getDouble("distance")/100.0)/10.0);
                packet.put("durationMin",Math.round(selected.getDouble("duration")/60.0));
                packet.put("alternatives",routes.length());
                packet.put("routePreviews",routePreviewGeometries(routes));
                packet.put("routeIndex",optionIndex);
                packet.put("provider",activeRouteProvider);
                boolean tomtom=selected.optBoolean("trafficAware",false) &&
                    activeRouteProvider.startsWith("TomTom Orbis v3");
                packet.put("trafficAvailable",tomtom);
                double delay=selected.optDouble("trafficDelaySeconds",Double.NaN);
                if(tomtom&&Double.isFinite(delay)&&delay>=0)
                    packet.put("trafficDelayMin",Math.round(delay/60.0));
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
                    running=verifiedGps&&!parsed.isEmpty();
                    Id4EnergyData.saveRoute(this,
                        packet.optDouble("distanceKm",Double.NaN),
                        packet.optDouble("durationMin",Double.NaN),
                        System.currentTimeMillis());
                    send("molaRoute",packet);
                    final JSONObject routeData=selected;
                    // Populate a GPS-independent current MODEL weather report at
                    // the selected start; actual GPS takes precedence when fresh.
                    try{
                        JSONArray start=routeData.getJSONObject("geometry")
                            .getJSONArray("coordinates").getJSONArray(0);
                        Location live=position;
                        boolean liveGps=live!=null && live.getAccuracy()<=60
                            && System.currentTimeMillis()-live.getTime()<30000;
                        fetchCurrentWeather(liveGps?live.getLatitude():start.getDouble(1),
                            liveGps?live.getLongitude():start.getDouble(0),liveGps,false);
                    }catch(Exception ignored){}
                    // Weather is a separate best-effort request after the drawn route.
                    jobs.execute(()->loadRouteWeather(routeData,myGeneration));
                    jobs.execute(()->loadJourneyWeather(routeData,myGeneration));
                    // Read-only EPDK snapshot route corridor; never block route rendering.
                    final int stationTicket=++routeStationRequestId;
                    stationJobs.execute(()->loadRouteStations(routeData,myGeneration,stationTicket));
                    stationJobs.execute(()->loadAlternativeCards(routes,myGeneration,stationTicket,
                        optionIndex,hasStop));
                    if(verifiedGps&&!parsed.isEmpty())announce("Rota hazır. "+packet.optDouble("distanceKm")+
                        " kilometre. Deneysel sesli yönlendirme başlıyor.");
                });
    }

    /**
     * Open-Meteo current-model report refreshed on a verified GPS fix or at the
     * chosen route start. No fake surface station readings or weather scraping.
     */
    private void fetchCurrentWeather(double lat,double lon,boolean fromGps,
                                     boolean userRefresh){
        if(!valid(lat,lon)||destroyed)return;
        long now=System.currentTimeMillis();
        boolean moved=false;
        if(Double.isFinite(lastCurrentWeatherLat)){
            float[] d=new float[1];
            Location.distanceBetween(lat,lon,lastCurrentWeatherLat,
                lastCurrentWeatherLon,d);
            moved=d[0]>15000;
        }
        if(!userRefresh && !moved && now-lastCurrentWeatherAt<600000)return;
        // Tap refresh has a minimum of 30sec to avoid hammering free service.
        if(userRefresh && !moved && now-lastCurrentWeatherAt<30000)return;
        lastCurrentWeatherAt=now;
        lastCurrentWeatherLat=lat;lastCurrentWeatherLon=lon;
        final boolean gps=fromGps;
        currentWeatherJobs.execute(()->{
            try{
                String uri="https://api.open-meteo.com/v1/forecast?latitude="+
                    String.format(Locale.US,"%.5f",lat)+"&longitude="+
                    String.format(Locale.US,"%.5f",lon)+
                    "&current=temperature_2m,relative_humidity_2m,"+
                    "precipitation,weather_code,wind_speed_10m,is_day"+
                    "&timezone=GMT&timeformat=unixtime";
                JSONObject model=new JSONObject(fetch(uri,100000))
                    .getJSONObject("current");
                double temp=model.optDouble("temperature_2m",Double.NaN);
                double precip=model.optDouble("precipitation",Double.NaN);
                double wind=model.optDouble("wind_speed_10m",Double.NaN);
                long modelTime=model.optLong("time",0);
                long age=Math.abs(System.currentTimeMillis()/1000-modelTime);
                if(!Double.isFinite(temp)||!Double.isFinite(precip)||
                    !Double.isFinite(wind)||modelTime<=0||age>5400)
                    throw new IllegalStateException("Güncel model bulunamadı");
                JSONObject result=new JSONObject()
                    .put("temperature",temp).put("precipitation",precip)
                    .put("wind",wind).put("weatherCode",
                        model.optInt("weather_code",-1))
                    .put("isDay",model.optInt("is_day",-1))
                    .put("modelTime",modelTime)
                    .put("receivedAt",System.currentTimeMillis())
                    .put("kind",gps?"gps":"route-start")
                    .put("source","Open-Meteo")
                    .put("modelBased",true).put("liveStationObservation",false);
                double humidity=model.optDouble("relative_humidity_2m",Double.NaN);
                if(Double.isFinite(humidity))result.put("humidity",humidity);
                if(!destroyed)send("molaCurrentWeather",result);
            }catch(Exception e){
                // Retry will be possible after 2 min rather than blanking a
                // still-valid prior report at the first transient failure.
                lastCurrentWeatherAt=System.currentTimeMillis()-480000;
                if(!destroyed)send("molaCurrentWeatherError",
                    record("weather","Güncel hava modeli alınamadı; bağlantı kontrol et."));
            }
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

    /**
     * Whole-journey Open-Meteo forecast at 0/25/50/75/100% of the OSRM
     * geometry and projected arrival time. Source resolution is hourly;
     * these are predictions, NOT actual localized weather observations.
     */
    private void loadJourneyWeather(JSONObject route,int routeGeneration){
        try{
            JSONArray coordinates=route.getJSONObject("geometry").getJSONArray("coordinates");
            if(coordinates.length()<2)throw new IllegalStateException("Rota geometrisi yok");
            double durationSec=route.getDouble("duration");
            if(durationSec<=0 || durationSec>72*3600)
                throw new IllegalStateException("Hava tahmin aralığı dışında");
            double[] cumulative=new double[coordinates.length()];
            for(int i=1;i<coordinates.length();i++){
                JSONArray p=coordinates.getJSONArray(i-1),q=coordinates.getJSONArray(i);
                float[] measured=new float[1];
                Location.distanceBetween(p.getDouble(1),p.getDouble(0),
                    q.getDouble(1),q.getDouble(0),measured);
                cumulative[i]=cumulative[i-1]+measured[0];
            }
            double total=cumulative[cumulative.length-1];
            if(total<10)throw new IllegalStateException("Boş güzergah");
            StringBuilder latitudes=new StringBuilder(),longitudes=new StringBuilder();
            double[] routeLats=new double[5],routeLons=new double[5];
            double[] fractions={0,.25,.5,.75,1};
            long now=System.currentTimeMillis()/1000L;
            for(double fraction:fractions){
                double desired=fraction*total;int j=1;
                while(j<cumulative.length-1 && cumulative[j]<desired)j++;
                JSONArray a=coordinates.getJSONArray(j-1),b=coordinates.getJSONArray(j);
                double divisor=Math.max(.00001,cumulative[j]-cumulative[j-1]);
                double t=Math.max(0,Math.min(1,(desired-cumulative[j-1])/divisor));
                double latitude=a.getDouble(1)+(b.getDouble(1)-a.getDouble(1))*t;
                double longitude=a.getDouble(0)+(b.getDouble(0)-a.getDouble(0))*t;
                int waypoint=(int)Math.round(fraction*4);
                routeLats[waypoint]=latitude;routeLons[waypoint]=longitude;
                if(latitudes.length()>0){latitudes.append(',');longitudes.append(',');}
                latitudes.append(String.format(Locale.US,"%.5f",latitude));
                longitudes.append(String.format(Locale.US,"%.5f",longitude));
            }
            int forecastHours=Math.max(5,Math.min(72,(int)Math.ceil(durationSec/3600)+3));
            String url="https://api.open-meteo.com/v1/forecast?latitude="+latitudes+
                "&longitude="+longitudes+
                "&hourly=temperature_2m,precipitation_probability,wind_speed_10m,weather_code"+
                "&forecast_hours="+forecastHours+"&timeformat=unixtime&timezone=GMT";
            JSONArray regions=new JSONArray(fetch(url,700000));
            if(regions.length()!=fractions.length)
                throw new IllegalStateException("Eksik yol hava tahmini");
            JSONArray stations=new JSONArray();
            for(int i=0;i<fractions.length;i++){
                JSONObject location=regions.getJSONObject(i);
                JSONObject data=location.getJSONObject("hourly");
                JSONArray hours=data.getJSONArray("time");
                JSONArray temp=data.getJSONArray("temperature_2m");
                JSONArray rain=data.getJSONArray("precipitation_probability");
                JSONArray wind=data.getJSONArray("wind_speed_10m");
                JSONArray codes=data.getJSONArray("weather_code");
                long at=now+(long)(fractions[i]*durationSec);
                long smallest=Long.MAX_VALUE;int closest=0;
                for(int j=0;j<hours.length();j++){
                    long delta=Math.abs(hours.optLong(j)-at);
                    if(delta<smallest){smallest=delta;closest=j;}
                }
                JSONObject item=new JSONObject()
                    .put("percent",(int)Math.round(fractions[i]*100))
                    .put("etaMinutes",(int)Math.round(fractions[i]*durationSec/60.0))
                    .put("latitude",routeLats[i])
                    .put("longitude",routeLons[i])
                    .put("forecastTime",hours.optLong(closest));
                double celsius=temp.optDouble(closest,Double.NaN);
                double precipitation=rain.optDouble(closest,Double.NaN);
                double windKmh=wind.optDouble(closest,Double.NaN);
                if(smallest<=5400 && Double.isFinite(celsius) &&
                   Double.isFinite(precipitation) && Double.isFinite(windKmh)) {
                    item.put("available",true).put("temperature",celsius)
                        .put("rainProbability",precipitation)
                        .put("wind",windKmh)
                        .put("weatherCode",codes.optInt(closest,-1));
                }else item.put("available",false);
                stations.put(item);
            }
            JSONObject result=new JSONObject().put("cards",stations)
                .put("source","Open-Meteo").put("resolution","saatlik model")
                .put("updatedAt",System.currentTimeMillis())
                .put("liveConditions",false);
            if(!destroyed && generation==routeGeneration)send("molaJourneyWeather",result);
        }catch(Exception e){
            if(!destroyed && generation==routeGeneration)
                send("molaJourneyWeatherError",record("weather",
                    "Yol boyunca saatlik hava tahmini alınamadı ("+
                    e.getClass().getSimpleName()+")."));
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
        // Consent does not extend to background tracking; save and stop here.
        if(tripJournal.recording())tripJournal.finish(this,System.currentTimeMillis());
        resumeRouteAfterPause=running;
        try{locationManager.removeUpdates(this);}catch(Exception ignored){}
        running=false; if(tts!=null)tts.stop();
        if(telemetry!=null)telemetry.stop();super.onPause();
    }
    @Override protected void onDestroy(){
        destroyed=true;running=false;generation++;stationRequestId++;routeStationRequestId++;
        try{locationManager.removeUpdates(this);}catch(Exception ignored){}
        jobs.shutdownNow();stationJobs.shutdownNow();currentWeatherJobs.shutdownNow();if(tts!=null){tts.stop();tts.shutdown();}
        if(telemetry!=null){telemetry.close();telemetry=null;}
        if(web!=null){web.removeJavascriptInterface("MolaNative");web.destroy();web=null;}
        super.onDestroy();
    }
}
