package me.aap.fermata.ev;

import android.content.Context;
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
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.zip.GZIPInputStream;

/**
 * Read-only EPDK public station snapshot. Operates outside the Android UI thread.
 * No realtime availability or connector-specific price is claimed.
 * Downloads only a bounded gzip snapshot; returns a capped set of pins to WebView.
 */
final class Id4StationCatalog {
    private static final String SNAPSHOT_URL =
        "https://raw.githubusercontent.com/AlpTimucin-cpu/ID4-Media-Hub/" +
        "feature/mola-32-migration/data/charging/epdk_public_stations.json.gz";
    private static final long FETCH_TTL_MS = 6L*60*60*1000;
    private static final int MAX_GZIP = 10_000_000, MAX_JSON = 22_000_000;
    private final Context context;
    private JSONArray stations;
    private String verifiedAt = "";
    private long snapshotAgeHours;

    Id4StationCatalog(Context context) {this.context=context.getApplicationContext();}

    static final class Point {
        final double lat,lon;
        Point(double lat,double lon){this.lat=lat;this.lon=lon;}
    }
    private static final class Candidate {
        final JSONObject station;
        final double rank;
        final int segment;
        Candidate(JSONObject station,double rank,int segment){
            this.station=station;this.rank=rank;this.segment=segment;
        }
    }
    private static boolean valid(double lat,double lon){
        return Double.isFinite(lat)&&Double.isFinite(lon)&&
               lat>=35&&lat<=43&&lon>=25&&lon<=46;
    }

    private static byte[] readLimited(InputStream stream,int limit)throws Exception {
        try(InputStream in=stream;ByteArrayOutputStream result=new ByteArrayOutputStream()){
            byte[] chunk=new byte[8192];
            for(int n;(n=in.read(chunk))!=-1;){
                if(result.size()+n>limit)throw new IllegalStateException("EPDK dosyası çok büyük");
                result.write(chunk,0,n);
            }
            return result.toByteArray();
        }
    }

    private byte[] download()throws Exception{
        HttpURLConnection conn=(HttpURLConnection)new URL(SNAPSHOT_URL).openConnection();
        conn.setInstanceFollowRedirects(false);
        conn.setConnectTimeout(9000);conn.setReadTimeout(12000);
        conn.setRequestProperty("Accept-Encoding","identity");
        conn.setRequestProperty("User-Agent","Mola-ID4-EPDK-Reader/1.0");
        try {
            if(conn.getResponseCode()!=200)throw new IllegalStateException("EPDK HTTP "+conn.getResponseCode());
            return readLimited(conn.getInputStream(),MAX_GZIP);
        } finally {conn.disconnect();}
    }

    private void parse(byte[] gzip)throws Exception {
        byte[] bytes=readLimited(new GZIPInputStream(new ByteArrayInputStream(gzip)),MAX_JSON);
        JSONObject root=new JSONObject(new String(bytes,java.nio.charset.StandardCharsets.UTF_8));
        if(root.optInt("schema_version")!=1 ||
           root.optBoolean("prices_included",true) ||
           root.optBoolean("live_status_included",true)){
            throw new IllegalStateException("EPDK istasyon şeması uyumsuz");
        }
        JSONArray data=root.getJSONArray("stations");
        if(data.length()<5000 || data.length()>100000)
            throw new IllegalStateException("Eksik EPDK verisi");
        Instant stamp=Instant.parse(root.getString("verified_at_utc"));
        long h=Duration.between(stamp,Instant.now()).toHours();
        if(h<0)throw new IllegalStateException("İleri tarihli istasyon verisi");
        stations=data;
        verifiedAt=stamp.toString();
        snapshotAgeHours=h;
    }

