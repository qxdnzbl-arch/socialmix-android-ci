package com.nzbl.pocket;

import android.content.*;
import android.util.Base64;
import javax.crypto.*;
import javax.crypto.spec.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.text.SimpleDateFormat;
import java.util.*;
import org.json.*;

final class CloudSync {
    static final String RPC_URL="https://suishoucun-sync.floot.app/_api/sync";
    static final int MAX_ASSET=66*1024*1024,MAX_MANIFEST=8*1024*1024,CHUNK_BYTES=256*1024;
    final Context context;
    final Store store;
    final File backgroundFile;
    final SharedPreferences prefs;
    interface Progress {void onStage(String stage);}
    volatile Progress progress;
    void report(String stage){Progress p=progress;if(p!=null)p.onStage(stage);}


    static final class Result {
        final boolean ok,changed;
        final String message;
        final long revision;
        Result(boolean o,boolean c,String m,long r){ok=o;changed=c;message=m;revision=r;}
    }
    static final class Remote {
        final long revision;
        final File encrypted;
        Remote(long r,File f){revision=r;encrypted=f;}
    }
    static final class Conflict extends IOException {
        final long revision;
        Conflict(long r){super("revision conflict");revision=r;}
    }

    CloudSync(Context c,Store s,File background){
        context=c.getApplicationContext();store=s;backgroundFile=background;
        prefs=context.getSharedPreferences("cloud_sync",Context.MODE_PRIVATE);
    }
    boolean enabled(){return validCode(code());}
    String code(){return prefs.getString("code","");}
    long revision(){return prefs.getLong("revision",0);}
    long lastSync(){return prefs.getLong("last_sync",0);}
    boolean dirty(){return prefs.getBoolean("dirty",false);}
    boolean backgroundDirty(){return prefs.getBoolean("background_dirty",false);}
    String lastSyncLabel(){
        long t=lastSync();if(t<=0)return "还没有完成同步";
        return "最近同步 "+new SimpleDateFormat("M月d日 HH:mm",Locale.CHINA).format(new Date(t));
    }
    boolean hasLocalData(){
        synchronized(store){return !store.categories.isEmpty()||!store.notes.isEmpty()||store.draft!=null||(backgroundFile!=null&&backgroundFile.isFile());}
    }
    void markDirty(boolean background){
        if(!enabled())return;
        synchronized(prefs){
            SharedPreferences.Editor e=prefs.edit().putLong("change_seq",prefs.getLong("change_seq",0)+1).putBoolean("dirty",true);
            if(background)e.putBoolean("background_dirty",true);
            e.commit();
        }
    }
    String beginNew(){
        String c=generateCode();
        prefs.edit().putString("code",c).putLong("revision",0).putBoolean("dirty",true)
            .putBoolean("background_dirty",backgroundFile!=null&&backgroundFile.isFile()).putLong("last_sync",0).apply();
        return c;
    }
    void disconnect(){
        prefs.edit().clear().apply();
    }
    Result connectExisting(String raw){
        String next=normalizeCode(raw);
        if(!validCode(next))return new Result(false,false,"同步码不正确。",0);
        String oldCode=code();long oldRev=revision(),oldLast=lastSync();
        boolean oldDirty=dirty(),oldBg=backgroundDirty();
        prefs.edit().putString("code",next).putLong("revision",0)
            .putBoolean("dirty",hasLocalData()).putBoolean("background_dirty",backgroundFile!=null&&backgroundFile.isFile())
            .putLong("last_sync",0).commit();
        Result result=syncNow(true);
        if(!result.ok){
            SharedPreferences.Editor e=prefs.edit().clear();
            if(validCode(oldCode))e.putString("code",oldCode).putLong("revision",oldRev).putLong("last_sync",oldLast)
                .putBoolean("dirty",oldDirty).putBoolean("background_dirty",oldBg);
            e.commit();
        }
        return result;
    }

