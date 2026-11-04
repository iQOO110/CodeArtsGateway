package com.codearts.gateway.service;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import com.codearts.gateway.R;
import com.codearts.gateway.core.LogRing;
import com.codearts.gateway.ui.MainActivity;
import com.codearts.gateway.core.Store;

/**
 * 悬浮球保活：拖动移动、单击启停网关、长按隐藏。
 * 必须以前台服务身份运行——vivo/OriginOS 等会拒绝【后台服务】的 addView
 * （表现为"开了开关没弹悬浮窗"，且 TYPE_APPLICATION_OVERLAY 的 token 被延迟校验）。
 * 因此 onCreate 先 startForeground 拿前台身份，再尝试 addView，失败后延迟重试一次。
 */
public class FloatingBallService extends Service {

    private WindowManager wm;
    private View ball;
    private WindowManager.LayoutParams lp;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean hidden;

    private static final String CHANNEL_ID = "float_ball";
    private static final int NOTIFY_ID = 1002;

    @Override
    public void onCreate() {
        super.onCreate();
        // WindowManager 必须最先初始化（v1.6.4 重写时被误删，导致 addView NPE——日志实测）
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        // 前台化：拿到前台 token 后，ROM 才允许 addView（尤其 vivo）
        startForegroundWithNotice();
        // 防御：无悬浮窗权限时绝不能 addView（BadTokenException 会崩溃）
        if (!android.provider.Settings.canDrawOverlays(this)) {
            LogRing.log("float", "未授予「显示在其他应用上层」权限，悬浮球不启动");
            stopSelf();
            return;
        }
        tryAddBall(0);
    }

