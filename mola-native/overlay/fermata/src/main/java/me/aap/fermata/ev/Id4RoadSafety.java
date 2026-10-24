package me.aap.fermata.ev;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Opt-in OpenStreetMap fixed speed-camera map layer.
 *
 * Radarbot's APK does not grant a license to its private database or APIs.
 * This retrieves ONLY publicly ODbL-licensed OSM "highway=speed_camera"
 * nodes in a bounded viewport. A public Overpass instance is rate-limited.
 * It is NOT a radar detector, a verified enforcement database or a source
 * for active speed limits, mobile radars or speed-camera-free routes.
 */
final class Id4RoadSafety {
    private static final String OVERPASS=
        "https://overpass-api.de/api/interpreter?data=";
    private static final int LIMIT=120,RESPONSE_LIMIT=320000;
    private Id4RoadSafety(){}

    static boolean validViewport(double south,double west,double north,double east){
        return Double.isFinite(south)&&Double.isFinite(north)&&
            Double.isFinite(west)&&Double.isFinite(east) &&
            south>=35&&north<=43&&west>=25&&east<=46&&
            south<north&&west<east&&(north-south)<=.42&&(east-west)<=.42;
    }

    static String queryText(double south,double west,double north,double east){
        if(!validViewport(south,west,north,east))
            throw new IllegalArgumentException("Zoom in to load fixed cameras");
        // 'out body 120' bounds the response; no relations or national crawls.
        return String.format(Locale.US,
            "[out:json][timeout:13];node[\"highway\"=\"speed_camera\"]"+
            "(%.6f,%.6f,%.6f,%.6f);out body 120;",
            south,west,north,east);
    }

    static JSONObject retrieve(double south,double west,double north,double east)
        throws Exception{
        String query=queryText(south,west,north,east);
        String request=OVERPASS+URLEncoder.encode(query,"UTF-8");
        HttpURLConnection c=(HttpURLConnection)new URL(request).openConnection();
        c.setConnectTimeout(8000);c.setReadTimeout(15000);
        c.setInstanceFollowRedirects(false);
        c.setRequestProperty("Accept","application/json");
        c.setRequestProperty("User-Agent",
            "ID4YolArkadasim-RoadSafety/0.1 (+https://github.com/AlpTimucin-cpu/ID4-Media-Hub)");
        try{
            int status=c.getResponseCode();
            if(status!=200)throw new IllegalStateException("Overpass HTTP "+status);
            ByteArrayOutputStream buffer=new ByteArrayOutputStream();
            try(InputStream in=c.getInputStream()){
                byte[] chunk=new byte[4096];int count;
                while((count=in.read(chunk))!=-1){
                    if(buffer.size()+count>RESPONSE_LIMIT)
                        throw new IllegalStateException("Too many OSM records");
                    buffer.write(chunk,0,count);
                }
            }
            JSONObject json=new JSONObject(buffer.toString("UTF-8"));
            JSONArray items=new JSONArray(),elements=json.optJSONArray("elements");
            if(elements!=null)for(int i=0;i<elements.length()&&items.length()<LIMIT;i++){
                JSONObject node=elements.optJSONObject(i);
                if(node==null||!"node".equals(node.optString("type")))continue;
                long id=node.optLong("id",0);
                double lat=node.optDouble("lat",Double.NaN);
                double lon=node.optDouble("lon",Double.NaN);
                if(id<=0||!Double.isFinite(lat)||!Double.isFinite(lon)||
                   lat<south||lat>north||lon<west||lon>east)continue;
                JSONObject tags=node.optJSONObject("tags");
                if(tags==null||!"speed_camera".equals(tags.optString("highway")))
                    continue;
                JSONObject item=new JSONObject().put("id",id)
                    .put("lat",lat).put("lon",lon).put("kind","fixed")
                    .put("source","OpenStreetMap");
                String maxspeed=tags.optString("maxspeed","");
                if(maxspeed.matches("[0-9]{1,3}"))
                    item.put("cameraTaggedSpeedKmh",
                        Math.min(180,Integer.parseInt(maxspeed)));
                items.put(item);
            }
            return new JSONObject().put("items",items)
                .put("queriedAt",System.currentTimeMillis())
                .put("source","© OpenStreetMap contributors (ODbL)")
                .put("verifiedLive",false)
                .put("coverage","map viewport only")
                .put("detail","Topluluk kayıtları; eksik veya güncelliğini yitirmiş olabilir.");
        }finally{c.disconnect();}
    }
}
