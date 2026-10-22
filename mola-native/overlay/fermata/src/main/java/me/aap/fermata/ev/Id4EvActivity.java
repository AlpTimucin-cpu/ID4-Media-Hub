package me.aap.fermata.ev;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.ViewGroup;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.webkit.WebViewAssetLoader;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.GZIPInputStream;

/**
 * Mola's opt-in ID.4 hub. Completely independent from Mola playback/session
 * components: no TV, IPTV, Web addon, MediaProjection or navigation rewrites.
 *
 * Runs network+JSON work in a single background executor and limits map pins
 * to 55 per viewport. Never assumes BMS/remote access to an ID.4 vehicle.
 */
public final class Id4EvActivity extends Activity {
    private static final String DATA =
            "https://raw.githubusercontent.com/AlpTimucin-cpu/ID4-Media-Hub/" +
            "feature/mola-32-migration/data/charging/epdk_public_stations.json.gz";
    private static final String PRICES =
            "https://raw.githubusercontent.com/AlpTimucin-cpu/ID4-Media-Hub/" +
            "feature/mola-32-migration/data/charging/operator_prices.json";
    private static final String MAP =
            "https://appassets.androidplatform.net/assets/id4/index.html";
    private static final String IDENTIFIER="Mola-ID4-EV-Preview/0.1";
    private static final int MAIN=0xff091e2a;
    private static final int AQUA=0xff66e1c9;
    private static final long THREE_DAYS=72L*60*60*1000;
    private final ExecutorService work=Executors.newSingleThreadExecutor();
    private LinearLayout root;
    private TextView status;
    private WebView map;
    private EditText address;
    private JSONArray records;
    private String verifiedAt="";
    private boolean mapReady;
    private boolean stopped;
    private int generation;

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().setStatusBarColor(MAIN);
        getWindow().setNavigationBarColor(MAIN);
        root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(MAIN);
        setContentView(root);
        LinearLayout tabs=new LinearLayout(this);
        root.addView(tabs);
        addTab(tabs,"‹ Mola",()->finish());
        addTab(tabs,"🗺 Rota",()->startActivity(new Intent(this,Id4NavigatorActivity.class)));
        addTab(tabs,"⚡ İstasyon",this::stationsScreen);
        addTab(tabs,"🔋 Araç",()->startActivity(new Intent(this,Id4ObdActivity.class)));
        addTab(tabs,"🔌 OBD",()->startActivity(new Intent(this,Id4ObdActivity.class)));
        status=text("ID.4 • Mola eklentisi • Resmî EPDK verileri",11,false);
        root.addView(status);
        // Enter the station list, never the deprecated long-press map flow.
        stationsScreen();
    }
    private void addTab(LinearLayout row,String label,Runnable action) {
        Button button=button(label,action);
        row.addView(button,new LinearLayout.LayoutParams(0,dp(52),1f));
    }
    private LinearLayout content() {
        if(map!=null){
            try {
                map.stopLoading();
                map.loadUrl("about:blank");
                if(map.getParent() instanceof ViewGroup)
                    ((ViewGroup)map.getParent()).removeView(map);
                map.destroy();
            }catch(Exception ignore){}
            map=null;
        }
        while(root.getChildCount()>2) root.removeViewAt(2);
        mapReady=false;
        LinearLayout layout=new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        root.addView(layout,new LinearLayout.LayoutParams(-1,0,1f));
        return layout;
    }
    private void mapScreen() {
        int g=++generation;
        LinearLayout body=content();
        body.addView(text("Mola içinde Türkiye haritası · halka açık şarj istasyonları",14,true));
        LinearLayout searchRow=new LinearLayout(this);
        address=new EditText(this);
        address.setTextColor(Color.WHITE);
        address.setHintTextColor(0xff91b6c3);
        address.setSingleLine(true);
        address.setTextSize(14f);
        address.setHint("İl, ilçe, sokak veya adres");
        searchRow.addView(address,new LinearLayout.LayoutParams(0,dp(48),1f));
        searchRow.addView(button("Ara",this::lookupAddress));
        body.addView(searchRow);
        WebViewAssetLoader loader=new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/",new WebViewAssetLoader.AssetsPathHandler(this))
                .build();
        map=new WebView(this);
        map.setBackgroundColor(0xffeff1ef);
        WebSettings settings=map.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setUserAgentString(settings.getUserAgentString()+" "+IDENTIFIER);
        map.setWebViewClient(new WebViewClient(){
            @Override public WebResourceResponse shouldInterceptRequest(
                    WebView view,WebResourceRequest req){
                android.net.Uri uri=req.getUrl();
                if(!"https".equals(uri.getScheme()))return denied();
                String host=uri.getHost();
                if("appassets.androidplatform.net".equals(host))
                    return loader.shouldInterceptRequest(uri);
                if("unpkg.com".equals(host)||
                   "tile.openstreetmap.org".equals(host)) return null;
                return denied();
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view,
                    WebResourceRequest req){return true;}
            @Override public void onPageFinished(WebView view,String url){
                if(!MAP.equals(url) || g!=generation || stopped)return;
                map.evaluateJavascript("window.id4IsReady && window.id4IsReady();", value->{
                    if(g!=generation || stopped)return;
                    mapReady="true".equals(value);
                    status.setText(mapReady ?
                       "Harita hazır · Hedef: dokun, başlangıç: uzun bas" :
                       "Harita yüklenemedi. İnterneti ve CDN erişimini kontrol et.");
                });
            }
        });
        body.addView(map,new LinearLayout.LayoutParams(-1,0,1f));
        LinearLayout commands=new LinearLayout(this);
        addTab(commands,"⚡ Şarjları göster",this::showMapStations);
        addTab(commands,"↗ Rota planla",()->startActivity(new Intent(this,Id4NavigatorActivity.class)));
        body.addView(commands);
        body.addView(text("© OpenStreetMap katkıcıları. Rota önizlemedir; " +
                "canlı trafik ve sesli dönüş talimatları henüz yoktur.",11,false));
        map.loadUrl(MAP);
    }
    private void showMapStations(){
        if(map==null||!mapReady){alert("Harita yükleniyor","Lütfen bekle.");return;}
        final int id=++generation;
        map.evaluateJavascript("window.id4MapBounds();",raw->{
            JSONArray bbox;
            try{bbox=new JSONArray(raw);if(bbox.length()!=4)throw new Exception();}
            catch(Exception error){setStatus("Harita alanı okunamadı.");return;}
            status.setText("EPDK istasyonları arka planda yükleniyor…");
            work.execute(()->{
                try{
                    JSONArray all=loadOfficialData();
                    JSONArray matches=new JSONArray();
                    int total=0;
                    double south=bbox.getDouble(0),west=bbox.getDouble(1);
                    double north=bbox.getDouble(2),east=bbox.getDouble(3);
                    for(int i=0;i<all.length();i++){
                        JSONObject s=all.optJSONObject(i);
                        if(s==null)continue;
                        double lat=s.optDouble("lat",0),lon=s.optDouble("lon",0);
                        if(lat<south||lat>north||lon<west||lon>east)continue;
                        total++;
                        if(matches.length()<55)matches.put(new JSONObject()
                            .put("lat",lat).put("lon",lon)
                            .put("title",s.optString("name").substring(0,
                                Math.min(90,s.optString("name").length())))
                            .put("operator",s.optString("operator"))
                            .put("sockets",formatSockets(s.optJSONArray("sockets"))));
                    }
                    int count=total;
                    runOnUiThread(()->{
                        if(id!=generation||stopped||!mapReady)return;
                        map.evaluateJavascript("window.id4ShowStations("+matches+");",null);
                        setStatus(count+" resmî halka açık nokta bu bölgede · "+
                            Math.min(count,55)+" işaret gösterildi · "+verifiedAt);
                    });
                }catch(Exception e){setStatus("EPDK okunamadı. İnternet veya veri güncelliğini kontrol et.");}
            });
        });
    }
    private static String formatSockets(JSONArray sockets){
        if(sockets==null)return "Soket: bilinmiyor";
        int ccs=0,ac=0,chademo=0;
        for(int i=0;i<sockets.length();i++){
            JSONObject s=sockets.optJSONObject(i);if(s==null)continue;
            String type=s.optString("type").toLowerCase(Locale.ROOT);
            if(type.contains("ccs")||type.contains("combo"))ccs++;
            else if(type.contains("type 2")||type.contains("type2"))ac++;
            else if(type.contains("chademo"))chademo++;
        }
        return "CCS "+ccs+" · Type 2 "+ac+" · CHAdeMO "+chademo;
    }
    private void lookupAddress(){
        if(address==null||!mapReady){alert("Harita","Önce harita açılsın.");return;}
        String q=address.getText().toString().trim();
        if(q.length()<3){alert("Adres", "En az 3 karakter gir.");return;}
        setStatus("Adres aranıyor…");
        int id=++generation;
        work.execute(()->{
            try{
                // Photon explicitly permits small, reasonable use of its
                // public demo API; do not use the restricted public Nominatim
                // endpoint as an app's generic AI-generated geocoder.
                String path="/api/?limit=6&lang=tr&bbox=25,35,46,43&q="+
                    URLEncoder.encode(q,"UTF-8");
                JSONArray features=new JSONObject(download("photon.komoot.io",
                    path,350000)).getJSONArray("features");
                List<JSONObject> points=new ArrayList<>();
                List<String> names=new ArrayList<>();
                for(int i=0;i<features.length();i++){
                    JSONObject feature=features.optJSONObject(i);
                    if(feature==null)continue;
                    JSONArray coords=feature.optJSONObject("geometry")==null ?
                        null:feature.optJSONObject("geometry").optJSONArray("coordinates");
                    if(coords==null||coords.length()<2)continue;
                    double lon=coords.optDouble(0,Double.NaN);
                    double lat=coords.optDouble(1,Double.NaN);
                    if(!valid(lat,lon))continue;
                    JSONObject props=feature.optJSONObject("properties");
                    String name=props==null?"Haritadaki sonuç":
                        (props.optString("name")+" "+props.optString("street")+
                        " "+props.optString("city")+" "+props.optString("state")).trim();
                    JSONObject normalized=new JSONObject()
                        .put("lat",lat).put("lon",lon).put("display_name",name);
                    points.add(normalized);names.add(name);
                }
                runOnUiThread(()->{
                    if(id!=generation||stopped)return;
                    if(points.isEmpty()){setStatus("Adres bulunamadı.");return;}
                    new AlertDialog.Builder(this).setTitle("Türkiye adres araması")
                        .setItems(names.toArray(new String[0]),(dialog,index)->{
                            JSONObject a=points.get(index);
                            String lat=a.optString("lat"),lon=a.optString("lon");
                            String args=lat+","+lon+","+
                                JSONObject.quote(a.optString("display_name").substring(0,
                                    Math.min(90,a.optString("display_name").length())));
                            map.evaluateJavascript("window.id4Focus("+args+");",null);
                        }).setNegativeButton("Vazgeç",null).show();
                    setStatus(points.size()+" adres bulundu.");
                });
            }catch(Exception e){setStatus("Adres servisine erişilemedi.");}
        });
    }
    private void drawRoute(){
        if(map==null||!mapReady){alert("Harita","Harita henüz hazır değil.");return;}
        map.evaluateJavascript("window.id4GetRouteEndpoints();",raw->{
            try{
                JSONArray coords=new JSONArray(raw);
                if(coords.length()!=4)throw new Exception();
                double fromLon=coords.getDouble(0),fromLat=coords.getDouble(1);
                double toLon=coords.getDouble(2),toLat=coords.getDouble(3);
                if(!valid(fromLat,fromLon)||!valid(toLat,toLon))throw new Exception();
                int id=++generation;
                setStatus("Rota hesaplanıyor…");
                work.execute(()->{
                    try{
                        String path=String.format(Locale.US,
                            "/route/v1/driving/%.6f,%.6f;%.6f,%.6f?overview=simplified&geometries=geojson&steps=false",
                            fromLon,fromLat,toLon,toLat);
                        JSONObject rawRoute=new JSONObject(download("router.project-osrm.org",
                            path,1500000));
                        JSONObject route=rawRoute.getJSONArray("routes").getJSONObject(0);
                        double km=route.getDouble("distance")/1000.0;
                        double minutes=route.getDouble("duration")/60.0;
                        JSONObject presentation=new JSONObject()
                            .put("coordinates",route.getJSONObject("geometry").getJSONArray("coordinates"))
                            .put("distanceKm",String.format(Locale.US,"%.1f",km))
                            .put("durationMin",String.format(Locale.US,"%.0f",minutes));
                        runOnUiThread(()->{
                            if(id!=generation||stopped||!mapReady)return;
                            map.evaluateJavascript("window.id4ShowRoute("+presentation+");",null);
                            setStatus(String.format(Locale.forLanguageTag("tr-TR"),
                                "Tahmini rota %.1f km • %.0f dakika",km,minutes));
                        });
                    }catch(Exception e){setStatus("Rota servisi kullanılamıyor.");}
                });
            }catch(Exception e){
                alert("Rota seçimi","Başlangıç için haritaya uzun bas, hedef için dokun.");
            }
        });
    }

    private void stationsScreen(){
        ++generation;
        LinearLayout body=content();
        body.addView(text("EPDK HALKA AÇIK ŞARJ İSTASYONLARI",17,true));
        body.addView(text("Resmî istasyon numarası, adres, operatör ve soket tipleri. "+
            "Canlı doluluk henüz doğrulanmıyor.",12,false));
        EditText query=new EditText(this);
        query.setSingleLine(true);query.setTextColor(Color.WHITE);
        query.setHintTextColor(0xffacc6d3);
        query.setHint("Şehir, ilçe, marka, operatör, adres");
        body.addView(query);
        ScrollView scroll=new ScrollView(this);
        LinearLayout list=new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);scroll.addView(list);
        body.addView(button("⚡ İstasyonları bul",()->{
            String q=query.getText().toString().trim().toLowerCase(Locale.forLanguageTag("tr"));
            int id=++generation;
            setStatus("Resmî istasyon kayıtları okunuyor…");
            work.execute(()->{
                try{
                    JSONArray all=loadOfficialData();
                    ArrayList<JSONObject> hits=new ArrayList<>();
                    int count=0;
                    for(int i=0;i<all.length();i++){
                        JSONObject station=all.optJSONObject(i);if(station==null)continue;
                        String searchable=(
                          station.optString("name")+" "+station.optString("address")+" "+
                          station.optString("operator")+" "+station.optString("brand"))
                            .toLowerCase(Locale.forLanguageTag("tr"));
                        if(!searchable.contains(q))continue;
                        count++;
                        if(hits.size()<35)hits.add(station);
                    }
                    int total=count;
                    runOnUiThread(()->{
                        if(id!=generation||stopped)return;
                        list.removeAllViews();
                        for(JSONObject s:hits){
                            String title=s.optString("name")+" · "+
                                s.optString("operator")+"\n"+s.optString("address")+
                                "\n"+formatSockets(s.optJSONArray("sockets"));
                            list.addView(button(title,()->{
                                double lat=s.optDouble("lat"),lon=s.optDouble("lon");
                                if(!valid(lat,lon)){
                                    alert("İstasyon konumu","Geçerli istasyon koordinatı bulunamadı.");
                                    return;
                                }
                                Id4EnergyData.saveStation(this,
                                    s.optString("name",""),s.optString("operator",""));
                                Intent intent=new Intent(this,Id4NavigatorActivity.class);
                                intent.putExtra("toLat",lat);
                                intent.putExtra("toLon",lon);
                                intent.putExtra("toName",s.optString("name","Seçilen şarj istasyonu"));
                                startActivity(intent);
                            }));
                        }
                        setStatus(total+" eşleşme. İlk "+hits.size()+" gösteriliyor.");
                    });
                }catch(Exception e){setStatus("EPDK verisine ulaşılamadı.");}
            });
        }));
        body.addView(scroll,new LinearLayout.LayoutParams(-1,0,1f));
    }

    private void showPrices(){
        setStatus("Resmî Trugo/ZES tarifeleri kontrol ediliyor…");
        work.execute(()->{
            try{
                JSONObject root=new JSONObject(download("raw.githubusercontent.com",
                    "/AlpTimucin-cpu/ID4-Media-Hub/feature/mola-32-migration/data/charging/operator_prices.json",150000));
                JSONArray operators=root.optJSONArray("operators");
                StringBuilder b=new StringBuilder();
                if(operators!=null)for(int i=0;i<operators.length();i++){
                    JSONObject operator=operators.optJSONObject(i);if(operator==null)continue;
                    b.append(operator.optString("operator")).append("\n");
                    JSONArray items=operator.optJSONArray("tariffs");
                    if(items==null)continue;
                    for(int j=0;j<items.length();j++){
                        JSONObject t=items.optJSONObject(j);if(t==null)continue;
                        String ts=t.optString("verified_at_utc");
                        boolean fresh=false;
                        try{
                            long age=Duration.between(Instant.parse(ts),Instant.now()).toHours();
                            fresh=age>=0&&age<=36;
                        }catch(Exception ignore){}
                        b.append("  ").append(t.optString("connector_band")).append(": ")
                         .append(t.optString("price_try_per_kwh")).append(" TL/kWh")
                         .append(fresh?" (güncel)":" (ESKİ / DOĞRULANAMADI)").append("\n");
                    }
                    b.append("\n");
                }
                if(b.length()==0)b.append("Resmî tarifeler şu an doğrulanamıyor.");
                String message=b+"\nOperatör genel tarifesi; istasyonun kesin soket fiyatı değildir.";
                runOnUiThread(()->{
                    if(stopped)return;
                    new AlertDialog.Builder(this).setTitle("Kaynaklı resmî tarifeler")
                        .setMessage(message).setPositiveButton("Tamam",null).show();
                    setStatus("Fiyatlar kaynak ve zamana göre kontrol edildi.");
                });
            }catch(Exception e){setStatus("Tarifelere erişilemiyor.");}
        });
    }
    private synchronized JSONArray loadOfficialData()throws Exception{
        if(records!=null)return records;
        File cache=new File(getCacheDir(),"mola_id4_epdk.gz");
        byte[] zipped=null;
        if(cache.isFile()&&System.currentTimeMillis()-cache.lastModified()<6*3600000L){
            zipped=readBounded(new FileInputStream(cache),10_000_000);
        }else{
            try{
                String path="/AlpTimucin-cpu/ID4-Media-Hub/"+
                    "feature/mola-32-migration/data/charging/epdk_public_stations.json.gz";
                zipped=downloadBytes("raw.githubusercontent.com",path,10_000_000);
            }catch(Exception e){
                if(cache.isFile())zipped=readBounded(new FileInputStream(cache),10_000_000);
                else throw e;
            }
        }
        ByteArrayOutputStream decoded=new ByteArrayOutputStream();
        try(InputStream in=new GZIPInputStream(new ByteArrayInputStream(zipped))){
            byte[] buf=new byte[8192];
            for(int n;(n=in.read(buf))!=-1;){
                if(decoded.size()+n>22_000_000)throw new Exception("Oversized EPDK data");
                decoded.write(buf,0,n);
            }
        }
        JSONObject root=new JSONObject(decoded.toString("UTF-8"));
        if(root.optInt("schema_version")!=1||root.optBoolean("prices_included",true)||
           root.optBoolean("live_status_included",true))throw new Exception();
        JSONArray stations=root.getJSONArray("stations");
        if(stations.length()<5000||stations.length()>100000)throw new Exception();
        Instant verified=Instant.parse(root.getString("verified_at_utc"));
        long age=Duration.between(verified,Instant.now()).toHours();
        if(age<0)throw new Exception("Future-dated official snapshot");
        // Station locations remain useful when daily background publishing
        // is delayed; label old data instead of inventing freshness.
        verifiedAt=root.getString("verified_at_utc").substring(0,16)+" UTC"+
            (age>72?" • ESKİ İSTASYON VERİSİ":"");
        // Once source/schema/time has been validated, preserve last valid cache.
        if(!cache.isFile()||System.currentTimeMillis()-cache.lastModified()>6*3600000L){
            try(FileOutputStream out=new FileOutputStream(cache)){out.write(zipped);}
        }
        return records=stations;
    }
    private static boolean valid(double lat,double lon){
        return Double.isFinite(lat)&&Double.isFinite(lon)&&
            lat>=35&&lat<=43&&lon>=25&&lon<=46;
    }
    private static byte[] readBounded(InputStream stream,int max)throws Exception{
        try(InputStream in=stream;ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] buffer=new byte[8192];
            for(int n;(n=in.read(buffer))!=-1;){
                if(out.size()+n>max)throw new Exception("Response too large");
                out.write(buffer,0,n);
            }
            return out.toByteArray();
        }
    }
    private static byte[] downloadBytes(String host,String path,int max)throws Exception{
        if(!("raw.githubusercontent.com".equals(host)||
             "photon.komoot.io".equals(host)||
             "router.project-osrm.org".equals(host)))throw new Exception();
        URL url=new URL("https://"+host+path);
        HttpURLConnection conn=(HttpURLConnection)url.openConnection();
        conn.setInstanceFollowRedirects(false);
        conn.setConnectTimeout(14000);conn.setReadTimeout(20000);
        conn.setRequestProperty("User-Agent",IDENTIFIER);
        conn.setRequestProperty("Accept","application/json");
        try{
            if(conn.getResponseCode()!=200)throw new Exception("HTTP "+conn.getResponseCode());
            return readBounded(conn.getInputStream(),max);
        }finally{conn.disconnect();}
    }
    private static String download(String host,String path,int max)throws Exception{
        return new String(downloadBytes(host,path,max),java.nio.charset.StandardCharsets.UTF_8);
    }
    private void setStatus(String text){
        runOnUiThread(()->{if(!stopped && status!=null)status.setText(text);});
    }
    private WebResourceResponse denied(){
        return new WebResourceResponse("text/plain","utf-8",
            403,"Blocked",java.util.Map.of(),
            new ByteArrayInputStream(new byte[0]));
    }
    private void alert(String title,String text){
        new AlertDialog.Builder(this).setTitle(title).setMessage(text)
            .setPositiveButton("Tamam",null).show();
    }
    private int dp(int d){return (int)(d*getResources().getDisplayMetrics().density);}
    private TextView text(String content,int size,boolean strong){
        TextView v=new TextView(this);
        v.setText(content);v.setTextSize(size);
        v.setTextColor(strong?AQUA:0xffc7d6dd);
        if(strong)v.setTypeface(v.getTypeface(),Typeface.BOLD);
        v.setPadding(dp(9),dp(10),dp(9),dp(10));
        return v;
    }
    private Button button(String label,Runnable r){
        Button b=new Button(this);
        b.setText(label);b.setTextSize(12);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        GradientDrawable g=new GradientDrawable();
        g.setColor(0xff22566a);g.setCornerRadius(dp(11));
        b.setBackground(g);b.setOnClickListener(v->r.run());
        return b;
    }
    @Override public void onDestroy(){
        stopped=true;generation++;work.shutdownNow();
        if(map!=null){
            try{map.stopLoading();map.loadUrl("about:blank");
                if(map.getParent() instanceof ViewGroup)
                    ((ViewGroup)map.getParent()).removeView(map);
                map.destroy();}catch(Exception ignore){}
            map=null;
        }
        super.onDestroy();
    }
}
