package com.qxdnzbl.shuangjichuan;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.*;
import android.util.Base64;
import android.util.Log;

import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.nearby.connection.*;

import org.json.JSONObject;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class TransferService extends Service {
  public static final String ACTION_CHANGED="com.qxdnzbl.shuangjichuan.CHANGED";
  private static final int NOTIFY_ID=31021, MESSAGE_ID=31022, DELIVERY_ID=31023, WAITING_ID=31024;
  private static final String ACK_PREFIX="__SJC_ACK_V1__:";
  private static final String INCOMING_CHANNEL="dual_incoming_v1", RECEIPT_CHANNEL="dual_delivery_v1";
  private static final String SERVICE_ID="com.qxdnzbl.shuangjichuan.v5";
  private PairCrypto crypto;
  private static final String[] RELAYS={
    "https://oppo-iphone-transfer-qr.onrender.com",
    "https://oppo-iphone-transfer.onrender.com"
  };

  private final ExecutorService io=Executors.newCachedThreadPool();
  private final ExecutorService payloadIo=Executors.newSingleThreadExecutor();
  private final ExecutorService sendIo=Executors.newSingleThreadExecutor();
  private final AtomicBoolean flushQueued=new AtomicBoolean();
  private final ScheduledExecutorService timer=Executors.newScheduledThreadPool(1);
  private final Set<String> endpoints=new CopyOnWriteArraySet<>();
  private final Set<String> requested=new CopyOnWriteArraySet<>();
  private final Map<Long,String> outgoing=new ConcurrentHashMap<>();
  private final Map<Long,File> temporaryEncrypted=new ConcurrentHashMap<>();
  private final Set<String> inFlight=ConcurrentHashMap.newKeySet();
  private final Map<Long,Payload> incomingFiles=new ConcurrentHashMap<>();
  private final Map<Long,FileMeta> incomingMeta=new ConcurrentHashMap<>();
  private final Map<Long,String> incomingPeers=new ConcurrentHashMap<>();
  private final Set<Long> completedIncoming=ConcurrentHashMap.newKeySet();

  private volatile boolean running=true;
  private volatile boolean nearbyStarted=false;
  private volatile long nextPairBeacon=0L;
  private volatile long lastWaitingAlert=0L;
  private volatile long nearbyStartedAt=0L;
  private TransferDb db;
  private ConnectionsClient nearby;
  private String deviceId;
  private String room;

  static class FileMeta {
    final String id,name;
    final long created,size;
    FileMeta(String id,String name,long created,long size){
      this.id=id;this.name=name;this.created=created;this.size=size;
    }
  }

  @Override public void onCreate(){
    super.onCreate();
    db=new TransferDb(this);

    SharedPreferences p=getSharedPreferences("dual",MODE_PRIVATE);
    crypto=new PairCrypto(this);
    deviceId=crypto.self();
    createChannel();
    startForeground(NOTIFY_ID,notification(crypto.configured()?"专属设备加密同步":"等待设备配对"));
    if(!crypto.configured()){
      stopSelf();
      return;
    }
    room=crypto.room();
    nearby=Nearby.getConnectionsClient(this);
    boolean ciNearbyOnly=(getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE)!=0
      && p.getBoolean("ci_nearby_only",false);
    if(!ciNearbyOnly)for(String relay:RELAYS)io.execute(()->relayReceiveLoop(relay));
    timer.scheduleWithFixedDelay(()->{
      requestFlush();
      if(!crypto.paired()&&System.currentTimeMillis()>=nextPairBeacon){
        nextPairBeacon=System.currentTimeMillis()+12000L;
        io.execute(this::sendPairBeacon);
      }
    },250,500,TimeUnit.MILLISECONDS);
    timer.scheduleWithFixedDelay(this::tryStartNearby,0,5,TimeUnit.SECONDS);
    io.execute(this::sendPairBeacon);
  }

  public static void start(Context c){
    Intent i=new Intent(c,TransferService.class);
    try{
      if(Build.VERSION.SDK_INT>=26) c.startForegroundService(i);
      else c.startService(i);
    }catch(Exception ignored){}
  }

  public static void wake(Context c){
    start(c);
  }

  private void createChannel(){
    if(Build.VERSION.SDK_INT>=26){
      NotificationManager manager=getSystemService(NotificationManager.class);
      NotificationChannel ch=new NotificationChannel(
        "transfer","双机传后台同步",NotificationManager.IMPORTANCE_LOW);
      ch.setDescription("自动接收另一台手机的消息和文件");
      manager.createNotificationChannel(ch);
      manager.createNotificationChannel(new NotificationChannel(
        INCOMING_CHANNEL,"收到新消息",NotificationManager.IMPORTANCE_HIGH));
      manager.createNotificationChannel(new NotificationChannel(
        RECEIPT_CHANNEL,"送达状态",NotificationManager.IMPORTANCE_DEFAULT));
    }
  }

  private Notification notification(String text){
    Notification.Builder b=Build.VERSION.SDK_INT>=26
      ? new Notification.Builder(this,"transfer")
      : new Notification.Builder(this);
    return b.setContentTitle("双机传")
      .setContentText(text)
      .setSmallIcon(android.R.drawable.stat_sys_upload_done)
      .setOngoing(true)
      .build();
  }

  private void eventNotification(int id,String channel,String title,String body){
    if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return;
    if(id==MESSAGE_ID&&MainActivity.chatForeground)return;
    Intent intent=new Intent(this,MainActivity.class);
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
    PendingIntent open=PendingIntent.getActivity(this,0,intent,
      PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    Notification.Builder builder=Build.VERSION.SDK_INT>=26
      ?new Notification.Builder(this,channel):new Notification.Builder(this);
    Notification notification=builder.setSmallIcon(android.R.drawable.stat_notify_chat)
      .setContentTitle(title).setContentText(body).setAutoCancel(true)
      .setContentIntent(open).setCategory(Notification.CATEGORY_MESSAGE)
      .setVisibility(Notification.VISIBILITY_PRIVATE).build();
    getSystemService(NotificationManager.class).notify(id,notification);
  }

  private void notifyIncoming(String kind,String name){
    eventNotification(MESSAGE_ID,INCOMING_CHANNEL,"双机传 · 收到新内容",
      "file".equals(kind)?(PhotoImages.isPhoto(name)?"收到一张图片":"收到一个文件"):"收到一条消息");
  }
  private void notifyDelivered(){
    eventNotification(DELIVERY_ID,RECEIPT_CHANNEL,"双机传 · 已送达","另一台手机已收到并保存");
  }
  private void receivedAck(String id){
    if(id!=null&&id.matches("[0-9a-fA-F-]{36}")&&db.markDelivered(id)){
      changed();notifyDelivered();
    }
  }
  private void ackNearby(String endpoint,String id){
    try{
      JSONObject ack=new JSONObject().put("op","ack").put("sender",deviceId).put("id",id);
      String frame=crypto.encrypt(ack);
      nearby.sendPayload(endpoint,Payload.fromBytes(frame.getBytes(StandardCharsets.UTF_8)));
    }catch(Exception e){Log.w("DualDelivery","encrypted Nearby ack "+e);}
  }

  private void ackRelay(String base,String id){
    io.execute(()->{
      try{
        JSONObject ack=new JSONObject().put("op","ack").put("sender",deviceId).put("id",id);
        relaySendText(crypto.encrypt(ack),base);
      }catch(Exception e){Log.w("DualDelivery","encrypted relay ack "+e);}
    });
  }

  private boolean relaySendText(String frame,String preferred){
    ArrayList<String> options=new ArrayList<>();
    if(preferred!=null)options.add(preferred);
    for(String relay:RELAYS)if(!options.contains(relay))options.add(relay);
    for(String base:options){
      HttpURLConnection c=null;
      try{
        URL url=new URL(base+"/api/dual/send/"+room+"/"+url(deviceId));
        c=(HttpURLConnection)url.openConnection();
        c.setRequestMethod("POST");c.setDoOutput(true);
        c.setConnectTimeout(7000);c.setReadTimeout(18000);
        c.setRequestProperty("x-dual-token",crypto.relayToken());
        c.setRequestProperty("x-dual-kind","text");
        c.setRequestProperty("x-dual-id",UUID.randomUUID().toString());
        c.setRequestProperty("x-dual-created",String.valueOf(System.currentTimeMillis()));
        byte[] bytes=frame.getBytes(StandardCharsets.UTF_8);
        c.setFixedLengthStreamingMode(bytes.length);
        try(OutputStream out=c.getOutputStream()){out.write(bytes);}
        if(c.getResponseCode()==200)return true;
      }catch(Exception e){Log.w("DualE2E","relay control "+e);}
      finally{if(c!=null)c.disconnect();}
    }
    return false;
  }

  private void sendPairBeacon(){
    if(!running||crypto==null||!crypto.configured())return;
    try{
      JSONObject pair=new JSONObject().put("op","pair").put("sender",deviceId);
      String frame=crypto.encrypt(pair);
      if(nearby!=null)for(String ep:endpoints)
        nearby.sendPayload(ep,Payload.fromBytes(frame.getBytes(StandardCharsets.UTF_8)));
      relaySendText(frame,null);
    }catch(Exception e){Log.w("DualE2E","pair beacon "+e);}
  }

  private void handleSecureFrame(String frame,String nearbyEndpoint,String relayBase){
    try{
      JSONObject j=crypto.decrypt(frame);
      String sender=j.optString("sender","");
      String op=j.optString("op","");
      if("pair".equals(op)){
        boolean wasPaired=crypto.paired();
        if(crypto.acceptPeer(sender)){
          if(!wasPaired){
            changed();
            io.execute(this::sendPairBeacon);
          }
        }
        return;
      }
      if(!crypto.fromPeer(sender)){
        Log.w("DualE2E","Rejected message from non-paired sender");
        return;
      }
      if("ack".equals(op)){
        receivedAck(j.optString("id"));
      }else if("text".equals(op)){
        String id=j.getString("id");
        if(!id.matches("[0-9a-fA-F-]{36}"))return;
        boolean fresh=db.addText(id,false,j.optString("text"),
          j.optLong("created",System.currentTimeMillis()),"received");
        if(fresh){changed();notifyIncoming("text","");}
        if(nearbyEndpoint!=null)ackNearby(nearbyEndpoint,id);
        else if(relayBase!=null)ackRelay(relayBase,id);
      }else if("file_meta".equals(op)&&nearbyEndpoint!=null){
        long pid=j.getLong("payloadId");
        incomingMeta.put(pid,new FileMeta(j.optString("id"),
          safeName(j.optString("name","文件")),j.optLong("created"),
          j.optLong("size")));
        incomingPeers.put(pid,nearbyEndpoint);
        tryFinalizeIncoming(pid);
      }
    }catch(Exception e){Log.w("DualE2E","Unreadable or tampered encrypted frame: "+e.getClass().getSimpleName());}
  }

  private void changed(){
    sendBroadcast(new Intent(ACTION_CHANGED).setPackage(getPackageName()));
  }

  private synchronized void setLinkState(String state){
    SharedPreferences p=getSharedPreferences("dual",MODE_PRIVATE);
    if(state.equals(p.getString("link_state",""))) return;
    p.edit().putString("link_state",state).apply();
    changed();
  }

  private boolean hasNearbyPermissions(){
    if(Build.VERSION.SDK_INT>=33){
      return checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE)==PackageManager.PERMISSION_GRANTED
        && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)==PackageManager.PERMISSION_GRANTED
        && checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)==PackageManager.PERMISSION_GRANTED
        && checkSelfPermission(Manifest.permission.NEARBY_WIFI_DEVICES)==PackageManager.PERMISSION_GRANTED;
    }
    if(Build.VERSION.SDK_INT>=31){
      return checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE)==PackageManager.PERMISSION_GRANTED
        && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)==PackageManager.PERMISSION_GRANTED
        && checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)==PackageManager.PERMISSION_GRANTED
        && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED;
    }
    if(Build.VERSION.SDK_INT>=29){
      return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED;
    }
    return checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED;
  }

  private synchronized void tryStartNearby(){
    if(!crypto.configured()||!hasNearbyPermissions())return;
    long now=System.currentTimeMillis();
    if(nearbyStarted){
      if(!endpoints.isEmpty()) return;
      if(now-nearbyStartedAt<15000) return;
      try{nearby.stopAdvertising();nearby.stopDiscovery();}catch(Exception ignored){}
      nearbyStarted=false;
      requested.clear();
    }
    nearbyStarted=true;
    nearbyStartedAt=now;
    setLinkState("searching");
    Strategy strategy=Strategy.P2P_POINT_TO_POINT;

    nearby.startAdvertising(
      deviceId,
      SERVICE_ID,
      lifecycle,
      new AdvertisingOptions.Builder().setStrategy(strategy).build()
    ).addOnFailureListener(e->{
      nearbyStarted=false;
      setLinkState("searching");
      Log.w("DualNearby","advertise "+e);
    });

    nearby.startDiscovery(
      SERVICE_ID,
      discovery,
      new DiscoveryOptions.Builder().setStrategy(strategy).build()
    ).addOnFailureListener(e->{
      nearbyStarted=false;
      setLinkState("searching");
      Log.w("DualNearby","discover "+e);
    });
  }

  private final EndpointDiscoveryCallback discovery=new EndpointDiscoveryCallback(){
    @Override public void onEndpointFound(String endpointId,DiscoveredEndpointInfo info){
      String peerName=info.getEndpointName()==null?"":info.getEndpointName();
      if(crypto.paired()&&!crypto.peer().equals(peerName))return;
      Log.i("DualNearby","found "+endpointId+" peer="+peerName);
      if(deviceId.compareTo(peerName)<0){
        requestPeer(endpointId);
      }else{
        io.execute(()->{
          try{Thread.sleep(1800);}catch(InterruptedException ignored){}
          if(running&&!endpoints.contains(endpointId)) requestPeer(endpointId);
        });
      }
    }

    @Override public void onEndpointLost(String endpointId){
      requested.remove(endpointId);
    }
  };

  private void requestPeer(String endpointId){
    if(!requested.add(endpointId)) return;
    setLinkState("connecting");
    nearby.requestConnection(deviceId,endpointId,lifecycle)
      .addOnFailureListener(e->{
        requested.remove(endpointId);
        setLinkState("searching");
      });
  }

  private final ConnectionLifecycleCallback lifecycle=new ConnectionLifecycleCallback(){
    @Override public void onConnectionInitiated(String endpointId,ConnectionInfo info){
      if(crypto.paired()&&!crypto.peer().equals(info.getEndpointName())){
        nearby.rejectConnection(endpointId);
        return;
      }
      setLinkState("connecting");
      nearby.acceptConnection(endpointId,payloads)
        .addOnFailureListener(e->requested.remove(endpointId));
    }

    @Override public void onConnectionResult(String endpointId,ConnectionResolution resolution){
      if(resolution.getStatus().isSuccess()){
        Log.i("DualNearby","connected "+endpointId);
        endpoints.add(endpointId);
        setLinkState("nearby");
        io.execute(TransferService.this::sendPairBeacon);
        requestFlush();
      }else{
        requested.remove(endpointId);
      }
    }

    @Override public void onDisconnected(String endpointId){
      endpoints.remove(endpointId);
      requested.remove(endpointId);
      outgoing.clear();
      inFlight.clear();
      nearbyStarted=false;
      setLinkState("searching");
      timer.execute(TransferService.this::tryStartNearby);
    }
  };

  private final PayloadCallback payloads=new PayloadCallback(){
    @Override public void onPayloadReceived(String endpointId,Payload payload){
      if(running)payloadIo.execute(()->handlePayloadReceived(endpointId,payload));
    }

    private void handlePayloadReceived(String endpointId,Payload payload){
      try{
        if(payload.getType()==Payload.Type.BYTES){
          String frame=new String(payload.asBytes(),StandardCharsets.UTF_8);
          handleSecureFrame(frame,endpointId,null);
        }else if(payload.getType()==Payload.Type.FILE){
          incomingPeers.put(payload.getId(),endpointId);
          incomingFiles.put(payload.getId(),payload);
          tryFinalizeIncoming(payload.getId());
        }
      }catch(Exception e){Log.w("DualNearby","encrypted payload "+e);}
    }

    @Override public void onPayloadTransferUpdate(
      String endpointId,
      PayloadTransferUpdate update
    ){
      if(running)payloadIo.execute(()->handlePayloadTransferUpdate(endpointId,update));
    }

    private void handlePayloadTransferUpdate(String endpointId,PayloadTransferUpdate update){
      long id=update.getPayloadId();

      if(update.getStatus()==PayloadTransferUpdate.Status.SUCCESS){
        String msgId=outgoing.remove(id);
        if(msgId!=null){
          inFlight.remove(msgId);
          File temp=temporaryEncrypted.remove(id);if(temp!=null)temp.delete();
          db.markSent(msgId);
          changed();
          return;
        }

        completedIncoming.add(id);
        tryFinalizeIncoming(id);
      }else if(
        update.getStatus()==PayloadTransferUpdate.Status.FAILURE
        || update.getStatus()==PayloadTransferUpdate.Status.CANCELED
      ){
        String failedMsg=outgoing.remove(id);
        if(failedMsg!=null)inFlight.remove(failedMsg);
        File temp=temporaryEncrypted.remove(id);if(temp!=null)temp.delete();
        Payload p=incomingFiles.remove(id);
        incomingMeta.remove(id);
        incomingPeers.remove(id);
        completedIncoming.remove(id);
        if(p!=null) p.close();
      }
    }
  };

  private void tryFinalizeIncoming(long pid){
    if(!completedIncoming.contains(pid))return;
    FileMeta outer=incomingMeta.get(pid);
    Payload payload=incomingFiles.get(pid);
    if(outer==null||payload==null)return;
    try{
      PairCrypto.ReceivedFile received;
      try(InputStream in=getContentResolver().openInputStream(payload.asFile().asUri())){
        received=crypto.decryptFile(this,in);
      }
      JSONObject m=received.metadata;
      String sender=m.optString("sender","");
      String id=m.optString("id","");
      String name=safeName(m.optString("name","文件"));
      if(!crypto.fromPeer(sender)||!id.matches("[0-9a-fA-F-]{36}")
          ||!id.equals(outer.id)||!name.equals(outer.name)||received.file.length()!=outer.size){
        received.file.delete();throw new SecurityException("Nearby encrypted metadata mismatch");
      }
      commitReceivedFile(received,id,name,m.optLong("created"));
      String endpoint=incomingPeers.get(pid);
      if(endpoint!=null)ackNearby(endpoint,id);
      try{getContentResolver().delete(payload.asFile().asUri(),null,null);}catch(Exception ignored){}
    }catch(Exception e){Log.w("DualE2E","nearby file rejected "+e);}
    finally{
      incomingFiles.remove(pid);incomingMeta.remove(pid);incomingPeers.remove(pid);
      completedIncoming.remove(pid);payload.close();
    }
  }

  private void commitReceivedFile(PairCrypto.ReceivedFile receive,String id,String name,long created)throws Exception{
    if(db.hasMessage(id)){receive.file.delete();return;}
    File folder=new File(getFilesDir(),"incoming");folder.mkdirs();
    File destination=new File(folder,id+"_"+safeName(name));
    if(destination.exists()&&!destination.delete()){receive.file.delete();throw new IOException("destination busy");}
    if(!receive.file.renameTo(destination)){receive.file.delete();throw new IOException("unable to commit file");}
    boolean fresh=db.addFile(id,false,name,destination.getAbsolutePath(),
      destination.length(),created,"received");
    if(fresh){changed();notifyIncoming("file",name);}
  }

  private void requestFlush(){
    if(!running||!flushQueued.compareAndSet(false,true))return;
    try{
      sendIo.execute(()->{
        try{if(running)flushPending();}
        catch(Exception e){Log.w("DualNearby","flush "+e);}
        finally{flushQueued.set(false);}
      });
    }catch(RejectedExecutionException ignored){flushQueued.set(false);}
  }

  private void flushPending(){

    List<TransferDb.Msg> pending=db.pending();
    if(pending.isEmpty()) return;
    if(pending.get(0).createdAt<System.currentTimeMillis()-30000L
        &&System.currentTimeMillis()-lastWaitingAlert>30*60*1000L){
      lastWaitingAlert=System.currentTimeMillis();
      eventNotification(WAITING_ID,RECEIPT_CHANNEL,"双机传 · 等待送达",
        "对方尚未收到，内容已保存在本机，正在自动重试");
    }

    boolean ciNearbyOnly=
      (getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE)!=0
      && getSharedPreferences("dual",MODE_PRIVATE).getBoolean("ci_nearby_only",false);

    if(!endpoints.isEmpty()){
      String endpoint=endpoints.iterator().next();
      for(TransferDb.Msg m:pending){
        if(sendNearby(endpoint,m)) continue;
        if(!ciNearbyOnly) tryRelaySend(m);
      }
    }else{
      if(!ciNearbyOnly){
        for(TransferDb.Msg m:pending){
          tryRelaySend(m);
        }
      }
    }
  }

  private boolean sendNearby(String endpoint,TransferDb.Msg m){
    if(!crypto.paired())return false;
    if(inFlight.contains(m.id))return true;
    try{
      if("text".equals(m.kind)){
        JSONObject j=new JSONObject().put("op","text").put("sender",deviceId)
          .put("id",m.id).put("created",m.createdAt)
          .put("text",m.text==null?"":m.text);
        byte[] bytes=crypto.encrypt(j).getBytes(StandardCharsets.UTF_8);
        if(bytes.length>30000)return false;
        Payload p=Payload.fromBytes(bytes);
        inFlight.add(m.id);outgoing.put(p.getId(),m.id);
        nearby.sendPayload(endpoint,p)
          .addOnFailureListener(e->{outgoing.remove(p.getId());inFlight.remove(m.id);});
        return true;
      }
      File src=new File(m.filePath==null?"":m.filePath);
      if(!src.isFile())return false;
      JSONObject info=new JSONObject().put("op","file").put("sender",deviceId)
        .put("id",m.id).put("created",m.createdAt)
        .put("name",safeName(m.fileName)).put("size",src.length())
        .put("sha",PairCrypto.shaFile(src));
      File encrypted=crypto.encryptFile(this,src,info);
      Payload fp=Payload.fromFile(encrypted);
      fp.setFileName("encrypted.sjc");
      JSONObject meta=new JSONObject().put("op","file_meta").put("sender",deviceId)
        .put("id",m.id).put("created",m.createdAt)
        .put("name",safeName(m.fileName)).put("size",src.length())
        .put("payloadId",fp.getId());
      String frame=crypto.encrypt(meta);
      nearby.sendPayload(endpoint,Payload.fromBytes(frame.getBytes(StandardCharsets.UTF_8)));
      inFlight.add(m.id);outgoing.put(fp.getId(),m.id);
      temporaryEncrypted.put(fp.getId(),encrypted);
      nearby.sendPayload(endpoint,fp)
        .addOnFailureListener(e->{
          outgoing.remove(fp.getId());inFlight.remove(m.id);
          File temp=temporaryEncrypted.remove(fp.getId());if(temp!=null)temp.delete();
        });
      return true;
    }catch(Exception e){Log.w("DualE2E","Nearby send "+e);return false;}
  }

  private void relayReceiveLoop(String base){
    while(running){
      HttpURLConnection c=null;

      try{
        URL u=new URL(
          base+"/api/dual/receive/"+room+"/"+url(deviceId));

        c=(HttpURLConnection)u.openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(65000);
        c.setRequestProperty("x-dual-token",SECRET);
        c.setUseCaches(false);

        int code=c.getResponseCode();
        if(code==200){
          if(endpoints.isEmpty())setLinkState("relay");
          String kind=c.getHeaderField("X-Dual-Kind");
          String id=c.getHeaderField("X-Dual-Id");
          long created=parseLong(
            c.getHeaderField("X-Dual-Created"),
            System.currentTimeMillis()
          );

          if("text".equals(kind)){
            byte[] b=readLimited(c.getInputStream(),1024*1024);
            String message=new String(b,StandardCharsets.UTF_8);
            if(message.startsWith(ACK_PREFIX)){
              receivedAck(message.substring(ACK_PREFIX.length()));
            }else{
              boolean fresh=db.addText(id,false,message,created,"received");
              if(fresh){changed();notifyIncoming("text","");}
              ackRelay(base,id);
            }
          }else if("file".equals(kind)){
            String name=decodeName(
              c.getHeaderField("X-Dual-File-Name"));

            File dir=new File(getFilesDir(),"incoming");
            dir.mkdirs();

            File dst=new File(dir,id+"_"+safeName(name));
            if(!db.hasMessage(id)){
              File tmp=new File(dir,id+".receiving");
              try(InputStream in=c.getInputStream();OutputStream out=new FileOutputStream(tmp)){
                copyLimited(in,out,500L*1024*1024);
              }catch(Exception failed){tmp.delete();throw failed;}
              if(!tmp.renameTo(dst)){tmp.delete();throw new IOException("cannot finalize incoming");}
              boolean fresh=db.addFile(id,false,name,dst.getAbsolutePath(),
                dst.length(),created,"received");
              if(fresh){changed();notifyIncoming("file",name);}
            }
            ackRelay(base,id);
          }
        }
      }catch(Exception ignored){
      }finally{
        if(c!=null) c.disconnect();
      }

      if(running){
        try{
          Thread.sleep(350);
        }catch(InterruptedException ignored){}
      }
    }
  }

  private boolean tryRelaySend(TransferDb.Msg m){
    for(String base:RELAYS){
      HttpURLConnection c=null;

      try{
        URL u=new URL(
          base+"/api/dual/send/"+room+"/"+url(deviceId));

        c=(HttpURLConnection)u.openConnection();
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setConnectTimeout(5000);
        c.setReadTimeout(65000);
        c.setUseCaches(false);

        c.setRequestProperty("x-dual-token",SECRET);
        c.setRequestProperty("x-dual-kind",m.kind);
        c.setRequestProperty("x-dual-id",m.id);
        c.setRequestProperty(
          "x-dual-created",
          String.valueOf(m.createdAt)
        );

        if("text".equals(m.kind)){
          byte[] b=(m.text==null?"":m.text)
            .getBytes(StandardCharsets.UTF_8);

          c.setFixedLengthStreamingMode(b.length);

          try(OutputStream out=c.getOutputStream()){
            out.write(b);
          }
        }else{
          File file=new File(m.filePath==null?"":m.filePath);
          if(!file.isFile()) return false;

          c.setRequestProperty(
            "x-dual-file-name",
            Base64.encodeToString(
              safeName(m.fileName).getBytes(StandardCharsets.UTF_8),
              Base64.NO_WRAP
            )
          );

          c.setFixedLengthStreamingMode(file.length());

          try(
            InputStream in=new FileInputStream(file);
            OutputStream out=c.getOutputStream()
          ){
            copy(in,out);
          }
        }

        int code=c.getResponseCode();

        if(code>=200&&code<300){
          if(endpoints.isEmpty())setLinkState("relay");
          getSystemService(NotificationManager.class).cancel(WAITING_ID);
          db.markSent(m.id);
          changed();
          return true;
        }
        Log.w("DualRelay","send "+m.kind+" status "+code);

        if(code==409) continue;
      }catch(Exception e){
        Log.w("DualRelay","send retry "+e.getClass().getSimpleName());
      }finally{
        if(c!=null) c.disconnect();
      }
    }

    return false;
  }

  @Override public int onStartCommand(
    Intent intent,
    int flags,
    int startId
  ){
    if(running&&!timer.isShutdown())timer.execute(this::tryStartNearby);
    requestFlush();
    return START_STICKY;
  }

  @Override public void onDestroy(){
    running=false;

    try{
      nearby.stopAdvertising();
      nearby.stopDiscovery();
      nearby.stopAllEndpoints();
    }catch(Exception ignored){}

    timer.shutdownNow();
    payloadIo.shutdownNow();
    sendIo.shutdownNow();
    io.shutdownNow();
    super.onDestroy();
  }

  @Override public IBinder onBind(Intent intent){
    return null;
  }

  private static String safeName(String s){
    String v=(s==null?"文件":s)
      .replace("/","_")
      .replace("\\","_")
      .trim();

    return v.isEmpty()?"文件":v;
  }

  private static String sha256(String s){
    try{
      byte[] d=MessageDigest
        .getInstance("SHA-256")
        .digest(s.getBytes(StandardCharsets.UTF_8));

      StringBuilder b=new StringBuilder();

      for(byte x:d){
        b.append(String.format(
          Locale.US,
          "%02x",
          x&255
        ));
      }

      return b.toString();
    }catch(Exception e){
      throw new RuntimeException(e);
    }
  }

  private static String url(String s){
    try{
      return URLEncoder.encode(s,"UTF-8");
    }catch(Exception e){
      return s;
    }
  }

  private static long parseLong(String s,long fallback){
    try{
      return Long.parseLong(s);
    }catch(Exception e){
      return fallback;
    }
  }

  private static String decodeName(String b64){
    try{
      return new String(
        Base64.decode(b64,Base64.DEFAULT),
        StandardCharsets.UTF_8
      );
    }catch(Exception e){
      return "文件";
    }
  }

  private static void copy(
    InputStream in,
    OutputStream out
  )throws IOException{
    byte[] b=new byte[65536];
    int n;

    while((n=in.read(b))>0){
      out.write(b,0,n);
    }
  }

  private static byte[] readLimited(
    InputStream in,
    long max
  )throws IOException{
    ByteArrayOutputStream out=new ByteArrayOutputStream();
    copyLimited(in,out,max);
    return out.toByteArray();
  }

  private static void copyLimited(
    InputStream in,
    OutputStream out,
    long max
  )throws IOException{
    byte[] b=new byte[65536];
    long total=0;
    int n;

    while((n=in.read(b))>0){
      total+=n;
      if(total>max) throw new IOException("too large");
      out.write(b,0,n);
    }
  }
}
