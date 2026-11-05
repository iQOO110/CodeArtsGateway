package com.codearts.gateway.ui;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.codearts.gateway.core.Actions;
import com.codearts.gateway.core.ArtifactIndex;
import com.codearts.gateway.core.DevCloudClient;
import com.codearts.gateway.core.DiagDump;
import com.codearts.gateway.core.Diagnostics;
import com.codearts.gateway.core.GatewayServer;
import com.codearts.gateway.core.LogRing;
import com.codearts.gateway.core.Store;
import com.codearts.gateway.service.FloatingBallService;
import com.codearts.gateway.service.GatewayService;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;

/**
 * 新版 Web 前端的 Android 桥接层。
 *
 * <p>前端通过 {@code window.CodeArtsNative.state()} 读取真实状态，
 * 通过 {@code callAsync(action, payload, callbackId)} 执行操作，
 * 完成后回调 {@code window.__nativeCallback(callbackId, ok, json)}。
 */
public class WebBridge {

    private final Activity activity;
    private final WebView webView;
    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile String balanceText = "";
    private volatile String welfareText = "";

    public WebBridge(Activity activity, WebView webView) {
        this.activity = activity;
        this.webView = webView;
    }

    @JavascriptInterface
    public String state() {
        return stateJson().toString();
    }

