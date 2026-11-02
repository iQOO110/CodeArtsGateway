package com.codearts.gateway.core;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

/**
 * 诊断报文落盘：把上游原始响应写到公共下载目录，便于外部读取定位 schema 差异。
 *
 * <p>Android 10+ 走 MediaStore.Downloads（应用写自己插入的文件，无需任何存储权限）；
 * Android 9 及以下回落到公共 Download 目录直写（无权限时会失败，属预期，仅记日志）。
 */
public final class DiagDump {

    private DiagDump() {
    }

    /**
     * 覆盖式写入 {@code fileName}。
     *
     * @return 人类可读的落地位置或错误描述（同时写入运行日志）
     */
    public static String save(Context ctx, String fileName, String content) {
        String result;
        try {
            if (content == null || content.isEmpty()) {
                result = "无内容，跳过";
            } else if (ctx == null) {
                // 无 Context（自检等场景）：回落到应用私有目录再尝试公共目录
                result = saveFallback(fileName, content);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                result = saveViaMediaStore(ctx, fileName, content);
            } else {
                result = saveViaFile(fileName, content);
            }
        } catch (Throwable t) {
            result = "落盘失败: " + t;
        }
        LogRing.log("diag", "诊断报文 " + fileName + " → " + result);
        return result;
    }

    /** 无 Context 时的兜底：直接写 /sdcard/Download（PRoot 沙箱与多数环境可写）。 */
    private static String saveFallback(String fileName, String content) {
        try {
            java.io.File dir = new java.io.File("/sdcard/Download");
            if (!dir.exists() && !dir.mkdirs()) {
                return "目录不可用: " + dir;
            }
            java.io.File f = new java.io.File(dir, fileName);
            try (java.io.FileOutputStream fos = new java.io.FileOutputStream(f)) {
                fos.write(content.getBytes("UTF-8"));
                fos.flush();
            }
            return f.getAbsolutePath();
        } catch (Throwable t) {
            return "fallback 失败: " + t;
        }
    }

    private static String saveViaMediaStore(Context ctx, String fileName, String content)
            throws Exception {
        ContentResolver cr = ctx.getApplicationContext().getContentResolver();
        Uri collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI;
        // 覆盖同名旧文件，避免下载目录堆积 codearts-welfare (1).json
        try (Cursor c = cr.query(collection, new String[]{MediaStore.Downloads._ID},
                MediaStore.Downloads.DISPLAY_NAME + "=?", new String[]{fileName}, null)) {
            if (c != null) {
                while (c.moveToNext()) {
                    cr.delete(Uri.withAppendedPath(collection, c.getString(0)), null, null);
                }
            }
        } catch (Throwable ignore) {
            // 查询失败不影响新建
        }
        ContentValues v = new ContentValues();
        v.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
        v.put(MediaStore.Downloads.MIME_TYPE, "application/json");
        v.put(MediaStore.Downloads.IS_PENDING, 1);
        Uri uri = cr.insert(collection, v);
        if (uri == null) {
            return "MediaStore insert 返回 null";
        }
        try (OutputStream os = cr.openOutputStream(uri)) {
            os.write(content.getBytes("UTF-8"));
            os.flush();
        }
        ContentValues done = new ContentValues();
        done.put(MediaStore.Downloads.IS_PENDING, 0);
        cr.update(uri, done, null, null);
        return "/sdcard/Download/" + fileName;
    }

    private static String saveViaFile(String fileName, String content) throws Exception {
        File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        if (dir == null) {
            dir = new File("/sdcard/Download");
        }
        if (!dir.exists() && !dir.mkdirs()) {
            return "目录不可用: " + dir;
        }
        File f = new File(dir, fileName);
        try (FileOutputStream fos = new FileOutputStream(f)) {
            fos.write(content.getBytes("UTF-8"));
            fos.flush();
        }
        return f.getAbsolutePath();
    }
}
