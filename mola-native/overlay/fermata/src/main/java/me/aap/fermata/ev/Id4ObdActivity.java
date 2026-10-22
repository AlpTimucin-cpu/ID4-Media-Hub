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
import java.text.SimpleDateFormat;
import java.util.Date;
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
    private final Id4ChargeHistory chargeHistory=new Id4ChargeHistory();
    private TextView chargeSummary;
    private TextView pastSessions;
    private final TextView[] fields = new TextView[Id4MebDecoder.SENSORS.size()];
    private final long[] updatedAt = new long[Id4MebDecoder.SENSORS.size()];

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xff091e2a);
        root.setPadding(14,25,14,20);
        setContentView(root);
        TextView title = label("ID.4 • CANLI ARAÇ VERİLERİ",22);
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
        Id4ChargeHistoryStore.load(this,chargeHistory);
        ScrollView sc = new ScrollView(this); root.addView(sc,new LinearLayout.LayoutParams(-1,0,1));
        readings=new LinearLayout(this); readings.setOrientation(LinearLayout.VERTICAL);
        sc.addView(readings);
        TextView overviewTitle=label("⚡  ŞARJ OTURUMLARI",18);
        overviewTitle.setTextColor(0xff6de4d2);
        readings.addView(overviewTitle);
        chargeSummary=label("Şarj algılanmadı · OBD gerekli",15);
        readings.addView(chargeSummary);
        pastSessions=label("Kayıt yok",14);
        readings.addView(pastSessions);
        Button clearHistory=new Button(this);
        clearHistory.setText("Yerel şarj geçmişini sil");
        clearHistory.setOnClickListener(v->new AlertDialog.Builder(this)
            .setTitle("Şarj kayıtlarını sil")
            .setMessage("Yalnızca bu uygulamadaki tarih ve SoC kayıtları silinir. Araç verisine müdahale edilmez.")
            .setNegativeButton("Vazgeç",null)
            .setPositiveButton("Sil",(d,w)->{
                chargeHistory.clear();
                Id4ChargeHistoryStore.clear(this);
                updateChargeHistory();
            }).show());
        readings.addView(clearHistory);
        updateChargeHistory();
        String category="";
        for(int i=0;i<Id4MebDecoder.SENSORS.size();i++){
            Id4MebDecoder.Sensor item=Id4MebDecoder.SENSORS.get(i);
            if(!category.equals(item.category)){
                category=item.category;
                TextView heading=label("▰ "+category.toUpperCase(Locale.forLanguageTag("tr-TR")),16);
                heading.setTextColor(0xff6de4d2);
                readings.addView(heading);
            }
            fields[i]=label(item.name+": —  (okunmadı)",16);
            readings.addView(fields[i]);
        }
        readings.addView(label("Bu değerler yalnızca ECU'dan doğrulanmış yanıt geldikten sonra görüntülenir. Anlık güç, kesin SOH ve menzil için ek VIN/protokol doğrulaması gereklidir.",13));
    }
    private void updateChargeHistory(){
        if(chargeSummary==null || pastSessions==null || isFinishing())return;
        if(chargeHistory.isCharging()){
            double soc=chargeHistory.observedSocChange();
            String change=Double.isFinite(soc)?
                String.format(Locale.forLanguageTag("tr-TR")," · +%.1f%% SoC",soc):"";
            chargeSummary.setText("Şarj oturumu sürüyor • "+chargeHistory.method()+change);
        }else chargeSummary.setText("Aktif şarj oturumu yok");
        List<Id4ChargeHistory.Session> history=chargeHistory.completed();
        if(history.isEmpty()){pastSessions.setText("Henüz tamamlanmış şarj kaydı yok.");return;}
        SimpleDateFormat fmt=new SimpleDateFormat("dd.MM.yyyy HH:mm",Locale.forLanguageTag("tr-TR"));
        StringBuilder b=new StringBuilder("SON ŞARJLAR (yalnızca bu cihaz)\n");
        for(Id4ChargeHistory.Session h:history){
            b.append(fmt.format(new Date(h.endedAtMs)))
             .append(" • ").append(h.chargingMethod)
             .append(" • ").append(String.format(Locale.forLanguageTag("tr-TR"),
                 "%.1f%% → %.1f%% (%.1f puan)",h.socStart,h.socEnd,h.socChangePercent()))
             .append(" • ").append(h.durationMinutes()).append(" dk\n");
        }
        b.append("KWh ve TL bu kayıtlardan kesin olarak ölçülmez. Fatura için operatör sayacı gerekir.");
        pastSessions.setText(b.toString());
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
                for(int i=0;i<Id4MebDecoder.SENSORS.size() && active;i++){
                    Id4MebDecoder.Sensor item=Id4MebDecoder.SENSORS.get(i);
                    String answer=exchange(in,out,"22"+item.did);
                    String result=Id4MebDecoder.decode(item,answer);
                    long sampleTime=System.currentTimeMillis();
                    if("028C".equals(item.did)){
                        Double soc=Id4MebDecoder.bmsSoc(answer);
                        if(soc!=null){
                            chargeHistory.acceptSoc(sampleTime,soc);
                            Id4EnergyData.saveSoc(this,soc,sampleTime);
                        }
                    }else if("7448".equals(item.did)){
                        Integer mode=Id4MebDecoder.chargeModeCode(answer);
                        if(mode!=null){
                            Id4ChargeHistory.Session saved=chargeHistory.acceptMode(sampleTime,mode);
                            if(saved!=null)Id4ChargeHistoryStore.save(this,chargeHistory);
                            ui.post(this::updateChargeHistory);
                        }
                    }
                    final int idx=i;
                    if(result!=null){
                        updatedAt[i]=System.currentTimeMillis();
                        final String line=item.name+": "+result+" • CANLI";
                        ui.post(()->{if(active&&!isFinishing())fields[idx].setText(line);});
                    }else if(System.currentTimeMillis()-updatedAt[i]>25000){
                        final String line=item.name+": —  (ECU yanıtı yok)";
                        ui.post(()->{if(active&&!isFinishing())fields[idx].setText(line);});
                    }
                    Thread.sleep(100);
                }
                Thread.sleep(2000);
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
    private synchronized void disconnect(){
        active=false;
        chargeHistory.telemetryLost();
        ui.post(this::updateChargeHistory);
        ui.post(()->{
            if(!isFinishing())for(int i=0;i<fields.length;i++){
                if(fields[i]!=null){
                    Id4MebDecoder.Sensor item=Id4MebDecoder.SENSORS.get(i);
                    fields[i].setText(item.name+": — (bağlantı yok)");
                }
            }
        });
        BluetoothSocket s=socket;socket=null;
        if(s!=null)try{s.close();}catch(Exception ignored){}
        if(worker!=null && worker!=Thread.currentThread())worker.interrupt();
        worker=null;
    }
    @Override protected void onDestroy(){
        disconnect();timeout.shutdownNow();super.onDestroy();
    }
}
