package com.nzbl.pocket;

import android.content.Context;
import android.util.AtomicFile;
import org.json.*;
import java.io.*;
import java.util.*;
import java.util.zip.*;

public final class Store {
    public static final String UNFILED="";
    public final Context context;
    public final File photos;
    public final AtomicFile file;
    public final ArrayList<Category> categories=new ArrayList<>();
    public final ArrayList<Note> notes=new ArrayList<>();
    public Note draft;
    public boolean ready=true;
    public String loadError="";
    public static class Category {
        public String id,name;
        public Category(String i,String n){id=i;name=n;}
        public JSONObject json() throws JSONException {return new JSONObject().put("id",id).put("name",name);}
    }
    public static class Note {
        public String id=UUID.randomUUID().toString(), category=UNFILED,title="",body="";
        public long updated=System.currentTimeMillis();
        public boolean pinned=false,deleted=false;
        public ArrayList<String> images=new ArrayList<>();
        public ArrayList<Long> imageTimes=new ArrayList<>();
        public void normalizeImageTimes(){while(imageTimes.size()<images.size())imageTimes.add(updated);while(imageTimes.size()>images.size())imageTimes.remove(imageTimes.size()-1);}
        public JSONObject json() throws JSONException {
            normalizeImageTimes();JSONArray times=new JSONArray();for(Long time:imageTimes)times.put(time);
            return new JSONObject().put("id",id).put("category",category).put("title",title).put("body",body)
              .put("updated",updated).put("pinned",pinned).put("deleted",deleted).put("images",new JSONArray(images)).put("imageTimes",times);
        }
        public static Note from(JSONObject j) throws JSONException {
            Note n=new Note();n.id=j.getString("id");n.category=j.getString("category");n.title=j.getString("title");
            n.body=j.getString("body");n.updated=j.getLong("updated");n.pinned=j.optBoolean("pinned");n.deleted=j.optBoolean("deleted");
            JSONArray a=j.getJSONArray("images");for(int i=0;i<a.length();i++){String s=a.getString(i); if(!s.matches("[a-zA-Z0-9_.-]+"))throw new JSONException("非法图片名");n.images.add(s);}
            JSONArray times=j.optJSONArray("imageTimes");if(times!=null)for(int i=0;i<Math.min(times.length(),n.images.size());i++)n.imageTimes.add(times.optLong(i,n.updated));n.normalizeImageTimes();
            return n;
        }
        public Note copy(){try{return from(json());}catch(Exception e){throw new IllegalStateException(e);}}
        public boolean hasContent(){return !title.trim().isEmpty()||!body.trim().isEmpty()||!images.isEmpty();}
        public String heading(){if(!title.trim().isEmpty())return title.trim();if(!body.trim().isEmpty())return body.trim().split("\\n",2)[0];return "图片记录";}
    }
    public Store(Context c){
        context=c;photos=new File(c.getFilesDir(),"photos");photos.mkdirs();file=new AtomicFile(new File(c.getFilesDir(),"collection.json"));
        if(file.getBaseFile().exists()||new File(file.getBaseFile()+".bak").exists()){
            try{parse(new JSONObject(new String(read(file.openRead(),16*1024*1024),"UTF-8")));}catch(Exception e){ready=false;loadError="记录未能读取。原文件已保留，请先导出备份。";}
        }
    }
    public JSONObject json() throws JSONException {
        JSONArray cs=new JSONArray(),ns=new JSONArray();for(Category c:categories)cs.put(c.json());for(Note n:notes)ns.put(n.json());
        return new JSONObject().put("format","suishoucun").put("version",1).put("categoryMode","custom").put("categories",cs).put("notes",ns).put("draft",draft==null?JSONObject.NULL:draft.json());
    }
    public void parse(JSONObject j) throws JSONException {
        if(!"suishoucun".equals(j.getString("format"))||j.getInt("version")!=1)throw new JSONException("备份版本不匹配");
        ArrayList<Category> cs=new ArrayList<>();ArrayList<Note> ns=new ArrayList<>();HashSet<String> ids=new HashSet<>();
        JSONArray a=j.getJSONArray("categories"),b=j.getJSONArray("notes");
        for(int i=0;i<a.length();i++){JSONObject v=a.getJSONObject(i);String id=v.getString("id"),name=v.getString("name").trim();if(id.isEmpty()||name.isEmpty()||!ids.add(id))throw new JSONException("分类数据无效");cs.add(new Category(id,name));}
        HashSet<String> noteIds=new HashSet<>();for(int i=0;i<b.length();i++){Note n=Note.from(b.getJSONObject(i));if((!UNFILED.equals(n.category)&&!ids.contains(n.category))||!noteIds.add(n.id))throw new JSONException("记录数据无效");ns.add(n);}
        Note d=j.isNull("draft")?null:Note.from(j.getJSONObject("draft"));if(d!=null&&!UNFILED.equals(d.category)&&!ids.contains(d.category))throw new JSONException("草稿分类无效");
        if(!"custom".equals(j.optString("categoryMode"))){
            HashSet<String> removed=new HashSet<>();
            Iterator<Category> it=cs.iterator();while(it.hasNext()){Category c=it.next();if(("hair".equals(c.id)&&"理发".equals(c.name))||("prompt".equals(c.id)&&"提示词".equals(c.name))||("daily".equals(c.id)&&"日常".equals(c.name))){removed.add(c.id);it.remove();}}
            for(Note n:ns)if(removed.contains(n.category))n.category=UNFILED;
            if(d!=null&&removed.contains(d.category))d.category=UNFILED;
        }
        categories.clear();categories.addAll(cs);notes.clear();notes.addAll(ns);draft=d;
    }
    public void save() throws Exception {
        if(!ready)throw new IOException("记录读取失败，不能覆盖原数据");
        byte[] data=json().toString().getBytes("UTF-8");FileOutputStream stream=null;
        try{stream=file.startWrite();stream.write(data);file.finishWrite(stream);}catch(Exception e){if(stream!=null)file.failWrite(stream);throw e;}
    }
    public boolean change(Runnable action){
        JSONObject old;try{old=json();}catch(Exception e){return false;}
        try{action.run();save();return true;}catch(Exception e){try{parse(old);}catch(Exception ignored){}return false;}
    }
    public Note find(String id){for(Note n:notes)if(n.id.equals(id))return n;return null;}
    public String categoryName(String id){for(Category c:categories)if(c.id.equals(id))return c.name;return "未分类";}
    public synchronized void replaceSync(JSONObject incoming) throws Exception {
        JSONObject old=json();
        try{
            parse(new JSONObject(incoming.toString()));
            save();
            cleanupPhotos();
        }catch(Exception e){
            try{parse(old);}catch(Exception ignored){}
            throw e;
        }
    }
    public synchronized int mergeSync(JSONObject incoming) throws Exception {
        Store remote=new Store(context);remote.parse(new JSONObject(incoming.toString()));
        JSONObject old=json();int changed=0;
        try{
            HashMap<String,String> categoryMap=new HashMap<>();categoryMap.put(UNFILED,UNFILED);
            for(Category rc:remote.categories){
                Category target=null;
                for(Category mine:categories)if(mine.id.equals(rc.id)){target=mine;break;}
                if(target==null)for(Category mine:categories)if(mine.name.equals(rc.name)){target=mine;break;}
                if(target==null){target=new Category(rc.id,rc.name);categories.add(target);changed++;}
                categoryMap.put(rc.id,target.id);
            }
            for(Note rn:remote.notes){
                Note copy=rn.copy();copy.category=categoryMap.containsKey(copy.category)?categoryMap.get(copy.category):UNFILED;
                Note local=find(copy.id);
                if(local==null){notes.add(copy);changed++;}
                else if(copy.updated>local.updated){notes.remove(local);notes.add(copy);changed++;}
            }
            if(remote.draft!=null){
                Note rd=remote.draft.copy();rd.category=categoryMap.containsKey(rd.category)?categoryMap.get(rd.category):UNFILED;
                if(draft==null||rd.updated>draft.updated){draft=rd;changed++;}
            }
            save();cleanupPhotos();return changed;
        }catch(Exception e){
            try{parse(old);}catch(Exception ignored){}
            throw e;
        }
    }
    public synchronized void cleanupPhotos(){
        HashSet<String> needed=new HashSet<>();for(Note n:notes)needed.addAll(n.images);if(draft!=null)needed.addAll(draft.images);
        File[] files=photos.listFiles();if(files!=null)for(File f:files)if(f.isFile()&&!needed.contains(f.getName()))f.delete();
    }
    public void exportZip(OutputStream output) throws Exception {
        ZipOutputStream z=new ZipOutputStream(output);
        try {
            byte[] data=ready?json().toString(2).getBytes("UTF-8"):read(new FileInputStream(file.getBaseFile()),16*1024*1024);
            z.putNextEntry(new ZipEntry("collection.json"));z.write(data);z.closeEntry();
            HashSet<String> needed=new HashSet<>();for(Note n:notes)needed.addAll(n.images);if(draft!=null)needed.addAll(draft.images);if(!ready){File[] all=photos.listFiles();if(all!=null)for(File f:all)if(f.isFile())needed.add(f.getName());}for(String name:needed){File f=new File(photos,name);if(!f.isFile())throw new IOException("记录图片缺失");z.putNextEntry(new ZipEntry("photos/"+name));copy(new FileInputStream(f),z,64*1024*1024);z.closeEntry();}
        }finally{z.close();}
    }
    public int importZip(InputStream input) throws Exception {
        File temp=new File(context.getCacheDir(),"import-"+UUID.randomUUID());temp.mkdirs();JSONObject incoming=null;long total=0;
        try{
            ZipInputStream zip=new ZipInputStream(input);ZipEntry entry;int entries=0;
            try{while((entry=zip.getNextEntry())!=null){if(++entries>5000)throw new IOException("备份内容过多");String name=entry.getName();if(name.equals("collection.json")){byte[] data=readEntry(zip,16*1024*1024);total+=data.length;incoming=new JSONObject(new String(data,"UTF-8"));}else if(name.matches("photos/[a-zA-Z0-9_.-]+")){File f=new File(temp,name.substring(7));FileOutputStream out=new FileOutputStream(f);try{long len=copyEntry(zip,out,64*1024*1024);total+=len;}finally{out.close();}}else throw new IOException("不是有效的随手存备份");if(total>512L*1024*1024)throw new IOException("备份超过512MB");zip.closeEntry();}}finally{zip.close();}
            if(incoming==null)throw new IOException("备份缺少记录");
            Store staged=new Store(context);if(!staged.ready)throw new IOException("当前记录未能读取");staged.parse(incoming);
            HashMap<String,String> imageMap=new HashMap<>();ArrayList<File> added=new ArrayList<>();
            ArrayList<Note> check=new ArrayList<>(staged.notes);if(staged.draft!=null)check.add(staged.draft);
            for(Note n:check)for(String name:n.images){File f=new File(temp,name);if(!f.isFile())throw new IOException("备份缺少图片");}
            HashMap<String,String> cats=new HashMap<>();cats.put(UNFILED,UNFILED);JSONObject old=json();int count=0;
            try{
                for(Category c:staged.categories){String id=null;for(Category mine:categories)if(mine.name.equals(c.name)){id=mine.id;break;}if(id==null){id=UUID.randomUUID().toString();categories.add(new Category(id,c.name));}cats.put(c.id,id);}
                for(Note n:staged.notes){Note existing=find(n.id);if(existing!=null&&existing.updated>=n.updated)continue;for(int i=0;i<n.images.size();i++){String name=n.images.get(i);String mapped=imageMap.get(name);if(mapped==null){mapped=UUID.randomUUID()+".img";File target=new File(photos,mapped);copy(new FileInputStream(new File(temp,name)),new FileOutputStream(target),64*1024*1024);added.add(target);imageMap.put(name,mapped);}n.images.set(i,mapped);}n.category=cats.get(n.category);if(existing!=null)notes.remove(existing);notes.add(n);count++;}
                if(staged.draft!=null){Note n=staged.draft;n.id=UUID.randomUUID().toString();n.category=cats.get(n.category);if(n.title.trim().isEmpty())n.title="导入的草稿";for(int i=0;i<n.images.size();i++){String name=n.images.get(i);String mapped=imageMap.get(name);if(mapped==null){mapped=UUID.randomUUID()+".img";File target=new File(photos,mapped);copy(new FileInputStream(new File(temp,name)),new FileOutputStream(target),64*1024*1024);added.add(target);imageMap.put(name,mapped);}n.images.set(i,mapped);}notes.add(n);count++;}
                save();return count;
            }catch(Exception e){parse(old);for(File f:added)f.delete();throw e;}
        }finally{File[] fs=temp.listFiles();if(fs!=null)for(File f:fs)f.delete();temp.delete();}
    }
    public static byte[] read(InputStream in,int max) throws IOException{try{ByteArrayOutputStream out=new ByteArrayOutputStream();copyEntry(in,out,max);return out.toByteArray();}finally{in.close();}}
    static byte[] readEntry(InputStream in,int max)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();copyEntry(in,out,max);return out.toByteArray();}
    static long copyEntry(InputStream in,OutputStream out,int max)throws IOException{byte[] b=new byte[8192];long len=0;int n;while((n=in.read(b))!=-1){len+=n;if(len>max)throw new IOException("文件过大");out.write(b,0,n);}return len;}
    static void copy(InputStream in,OutputStream out,int max)throws IOException{try{copyEntry(in,out,max);out.flush();}finally{in.close();if(!(out instanceof ZipOutputStream))out.close();}}
}
