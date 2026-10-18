package me.aap.fermata.ev;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/** Optional, read-only VW MEB OBD monitor. Must be validated on the user's specific ID.4.
 *  No ECU write, diagnostic reset, coding, fault erasure or vehicle control.
 *  Uses a paired classic Bluetooth ELM327; do not run Car Scanner simultaneously.
 */
public final class Id4ObdActivity extends Activity {
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ScheduledExecutorService timeout = Executors.newSingleThreadScheduledExecutor();
    private final UUID spp = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private volatile boolean active;
    private volatile BluetoothSocket socket;
    private TextView status;
    private LinearLayout readings;
    private Thread worker;
    private static final String[] DIDS = {"028C","1E3B","2A0B","7448","189D"};
    private final String[] labels = {"BMS SoC", "HV gerilim", "Batarya sıcaklığı",
      "Çalışma/şarj modu", "Batarya soğutma çıkışı"};
    private final TextView[] fields = new TextView[DIDS.length];

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xff091e2a);
        root.setPadding(14,25,14,20);
        setContentView(root);
        TextView title = label("ID.4 • OBD CANLI ÖLÇÜM",22);
        root.addView(title);
        root.addView(label("Yalnızca eşleştirilmiş Bluetooth Classic ELM327. Car Scanner'ı önce kapat. Ölçümler VIN bazında doğrulanmadı.",13));
        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        Button connect = new Button(this); connect.setText("OBD bağlan");
        connect.setOnClickListener(v -> selectAdapter());
        buttons.addView(connect,new LinearLayout.LayoutParams(0,55,1));
        Button stop = new Button(this); stop.setText("Durdur");
        stop.setOnClickListener(v -> { disconnect(); showStatus("Bağlantı durduruldu"); });
        buttons.addView(stop,new LinearLayout.LayoutParams(0,55,1));
        root.addView(buttons);
        status=label("OBD bağlı değil; canlı ölçüm yok.",15);
        root.addView(status);
        ScrollView sc = new ScrollView(this); root.addView(sc,new LinearLayout.LayoutParams(-1,0,1));
        readings=new LinearLayout(this); readings.setOrientation(LinearLayout.VERTICAL);
        sc.addView(readings);
        for(int i=0;i<DIDS.length;i++){
            fields[i]=label(labels[i]+": —",19);
            readings.addView(fields[i]);
        }
        readings.addView(label("Bu değerler yalnızca ECU'dan doğrulanmış yanıt geldikten sonra görüntülenir. Anlık güç, kesin SOH ve menzil için ek VIN/protokol doğrulaması gereklidir.",13));
    }
    private TextView label(String s,int sp){
        TextView t=new TextView(this);t.setTextColor(Color.WHITE);t.setTextSize(sp);
        t.setText(s);t.setPadding(12,14,12,14);return t;
    }
    private void showStatus(String text){ui.post(()->{if(!isFinishing()&&status!=null)status.setText(text);});}
    @SuppressWarnings("deprecation")
    private void selectAdapter(){
        if(Build.VERSION.SDK_INT>=31 && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT},701);return;
        }
        try {
            BluetoothAdapter adapter=BluetoothAdapter.getDefaultAdapter();
            if(adapter==null){showStatus("Bluetooth donanımı bulunamadı");return;}
            if(!adapter.isEnabled()){showStatus("Önce telefonda Bluetooth'u aç");return;}
            Set<BluetoothDevice> bonded=adapter.getBondedDevices();
            if(bonded.isEmpty()){showStatus("Önce ELM327 adaptörünü Android Bluetooth ayarlarında eşleştir");return;}
            List<BluetoothDevice> devices=new ArrayList<>(bonded);
            String[] names=new String[devices.size()];
            for(int i=0;i<names.length;i++){
                String n=devices.get(i).getName();names[i]=n==null?"Eşleştirilmiş adaptör "+(i+1):n;
            }
            new AlertDialog.Builder(this).setTitle("ELM327 adaptörünü seç")
                .setItems(names,(dialog,index)->connect(devices.get(index)))
                .setNegativeButton("Vazgeç",null).show();
        }catch(SecurityException e){showStatus("Bluetooth izinleri gerekli");}
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){
        super.onRequestPermissionsResult(request,permissions,results);
        if(request==701 && results.length>0 && results[0]==PackageManager.PERMISSION_GRANTED) selectAdapter();
        else if(request==701) showStatus("Bluetooth izni verilmedi");
    }
    private synchronized void connect(BluetoothDevice device){
        disconnect();active=true;
        worker=new Thread(()->readLoop(device),"Mola-OBD-read-only");worker.start();
    }
    private void readLoop(BluetoothDevice device){
        try {
            showStatus("Bluetooth bağlantısı kuruluyor...");
            BluetoothSocket s=device.createRfcommSocketToServiceRecord(spp);
            socket=s;s.connect();
            InputStream in=s.getInputStream();OutputStream out=s.getOutputStream();
            for(String command:new String[]{"ATZ","ATE0","ATL0","ATS0","ATH0","ATSP7","ATSH17FC007B","ATCRA17FE007B"}){
                String response=exchange(in,out,command);
                if(response.indexOf('?')>=0 || response.toUpperCase(Locale.ROOT).contains("ERROR"))
                    throw new IllegalStateException("Adaptör komutu desteklemiyor: "+command);
            }
            showStatus("Bağlandı: VW MEB salt-okunur deneysel ölçüm");
            while(active){
                for(int i=0;i<DIDS.length && active;i++){
                    String answer=exchange(in,out,"22"+DIDS[i]);
                    String result=decode(DIDS[i],answer);
                    if(result!=null){
                        final int idx=i; final String label=labels[i]+": "+result;
                        ui.post(()->{if(active&&!isFinishing())fields[idx].setText(label);});
                    }
                }
                Thread.sleep(1800);
            }
        }catch(Exception e){
            if(active)showStatus("OBD ölçümü durdu: "+e.getClass().getSimpleName());
        }finally{disconnect();}
    }
    private String exchange(InputStream in,OutputStream out,String command)throws Exception{
        BluetoothSocket current=socket;
        ScheduledFuture<?> cancel=timeout.schedule(()->{try{if(current!=null)current.close();}catch(Exception ignored){}},8,TimeUnit.SECONDS);
        try{
            out.write((command+"\r").getBytes(StandardCharsets.US_ASCII));out.flush();
            ByteArrayOutputStream b=new ByteArrayOutputStream();
            while(active && b.size()<8192){
                int c=in.read();if(c<0)throw new IllegalStateException("Soket kapandı");
                if(c=='>')return b.toString("US-ASCII");
                b.write(c);
            }
            throw new IllegalStateException("Eksik adaptör yanıtı");
        }finally{cancel.cancel(false);}
    }
    private String decode(String did,String response){
        if(response==null)return null;
        String compact=response.toUpperCase(Locale.ROOT).replaceAll("[^0-9A-F]","");
        int at=compact.indexOf("62"+did);
        if(at<0)return null;
        String hex=compact.substring(at+6);
        try{
            double v;String unit;
            switch(did){
                case "028C":v=Integer.parseInt(hex.substring(0,2),16)/2.5;unit="%";if(v<0||v>100)return null;break;
                case "1E3B":v=Integer.parseInt(hex.substring(0,4),16)/4.0;unit="V";if(v<150||v>550)return null;break;
                case "2A0B":v=Integer.parseInt(hex.substring(0,2),16)/2.0-40;unit="°C";if(v< -40||v>100)return null;break;
                case "189D":v=Integer.parseInt(hex.substring(0,4),16)/64.0;unit="°C";if(v< -40||v>100)return null;break;
                case "7448":v=Integer.parseInt(hex.substring(0,2),16);unit="";if(v==4)return "AC şarj";if(v==6)return "DC şarj";if(v==1)return "Sürüş";if(v==0)return "Bekleme";return null;
                default:return null;
            }
            return String.format(Locale.forLanguageTag("tr-TR"),"%.1f %s",v,unit);
        }catch(Exception ignored){return null;}
    }
    private synchronized void disconnect(){
        active=false;
        BluetoothSocket s=socket;socket=null;
        if(s!=null)try{s.close();}catch(Exception ignored){}
        if(worker!=null && worker!=Thread.currentThread())worker.interrupt();
        worker=null;
    }
    @Override protected void onDestroy(){
        disconnect();timeout.shutdownNow();super.onDestroy();
    }
}
