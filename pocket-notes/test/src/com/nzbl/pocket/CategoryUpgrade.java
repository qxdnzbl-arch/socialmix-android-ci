package com.nzbl.pocket;

import android.app.*;
import android.content.*;
import android.os.*;
import org.json.*;
import java.io.*;
import java.security.MessageDigest;

/** Runs against the previously shipped APK, then the replacement APK, with data intact. */
public class CategoryUpgrade extends Acceptance {
    boolean seed;
    @Override public void onCreate(Bundle args){seed="legacy".equals(args.getString("stage"));super.onCreate(args);}
    @Override public void onStart(){
        Bundle result=new Bundle();
        try{
            dir=new File(getTargetContext().getExternalFilesDir(null),"qa");dir.mkdirs();
            Intent i=new Intent(Intent.ACTION_MAIN).setClassName(getTargetContext(),"com.nzbl.pocket.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            a=(MainActivity)startActivitySync(i);idle();
            if(seed){
                check(getTargetContext().getPackageManager().getPackageInfo("com.nzbl.pocket",0).versionCode==1,"seed runs on previously delivered version 1 APK");
                check(a.store.categories.size()==3,"old release really has seeded categories");
                byte[] original=Store.read(new ParcelFileDescriptor.AutoCloseInputStream(getUiAutomation().executeShellCommand("cat /sdcard/Download/reference-fixture.png")),1024*1024);
                File image=new File(a.store.photos,"upgrade-test.img");try(FileOutputStream out=new FileOutputStream(image)){out.write(original);}
                main(()->a.newNote("hair"));main(()->{a.titleInput.setText("更新前的真实记录");a.bodyInput.setText("中文和换行都保留。\n图片与常用状态也保留。");a.editing.images.add(image.getName());a.editing.pinned=true;a.renderEditorPhotos();});tap("保存");
                check(a.store.change(()->{for(Store.Category c:a.store.categories)if("daily".equals(c.id))c.name="自建分类";}),"legacy renamed category persisted before upgrade");
                main(()->a.newNote("daily"));main(()->{a.titleInput.setText("改过分类名的记录");a.bodyInput.setText("这个分类是用户改过的，必须保留。");});tap("保存");
                main(()->a.newNote("prompt"));main(()->{a.titleInput.setText("更新前的草稿");a.bodyInput.setText("未保存的输入也不能丢失。");});tap("取消");tapDialog("保留草稿");
                JSONObject before=new JSONObject().put("noteId",a.store.notes.get(0).id).put("secondId",a.store.notes.get(1).id).put("imageSha",sha(original));
                write("upgrade-before.json",before);
                JSONObject disk=new JSONObject(new String(Store.read(a.store.file.openRead(),16*1024*1024),"UTF-8"));check(!disk.has("categoryMode"),"upgrade input is actual old data format");shot("10-before-upgrade");
            }else{
                check(getTargetContext().getPackageManager().getPackageInfo("com.nzbl.pocket",0).versionCode==2,"replacement installs over existing APK");
                JSONObject before=new JSONObject(new String(Store.read(new FileInputStream(new File(dir,"upgrade-before.json")),1024*1024),"UTF-8"));
                check(a.store.ready&&a.store.notes.size()==2,"upgrade reads all original notes");
                Store.Note n=a.store.find(before.getString("noteId"));Store.Note second=a.store.find(before.getString("secondId"));
                check(n!=null&&"更新前的真实记录".equals(n.title)&&"中文和换行都保留。\n图片与常用状态也保留。".equals(n.body),"upgrade preserves exact title body and stable note ID");
                check(n.pinned&&n.images.size()==1,"upgrade preserves pinned status and image attachment");
                check(before.getString("imageSha").equals(sha(Store.read(new FileInputStream(new File(a.store.photos,n.images.get(0))),1024*1024))),"upgrade preserves original image bytes");
                check("".equals(n.category),"old example category removed without deleting its note");
                check(a.store.categories.size()==1&&"自建分类".equals(a.store.categories.get(0).name)&&"daily".equals(a.store.categories.get(0).id),"user-renamed category preserved with original ID");
                check(second!=null&&"daily".equals(second.category),"record inside renamed category keeps its assignment");
                check(a.store.draft!=null&&"更新前的草稿".equals(a.store.draft.title)&&"未保存的输入也不能丢失。".equals(a.store.draft.body)&&"".equals(a.store.draft.category),"upgrade preserves draft and clears seeded assignment");
                check(find(a.root,"理发")==null&&find(a.root,"提示词")==null&&find(a.root,"日常")==null,"upgraded home has no fixed example categories");shot("11-after-upgrade");
                main(()->a.categoriesDialog());tapDialog("自建分类");tapDialog("删除分类");tapDialog("删除分类");
                check(a.store.categories.isEmpty()&&a.store.notes.size()==2&&"".equals(second.category),"previously protected category is deletable with records retained");
                Store reloaded=new Store(getTargetContext());check(reloaded.ready&&reloaded.categories.isEmpty()&&reloaded.notes.size()==2&&reloaded.draft!=null,"migrated state persists and default categories stay absent");
                tap("继续上次没写完的记录");check("未保存的输入也不能丢失。".equals(a.bodyInput.getText().toString()),"legacy draft resumes after update");tap("取消");tapDialog("保留草稿");shot("12-upgrade-no-categories");
            }
            write(seed?"upgrade-seed.json":"upgrade-acceptance.json",new JSONObject().put("passed",true).put("checks",new JSONArray(checks)));
            result.putString("stream","\nPASS: "+checks.size()+" actual "+(seed?"legacy seed":"update preservation")+" checks\n");finish(Activity.RESULT_OK,result);
        }catch(Throwable e){try{shot("upgrade-failure");try(PrintWriter out=new PrintWriter(new File(dir,"upgrade-failure.txt"))){e.printStackTrace(out);}}catch(Exception ignored){}result.putString("stream","\nFAIL: "+e+"\n");finish(Activity.RESULT_CANCELED,result);}
    }
    void write(String name,JSONObject value)throws Exception{try(FileOutputStream out=new FileOutputStream(new File(dir,name))){out.write(value.toString(2).getBytes("UTF-8"));}}
    String sha(byte[] data)throws Exception{StringBuilder value=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(data))value.append(String.format("%02x",b&255));return value.toString();}
}
