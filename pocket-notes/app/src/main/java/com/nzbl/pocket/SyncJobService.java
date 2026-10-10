package com.nzbl.pocket;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import java.io.File;

public final class SyncJobService extends JobService {
    private static final int JOB_ID=76321;
    private static final long PERIOD_MS=15*60*1000L;

    static void schedule(Context context){
        JobScheduler scheduler=(JobScheduler)context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if(scheduler==null)return;
        if(scheduler.getPendingJob(JOB_ID)!=null)return;
        JobInfo info=new JobInfo.Builder(JOB_ID,new ComponentName(context,SyncJobService.class))
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setPersisted(true)
            .setPeriodic(PERIOD_MS).build();
        scheduler.schedule(info);
    }
    static void cancel(Context context){
        JobScheduler scheduler=(JobScheduler)context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if(scheduler!=null)scheduler.cancel(JOB_ID);
    }
    @Override public boolean onStartJob(JobParameters params){
        if(MainActivity.foreground){jobFinished(params,false);return false;}
        new Thread(()->{
            try{
                Store store=new Store(this);
                if(!store.ready)return;
                CloudSync sync=new CloudSync(this,store,new File(getFilesDir(),"background.img"));
                if(!sync.enabled())return;
                boolean dirty=sync.dirty();long deliveredBefore=sync.deliveredRevision();
                CloudSync.Result result=sync.syncNow(false);
                if(result.ok){
                    if(result.changed)SyncNotifier.received(this,result.revision);
                    if(sync.deliveredRevision()>deliveredBefore)SyncNotifier.delivered(this,sync.deliveredRevision());
                    if(dirty&&!sync.dirty())SyncNotifier.uploaded(this,result.revision);
                }else SyncNotifier.failed(this,result.message);
            }catch(Exception e){
                SyncNotifier.failed(this,"同步暂时不可用");
            }finally{jobFinished(params,false);}
        },"Suishoucun-Background-Sync").start();
        return true;
    }
    @Override public boolean onStopJob(JobParameters params){return true;}
}
