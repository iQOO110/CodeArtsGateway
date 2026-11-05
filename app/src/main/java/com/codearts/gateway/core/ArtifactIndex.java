package com.codearts.gateway.core;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * 产物索引：记录 {@link ArtifactStore#save} 落盘的文件，供 App 内“码道产物”页面展示。
 *
 * <p>1.9.18 之前没有索引，这里额外扫描 MediaStore（Android 10+）和应用外部目录，
 * 让历史产物也能出现在列表里。
 */
public final class ArtifactIndex {

    private static final String PREF = "artifact_index";
    private static final String KEY = "items";
    private static final int MAX = 500;

    private ArtifactIndex() {
    }

    public static final class Item {
        public String path = "";
        public String name = "";
        public String uri = "";
        public String mime = "";
        public String sessionId = "";
        public long size;
        public long time;
    }

    public static synchronized void add(Context ctx, String path, String name, long size,
                                        String sessionId, String uri, String mime) {
        if (ctx == null || path == null || path.isEmpty()) {
            return;
        }
        List<Item> list = load(ctx);
        for (Iterator<Item> it = list.iterator(); it.hasNext(); ) {
            if (path.equals(it.next().path)) {
                it.remove();
            }
        }
        Item x = new Item();
        x.path = path;
        x.name = name == null ? "" : name;
        x.size = size;
        x.sessionId = sessionId == null ? "" : sessionId;
        x.uri = uri == null ? "" : uri;
        x.mime = mime == null ? "" : mime;
        x.time = System.currentTimeMillis();
        list.add(0, x);
        while (list.size() > MAX) {
            list.remove(list.size() - 1);
        }
        save(ctx, list);
    }

    /** 索引 + 历史文件扫描，按时间倒序。 */
    public static synchronized List<Item> list(Context ctx) {
        List<Item> merged = new ArrayList<>(load(ctx));
        Set<String> seen = new HashSet<>();
        for (Item i : merged) {
            seen.add(i.path);
        }
        try {
            scanMediaStore(ctx, merged, seen);
        } catch (Throwable ignore) {
            // 无权限或无 Downloads 集合时静默
        }
        try {
            scanAppDir(ctx, merged, seen);
        } catch (Throwable ignore) {
            // 忽略
        }
        merged.sort((a, b) -> Long.compare(b.time, a.time));
        return merged;
    }

    private static void scanMediaStore(Context ctx, List<Item> out, Set<String> seen) {
        if (Build.VERSION.SDK_INT < 29) {
            return;
        }
        String[] proj = {MediaStore.Downloads._ID, MediaStore.Downloads.DISPLAY_NAME,
                MediaStore.Downloads.SIZE, MediaStore.Downloads.DATE_ADDED,
                MediaStore.Downloads.RELATIVE_PATH, MediaStore.Downloads.MIME_TYPE};
        String sel = MediaStore.Downloads.RELATIVE_PATH + " LIKE ?";
        String[] args = {Environment.DIRECTORY_DOWNLOADS + "/" + ArtifactStore.ROOT + "/%"};
        Cursor c = ctx.getContentResolver().query(MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                proj, sel, args, MediaStore.Downloads.DATE_ADDED + " DESC");
        if (c == null) {
            return;
        }
        try {
            while (c.moveToNext()) {
                String name = c.getString(1);
                String rel = c.getString(4);
                String base = "/sdcard/" + (rel == null ? "" : rel);
                while (base.endsWith("/")) {
                    base = base.substring(0, base.length() - 1);
                }
                String path = base + "/" + (name == null ? "artifact.bin" : name);
                if (seen.contains(path)) {
                    continue;
                }
                Item x = new Item();
                x.path = path;
                x.name = name == null ? "" : name;
                x.size = c.getLong(2);
                x.time = c.getLong(3) * 1000L;
                x.uri = Uri.withAppendedPath(MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                        String.valueOf(c.getLong(0))).toString();
                x.mime = c.getString(5);
                out.add(x);
                seen.add(path);
            }
        } finally {
            c.close();
        }
    }

    private static void scanAppDir(Context ctx, List<Item> out, Set<String> seen) {
        File base = ctx.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (base == null) {
            return;
        }
        walk(new File(base, ArtifactStore.ROOT), out, seen);
    }

    private static void walk(File f, List<Item> out, Set<String> seen) {
        if (f == null) {
            return;
        }
        if (f.isFile()) {
            String p = f.getAbsolutePath();
            if (seen.contains(p)) {
                return;
            }
            Item x = new Item();
            x.path = p;
            x.name = f.getName();
            x.size = f.length();
            x.time = f.lastModified();
            out.add(x);
            seen.add(p);
            return;
        }
        File[] kids = f.listFiles();
        if (kids == null) {
            return;
        }
        for (File k : kids) {
            walk(k, out, seen);
        }
    }

    private static List<Item> load(Context ctx) {
        List<Item> out = new ArrayList<>();
        try {
            SharedPreferences sp = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE);
            JSONArray arr = new JSONArray(sp.getString(KEY, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) {
                    continue;
                }
                Item x = new Item();
                x.path = o.optString("path", "");
                x.name = o.optString("name", "");
                x.uri = o.optString("uri", "");
                x.mime = o.optString("mime", "");
                x.sessionId = o.optString("sessionId", "");
                x.size = o.optLong("size", 0L);
                x.time = o.optLong("time", 0L);
                if (!x.path.isEmpty()) {
                    out.add(x);
                }
            }
        } catch (Throwable ignore) {
            // 损坏的索引直接忽略
        }
        return out;
    }

    private static void save(Context ctx, List<Item> list) {
        try {
            JSONArray arr = new JSONArray();
            for (Item x : list) {
                JSONObject o = new JSONObject();
                o.put("path", x.path);
                o.put("name", x.name);
                o.put("uri", x.uri);
                o.put("mime", x.mime);
                o.put("sessionId", x.sessionId);
                o.put("size", x.size);
                o.put("time", x.time);
                arr.put(o);
            }
            ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                    .edit().putString(KEY, arr.toString()).apply();
        } catch (Throwable ignore) {
            // 忽略
        }
    }
}