    synchronized Result syncNow(boolean requireExisting){
        if(!enabled())return new Result(false,false,"尚未开启多端同步。",0);
        String syncCode=code(),auth;
        try{auth=authForCode(syncCode);}catch(Exception e){return new Result(false,false,"同步码无效。",0);}
        boolean changed=false;
        for(int attempt=0;attempt<3;attempt++){
            File remoteFile=null;
            try{
                report("正在检查云端记录…");
                long changeSeq=prefs.getLong("change_seq",0);
                Remote remote=getManifest(auth,revision());
                remoteFile=remote.encrypted;
                long localRevision=revision();
                boolean localDirty=dirty(),bgDirty=backgroundDirty();

                if(requireExisting&&remote.revision==0){
                    return new Result(false,false,"没有找到这份云端数据，请检查同步码。",0);
                }
                if(remote.revision<localRevision){
                    prefs.edit().putLong("revision",remote.revision).putBoolean("dirty",true).commit();
                    localRevision=remote.revision;localDirty=true;
                }
                if(remote.revision>localRevision){
                    JSONObject manifest=decryptManifest(remote.encrypted,syncCode);
                    JSONObject collection=manifest.getJSONObject("collection");
                    report("正在下载另一台手机的记录…");
                    ensureRemotePhotos(auth,syncCode,collection);
                    boolean shouldMerge=localDirty||dirty()||prefs.getLong("change_seq",0)!=changeSeq;
                    synchronized(store){
                        if(shouldMerge)store.mergeSync(collection);else store.replaceSync(collection);
                    }
                    if(!bgDirty)applyRemoteBackground(auth,syncCode,manifest.optBoolean("background",false),manifest.optString("backgroundHash",""));
                    localRevision=remote.revision;changed=true;
                    prefs.edit().putLong("revision",localRevision).commit();
                    if(!shouldMerge){
                        synchronized(prefs){
                            boolean edited=prefs.getLong("change_seq",0)!=changeSeq;
                            prefs.edit().putBoolean("dirty",edited).putBoolean("background_dirty",edited&&prefs.getBoolean("background_dirty",false))
                                .putLong("last_sync",System.currentTimeMillis()).commit();
                        }
                        return new Result(true,true,"同步完成",localRevision);
                    }
                    localDirty=true;
                }
                if(localDirty||remote.revision==0){
                    uploadLocalAssets(auth,syncCode,bgDirty||remote.revision==0);
                    JSONObject manifest=localManifest();
                    File encrypted=encryptManifest(manifest,syncCode);
                    try{
                        long next=putManifest(auth,remote.revision,encrypted);
                        synchronized(prefs){
                            boolean edited=prefs.getLong("change_seq",0)!=changeSeq;
                            prefs.edit().putLong("revision",next).putBoolean("dirty",edited)
                                .putBoolean("background_dirty",edited&&prefs.getBoolean("background_dirty",false))
                                .putLong("last_sync",System.currentTimeMillis()).commit();
                        }
                        return new Result(true,changed,"同步完成",next);
                    }finally{encrypted.delete();}
                }
                prefs.edit().putLong("last_sync",System.currentTimeMillis()).commit();
                return new Result(true,changed,"同步完成",remote.revision);
            }catch(Conflict conflict){
                prefs.edit().putLong("revision",Math.min(revision(),conflict.revision)).commit();
            }catch(Exception e){
                return new Result(false,changed,readableError(e),revision());
            }finally{if(remoteFile!=null)remoteFile.delete();}
        }
        return new Result(false,changed,"云端数据刚刚有变化，请再同步一次。",revision());
    }

