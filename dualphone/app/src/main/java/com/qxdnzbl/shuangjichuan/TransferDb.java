package com.qxdnzbl.shuangjichuan;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import java.util.*;

public class TransferDb extends SQLiteOpenHelper {
    public static class Msg {
        public String id, kind, text, fileName, filePath, status;
        public long fileSize, createdAt;
        public boolean mine;
    }

    public TransferDb(Context c) {
        super(c, "shuangjichuan.db", null, 1);
    }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE messages (" +
            "id TEXT PRIMARY KEY," +
            "mine INTEGER NOT NULL," +
            "kind TEXT NOT NULL," +
            "text_content TEXT," +
            "file_name TEXT," +
            "file_path TEXT," +
            "file_size INTEGER NOT NULL DEFAULT 0," +
            "created_at INTEGER NOT NULL," +
            "status TEXT NOT NULL)");
        db.execSQL("CREATE INDEX idx_messages_time ON messages(created_at)");
        db.execSQL("CREATE INDEX idx_messages_pending ON messages(mine,status)");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {}

    public void addText(String id, boolean mine, String text, long createdAt, String status) {
        ContentValues v = base(id, mine, "text", createdAt, status);
        v.put("text_content", text);
        getWritableDatabase().insertWithOnConflict("messages", null, v, SQLiteDatabase.CONFLICT_IGNORE);
    }

    public void addFile(String id, boolean mine, String name, String path, long size, long createdAt, String status) {
        ContentValues v = base(id, mine, "file", createdAt, status);
        v.put("file_name", name);
        v.put("file_path", path);
        v.put("file_size", size);
        getWritableDatabase().insertWithOnConflict("messages", null, v, SQLiteDatabase.CONFLICT_IGNORE);
    }

    private ContentValues base(String id, boolean mine, String kind, long createdAt, String status) {
        ContentValues v = new ContentValues();
        v.put("id", id);
        v.put("mine", mine ? 1 : 0);
        v.put("kind", kind);
        v.put("created_at", createdAt);
        v.put("status", status);
        return v;
    }

    public void markSent(String id) {
        ContentValues v = new ContentValues();
        v.put("status", "sent");
        getWritableDatabase().update("messages", v, "id=?", new String[]{id});
    }

    public List<Msg> all() {
        return query("SELECT * FROM messages ORDER BY created_at ASC", null);
    }

    public List<Msg> pending() {
        return query("SELECT * FROM messages WHERE mine=1 AND status='pending' ORDER BY created_at ASC", null);
    }

    private List<Msg> query(String sql, String[] args) {
        ArrayList<Msg> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery(sql, args)) {
            while (c.moveToNext()) {
                Msg m = new Msg();
                m.id = c.getString(c.getColumnIndexOrThrow("id"));
                m.mine = c.getInt(c.getColumnIndexOrThrow("mine")) == 1;
                m.kind = c.getString(c.getColumnIndexOrThrow("kind"));
                m.text = c.getString(c.getColumnIndexOrThrow("text_content"));
                m.fileName = c.getString(c.getColumnIndexOrThrow("file_name"));
                m.filePath = c.getString(c.getColumnIndexOrThrow("file_path"));
                m.fileSize = c.getLong(c.getColumnIndexOrThrow("file_size"));
                m.createdAt = c.getLong(c.getColumnIndexOrThrow("created_at"));
                m.status = c.getString(c.getColumnIndexOrThrow("status"));
                out.add(m);
            }
        }
        return out;
    }
}