    /** 前台化：拿到前台 token 后，ROM 才允许 addView（尤其 vivo）。 */
    private void startForegroundWithNotice() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CHANNEL_ID) == null) {
            NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "悬浮球",
                    NotificationManager.IMPORTANCE_MIN);
            ch.setShowBadge(false);
            nm.createNotificationChannel(ch);
        }
        Notification n;
        if (Build.VERSION.SDK_INT >= 26) {
            n = new Notification.Builder(this, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle("悬浮球保活中")
                    .setContentText("点按启停网关 · 长按隐藏")
                    .setOngoing(true)
                    .build();
        } else {
            n = new Notification.Builder(this)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle("悬浮球保活中")
                    .setOngoing(true)
                    .build();
        }
        Intent click = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, click,
                PendingIntent.FLAG_IMMUTABLE);
        Notification withContent = buildNotice(n, pi);
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(NOTIFY_ID, withContent,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            } else {
                startForeground(NOTIFY_ID, withContent);
            }
        } catch (Throwable t) {
            // Manifest 已声明 specialUse；这里兜底 ROM 层校验差异，绝不因通知崩溃主线程
            LogRing.log("float", "startForeground 失败（不致命）: " + t);
        }
    }

    private Notification buildNotice(Notification base, PendingIntent pi) {
        // 复用 base，避免重复构建样式差异
        try {
            Notification.Builder b;
            if (Build.VERSION.SDK_INT >= 26) {
                b = Notification.Builder.recoverBuilder(this, base);
            } else {
                b = new Notification.Builder(this);
            }
            b.setContentIntent(pi);
            return b.build();
        } catch (Throwable t) {
            return base;
        }
    }

    /** 尝试添加悬浮球；失败则延迟重试（ROM token 延迟校验场景）。 */
    private void tryAddBall(final int attempt) {
        handler.post(() -> {
            try {
                showBall();
                LogRing.log("float", "悬浮球已显示" + (attempt > 0 ? "（第 " + (attempt + 1) + " 次尝试）" : ""));
            } catch (Throwable t) {
                LogRing.log("float", "悬浮窗创建失败（第 " + (attempt + 1) + " 次）: " + t);
                ball = null;
                if (attempt < 1) {
                    handler.postDelayed(() -> tryAddBall(attempt + 1), 600);
                } else {
                    LogRing.log("float", "重试仍失败，悬浮球放弃本次显示");
                    stopSelf();
                }
            }
        });
    }

    @SuppressLint("ClickableViewAccessibility")
    private void showBall() {
        if (ball != null) {
            return;
        }
        ball = new TextView(this);
        TextView tv = (TextView) ball;
        tv.setText("道");
        tv.setTextColor(0xFFFFFFFF);
        tv.setTextSize(16);
        tv.setGravity(Gravity.CENTER);
        tv.setBackground(new android.graphics.drawable.GradientDrawable() {{
            setShape(android.graphics.drawable.GradientDrawable.OVAL);
            setColors(new int[]{0xFF4353FF, 0xFF9B4DFF});
            setOrientation(android.graphics.drawable.GradientDrawable.Orientation.TL_BR);
        }});

        int size = (int) (52 * getResources().getDisplayMetrics().density);
        lp = new WindowManager.LayoutParams(size, size,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = 24;
        lp.y = 240;
        wm.addView(ball, lp);
        setupTouch();
        updateVisual();
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupTouch() {
        final float[] down = new float[2];
        final int[] start = new int[2];
        final boolean[] moved = {false};
        final long[] downAt = {0};

        ball.setOnTouchListener((v, ev) -> {
            switch (ev.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    down[0] = ev.getRawX();
                    down[1] = ev.getRawY();
                    start[0] = lp.x;
                    start[1] = lp.y;
                    moved[0] = false;
                    downAt[0] = System.currentTimeMillis();
                    handler.postDelayed(() -> {
                        if (!moved[0]) {
                            hideThisSession();
                        }
                    }, 800);
                    return true;
                case MotionEvent.ACTION_MOVE: {
                    float dx = ev.getRawX() - down[0];
                    float dy = ev.getRawY() - down[1];
                    if (Math.abs(dx) > 8 || Math.abs(dy) > 8) {
                        moved[0] = true;
                        lp.x = start[0] + (int) dx;
                        lp.y = start[1] + (int) dy;
                        try {
                            wm.updateViewLayout(ball, lp);
                        } catch (Throwable ignored) {
                        }
                    }
                    return true;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    handler.removeCallbacksAndMessages(null);
                    if (!moved[0] && System.currentTimeMillis() - downAt[0] < 800) {
                        toggleGateway();
                    }
                    return true;
                default:
                    return false;
            }
        });
    }

    private void toggleGateway() {
        boolean running = GatewayService.isRunning();
        if (running) {
            GatewayService.ensureStopped(this);
        } else {
            GatewayService.ensureStarted(this);
        }
        LogRing.log("float", (running ? "停止" : "启动") + "网关（悬浮球单击）");
        handler.postDelayed(this::updateVisual, 600);
    }

    private void updateVisual() {
        if (ball == null) {
            return;
        }
        boolean running = GatewayService.isRunning();
        ((TextView) ball).setText(running ? "停" : "启");
        ball.setAlpha(running ? 1f : 0.75f);
    }

    /** 长按隐藏：移除悬浮窗、停止前台身份并停止自身；写入"本次隐藏"标记，
     *  主页 onResume 的 syncFloatBall 看到标记不再自动拉起（重新保存开关即清除标记）。 */
    private void hideThisSession() {
        if (ball != null) {
            try {
                wm.removeView(ball);
            } catch (Throwable ignored) {
            }
            ball = null;
        }
        Store.get(this).setFloatBallHidden(true);
        LogRing.log("float", "悬浮球已隐藏（长按）。重新在设置里保存开关可恢复");
        try {
            stopForeground(STOP_FOREGROUND_REMOVE);
        } catch (Throwable ignored) {
        }
        stopSelf();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (ball == null) {
            // 服务可能还在（未 stopSelf）或被重建：onCreate 已做权限检查，这里重新尝试显示
            tryAddBall(0);
        } else {
            updateVisual();
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (ball != null) {
            try {
                wm.removeView(ball);
            } catch (Throwable ignored) {
            }
            ball = null;
        }
        super.onDestroy();
    }

    @Override
    public android.os.IBinder onBind(Intent intent) {
        return null;
    }
}
