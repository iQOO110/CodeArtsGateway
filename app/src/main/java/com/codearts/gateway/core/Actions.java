package com.codearts.gateway.core;

import android.content.Context;

import java.util.List;

/** App 内拉取 DevCloud 模型与签到。网络调用走后台线程，回调回主线程。 */
public final class Actions {

    public interface ModelCallback {
        void onSuccess(List<Store.ModelInfo> models);

        void onError(String message);
    }

    public interface CheckinCallback {
        /** checkinDone: 成功领取积分；message 已人类可读。 */
        void onResult(DevCloudClient.WelfareResult result);
    }

    private Actions() {
    }

    public static void fetchModels(Context ctx, ModelCallback cb) {
        Store store = Store.get(ctx);
        new Thread(() -> {
            String cookie = store.getCookie();
            if (cookie.isEmpty()) {
                post(() -> cb.onError("尚未登录 DevCloud，请先登录并保存会话"));
                return;
            }
            List<Store.ModelInfo> models = DevCloudClient.fetchModels(cookie);
            if (models.isEmpty()) {
                post(() -> cb.onError("拉取到 0 个模型（Cookie 可能失效，请重新登录）"));
                return;
            }
            store.setModelsCache(Store.modelsToOpenAi(models));
            post(() -> cb.onSuccess(models));
        }, "fetch-models").start();
    }

    public static void checkin(Context ctx, CheckinCallback cb) {
        Store store = Store.get(ctx);
        new Thread(() -> {
            String cookie = store.getCookie();
            if (cookie.isEmpty()) {
                DevCloudClient.WelfareResult r = new DevCloudClient.WelfareResult();
                r.success = false;
                r.message = "尚未登录 DevCloud";
                post(() -> cb.onResult(r));
                return;
            }
            final DevCloudClient.WelfareResult r = DevCloudClient.dailyCheckin(cookie);
            // 诊断落盘：/sdcard/Download/codearts-welfare.json（含 delivery/claim/confirm 原始报文）
            String dumped = DiagDump.save(ctx.getApplicationContext(), "codearts-welfare.json",
                    r.trace + "\n== message: " + r.message
                            + "\n== claimedAmount: " + r.claimedAmount
                            + "\n== items: " + (r.items == null ? 0 : r.items.size())
                            + "\n== claimAttempted: " + r.claimAttempted + "\n");
            LogRing.log("welfare", "诊断报文: " + dumped);
            post(() -> cb.onResult(r));
        }, "checkin").start();
    }

    private static void post(Runnable r) {
        new android.os.Handler(android.os.Looper.getMainLooper()).post(r);
    }
}
