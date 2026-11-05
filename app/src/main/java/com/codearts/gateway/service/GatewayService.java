package com.codearts.gateway.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import com.codearts.gateway.R;
import com.codearts.gateway.core.AppConfig;
import com.codearts.gateway.core.GatewayServer;
import com.codearts.gateway.core.LogRing;
import com.codearts.gateway.core.Store;
import com.codearts.gateway.ui.MainActivity;

/** 前台服务：托管本地网关，保证进程不被随手回收。 */
public class GatewayService extends Service {

    public static final String ACTION_START = "com.codearts.gateway.action.START";
    public static final String ACTION_STOP = "com.codearts.gateway.action.STOP";
    public static final String ACTION_TOGGLE = "com.codearts.gateway.action.TOGGLE";

    private static final String CHANNEL_ID = "gateway_status";
    private static final int NOTIFY_ID = 1001;

    private static volatile GatewayServer sServer;

    public static boolean isRunning() {
        GatewayServer s = sServer;
        return s != null && s.isRunning();
    }

    public static int currentPort() {
        GatewayServer s = sServer;
        return s == null ? -1 : s.getPort();
    }

    public static GatewayServer server() {
        return sServer;
    }

    /** 供界面直接启停（同一进程内）。 */
    public static void ensureStarted(Context ctx) {
        Intent i = new Intent(ctx, GatewayService.class);
        i.setAction(ACTION_START);
        if (Build.VERSION.SDK_INT >= 26) {
            ctx.startForegroundService(i);
        } else {
            ctx.startService(i);
        }
    }

    public static void ensureStopped(Context ctx) {
        Intent i = new Intent(ctx, GatewayService.class);
        i.setAction(ACTION_STOP);
        ctx.startService(i);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        LogRing.attach(this);
        createChannel();
        acquireWakeLock();
    }

    private void acquireWakeLock() {
        if (!Store.get(this).isWakeLock()) {
            LogRing.log("gateway", "CPU 保活开关未开启，跳过 WakeLock");
            return;
        }
        try {
            android.os.PowerManager pm = (android.os.PowerManager)
                    getSystemService(android.content.Context.POWER_SERVICE);
            sWakeLock = pm.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK,
                    "codearts:gateway");
            sWakeLock.setReferenceCounted(false);
            sWakeLock.acquire();
            LogRing.log("gateway", "已持有 PARTIAL_WAKE_LOCK（CPU 不休眠，转发不中断）");
        } catch (Throwable t) {
            LogRing.log("gateway", "WakeLock 获取失败: " + t);
        }
    }

    private void releaseWakeLock() {
        if (sWakeLock != null && sWakeLock.isHeld()) {
            try {
                sWakeLock.release();
            } catch (Throwable ignored) {
            }
        }
        sWakeLock = null;
    }

    private static android.os.PowerManager.WakeLock sWakeLock;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_START : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            stopServer();
            releaseWakeLock();
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }

        try {
            startForeground(NOTIFY_ID, buildNotification("正在启动…"));
        } catch (Throwable t) {
            LogRing.log("gateway", "startForeground 异常（不致命）: " + t);
        }

        if (ACTION_TOGGLE.equals(action) && isRunning()) {
            stopServer();
            releaseWakeLock();
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }

        startServer();
        return START_STICKY;
    }

    private void startServer() {
        if (isRunning()) {
            updateNotification("运行中 · 端口 " + currentPort());
            return;
        }
        try {
            GatewayServer server = new GatewayServer(getApplicationContext());
            server.start();
            sServer = server;
            updateNotification("运行中 · 端口 " + server.getPort());
        } catch (Throwable t) {
            sServer = null;
            LogRing.log("gateway", "启动失败: " + t);
            updateNotification("启动失败：" + t.getMessage() + "（端口可能被占用）");
        }
    }

    private void stopServer() {
        GatewayServer s = sServer;
        if (s != null) {
            s.shutdown();
            sServer = null;
        }
        LogRing.log("gateway", "服务已停止");
    }

    @Override
    public void onDestroy() {
        releaseWakeLock();
        stopServer();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) {
                return;
            }
            // 常驻通知保护：开启时可见（IMPORTANCE_DEFAULT 常驻下拉栏），
            // 关闭时静默（IMPORTANCE_MIN 不响铃不横幅，但仍以前台身份保优先级）。
            // 注意：渠道 importance 创建后不可变——开关切换时必须删旧渠道重建，
            // 否则永远停留在首次创建的优先级（用户实测：开了开关通知栏也不显示）。
            int importance = Store.get(this).isNotifyGuard()
                    ? NotificationManager.IMPORTANCE_DEFAULT
                    : NotificationManager.IMPORTANCE_MIN;
            NotificationChannel old = nm.getNotificationChannel(CHANNEL_ID);
            if (old != null && old.getImportance() != importance) {
                nm.deleteNotificationChannel(CHANNEL_ID);
            }
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, getString(R.string.svc_channel_name), importance);
            ch.setShowBadge(false);
            nm.createNotificationChannel(ch);
        }
    }

    private Notification buildNotification(String text) {
        Intent open = new Intent(this, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(this, 0, open,
                Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);

        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        b.setContentTitle(getString(R.string.svc_notify_title))
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_notification)
                .setOngoing(true)
                .setContentIntent(pi);
        if (Build.VERSION.SDK_INT >= 21) {
            b.setShowWhen(false);
        }
        return b.build();
    }

    private void updateNotification(String text) {
        try {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                nm.notify(NOTIFY_ID, buildNotification(text));
            }
        } catch (Throwable ignored) {
        }
    }

    public static String describe() {
        GatewayServer s = sServer;
        if (s == null || !s.isRunning()) {
            return "未运行";
        }
        return "运行中 · 端口 " + s.getPort() + " · 请求 " + s.getRequestCount()
                + " · 会话 " + s.getSessionCount() + " · " + AppConfig.VERSION;
    }
    /** WakeLock 是否在持（设置页判断用）。 */
    public static boolean wakeLockHeld() {
        return sWakeLock != null && sWakeLock.isHeld();
    }
}