    JSONObject localManifest() throws Exception{
        JSONObject collection;
        synchronized(store){collection=new JSONObject(store.json().toString());}
        boolean hasBackground=backgroundFile!=null&&backgroundFile.isFile();
        return new JSONObject().put("format","suishoucun-cloud").put("version",1)
            .put("collection",collection).put("background",hasBackground)
            .put("backgroundHash",hasBackground?fileHash(backgroundFile):"");
    }
    void uploadLocalAssets(String auth,String syncCode,boolean forceBackground)throws Exception{
        JSONObject collection;
        synchronized(store){collection=new JSONObject(store.json().toString());}
        LinkedHashSet<String> names=imageNames(collection);
        JSONArray objects=new JSONArray();for(String name:names)objects.put("asset/"+name);
        if(backgroundFile!=null&&backgroundFile.isFile())objects.put("asset/background");
        report("正在核对 "+names.size()+" 张图片…");
        HashSet<String> available=new HashSet<>();
        if(objects.length()>0){
            JSONObject status=rpc("suishoucun_sync_assets_status_v3",auth,new JSONObject().put("p_objects",objects));
            JSONArray list=status.getJSONArray("complete");for(int i=0;i<list.length();i++)available.add(list.getString(i));
        }
        int i=0;for(String name:names){
            i++;File file=new File(store.photos,name);
            if(!file.isFile())throw new IOException("本地图片缺失");
            if(!available.contains("asset/"+name)){
                report("正在上传图片 "+i+"/"+names.size()+"…");
                uploadEncryptedAsset(auth,syncCode,name,file);
            }
        }
        if(backgroundFile!=null&&backgroundFile.isFile()&&(forceBackground||!available.contains("asset/background"))){
            report("正在上传背景图…");
            uploadEncryptedAsset(auth,syncCode,"background",backgroundFile);
        }
    }
    void ensureRemotePhotos(String auth,String syncCode,JSONObject collection)throws Exception{
        LinkedHashSet<String> names=imageNames(collection);int i=0;
        for(String name:names){
            i++;File target=new File(store.photos,name);
            if(target.isFile()&&target.length()>0)continue;
            report("正在接收图片 "+i+"/"+names.size()+"…");
            downloadEncryptedAsset(auth,syncCode,name,target);
        }
    }
    void applyRemoteBackground(String auth,String syncCode,boolean hasBackground,String expectedHash)throws Exception{
        if(!hasBackground){if(backgroundFile!=null&&backgroundFile.isFile())backgroundFile.delete();return;}
        if(backgroundFile!=null&&backgroundFile.isFile()&&expectedHash.matches("[0-9a-f]{64}")
            &&expectedHash.equals(fileHash(backgroundFile)))return;
        report("正在接收背景图…");
        File tmp=new File(context.getCacheDir(),"sync-bg-"+UUID.randomUUID()+".tmp");
        try{
            downloadEncryptedAsset(auth,syncCode,"background",tmp);
            if(backgroundFile.exists()&&!backgroundFile.delete())throw new IOException("背景图无法替换");
            if(!tmp.renameTo(backgroundFile)){copyFile(tmp,backgroundFile,MAX_ASSET);tmp.delete();}
        }finally{tmp.delete();}
    }

    static String fileHash(File file)throws Exception{
        MessageDigest d=MessageDigest.getInstance("SHA-256");
        try(FileInputStream in=new FileInputStream(file)){byte[] buf=new byte[65536];int n;while((n=in.read(buf))!=-1)d.update(buf,0,n);}
        StringBuilder out=new StringBuilder();for(byte b:d.digest())out.append(String.format(Locale.ROOT,"%02x",b&255));return out.toString();
    }
    static LinkedHashSet<String> imageNames(JSONObject collection)throws Exception{
        LinkedHashSet<String> names=new LinkedHashSet<>();
        JSONArray notes=collection.getJSONArray("notes");
        for(int i=0;i<notes.length();i++)addImages(names,notes.getJSONObject(i));
        if(!collection.isNull("draft"))addImages(names,collection.getJSONObject("draft"));
        return names;
    }
    static void addImages(Set<String> names,JSONObject note)throws Exception{
        JSONArray a=note.getJSONArray("images");
        for(int i=0;i<a.length();i++){String s=a.getString(i);if(!s.matches("[a-zA-Z0-9_.-]+"))throw new IOException("图片名无效");names.add(s);}
    }