    synchronized void ensureLoaded()throws Exception {
        if(stations!=null)return;
        File file=new File(context.getCacheDir(),"mola_id4_epdk.gz");
        boolean fresh=file.isFile() &&
            System.currentTimeMillis()-file.lastModified()>=0 &&
            System.currentTimeMillis()-file.lastModified()<FETCH_TTL_MS;
        byte[] zipped=null;
        if(fresh)try{zipped=readLimited(new FileInputStream(file),MAX_GZIP);parse(zipped);}
            catch(Exception ignored){zipped=null;stations=null;}
        if(stations!=null)return;
        try {
            zipped=download();
            parse(zipped);
            try(FileOutputStream out=new FileOutputStream(file)){out.write(zipped);}
        } catch(Exception downloadError){
            if(file.isFile()){
                zipped=readLimited(new FileInputStream(file),MAX_GZIP);
                parse(zipped);
            }else throw downloadError;
        }
    }

    private static boolean hasCCS(JSONObject s){
        JSONArray connectors=s.optJSONArray("sockets");
        if(connectors==null)return false;
        for(int i=0;i<connectors.length();i++){
            JSONObject c=connectors.optJSONObject(i);
            if(c==null)continue;
            String type=c.optString("type").toLowerCase(Locale.ROOT);
            if(type.contains("ccs")||type.contains("combo"))return true;
        }
        return false;
    }
    private static String types(JSONObject station){
        JSONArray sockets=station.optJSONArray("sockets");
        int ccs=0,ac=0;
        if(sockets!=null)for(int i=0;i<sockets.length();i++){
            JSONObject s=sockets.optJSONObject(i);if(s==null)continue;
            String t=s.optString("type").toLowerCase(Locale.ROOT);
            if(t.contains("ccs")||t.contains("combo"))ccs++;
            if(t.contains("type 2")||t.contains("type2"))ac++;
        }
        return "CCS "+ccs+" · Type 2 "+ac;
    }
    private static JSONObject pin(JSONObject s,double distanceKm)throws Exception{
        return new JSONObject().put("lat",s.getDouble("lat")).put("lon",s.getDouble("lon"))
            .put("title",s.optString("name")).put("operator",s.optString("operator"))
            .put("address",s.optString("address")).put("sockets",types(s))
            .put("ccs",hasCCS(s)).put("distanceKm",distanceKm);
    }
    private JSONObject response(String kind,List<Candidate> candidates,int total,
                                boolean onlyCcs)throws Exception {
        candidates.sort(Comparator.comparingDouble(c->c.rank));
        JSONArray result=new JSONArray();
        int cap="route".equals(kind)?45:55;
        List<Candidate> selected=new ArrayList<>();
        if("route".equals(kind)){
            // First choose one nearby CCS stop from each longitudinal route sector.
            // This prevents 45 city-centre pins from hiding rural highway stations.
            java.util.HashSet<Integer> seen=new java.util.HashSet<>();
            for(Candidate c:candidates) {
                if(seen.add(c.segment))selected.add(c);
            }
        }
        for(Candidate c:candidates){
            if(selected.size()>=cap)break;
            if(!selected.contains(c))selected.add(c);
        }
        for(Candidate c:selected){
            if(result.length()>=cap)break;
            result.put(pin(c.station,c.rank));
        }
        return new JSONObject()
            .put("kind",kind).put("items",result).put("total",total)
            .put("updatedAt",verifiedAt).put("snapshotAgeHours",snapshotAgeHours)
            .put("liveAvailability",false).put("livePrices",false)
            .put("onlyCcs",onlyCcs);
    }

