package me.aap.fermata.ev;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Local storage of at most 30 completed charge sessions.
 * No VIN, GPS, VW account, OBD raw frames or passwords are persisted.
 * A session stores only start/end timestamps and SoC percentages.
 */
public final class Id4ChargeHistoryStore {
    private static final String PREFERENCES="id4_local_charge_history";
    private static final String KEY="completed_v1";
    private Id4ChargeHistoryStore(){}

    public static void load(Context context,Id4ChargeHistory history){
        String raw=context.getSharedPreferences(PREFERENCES,Context.MODE_PRIVATE)
            .getString(KEY,"[]");
        try {
            JSONArray rows=new JSONArray(raw);
            for(int i=0;i<rows.length() && i<30;i++){
                JSONObject row=rows.optJSONObject(i);
                if(row==null)continue;
                Id4ChargeHistory.Session entry=new Id4ChargeHistory.Session(
                    row.optLong("start",-1),row.optLong("end",-1),
                    row.optDouble("soc_start",Double.NaN),
                    row.optDouble("soc_end",Double.NaN),row.optString("method",""));
                history.importCompleted(entry);
            }
        }catch(Exception ignored){
            // Corrupt or out-of-date local data cannot become live vehicle data.
        }
    }
    public static void save(Context context,Id4ChargeHistory history){
        JSONArray rows=new JSONArray();
        for(Id4ChargeHistory.Session entry:history.completed()){
            if(!entry.plausible())continue;
            JSONObject row=new JSONObject();
            try{
                row.put("start",entry.startedAtMs);
                row.put("end",entry.endedAtMs);
                row.put("soc_start",entry.socStart);
                row.put("soc_end",entry.socEnd);
                row.put("method",entry.chargingMethod);
                rows.put(row);
            }catch(Exception ignored){}
        }
        SharedPreferences p=context.getSharedPreferences(PREFERENCES,Context.MODE_PRIVATE);
        p.edit().putString(KEY,rows.toString()).apply();
    }
    public static void clear(Context context){
        context.getSharedPreferences(PREFERENCES,Context.MODE_PRIVATE)
            .edit().remove(KEY).apply();
    }
}
