package com.codearts.gateway.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;

import androidx.core.content.FileProvider;

import com.codearts.gateway.core.Actions;
import com.codearts.gateway.core.ArtifactIndex;
import com.codearts.gateway.core.DevCloudClient;
import com.codearts.gateway.core.DiagDump;
import com.codearts.gateway.core.Diagnostics;
import com.codearts.gateway.core.GatewayServer;
import com.codearts.gateway.core.Store;
import com.codearts.gateway.service.FloatingBallService;
import com.codearts.gateway.service.GatewayService;

import org.json.JSONObject;

import java.io.File;
import java.util.List;
import java.util.Locale;

/**
 * 旧版业务逻辑的唯一出口。
 *
 * <p>签到、额度、模型、诊断、设置、产物都按旧版 Activity 的实现搬运到这里，
 * 新版 WebBridge 直接调用，不再另写一套逻辑。
 */
public final class LegacyOps {

    public interface TextCallback {
        void done(boolean ok, String text);
    }

    public interface ModelsCallback {
        void done(boolean ok, List<Store.ModelInfo> models, String message);
    }

    public interface DiagCallback {
        void done(Diagnostics.Report report);
    }

    public interface Done {
        void done(boolean ok, String message);
    }

    private LegacyOps() {
    }

    public static boolean loggedIn(Context ctx) {
        String cookie = Store.get(ctx).getCookie();
        return !cookie.isEmpty() && cookie.toLowerCase(Locale.ROOT).contains("cftk");
    }

    /** 旧版 MainActivity.doCheckin 的文本与判断。 */
    public static void checkin(Context ctx, TextCallback cb) {
        if (!loggedIn(ctx)) {
            cb.done(false, "请先登录码道");
            return;
        }
        Actions.checkin(ctx, r -> {
            StringBuilder sb = new StringBuilder();
            sb.append("签到结果：").append(r.message == null ? "完成" : r.message);
            if (r.claimedAmount > 0) {
                sb.append("（+").append(r.claimedAmount).append(" 积分）");
            }
            sb.append('\n');
            if (r.items != null) {
                for (DevCloudClient.Welfare w : r.items) {
                    sb.append("· ").append(w.title == null || w.title.isEmpty() ? w.type : w.title)
                            .append("  [").append(w.typeNorm).append('/')
                            .append(w.rawStatus == null || w.rawStatus.isEmpty() ? "?" : w.rawStatus)
                            .append(']');
                    if (w.pendingAmount > 0) {
                        sb.append("  +").append(w.pendingAmount);
                    }
                    sb.append('\n');
                }
            }
            cb.done(r.success, sb.toString());
        });
    }

    /** 旧版 MainActivity.fetchBalance 的完整文本（积分 + 免费额度 + 套餐 token）。 */
    public static void balance(Context ctx, TextCallback cb) {
        if (!loggedIn(ctx)) {
            cb.done(false, "请先登录码道");
            return;
        }
        final String cookie = Store.get(ctx).getCookie();
        new Thread(() -> {
            DevCloudClient.Balance b = DevCloudClient.fetchBalance(cookie);
            DevCloudClient.PackageStats p = DevCloudClient.fetchPackageStats(cookie);
            DiagDump.save(ctx.getApplicationContext(), "codearts-package.json",
                    "== 额度探针（点「查询额度」时写入）\n"
                            + "== GET /snap-manager/v1/statistics/plugin → ok=" + p.ok
                            + " package=" + p.packageName + " specCode=" + p.specCode
                            + " isCreditPackage=" + p.isCreditPackage
                            + "\n== credit used/total/remain = " + p.creditUsed + " / " + p.creditTotal
                            + " / " + p.creditRemain
                            + "\n== token used/total/unlimited = " + p.tokenUsed + " / " + p.tokenTotal
                            + " / " + p.unlimited
                            + "\n== balance(免费额度) used/total = " + b.usedAmount + " / " + b.totalQuota
                            + "\n== message: " + p.message + " | " + b.message
                            + "\n== balance raw（截断 4000 字符）: "
                            + (b.raw.length() > 4000 ? b.raw.substring(0, 4000) : b.raw) + "\n");
            StringBuilder sb = new StringBuilder();
            if (p.ok && (p.creditUsed != null || p.creditTotal != null)) {
                sb.append("积分：剩余 ").append(DevCloudClient.fmtNum(p.creditRemain));
                if (p.creditTotal != null) {
                    sb.append(" / ").append(DevCloudClient.fmtNum(p.creditTotal));
                }
                Double cp = p.creditPercent();
                if (cp != null) {
                    sb.append("（已用 ").append(String.format(Locale.ROOT, "%.1f", cp)).append("%）");
                }
                sb.append('\n');
            } else if (!p.ok && p.message != null && !p.message.isEmpty()) {
                sb.append("积分额度查询失败：").append(p.message).append('\n');
            }
            if (b.ok) {
                sb.append("免费额度：");
                if (b.balance() != null) {
                    sb.append("剩余 ").append(b.balance());
                    if (b.totalQuota != null) {
                        sb.append(" / ").append(b.totalQuota);
                    }
                    Integer pct = b.percentUsed();
                    if (pct != null) {
                        sb.append("（已用 ").append(pct).append("%）");
                    }
                } else if (b.totalQuota != null) {
                    sb.append("总额度 ").append(b.totalQuota);
                } else if (b.usedAmount != null) {
                    sb.append("已用 ").append(b.usedAmount);
                }
            } else if (p.ok) {
                sb.append("免费额度：上游未返回（不影响模型与对话）");
            } else {
                sb.append("免费额度查询失败：").append(b.message);
            }
            if (p.ok && p.tokenTotal != null) {
                sb.append("\n套餐 token：").append(DevCloudClient.fmtNum(p.tokenUsed))
                        .append(" / ").append(DevCloudClient.fmtNum(p.tokenTotal));
            } else if (p.unlimited) {
                sb.append("\n套餐 token：∞ 无限制");
            }
            cb.done(true, sb.toString());
        }, "legacy-balance").start();
    }

