package com.codearts.gateway.ui;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.URLUtil;
import android.webkit.WebView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * WebView 下载桥：
 * <ul>
 *   <li>http/https：走系统 DownloadManager</li>
 *   <li>blob/data：网页里 fetch/读取内容，经 JS Bridge 交原生保存到
 *       Download/CodeArtsGateway</li>
 *   <li>APK 保存后自动打开系统安装界面</li>
 * </ul>
 */
public final class WebDownloadUtil {

    private static final long MAX_BASE64_CHARS = 120L * 1024 * 1024;

    private WebDownloadUtil() {
    }

    public static void attach(Activity activity, WebView webView) {
        if (activity == null || webView == null) {
            return;
        }
        webView.addJavascriptInterface(new Bridge(activity), "CodeArtsDownload");
        webView.setDownloadListener(
                (url, userAgent, contentDisposition, mimeType, contentLength) -> {
                    if (url == null) {
                        return;
                    }
                    if (url.startsWith("data:")) {
                        saveDataUrl(activity, url, mimeType);
                        return;
                    }
                    if (url.startsWith("blob:")) {
                        fetchBlob(activity, webView, url, mimeType, contentDisposition);
                        return;
                    }
                    downloadHttp(activity, url, userAgent, contentDisposition, mimeType);
                });
    }