    File encryptManifest(JSONObject manifest,String code)throws Exception{
        byte[] bytes=manifest.toString().getBytes(StandardCharsets.UTF_8);
        if(bytes.length>MAX_MANIFEST)throw new IOException("记录数据过大");
        File plain=new File(context.getCacheDir(),"sync-manifest-"+UUID.randomUUID()+".json");
        File encrypted=new File(context.getCacheDir(),"sync-manifest-"+UUID.randomUUID()+".bin");
        try(FileOutputStream out=new FileOutputStream(plain)){out.write(bytes);}
        try{encryptFile(plain,encrypted,keyForCode(code),"SSM1");return encrypted;}finally{plain.delete();}
    }
    JSONObject decryptManifest(File encrypted,String code)throws Exception{
        File plain=new File(context.getCacheDir(),"sync-manifest-"+UUID.randomUUID()+".json");
        try{
            decryptFile(encrypted,plain,keyForCode(code),"SSM1",MAX_MANIFEST);
            byte[] bytes=Store.read(new FileInputStream(plain),MAX_MANIFEST);
            JSONObject m=new JSONObject(new String(bytes,StandardCharsets.UTF_8));
            if(!"suishoucun-cloud".equals(m.optString("format"))||m.optInt("version")!=1)throw new IOException("云端数据版本不兼容");
            return m;
        }finally{plain.delete();}
    }
    void uploadEncryptedAsset(String auth,String code,String name,File source)throws Exception{
        if(source.length()>64L*1024*1024)throw new IOException("单张图片超过64MB，暂时不能同步");
        File encrypted=new File(context.getCacheDir(),"sync-asset-"+UUID.randomUUID()+".bin");
        try{encryptFile(source,encrypted,keyForCode(code),"SSA1");putAsset(auth,name,encrypted);}finally{encrypted.delete();}
    }
    void downloadEncryptedAsset(String auth,String code,String name,File target)throws Exception{
        File encrypted=getAsset(auth,name);
        File tmp=new File(context.getCacheDir(),"sync-asset-"+UUID.randomUUID()+".tmp");
        try{
            decryptFile(encrypted,tmp,keyForCode(code),"SSA1",MAX_ASSET);
            File parent=target.getParentFile();if(parent!=null)parent.mkdirs();
            if(target.exists()&&!target.delete())throw new IOException("文件无法替换");
            if(!tmp.renameTo(target)){copyFile(tmp,target,MAX_ASSET);tmp.delete();}
        }finally{encrypted.delete();tmp.delete();}
    }

