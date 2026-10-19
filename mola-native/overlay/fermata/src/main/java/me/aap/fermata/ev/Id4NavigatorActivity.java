package me.aap.fermata.ev;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.view.ViewGroup;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Navigation-first, same-app route preview for ID.4.
 * Beta routing via OSRM demo; NOT a validated live-traffic, offline or safety-grade navigator.
 * The map uses OSM tiles. Never launches ABRP/Maps/other apps.
 */
public final class Id4NavigatorActivity extends Activity implements LocationListener {
    private static final String PAGE="https://appassets.androidplatform.net/assets/id4/navigator.html";
    private final ExecutorService jobs=Executors.newSingleThreadExecutor();
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final ArrayList<Step> steps=new ArrayList<>();
    private WebView web;
    private LocationManager locationManager;
    private TextToSpeech tts;
    private Location position;
    private boolean running, voice=true, destroyed;
    private volatile String activeRouteProvider="OSRM demo";
    private int generation, nextStep;
    private long lastSpeech;
    private static final class Step {
        final double lat, lon;
        final String instruction;
        Step(double lat,double lon,String instruction){this.lat=lat;this.lon=lon;this.instruction=instruction;}
    }

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().setStatusBarColor(0xff061e2b);
        getWindow().setNavigationBarColor(0xff061e2b);
        locationManager=(LocationManager)getSystemService(LOCATION_SERVICE);
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
        @JavascriptInterface public void stop() {running=false;generation++;steps.clear();nextStep=0;}
        @JavascriptInterface public void suggest(String field,String query,int requestId){
            suggestPlaces(field,query,requestId);
        }
        @JavascriptInterface public void route(double fromLat,double fromLon,
              double toLat,double toLon,boolean useGps){
            plan(fromLat,fromLon,toLat,toLon,useGps);
        }
    }
    private void requestGps() {
        if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION},41);return;
        }
        try{
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,1500,3,this);
            Location loc=locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if(loc!=null && System.currentTimeMillis()-loc.getTime()<90000)onLocationChanged(loc);
            send("molaStatus",record("gps","Konum izni verildi. GPS aranıyor..."));
        }catch(Exception e){send("molaStatus",record("error","GPS açılamadı"));}
    }
    @Override public void onRequestPermissionsResult(int code,String[] permissions,int[] results){
        super.onRequestPermissionsResult(code,permissions,results);
        if(code==41 && results.length>0 && results[0]==PackageManager.PERMISSION_GRANTED)requestGps();
        else send("molaStatus",record("error","Konum izni olmadan canlı yönlendirme yapılamaz"));
    }
    @Override public void onLocationChanged(Location location) {
        if(destroyed)return;
        position=location;
        JSONObject j=new JSONObject();
        try{j.put("lat",location.getLatitude());j.put("lon",location.getLongitude());
            j.put("accuracy",location.getAccuracy());}catch(Exception ignored){}
        send("molaPosition",j);
        if(!running || location.getAccuracy()>60 || nextStep>=steps.size())return;
        Step step=steps.get(nextStep);
        float[] meters=new float[1];
        Location.distanceBetween(location.getLatitude(),location.getLongitude(),step.lat,step.lon,meters);
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
        if(!valid(fromLat,fromLon)||!valid(toLat,toLon)){
            send("molaStatus",record("error","Başlangıç ve varış için listeden konum seç."));return;
        }
        boolean verifiedGps=useGps && position!=null && position.getAccuracy()<=60 &&
            System.currentTimeMillis()-position.getTime()<=30000;
        final int myGeneration=++generation;
        jobs.execute(()->{
            try{
                String a=String.format(Locale.US,"%.6f,%.6f",fromLon,fromLat);
                String b=String.format(Locale.US,"%.6f,%.6f",toLon,toLat);
                String query="/route/v1/driving/"+a+";"+b+
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
                JSONObject selected=routes.getJSONObject(0);
                JSONObject packet=new JSONObject();
                packet.put("coordinates",selected.getJSONObject("geometry").getJSONArray("coordinates"));
                packet.put("distanceKm",Math.round(selected.getDouble("distance")/100.0)/10.0);
                packet.put("durationMin",Math.round(selected.getDouble("duration")/60.0));
                packet.put("alternatives",routes.length());
                packet.put("provider",activeRouteProvider);
                packet.put("trafficAvailable",false);
                packet.put("navigationActive",verifiedGps);
                JSONArray roadSteps=selected.getJSONArray("legs").getJSONObject(0).getJSONArray("steps");
                ArrayList<Step> parsed=new ArrayList<>();
                JSONArray labels=new JSONArray();
                for(int i=0;i<roadSteps.length() && i<180;i++){
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
                packet.put("steps",labels);
                ui.post(()->{
                    if(myGeneration!=generation||destroyed)return;
                    steps.clear();steps.addAll(parsed);nextStep=0;
                    running=verifiedGps;
                    send("molaRoute",packet);
                    // Weather is a separate best-effort request after the drawn route.
                    final JSONObject routeData=selected;
                    jobs.execute(()->loadRouteWeather(routeData,myGeneration));
                    if(verifiedGps)announce("Rota hazır. "+packet.optDouble("distanceKm")+
                        " kilometre. Deneysel sesli yönlendirme başlıyor.");
                });
            }catch(Exception e){send("molaStatus",record("error",
                "Rota çizilemedi: "+e.getMessage()+". Ağ veya rota servisini kontrol et."));}
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
    @Override protected void onResume(){super.onResume(); if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED)requestGps();}
    @Override protected void onPause(){
        try{locationManager.removeUpdates(this);}catch(Exception ignored){}
        running=false; if(tts!=null)tts.stop();super.onPause();
    }
    @Override protected void onDestroy(){
        destroyed=true;running=false;generation++;
        try{locationManager.removeUpdates(this);}catch(Exception ignored){}
        jobs.shutdownNow();if(tts!=null){tts.stop();tts.shutdown();}
        if(web!=null){web.removeJavascriptInterface("MolaNative");web.destroy();web=null;}
        super.onDestroy();
    }
}
