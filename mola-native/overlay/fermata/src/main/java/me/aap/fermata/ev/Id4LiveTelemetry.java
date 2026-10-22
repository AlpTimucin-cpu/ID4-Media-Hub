package me.aap.fermata.ev;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.bluetooth.BluetoothSocket;
import android.os.Handler;
import android.os.Looper;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/** Foreground-only, read-only ELM327 observer for the ID.4 overview.
 * Bluetooth must already be paired. Does not share an active socket with Car Scanner.
 * Every displayed measurement is tied to a fresh, positive ECU answer.
 */
final class Id4LiveTelemetry {
    interface Listener { void onData(Data data); }
    static final class Data {
        String status="OBD bağlı değil";
        String soc="—", voltage="—", batteryTemp="—", chargeMode="—", cooling="—";
        long updatedAt=0L;
        boolean connected=false;
        Data copy(){
            Data d=new Data();d.status=status;d.soc=soc;d.voltage=voltage;
            d.batteryTemp=batteryTemp;d.chargeMode=chargeMode;d.cooling=cooling;
            d.updatedAt=updatedAt;d.connected=connected;return d;
        }
    }
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ScheduledExecutorService watchdog=Executors.newSingleThreadScheduledExecutor();
    private final Listener listener;
    private final Context context;
    private volatile boolean enabled=false;
    private volatile BluetoothSocket socket;
    private Thread thread;
    private Data data=new Data();
    private static final UUID SPP=UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    Id4LiveTelemetry(Context context,Listener listener){
        this.context=context.getApplicationContext();this.listener=listener;
    }
    synchronized void start(BluetoothDevice device){
        stop();
        enabled=true;
        publish("ELM327 bağlanıyor...",false);
        thread=new Thread(()->loop(device),"Mola-ID4-dashboard-readonly");
        thread.start();
    }
    private synchronized void publish(String status,boolean connected){
        data.status=status;
        data.connected=connected;
        if(!connected) {
            data.soc="—";data.voltage="—";data.batteryTemp="—";
            data.chargeMode="—";data.cooling="—";data.updatedAt=0L;
        }
        Data result=data.copy();
        main.post(()->listener.onData(result));
    }
    private synchronized void measurement(String did,String response){
        if(!enabled)return;
        String decoded=null;
        for(Id4MebDecoder.Sensor sensor:Id4MebDecoder.SENSORS){
            if(did.equals(sensor.did)) {decoded=Id4MebDecoder.decode(sensor,response);break;}
        }
        if(decoded==null)return;
        switch(did){
            case "028C":
                data.soc=decoded;
                Double verified=Id4MebDecoder.bmsSoc(response);
                if(verified!=null)Id4EnergyData.saveSoc(context,verified,System.currentTimeMillis());
                break;
            case "1E3B": data.voltage=decoded;break;
            case "2A0B": data.batteryTemp=decoded;break;
            case "7448": data.chargeMode=decoded;break;
            case "189D": data.cooling=decoded;break;
            default:return;
        }
        data.updatedAt=System.currentTimeMillis();
        Data result=data.copy();main.post(()->listener.onData(result));
    }
    private void loop(BluetoothDevice device){
        try{
            BluetoothSocket s=device.createRfcommSocketToServiceRecord(SPP);
            socket=s;s.connect();
            InputStream in=s.getInputStream();OutputStream out=s.getOutputStream();
            for(String cmd:new String[]{"ATZ","ATE0","ATL0","ATS0","ATH0","ATSP7","ATSH17FC007B","ATCRA17FE007B"}){
                String answer=ask(in,out,cmd);
                if(answer.toUpperCase(Locale.ROOT).contains("ERROR")||
                    answer.contains("?"))throw new IllegalStateException("Adaptör komutu reddedildi");
            }
            publish("ELM327 bağlı · veriler okunuyor",true);
            String[] dids={"028C","7448","1E3B","2A0B","189D"};
            while(enabled){
                for(String did:dids){
                    if(!enabled)break;
                    String answer=ask(in,out,"22"+did);
                    measurement(did,answer);
                    Thread.sleep(90);
                }
                Thread.sleep(1600);
            }
        }catch(Exception e){
            if(enabled)publish("OBD verisi alınamadı: "+e.getClass().getSimpleName(),false);
        }finally{stop();}
    }
    private String ask(InputStream in,OutputStream out,String command)throws Exception{
        final BluetoothSocket current=socket;
        ScheduledFuture<?> timeout=watchdog.schedule(()->{
            try{if(current!=null)current.close();}catch(Exception ignored){}
        },8,TimeUnit.SECONDS);
        try{
            out.write((command+"\r").getBytes(StandardCharsets.US_ASCII));out.flush();
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();
            while(enabled && bytes.size()<8192){
                int c=in.read();if(c<0)throw new IllegalStateException("Adapter bağlantısı koptu");
                if(c=='>')return bytes.toString("US-ASCII");
                bytes.write(c);
            }
            throw new IllegalStateException("OBD yanıtı eksik");
        }finally{timeout.cancel(false);}
    }
    synchronized void stop(){
        enabled=false;
        BluetoothSocket s=socket;socket=null;
        if(s!=null)try{s.close();}catch(Exception ignored){}
        if(thread!=null && thread!=Thread.currentThread())thread.interrupt();
        thread=null;
        publish("OBD bağlı değil",false);
    }
    void close(){stop();watchdog.shutdownNow();}
}
