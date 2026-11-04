package com.codearts.gateway.core;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

/**
 * 码道产物落盘（A15）。
 *
 * <p>Android 10+ 写入公共下载目录：{@code Download/CodeArtsArtifacts/<session>/<原相对路径>}，
 * 其他 App / agent 容器可直接按 {@code /sdcard/Download/...} 访问；
 * Android 9 及以下退化为应用外部文件目录（无需存储权限）。
 */
public final class ArtifactStore {

    public static final String ROOT = "CodeArtsArtifacts";

    private ArtifactStore() {
    }

    /**
     * 保存产物，返回可展示的本地绝对路径；失败返回空串。
     *
     * @param sessionId  上游会话 id（截短后作为子目录，避免不同会话同名覆盖）
     * @param remotePath 码道工作区路径，如 {@code /workspace/a.docx}
     */
    public static String save(Context ctx, String sessionId, String remotePath,
                              byte[] data, String mime) {
        if (ctx == null || data == null || remotePath == null) {
            return "";
        }
        String rel = relative(remotePath);
        String name = basename(rel);
        String dir = ROOT + "/" + shortId(sessionId) + "/" + parent(rel);
        if (name.isEmpty()) {
            name = "artifact.bin";
        }
        if (Build.VERSION.SDK_INT >= 29) {
            try {
                ContentResolver cr = ctx.getContentResolver();
                ContentValues v = new ContentValues();
                v.put(MediaStore.Downloads.DISPLAY_NAME, name);
                v.put(MediaStore.Downloads.MIME_TYPE,
                        mime == null || mime.isEmpty() ? "application/octet-stream" : mime);
                v.put(MediaStore.Downloads.RELATIVE_PATH,
                        Environment.DIRECTORY_DOWNLOADS + "/" + dir);
                v.put(MediaStore.Downloads.IS_PENDING, 1);
                Uri uri = cr.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
                if (uri == null) {
                    return "";
                }
                try (OutputStream os = cr.openOutputStream(uri)) {
                    os.write(data);
                    os.flush();
                }
                ContentValues done = new ContentValues();
                done.put(MediaStore.Downloads.IS_PENDING, 0);
                cr.update(uri, done, null, null);
                return "/sdcard/" + Environment.DIRECTORY_DOWNLOADS + "/" + dir + "/" + name;
            } catch (Throwable t) {
                LogRing.log("artifact", "保存到下载目录失败: " + t);
            }
        }
        try {
            File base = ctx.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
            if (base == null) {
                base = new File(ctx.getCacheDir(), "downloads");
            }
            File f = new File(base, dir + "/" + name);
            File p = f.getParentFile();
            if (p != null && !p.exists() && !p.mkdirs()) {
                return "";
            }
            try (FileOutputStream fos = new FileOutputStream(f)) {
                fos.write(data);
                fos.flush();
            }
            return f.getAbsolutePath();
        } catch (Throwable t) {
            LogRing.log("artifact", "保存产物失败: " + t);
            return "";
        }
    }

    /** 供网关 HTTP 下载端点使用的缓存文件。 */
    public static File cache(Context ctx, String token, String name, byte[] data) {
        try {
            File dir = new File(ctx.getCacheDir(), "artifact-serve/" + token);
            if (!dir.exists() && !dir.mkdirs()) {
                return null;
            }
            File f = new File(dir, name == null || name.isEmpty() ? "artifact.bin" : name);
            try (FileOutputStream fos = new FileOutputStream(f)) {
                fos.write(data);
                fos.flush();
            }
            return f;
        } catch (Throwable t) {
            return null;
        }
    }

    /** 去掉开头的 /、盘符与 ..，保留工作区内的相对层级。 */
    private static String relative(String path) {
        String p = path.replace('\\', '/').trim();
        while (p.startsWith("/")) {
            p = p.substring(1);
        }
        if (p.matches("^[A-Za-z]:/.*")) {
            p = p.substring(3);
        }
        StringBuilder out = new StringBuilder();
        for (String seg : p.split("/")) {
            if (seg.isEmpty() || ".".equals(seg) || "..".equals(seg)) {
                continue;
            }
            if (out.length() > 0) {
                out.append('/');
            }
            out.append(seg);
        }
        return out.toString();
    }

    private static String parent(String path) {
        int i = path.lastIndexOf('/');
        return i <= 0 ? "workspace" : path.substring(0, i);
    }

    private static String basename(String path) {
        int i = path.lastIndexOf('/');
        return i < 0 ? path : path.substring(i + 1);
    }

    private static String shortId(String id) {
        if (id == null || id.isEmpty()) {
            return "session";
        }
        String s = id.replaceAll("[^A-Za-z0-9_-]", "");
        return s.length() <= 8 ? s : s.substring(s.length() - 8);
    }
}