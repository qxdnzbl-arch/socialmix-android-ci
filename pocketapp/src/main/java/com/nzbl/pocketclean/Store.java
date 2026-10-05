package com.nzbl.pocketclean;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class Store {
    public static final class Category {
        public String id;
        public String name;
        Category(String id, String name) { this.id=id; this.name=name; }
    }

    public static final class Note {
        public String id = UUID.randomUUID().toString();
        public String title = "";
        public String text = "";
        public String categoryId = "";
        public boolean favorite = false;
        public boolean trashed = false;
        public long createdAt = System.currentTimeMillis();
        public long updatedAt = createdAt;
        public final ArrayList<String> photos = new ArrayList<>();
    }

    private final SharedPreferences prefs;
    public final ArrayList<Category> categories = new ArrayList<>();
    public final ArrayList<Note> notes = new ArrayList<>();

    public Store(Context c) {
        prefs = c.getSharedPreferences("pocket_store_v2", Context.MODE_PRIVATE);
        load();
    }

    private void load() {
        categories.clear();
        notes.clear();
        String raw = prefs.getString("data", "");
        if (raw == null || raw.isEmpty()) return;
        try {
            JSONObject root = new JSONObject(raw);
            JSONArray cs = root.optJSONArray("categories");
            if (cs != null) for (int i=0;i<cs.length();i++) {
                JSONObject o=cs.optJSONObject(i);
                if (o==null) continue;
                String id=o.optString("id","");
                String name=o.optString("name","").trim();
                if (!id.isEmpty() && !name.isEmpty()) categories.add(new Category(id,name));
            }
            JSONArray ns=root.optJSONArray("notes");
            if (ns!=null) for (int i=0;i<ns.length();i++) {
                JSONObject o=ns.optJSONObject(i);
                if (o==null) continue;
                Note n=new Note();
                n.id=o.optString("id",UUID.randomUUID().toString());
                n.title=o.optString("title","");
                n.text=o.optString("text","");
                n.categoryId=o.optString("categoryId","");
                n.favorite=o.optBoolean("favorite",false);
                n.trashed=o.optBoolean("trashed",false);
                n.createdAt=o.optLong("createdAt",System.currentTimeMillis());
                n.updatedAt=o.optLong("updatedAt",n.createdAt);
                JSONArray ps=o.optJSONArray("photos");
                if (ps!=null) for(int j=0;j<ps.length();j++){
                    String p=ps.optString(j,"");
                    if(!p.isEmpty()) n.photos.add(p);
                }
                notes.add(n);
            }
        } catch (Exception ignored) {
            categories.clear();
            notes.clear();
        }
        // Historical bad defaults are never recreated. New installs stay empty.
        // If a category was deleted in a previous version, orphan notes become uncategorized.
        for (Note n:notes) if(!n.categoryId.isEmpty() && findCategory(n.categoryId)==null) n.categoryId="";
    }

    public synchronized void save() {
        try {
            JSONObject root=new JSONObject();
            JSONArray cs=new JSONArray();
            for(Category c:categories){
                JSONObject o=new JSONObject();
                o.put("id",c.id); o.put("name",c.name); cs.put(o);
            }
            JSONArray ns=new JSONArray();
            for(Note n:notes){
                JSONObject o=new JSONObject();
                o.put("id",n.id); o.put("title",n.title); o.put("text",n.text);
                o.put("categoryId",n.categoryId); o.put("favorite",n.favorite);
                o.put("trashed",n.trashed); o.put("createdAt",n.createdAt); o.put("updatedAt",n.updatedAt);
                JSONArray ps=new JSONArray(); for(String p:n.photos) ps.put(p); o.put("photos",ps);
                ns.put(o);
            }
            root.put("categories",cs); root.put("notes",ns); root.put("version",2);
            prefs.edit().putString("data",root.toString()).apply();
        } catch(Exception ignored){}
    }

    public Category findCategory(String id){
        if(id==null || id.isEmpty()) return null;
        for(Category c:categories) if(c.id.equals(id)) return c;
        return null;
    }

    public Note findNote(String id){
        for(Note n:notes) if(n.id.equals(id)) return n;
        return null;
    }

    public boolean categoryNameExists(String name, String exceptId){
        String x=name.trim();
        for(Category c:categories) if((exceptId==null || !c.id.equals(exceptId)) && c.name.equalsIgnoreCase(x)) return true;
        return false;
    }

    public Category addCategory(String name){
        Category c=new Category(UUID.randomUUID().toString(),name.trim());
        categories.add(c); save(); return c;
    }

    public void renameCategory(Category c,String name){ c.name=name.trim(); save(); }

    public void deleteCategory(Category c){
        categories.remove(c);
        for(Note n:notes) if(c.id.equals(n.categoryId)) n.categoryId="";
        save();
    }

    public void upsert(Note n){
        Note old=findNote(n.id);
        if(old==null) notes.add(0,n);
        n.updatedAt=System.currentTimeMillis();
        save();
    }

    public List<Note> activeNotes(){
        ArrayList<Note> out=new ArrayList<>();
        for(Note n:notes) if(!n.trashed) out.add(n);
        out.sort((a,b)->Long.compare(b.updatedAt,a.updatedAt));
        return out;
    }

    public String exportJson(){
        save();
        return prefs.getString("data","{}");
    }

    public boolean importJson(String raw){
        try {
            new JSONObject(raw);
            prefs.edit().putString("data",raw).commit();
            load();
            save();
            return true;
        } catch(Exception e){ return false; }
    }
}
