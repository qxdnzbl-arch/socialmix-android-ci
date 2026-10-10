package com.nzbl.pocket;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.Build;

final class SyncNotifier {
    private static final String CHANNEL = "suishoucun_sync_events";
    private static final String PREFS = "suishoucun_notifications";
    private static final int UPLOADED = 201, RECEIVED = 202, FAILURE = 203;

    static void requestPermission(Activity activity) {
        if(Build.VERSION.SDK_INT>=33 &&
                activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){
            android.content.SharedPreferences p=activity.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
            if(p.getBoolean("permission_requested",false))return;
            p.edit().putBoolean("permission_requested",true).apply();
            activity.requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},3821);
        }
    }
    static void uploaded(Context context,long revision){
        show(context,UPLOADED,revision,"已保存到云端","记录和图片已完整上传。另一台手机同步后即可收到。");
    }
    static void received(Context context,long revision){
        show(context,RECEIVED,revision,"随手存 · 已收到新内容","另一台手机的记录和图片已同步到本机。");
    }
    static void failed(Context context,String reason){
        long now=System.currentTimeMillis();
        android.content.SharedPreferences p=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
        if(now-p.getLong("last_fail",0)<3600_000L)return;
        p.edit().putLong("last_fail",now).apply();
        show(context,FAILURE,now,"随手存 · 同步未完成","本机内容仍安全保存。网络恢复后会自动重试。");
    }
    private static void show(Context context,int type,long version,String title,String text){
        Context app=context.getApplicationContext();
        if(Build.VERSION.SDK_INT>=33&&
           app.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return;
        android.content.SharedPreferences p=app.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
        String lastKey="revision_"+type;
        if(type!=FAILURE&&version<=p.getLong(lastKey,0))return;
        NotificationManager nm=(NotificationManager)app.getSystemService(Context.NOTIFICATION_SERVICE);
        if(nm==null)return;
        nm.createNotificationChannel(new NotificationChannel(CHANNEL,"多端同步提醒",NotificationManager.IMPORTANCE_DEFAULT));
        Intent intent=new Intent(app,MainActivity.class)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pending=PendingIntent.getActivity(app,type,intent,
            PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification notification=new Notification.Builder(app,CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle(title).setContentText(text)
            .setAutoCancel(true).setOnlyAlertOnce(type==UPLOADED).setContentIntent(pending)
            .setCategory(Notification.CATEGORY_STATUS).build();
        nm.notify(type,notification);
        if(type!=FAILURE)p.edit().putLong(lastKey,version).apply();
    }
}