    /** 旧版 ModelsActivity.refresh 的 Actions.fetchModels。 */
    public static void refreshModels(Context ctx, ModelsCallback cb) {
        if (!loggedIn(ctx)) {
            cb.done(false, null, "请先登录码道");
            return;
        }
        Actions.fetchModels(ctx, new Actions.ModelCallback() {
            @Override
            public void onSuccess(List<Store.ModelInfo> models) {
                cb.done(true, models, "");
            }

            @Override
            public void onError(String message) {
                cb.done(false, null, message);
            }
        });
    }

    /** 旧版 DiagActivity.runDiag 的 Diagnostics.diagnose。 */
    public static void diagnose(Context ctx, DiagCallback cb) {
        new Thread(() -> cb.done(Diagnostics.diagnose(Store.get(ctx), ctx)), "legacy-diag").start();
    }

    /** 旧版 SettingsActivity.save 的保存、悬浮球权限、通知/保活重启逻辑。 */
    public static void saveSettings(Activity activity, JSONObject p, Done done) {
        Store store = Store.get(activity);
        int port = p.optInt("port", store.getPort());
        if (port < 1024 || port > 65535) {
            done.done(false, "端口需在 1024–65535 之间");
            return;
        }
        JSONObject s = p.optJSONObject("settings");
        boolean lan = p.optBoolean("lan", store.isLanEnabled());
        boolean wasRunning = GatewayService.isRunning();
        boolean notifyBefore = store.isNotifyGuard();
        boolean restart = wasRunning
                && (port != store.getPort() || lan != store.isLanEnabled());

        store.setPort(port);
        store.setLanEnabled(lan);
        if (s != null) {
            store.setAutostart(s.optBoolean("autostart", store.isAutostart()));
            store.setWakeLock(s.optBoolean("wakeLock", store.isWakeLock()));
            store.setNotifyGuard(s.optBoolean("notify", store.isNotifyGuard()));
            store.setTrimSystem(s.optBoolean("trimSystem", store.isTrimSystem()));
            store.setStreamThinking(s.optBoolean("streamThinking", store.isStreamThinking()));
            store.setOnlyLastUser(s.optBoolean("onlyLastUser", store.isOnlyLastUser()));
            store.setDeliverables(s.optBoolean("deliverables", store.isDeliverables()));
            store.setFloatBall(s.optBoolean("floatBall", store.isFloatBall()));
        }
        GatewayServer.sTrimSystem = store.isTrimSystem();
        GatewayServer.sStreamThinking = store.isStreamThinking();
        GatewayServer.sOnlyLastUser = store.isOnlyLastUser();
        GatewayServer.sDeliverables = store.isDeliverables();

        boolean wantFloat = store.isFloatBall();
        if (wantFloat && !Settings.canDrawOverlays(activity)) {
            wantFloat = false;
            store.setFloatBall(false);
            requestOverlayPermission(activity);
        }
        store.setFloatBall(wantFloat);
        if (wantFloat) {
            store.setFloatBallHidden(false);
            try {
                activity.startService(new Intent(activity, FloatingBallService.class));
            } catch (Throwable ignore) {
                // 忽略
            }
        } else {
            activity.stopService(new Intent(activity, FloatingBallService.class));
        }

        boolean notifyChanged = store.isNotifyGuard() != notifyBefore;
        boolean wakeChanged = wasRunning && store.isWakeLock() != GatewayService.wakeLockHeld();
        if (restart || (wasRunning && notifyChanged) || wakeChanged) {
            GatewayService.ensureStopped(activity);
            final boolean wantFloatF = wantFloat;
            new android.os.Handler(android.os.Looper.getMainLooper())
                    .postDelayed(() -> {
                        GatewayService.ensureStarted(activity);
                        if (wantFloatF) {
                            try {
                                activity.startService(new Intent(activity, FloatingBallService.class));
                            } catch (Throwable ignore) {
                                // 忽略
                            }
                        }
                    }, 500);
            done.done(true, "已保存，网关正以新配置重启");
        } else {
            done.done(true, "已保存");
        }
    }

    public static ArtifactIndex.Item findArtifact(Context ctx, String path) {
        if (path == null || path.isEmpty()) {
            return null;
        }
        for (ArtifactIndex.Item it : ArtifactIndex.list(ctx)) {
            if (path.equals(it.path)) {
                return it;
            }
        }
        return null;
    }

    /** 旧版 ArtifactsActivity.open。 */
    public static boolean openArtifact(Activity activity, ArtifactIndex.Item item) {
        Uri uri = contentUri(activity, item);
        if (uri == null) {
            return false;
        }
        String mime = item.mime == null || item.mime.isEmpty()
                ? "application/octet-stream" : item.mime;
        try {
            activity.startActivity(new Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, mime)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static Uri contentUri(Context ctx, ArtifactIndex.Item item) {
        if (item.uri != null && !item.uri.isEmpty()) {
            return Uri.parse(item.uri);
        }
        File f = new File(item.path);
        if (!f.exists()) {
            return null;
        }
        try {
            return FileProvider.getUriForFile(ctx,
                    ctx.getPackageName() + ".fileprovider", f);
        } catch (Throwable t) {
            return null;
        }
    }

    private static void requestOverlayPermission(Activity activity) {
        try {
            activity.startActivity(new Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + activity.getPackageName())));
        } catch (Throwable ignore) {
            // 忽略
        }
    }
}