    private static void downloadHttp(Activity activity, String url, String userAgent,
                                     String contentDisposition, String mimeType) {
        if (!ensureLegacyStorage(activity)) {
            return;
        }
        try {
            String fileName = URLUtil.guessFileName(url, contentDisposition, mimeType);
            if (fileName == null || fileName.isEmpty()) {
                fileName = defaultName(mimeType);
            }
            DownloadManager.Request req = new DownloadManager.Request(Uri.parse(url));
            if (mimeType != null && !mimeType.isEmpty()) {
                req.setMimeType(mimeType);
            }
            if (userAgent != null) {
                req.addRequestHeader("User-Agent", userAgent);
            }
            String cookie = CookieManager.getInstance().getCookie(url);
            if (cookie != null && !cookie.isEmpty()) {
                req.addRequestHeader("Cookie", cookie);
            }
            req.setTitle(fileName);
            req.setDescription("CodeArts Gateway 下载");
            req.setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);
            DownloadManager dm = (DownloadManager) activity.getSystemService(
                    Context.DOWNLOAD_SERVICE);
            if (dm == null) {
                throw new IllegalStateException("系统下载服务不可用");
            }
            dm.enqueue(req);
            Toast.makeText(activity,
                    "已开始下载：" + fileName + "\n完成后在通知栏或下载管理器打开/安装",
                    Toast.LENGTH_LONG).show();
        } catch (Throwable t) {
            Toast.makeText(activity, "下载失败：" + t, Toast.LENGTH_LONG).show();
        }
    }

    /** blob URL：让网页 fetch 出内容，再经 JS Bridge 回传原生保存。 */
    private static void fetchBlob(Activity activity, WebView webView, String url,
                                  String mimeType, String contentDisposition) {
        String fileName = URLUtil.guessFileName(url, contentDisposition, mimeType);
        if (fileName == null || fileName.isEmpty() || "downloadfile.bin".equals(fileName)) {
            fileName = defaultName(mimeType);
        }
        String mime = mimeType == null ? "" : mimeType;
        String js = "(function(){fetch(" + JSONObject.quote(url) + ")"
                + ".then(function(r){return r.blob();})"
                + ".then(function(b){var fr=new FileReader();"
                + "fr.onload=function(){CodeArtsDownload.saveBase64("
                + JSONObject.quote(fileName) + "," + JSONObject.quote(mime)
                + ",fr.result);};"
                + "fr.onerror=function(){CodeArtsDownload.fail('读取文件失败');};"
                + "fr.readAsDataURL(b);})"
                + ".catch(function(e){CodeArtsDownload.fail('准备下载失败: '+e);});})();";
        webView.post(() -> webView.evaluateJavascript(js, null));
        Toast.makeText(activity, "正在准备下载：" + fileName, Toast.LENGTH_SHORT).show();
    }

    private static void saveDataUrl(Activity activity, String dataUrl, String mimeType) {
        try {
            int comma = dataUrl.indexOf(',');
            if (comma < 0) {
                throw new IllegalArgumentException("data URL 格式错误");
            }
            String meta = dataUrl.substring(5, comma);
            String payload = dataUrl.substring(comma + 1);
            boolean base64 = meta.contains("base64");
            String mime = mimeType;
            if (mime == null || mime.isEmpty()) {
                int semi = meta.indexOf(';');
                mime = semi > 0 ? meta.substring(0, semi) : "application/octet-stream";
            }
            byte[] bytes = base64
                    ? Base64.decode(payload, Base64.DEFAULT)
                    : java.net.URLDecoder.decode(payload, "UTF-8")
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8);
            saveBytes(activity, defaultName(mime), mime, bytes);
        } catch (Throwable t) {
            Toast.makeText(activity, "下载失败：" + t, Toast.LENGTH_LONG).show();
        }
    }

    private static void saveBytes(Activity activity, String rawName, String mime, byte[] bytes) {
        String name = sanitize(rawName);
        try {
            Uri uri;
            if (Build.VERSION.SDK_INT >= 29) {
                ContentValues v = new ContentValues();
                v.put(MediaStore.Downloads.DISPLAY_NAME, name);
                v.put(MediaStore.Downloads.MIME_TYPE, mime);
                v.put(MediaStore.Downloads.RELATIVE_PATH,
                        Environment.DIRECTORY_DOWNLOADS + "/CodeArtsGateway");
                v.put(MediaStore.Downloads.IS_PENDING, 1);
                uri = activity.getContentResolver()
                        .insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
                if (uri == null) {
                    throw new IllegalStateException("无法创建下载文件");
                }
                try (OutputStream os = activity.getContentResolver().openOutputStream(uri)) {
                    if (os == null) {
                        throw new IllegalStateException("无法写入下载文件");
                    }
                    os.write(bytes);
                    os.flush();
                }
                v.clear();
                v.put(MediaStore.Downloads.IS_PENDING, 0);
                activity.getContentResolver().update(uri, v, null, null);
            } else {
                if (!ensureLegacyStorage(activity)) {
                    return;
                }
                File dir = new File(Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_DOWNLOADS), "CodeArtsGateway");
                if (!dir.exists() && !dir.mkdirs()) {
                    throw new IllegalStateException("无法创建下载目录");
                }
                File f = new File(dir, name);
                try (FileOutputStream fos = new FileOutputStream(f)) {
                    fos.write(bytes);
                    fos.flush();
                }
                uri = FileProvider.getUriForFile(activity,
                        activity.getPackageName() + ".fileprovider", f);
            }
            final Uri saved = uri;
            final boolean apk = name.toLowerCase(Locale.ROOT).endsWith(".apk");
            activity.runOnUiThread(() -> {
                Toast.makeText(activity,
                        "已保存到 Download/CodeArtsGateway/" + name, Toast.LENGTH_LONG).show();
                if (apk) {
                    openInstaller(activity, saved);
                }
            });
        } catch (Throwable t) {
            activity.runOnUiThread(() ->
                    Toast.makeText(activity, "保存失败：" + t, Toast.LENGTH_LONG).show());
        }
    }

    private static void openInstaller(Activity activity, Uri uri) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            activity.startActivity(i);
        } catch (Throwable t) {
            Toast.makeText(activity,
                    "已下载，请在文件管理器打开安装：" + t, Toast.LENGTH_LONG).show();
        }
    }

    private static boolean ensureLegacyStorage(Activity activity) {
        if (Build.VERSION.SDK_INT >= 29) {
            return true;
        }
        if (activity.checkSelfPermission(
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            activity.requestPermissions(new String[]{
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, 7001);
            Toast.makeText(activity, "请授权存储权限后重新下载", Toast.LENGTH_LONG).show();
            return false;
        }
        return true;
    }

    private static String sanitize(String name) {
        String n = name == null ? "" : name.trim();
        n = n.replaceAll("[\\\\/:*?\"<>|]", "_");
        if (n.isEmpty()) {
            n = "codearts-download.bin";
        }
        if (n.length() > 120) {
            n = n.substring(n.length() - 120);
        }
        return n;
    }

    private static String defaultName(String mime) {
        String ext = ".bin";
        if (mime != null) {
            String m = mime.toLowerCase(Locale.ROOT);
            if (m.contains("android.package")) {
                ext = ".apk";
            } else if (m.contains("zip")) {
                ext = ".zip";
            } else if (m.contains("json")) {
                ext = ".json";
            } else if (m.contains("text")) {
                ext = ".txt";
            } else if (m.contains("png")) {
                ext = ".png";
            } else if (m.contains("jpeg") || m.contains("jpg")) {
                ext = ".jpg";
            } else if (m.contains("pdf")) {
                ext = ".pdf";
            }
        }
        return "codearts-"
                + new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(new Date())
                + ext;
    }

    private static final class Bridge {
        private final Activity activity;

        Bridge(Activity activity) {
            this.activity = activity;
        }

        @JavascriptInterface
        public void saveBase64(String name, String mime, String dataUrl) {
            if (dataUrl == null || dataUrl.length() > MAX_BASE64_CHARS) {
                fail("文件过大或数据为空");
                return;
            }
            new Thread(() -> {
                try {
                    int comma = dataUrl.indexOf(',');
                    if (comma < 0) {
                        throw new IllegalArgumentException("数据格式错误");
                    }
                    byte[] bytes = Base64.decode(dataUrl.substring(comma + 1), Base64.DEFAULT);
                    String mimeType = (mime == null || mime.isEmpty())
                            ? "application/octet-stream" : mime;
                    String fileName = (name == null || name.isEmpty())
                            ? defaultName(mimeType) : name;
                    saveBytes(activity, fileName, mimeType, bytes);
                } catch (Throwable t) {
                    fail(String.valueOf(t));
                }
            }, "web-download").start();
        }

        @JavascriptInterface
        public void fail(String message) {
            activity.runOnUiThread(() ->
                    Toast.makeText(activity, "下载失败：" + message, Toast.LENGTH_LONG).show());
        }
    }
}