    static void encryptFile(File input,File output,byte[] key,String magic)throws Exception{
        byte[] iv=new byte[12];new SecureRandom().nextBytes(iv);
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,iv));
        try(FileOutputStream raw=new FileOutputStream(output)){
            raw.write(magic.getBytes(StandardCharsets.US_ASCII));raw.write(iv);
            try(CipherOutputStream encrypted=new CipherOutputStream(raw,cipher);FileInputStream in=new FileInputStream(input)){copy(in,encrypted,MAX_ASSET+MAX_MANIFEST);}
        }
    }
    static void decryptFile(File input,File output,byte[] key,String magic,int max)throws Exception{
        try(FileInputStream raw=new FileInputStream(input)){
            byte[] header=new byte[4];readFully(raw,header);if(!magic.equals(new String(header,StandardCharsets.US_ASCII)))throw new IOException("同步文件格式无效");
            byte[] iv=new byte[12];readFully(raw,iv);
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,iv));
            try(CipherInputStream decrypted=new CipherInputStream(raw,cipher);FileOutputStream out=new FileOutputStream(output)){copy(decrypted,out,max);}
        }catch(Exception e){output.delete();throw e;}
    }
    static void readFully(InputStream in,byte[] bytes)throws IOException{
        int off=0,n;while(off<bytes.length&&(n=in.read(bytes,off,bytes.length-off))>0)off+=n;if(off!=bytes.length)throw new EOFException();
    }
    static long copy(InputStream in,OutputStream out,long max)throws IOException{
        byte[] buf=new byte[64*1024];long total=0;int n;
        while((n=in.read(buf))!=-1){total+=n;if(total>max)throw new IOException("同步文件过大");out.write(buf,0,n);}
        out.flush();return total;
    }
    static void copyFile(File from,File to,long max)throws IOException{
        try(FileInputStream in=new FileInputStream(from);FileOutputStream out=new FileOutputStream(to)){copy(in,out,max);}
    }

    JSONObject rpc(String name,String auth,JSONObject body)throws Exception{
        HttpURLConnection c=(HttpURLConnection)new URL(RPC_URL).openConnection();
        c.setConnectTimeout(12000);c.setReadTimeout(60000);c.setRequestMethod("POST");c.setDoOutput(true);c.setUseCaches(false);
        c.setRequestProperty("x-sync-auth",auth);
        c.setRequestProperty("Content-Type","application/json");c.setRequestProperty("Accept","application/json");c.setRequestProperty("User-Agent","Suishoucun-Android");
        JSONObject payload=new JSONObject(body.toString()).put("action",name);
        byte[] request=payload.toString().getBytes(StandardCharsets.UTF_8);c.setFixedLengthStreamingMode(request.length);
        try(OutputStream out=c.getOutputStream()){out.write(request);}
        int status=c.getResponseCode();InputStream in=status>=200&&status<300?c.getInputStream():c.getErrorStream();
        String response="";if(in!=null){ByteArrayOutputStream out=new ByteArrayOutputStream();try{copy(in,out,4*1024*1024);}catch(Exception ignored){}response=out.toString("UTF-8");}
        c.disconnect();
        if(status<200||status>=300)throw new IOException("云端返回 "+status+" "+response);
        if(response.trim().isEmpty())return new JSONObject();
        return new JSONObject(response);
    }
    JSONObject objectStatus(String auth,String object)throws Exception{
        return rpc("suishoucun_sync_object_status_v2",auth,new JSONObject().put("p_object",object));
    }
    void putObject(String auth,String object,File file)throws Exception{
        long length=file.length();int chunks=(int)Math.max(1,(length+CHUNK_BYTES-1)/CHUNK_BYTES);
        rpc("suishoucun_sync_prepare_object_v2",auth,new JSONObject().put("p_object",object).put("p_chunk_count",chunks));
        try(FileInputStream in=new FileInputStream(file)){
            byte[] buf=new byte[CHUNK_BYTES];
            for(int i=0;i<chunks;){
                JSONArray parts=new JSONArray();
                for(int group=0;group<3&&i<chunks;group++,i++){
                    int need=(int)Math.min(CHUNK_BYTES,Math.max(0,length-(long)i*CHUNK_BYTES)),off=0,n;
                    while(off<need&&(n=in.read(buf,off,need-off))>0)off+=n;
                    if(off!=need)throw new EOFException();
                    byte[] part=off==buf.length?buf:Arrays.copyOf(buf,off);
                    parts.put(new JSONObject().put("index",i).put("payload",Base64.encodeToString(part,Base64.NO_WRAP)));
                }
                rpc("suishoucun_sync_put_batch_v3",auth,new JSONObject().put("p_object",object).put("p_chunk_count",chunks).put("p_chunks",parts));
            }
        }
        JSONObject status=objectStatus(auth,object);if(!status.optBoolean("complete",false))throw new IOException("云端文件上传不完整");
    }
    File getObject(String auth,String object,int max)throws Exception{
        JSONObject status=objectStatus(auth,object);if(!status.optBoolean("complete",false))throw new FileNotFoundException("云端文件不存在");
        int chunks=status.optInt("chunks",0);if(chunks<1||chunks>4096)throw new IOException("云端文件分片无效");
        File out=new File(context.getCacheDir(),"sync-download-"+UUID.randomUUID()+".bin");long total=0;
        try(FileOutputStream file=new FileOutputStream(out)){
            for(int i=0;i<chunks;){
                JSONArray indices=new JSONArray();int num=Math.min(3,chunks-i);
                for(int j=0;j<num;j++)indices.put(i+j);
                JSONObject batch=rpc("suishoucun_sync_get_batch_v3",auth,new JSONObject().put("p_object",object).put("p_indices",indices));
                if(!batch.optBoolean("found",false)||batch.optInt("chunks",0)!=chunks)throw new IOException("云端文件分片缺失");
                JSONArray parts=batch.getJSONArray("parts");if(parts.length()!=num)throw new IOException("云端分片数量错误");
                for(int j=0;j<num;j++,i++){
                    JSONObject part=parts.getJSONObject(j);
                    if(part.optInt("index",-1)!=i)throw new IOException("云端文件分片顺序错误");
                    byte[] bytes=Base64.decode(part.getString("payload"),Base64.DEFAULT);
                    total+=bytes.length;if(total>max)throw new IOException("同步文件过大");file.write(bytes);
                }
            }
        }catch(Exception e){out.delete();throw e;}
        return out;
    }
    Remote getManifest(String auth)throws Exception{return getManifest(auth,-1);}
    Remote getManifest(String auth,long localRevision)throws Exception{
        JSONObject head=rpc("suishoucun_sync_head_v2",auth,new JSONObject());long revision=head.optLong("revision",0);
        if(revision<=0)return new Remote(0,null);
        if(revision==localRevision)return new Remote(revision,null);
        String object=head.optString("manifest","");if(object.isEmpty())throw new IOException("云端清单缺失");
        return new Remote(revision,getObject(auth,object,MAX_MANIFEST+1024));
    }
    long putManifest(String auth,long expected,File file)throws Exception{
        String object="manifest/"+UUID.randomUUID().toString().replace("-","");
        putObject(auth,object,file);
        JSONObject response=rpc("suishoucun_sync_commit_v2",auth,new JSONObject().put("p_expected",expected).put("p_manifest",object));
        if(response.optBoolean("conflict",false)){
            try{rpc("suishoucun_sync_delete_object_v2",auth,new JSONObject().put("p_object",object));}catch(Exception ignored){}
            throw new Conflict(response.optLong("revision",0));
        }
        if(!response.optBoolean("ok",false))throw new IOException("云端提交失败");
        return response.optLong("revision",0);
    }
    boolean assetExists(String auth,String name)throws Exception{
        return objectStatus(auth,"asset/"+name).optBoolean("complete",false);
    }
    void putAsset(String auth,String name,File file)throws Exception{putObject(auth,"asset/"+name,file);}
    File getAsset(String auth,String name)throws Exception{return getObject(auth,"asset/"+name,MAX_ASSET+1024);}
    void deleteRemote()throws Exception{
        if(!enabled())return;String auth=authForCode(code());JSONObject r=rpc("suishoucun_sync_delete_v2",auth,new JSONObject());
        if(!r.optBoolean("ok",false))throw new IOException("删除云端数据失败");
    }

    static String generateCode(){
        byte[] bytes=new byte[32];new SecureRandom().nextBytes(bytes);
        return Base64.encodeToString(bytes,Base64.URL_SAFE|Base64.NO_WRAP|Base64.NO_PADDING);
    }
    static String normalizeCode(String raw){return raw==null?"":raw.replaceAll("\\s+","").trim();}
    static boolean validCode(String raw){
        try{return decodeCode(normalizeCode(raw)).length==32;}catch(Exception e){return false;}
    }
    static byte[] decodeCode(String raw){
        return Base64.decode(normalizeCode(raw),Base64.URL_SAFE|Base64.NO_WRAP|Base64.NO_PADDING);
    }
    static byte[] derive(String code,String label)throws Exception{
        MessageDigest d=MessageDigest.getInstance("SHA-256");d.update(label.getBytes(StandardCharsets.UTF_8));d.update((byte)0);d.update(decodeCode(code));return d.digest();
    }
    static byte[] keyForCode(String code)throws Exception{return derive(code,"suishoucun-encryption-v1");}
    static String authForCode(String code)throws Exception{return Base64.encodeToString(derive(code,"suishoucun-auth-v1"),Base64.URL_SAFE|Base64.NO_WRAP|Base64.NO_PADDING);}
    static String displayCode(String code){
        String c=normalizeCode(code);StringBuilder b=new StringBuilder();
        for(int i=0;i<c.length();i++){if(i>0&&i%4==0)b.append(' ');b.append(c.charAt(i));}return b.toString();
    }
    static String readableError(Exception e){
        String m=e.getMessage()==null?"":e.getMessage();
        if(m.contains("Unable to resolve host")||m.contains("timed out")||m.contains("Network"))return "现在没连上云端，本机数据没有受影响。联网后会继续同步。";
        if(m.contains("64MB"))return m;
        return "同步没有完成，本机数据没有受影响。稍后再试。";
    }
}