    @JavascriptInterface
    public void copy(String text) {
        String value = text == null ? "" : text;
        main.post(() -> {
            ClipboardManager cm = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(ClipData.newPlainText("codearts", value));
                Toast.makeText(activity, "已复制", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @JavascriptInterface
    public void callAsync(String action, String payload, String callbackId) {
        if (action == null) {
            return;
        }
        JSONObject p = parse(payload);
        try {
            switch (action) {
                case "start":
                    GatewayService.ensureStarted(activity);
                    respond(callbackId, true, stateJson());
                    break;
                case "stop":
                    GatewayService.ensureStopped(activity);
                    respond(callbackId, true, stateJson());
                    break;
                case "checkin":
                    checkin(callbackId);
                    break;
                case "balance":
                    balance(callbackId);
                    break;
                case "refreshModels":
                    refreshModels(callbackId);
                    break;
                case "diagnose":
                    diagnose(callbackId);
                    break;
                case "saveSettings":
                    saveSettings(p, callbackId);
                    break;
                case "addKey":
                    addKey(p, callbackId);
                    break;
                case "removeKey":
                    removeKey(p, callbackId);
                    break;
                case "openLogin":
                    activity.startActivity(new Intent(activity, LoginActivity.class));
                    respond(callbackId, true, new JSONObject());
                    break;
                case "openScreen":
                    openScreen(p, callbackId);
                    break;
                case "saveCookie":
                    saveCookie(p, callbackId);
                    break;
                case "logout":
                    logout(callbackId);
                    break;
                case "downloadLog":
                    downloadLog(callbackId);
                    break;
                case "openArtifact":
                    openArtifact(p.optString("path", ""), callbackId);
                    break;
                case "saveArtifact":
                    saveArtifact(p.optString("path", ""), callbackId);
                    break;
                case "battery":
                    activity.startActivity(new Intent(
                            Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
                    respond(callbackId, true, new JSONObject());
                    break;
                case "notifySettings":
                    openNotifySettings();
                    respond(callbackId, true, new JSONObject());
                    break;
                case "clearLog":
                    LogRing.clear();
                    respond(callbackId, true, stateJson());
                    break;
                default:
                    respond(callbackId, false, message("未知操作: " + action));
                    break;
            }
        } catch (Throwable t) {
            respond(callbackId, false, message(String.valueOf(t)));
        }
    }

    private JSONObject stateJson() {
        Store store = Store.get(activity);
        JSONObject o = new JSONObject();
        try {
            o.put("running", GatewayService.isRunning());
            o.put("port", GatewayService.isRunning() ? GatewayService.currentPort() : store.getPort());
            o.put("lan", store.isLanEnabled());
            String cookie = store.getCookie();
            String lowCookie = cookie.toLowerCase(Locale.ROOT);
            boolean loggedIn = !cookie.isEmpty() && lowCookie.contains("cftk");
            o.put("loggedIn", loggedIn);
            o.put("cookie", cookie);
            o.put("sessionTail", cookie.length() > 6 ? cookie.substring(cookie.length() - 6) : cookie);

            JSONArray keys = new JSONArray();
            for (String k : store.getApiKeys()) {
                keys.put(k);
            }
            o.put("keys", keys);

            JSONArray models = new JSONArray();
            if (loggedIn) {
                for (Store.ModelInfo m : store.cachedModels()) {
                    JSONObject x = new JSONObject();
                    x.put("id", m.id);
                    x.put("name", m.name);
                    JSONArray tags = new JSONArray();
                    for (String t : m.tags) {
                        tags.put(t);
                    }
                    x.put("tags", tags);
                    models.put(x);
                }
            }
            o.put("models", models);
            GatewayServer srv = GatewayService.server();
            o.put("requests", srv == null ? 0 : srv.getRequestCount());
            o.put("devSessions", srv == null ? 0 : srv.getDevCloudSessionCount());
            o.put("balanceText", balanceText);
            o.put("welfareText", welfareText);

            JSONArray logs = new JSONArray();
            for (String line : LogRing.snapshot()) {
                logs.put(line);
            }
            o.put("logs", logs);

            JSONObject settings = new JSONObject();
            settings.put("autostart", store.isAutostart());
            settings.put("floatBall", store.isFloatBall());
            settings.put("wakeLock", store.isWakeLock());
            settings.put("notify", store.isNotifyGuard());
            settings.put("trimSystem", store.isTrimSystem());
            settings.put("streamThinking", store.isStreamThinking());
            settings.put("onlyLastUser", store.isOnlyLastUser());
            settings.put("deliverables", store.isDeliverables());
            o.put("settings", settings);

            JSONArray arts = new JSONArray();
            SimpleDateFormat fmt = new SimpleDateFormat("HH:mm", Locale.ROOT);
            for (ArtifactIndex.Item it : ArtifactIndex.list(activity)) {
                JSONObject a = new JSONObject();
                a.put("name", it.name);
                a.put("path", it.path);
                a.put("kind", kind(it.name));
                a.put("meta", size(it.size) + " · " + kind(it.name) + " · "
                        + (it.time > 0 ? fmt.format(new Date(it.time)) : "时间未知"));
                arts.put(a);
            }
            o.put("artifacts", arts);
            o.put("lanIp", localIp());
        } catch (Throwable t) {
            try {
                o.put("error", String.valueOf(t));
            } catch (Throwable ignore) {
                // 忽略
            }
        }
        return o;
    }

    private void checkin(String callbackId) {
        LegacyOps.checkin(activity, (ok, text) -> {
            welfareText = text;
            JSONObject o = new JSONObject();
            try {
                o.put("text", text);
            } catch (Throwable ignore) {
                // 忽略
            }
            respond(callbackId, ok, o);
        });
    }

    private void balance(String callbackId) {
        LegacyOps.balance(activity, (ok, text) -> {
            balanceText = text;
            JSONObject o = new JSONObject();
            try {
                o.put("text", text);
            } catch (Throwable ignore) {
                // 忽略
            }
            respond(callbackId, ok, o);
        });
    }

    private void refreshModels(String callbackId) {
        LegacyOps.refreshModels(activity, (ok, models, msg) -> {
            if (!ok) {
                respond(callbackId, false, message(msg));
                return;
            }
            JSONObject o = new JSONObject();
            try {
                o.put("models", modelsJson(models));
            } catch (Throwable ignore) {
                // 忽略
            }
            respond(callbackId, true, o);
        });
    }

    private static JSONArray modelsJson(List<Store.ModelInfo> models) throws Exception {
        JSONArray arr = new JSONArray();
        for (Store.ModelInfo m : models) {
            JSONObject x = new JSONObject();
            x.put("id", m.id);
            x.put("name", m.name);
            JSONArray tags = new JSONArray();
            for (String t : m.tags) {
                tags.put(t);
            }
            x.put("tags", tags);
            arr.put(x);
        }
        return arr;
    }

    private void diagnose(String callbackId) {
        LegacyOps.diagnose(activity, r -> {
            JSONObject o = new JSONObject();
            try {
                JSONArray arr = new JSONArray();
                for (Diagnostics.Item it : r.items) {
                    JSONObject x = new JSONObject();
                    x.put("name", it.name);
                    x.put("ok", it.ok);
                    x.put("detail", it.detail);
                    arr.put(x);
                }
                o.put("items", arr);
                o.put("allOk", r.allOk);
                o.put("verdict", r.verdict);
            } catch (Throwable ignore) {
                // 忽略
            }
            respond(callbackId, true, o);
        });
    }

    private void saveSettings(JSONObject p, String callbackId) {
        LegacyOps.saveSettings(activity, p, (ok, msg) -> {
            if (!ok) {
                respond(callbackId, false, message(msg));
                return;
            }
            respond(callbackId, true, stateJson());
        });
    }

    private void addKey(JSONObject p, String callbackId) {
        Store store = Store.get(activity);
        String key = p.optString("key", "").trim();
        if (key.isEmpty()) {
            key = Store.newApiKey();
        }
        store.addApiKey(key);
        respond(callbackId, true, stateJson());
    }

    private void removeKey(JSONObject p, String callbackId) {
        Store.get(activity).removeApiKey(p.optString("key", ""));
        respond(callbackId, true, stateJson());
    }

    private void logout(String callbackId) {
        Store store = Store.get(activity);
        store.setCookie("");
        store.setModelsCache("");
        balanceText = "";
        welfareText = "";
        try {
            CookieManager cm = CookieManager.getInstance();
            cm.removeAllCookies(null);
            cm.flush();
        } catch (Throwable ignore) {
            // 忽略
        }
        try {
            GatewayServer gw = GatewayService.server();
            if (gw != null) {
                gw.resetSessions();
            }
        } catch (Throwable ignore) {
            // 忽略
        }
        respond(callbackId, true, stateJson());
    }

    private void downloadLog(String callbackId) {
        String content = LogRing.dump();
        if (content.isEmpty()) {
            respond(callbackId, false, message("暂无日志"));
            return;
        }
        String name = "codearts-gateway-log-"
                + new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(new Date()) + ".txt";
        String path = DiagDump.save(activity, name, content);
        JSONObject o = new JSONObject();
        try {
            o.put("path", path == null ? "" : path);
        } catch (Throwable ignore) {
            // 忽略
        }
        respond(callbackId, true, o);
    }

    /** 新版只做导航：具体功能打开旧版 Activity，复用旧版已经调好的逻辑。 */
    private void openScreen(JSONObject p, String callbackId) {
        String screen = p.optString("screen", "");
        Intent i;
        switch (screen) {
            case "main":
                i = new Intent(activity, MainActivity.class);
                String action = p.optString("action", "");
                if (!action.isEmpty()) {
                    i.putExtra("auto_action", action);
                }
                break;
            case "models":
                i = new Intent(activity, ModelsActivity.class);
                break;
            case "diag":
                i = new Intent(activity, DiagActivity.class).putExtra("auto_run", true);
                break;
            case "artifacts":
                i = new Intent(activity, ArtifactsActivity.class);
                break;
            case "settings":
                i = new Intent(activity, SettingsActivity.class);
                break;
            case "keys":
                i = new Intent(activity, ApiKeysActivity.class);
                break;
            case "login":
                i = new Intent(activity, LoginActivity.class);
                break;
            default:
                respond(callbackId, false, message("未知页面: " + screen));
                return;
        }
        try {
            activity.startActivity(i);
            respond(callbackId, true, new JSONObject());
        } catch (Throwable t) {
            respond(callbackId, false, message(String.valueOf(t)));
        }
    }

    private void saveCookie(JSONObject p, String callbackId) {
        Store store = Store.get(activity);
        String cookie = p.optString("cookie", "").trim();
        if (cookie.isEmpty()) {
            cookie = store.getCookie();
        }
        if (cookie.isEmpty()) {
            respond(callbackId, false, message("还没有可保存的会话，请先网页登录"));
            return;
        }
        String low = cookie.toLowerCase(Locale.ROOT);
        if (!low.contains("cftk") && !low.contains("session")) {
            respond(callbackId, false, message("Cookie 里没有会话键（cftk/session）"));
            return;
        }
        store.setCookie(cookie);
        store.addCookieHistory(cookie);
        respond(callbackId, true, stateJson());
    }

    private void openArtifact(String path, String callbackId) {
        ArtifactIndex.Item item = LegacyOps.findArtifact(activity, path);
        if (item == null) {
            respond(callbackId, false, message("产物不存在或已被移动"));
            return;
        }
        if (!LegacyOps.openArtifact(activity, item)) {
            respond(callbackId, false, message("文件不存在或没有可打开的应用"));
            return;
        }
        respond(callbackId, true, new JSONObject());
    }

    private void saveArtifact(String path, String callbackId) {
        if (activity instanceof WebConsoleActivity) {
            ((WebConsoleActivity) activity).startSaveArtifact(path);
            respond(callbackId, true, new JSONObject());
        } else {
            respond(callbackId, false, message("当前页面不支持另存"));
        }
    }

    private void openNotifySettings() {
        try {
            Intent i = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, activity.getPackageName());
            activity.startActivity(i);
        } catch (Throwable t) {
            try {
                activity.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        .setData(Uri.parse("package:" + activity.getPackageName())));
            } catch (Throwable ignore) {
                // 忽略
            }
        }
    }

    private ArtifactIndex.Item findArtifact(String path) {
        if (path == null || path.isEmpty()) {
            return null;
        }
        for (ArtifactIndex.Item it : ArtifactIndex.list(activity)) {
            if (path.equals(it.path)) {
                return it;
            }
        }
        return null;
    }

    private Uri contentUri(ArtifactIndex.Item item) {
        if (item.uri != null && !item.uri.isEmpty()) {
            return Uri.parse(item.uri);
        }
        File f = new File(item.path);
        if (!f.exists()) {
            return null;
        }
        try {
            return FileProvider.getUriForFile(activity,
                    activity.getPackageName() + ".fileprovider", f);
        } catch (Throwable t) {
            return null;
        }
    }

    private void respond(String callbackId, boolean ok, JSONObject data) {
        if (callbackId == null || callbackId.isEmpty()) {
            return;
        }
        String json = data == null ? "{}" : data.toString();
        String js = "window.__nativeCallback(" + JSONObject.quote(callbackId) + "," + ok + ","
                + JSONObject.quote(json) + ")";
        main.post(() -> {
            try {
                webView.evaluateJavascript(js, null);
            } catch (Throwable ignore) {
                // 页面已销毁
            }
        });
    }

    private static JSONObject parse(String payload) {
        if (payload == null || payload.isEmpty()) {
            return new JSONObject();
        }
        try {
            return new JSONObject(payload);
        } catch (Throwable t) {
            return new JSONObject();
        }
    }

    private static JSONObject message(String text) {
        JSONObject o = new JSONObject();
        try {
            o.put("message", text == null ? "" : text);
        } catch (Throwable ignore) {
            // 忽略
        }
        return o;
    }

    private static String size(long bytes) {
        if (bytes <= 0) {
            return "大小未知";
        }
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024L * 1024) {
            return String.format(Locale.ROOT, "%.1f KB", bytes / 1024.0);
        }
        return String.format(Locale.ROOT, "%.1f MB", bytes / 1024.0 / 1024.0);
    }

    private static String kind(String name) {
        String n = name == null ? "" : name.toLowerCase(Locale.ROOT);
        if (n.endsWith(".apk")) {
            return "APK";
        }
        if (n.endsWith(".md") || n.endsWith(".markdown")) {
            return "MD";
        }
        if (n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg")
                || n.endsWith(".webp") || n.endsWith(".gif")) {
            return "IMG";
        }
        if (n.endsWith(".log")) {
            return "LOG";
        }
        if (n.endsWith(".json")) {
            return "JSON";
        }
        if (n.endsWith(".zip")) {
            return "ZIP";
        }
        return "FILE";
    }

    private static String localIp() {
        try {
            Enumeration<NetworkInterface> ifs = NetworkInterface.getNetworkInterfaces();
            while (ifs != null && ifs.hasMoreElements()) {
                NetworkInterface nif = ifs.nextElement();
                if (nif == null || !nif.isUp() || nif.isLoopback()) {
                    continue;
                }
                Enumeration<InetAddress> addrs = nif.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress a = addrs.nextElement();
                    if (a instanceof Inet4Address && !a.isLoopbackAddress()) {
                        return a.getHostAddress();
                    }
                }
            }
        } catch (Throwable ignore) {
            // 忽略
        }
        return "127.0.0.1";
    }
}