    synchronized JSONObject inBounds(double south,double west,double north,double east)throws Exception{
        if(!Double.isFinite(south)||!Double.isFinite(west)||
           !Double.isFinite(north)||!Double.isFinite(east)||
           south>north||west>east)
            throw new IllegalArgumentException("Harita sınırları geçersiz");
        // A wide zoom level often includes Greece/Black Sea: safely clip to Türkiye.
        south=Math.max(south,35);north=Math.min(north,43);
        west=Math.max(west,25);east=Math.min(east,46);
        if(south>north||west>east)
            return new JSONObject().put("kind","visible").put("items",new JSONArray())
                .put("total",0).put("updatedAt",verifiedAt).put("snapshotAgeHours",snapshotAgeHours)
                .put("liveAvailability",false).put("livePrices",false);
        ensureLoaded();
        List<Candidate> candidates=new ArrayList<>();
        int total=0;
        double centerLat=(south+north)/2.0,centerLon=(west+east)/2.0;
        for(int i=0;i<stations.length();i++){
            JSONObject s=stations.optJSONObject(i);if(s==null)continue;
            double lat=s.optDouble("lat",Double.NaN),lon=s.optDouble("lon",Double.NaN);
            if(!valid(lat,lon)||lat<south||lat>north||lon<west||lon>east)continue;
            total++;
            candidates.add(new Candidate(s,haversineKm(lat,lon,centerLat,centerLon),0));
        }
        return response("visible",candidates,total,false);
    }

    synchronized JSONObject alongRoute(List<Point> path)throws Exception{
        if(path==null||path.size()<2)throw new IllegalArgumentException("Rota yok");
        ensureLoaded();
        List<Point> selected=new ArrayList<>();
        final int steps=Math.min(180,path.size()-1);
        for(int i=0;i<=steps;i++){
            Point p=path.get((int)Math.round((path.size()-1)*(double)i/steps));
            if(valid(p.lat,p.lon))selected.add(p);
        }
        if(selected.size()<2)throw new IllegalArgumentException("Rota koordinatları geçersiz");
        double south=90,north=-90,west=180,east=-180;
        for(Point p:selected){
            south=Math.min(south,p.lat);north=Math.max(north,p.lat);
            west=Math.min(west,p.lon);east=Math.max(east,p.lon);
        }
        // Screen fast-pre-filter: approximate 15–20 km around route.
        south-=0.20;north+=0.20;west-=0.25;east+=0.25;
        List<Candidate> candidates=new ArrayList<>();
        int total=0;
        for(int i=0;i<stations.length();i++){
            JSONObject s=stations.optJSONObject(i);if(s==null||!hasCCS(s))continue;
            double lat=s.optDouble("lat",Double.NaN),lon=s.optDouble("lon",Double.NaN);
            if(!valid(lat,lon)||lat<south||lat>north||lon<west||lon>east)continue;
            double nearest=Double.POSITIVE_INFINITY;
            int closestSegment=0;
            for(int j=1;j<selected.size();j++){
                double d=distanceToSegmentKm(lat,lon,selected.get(j-1),selected.get(j));
                if(d<nearest){nearest=d;closestSegment=j;}
            }
            if(nearest>12.0)continue; // straight-line proximity, not detour time
            total++;
            int sector=(int)(20.0*closestSegment/Math.max(1,selected.size()-1));
            candidates.add(new Candidate(s,nearest,sector));
        }
        return response("route",candidates,total,true);
    }

    private static double haversineKm(double latA,double lonA,double latB,double lonB){
        double dLat=Math.toRadians(latB-latA),dLon=Math.toRadians(lonB-lonA);
        double a=Math.pow(Math.sin(dLat/2),2)+Math.cos(Math.toRadians(latA))*
            Math.cos(Math.toRadians(latB))*Math.pow(Math.sin(dLon/2),2);
        return 12742.0*Math.asin(Math.min(1,Math.sqrt(Math.max(0,a))));
    }
    /** Equirectangular projection is adequate for a 12-km corridor shortlist. */
    static double distanceToSegmentKm(double lat,double lon,Point a,Point b){
        double scale=111.2,cos=Math.cos(Math.toRadians((lat+a.lat+b.lat)/3.0));
        double x=(lon-a.lon)*cos*scale, y=(lat-a.lat)*scale;
        double vx=(b.lon-a.lon)*cos*scale,vy=(b.lat-a.lat)*scale;
        double len=vx*vx+vy*vy;
        double t=len>0?Math.max(0,Math.min(1,(x*vx+y*vy)/len)):0;
        return Math.hypot(x-t*vx,y-t*vy);
    }
}
