package com.codearts.gateway.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;

import com.codearts.gateway.core.LogRing;
import com.codearts.gateway.core.Store;

/** 开机自启（全量功能开关之一）。 */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent == null ? "" : String.valueOf(intent.getAction());
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action)
                && !"android.intent.action.QUICKBOOT_POWERON".equals(action)) {
            return;
        }
        if (!Store.get(context).isAutostart()) {
            return;
        }
        LogRing.log("boot", "开机自启触发");
        try {
            GatewayService.ensureStarted(context);
        } catch (Throwable t) {
            LogRing.log("boot", "开机启动失败: " + t);
        }
        // 悬浮球保活（若开关开启且已授予悬浮窗权限）
        if (Store.get(context).isFloatBall() && Settings.canDrawOverlays(context)) {
            // canDrawOverlays 前置判断保留；服务内部还有二次防御
            try {
                context.startService(new Intent(context, FloatingBallService.class));
            } catch (Throwable ignored) {
            }
        }
    }
}
