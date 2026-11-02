package com.codearts.gateway.ui;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;

import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;

import java.net.Inet4Address;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

import com.codearts.gateway.R;
import com.codearts.gateway.core.Actions;
import com.codearts.gateway.core.DevCloudClient;
import com.codearts.gateway.core.DiagDump;
import com.codearts.gateway.core.GatewayServer;
import com.codearts.gateway.core.LogRing;
import com.codearts.gateway.core.Store;
import com.codearts.gateway.service.FloatingBallService;
import com.codearts.gateway.service.GatewayService;

public class MainActivity extends AppCompatActivity {

    private TextView tvState;
    private TextView tvDetail;
    private TextView tvBase;
    private TextView tvEndpoints;
    private TextView tvWelfare;
    private android.view.View dotState;
    private Chip chipPort;
    private MaterialButton btnToggle;

    private Store store;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            refresh();
            handler.postDelayed(this, 1500);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        store = Store.get(this);

        tvState = findViewById(R.id.tv_state);
        tvDetail = findViewById(R.id.tv_detail);
        tvBase = findViewById(R.id.tv_base);
        tvEndpoints = findViewById(R.id.tv_endpoints);
        tvWelfare = findViewById(R.id.tv_welfare);
        dotState = findViewById(R.id.dot_state);
        chipPort = findViewById(R.id.chip_port);
        btnToggle = findViewById(R.id.btn_toggle);

        // 点端口 Chip → 弹窗自定义端口
        chipPort.setOnClickListener(v -> promptPort());

        btnToggle.setOnClickListener(v -> {
            if (GatewayService.isRunning()) {
                GatewayService.ensureStopped(this);
            } else {
                GatewayService.ensureStarted(this);
            }
            handler.postDelayed(this::refresh, 600);
        });

        findViewById(R.id.btn_login).setOnClickListener(v ->
                startActivity(new Intent(this, LoginActivity.class)));

        findViewById(R.id.btn_models).setOnClickListener(v ->
                startActivity(new Intent(this, ModelsActivity.class)));

        findViewById(R.id.btn_copy).setOnClickListener(v -> {
            String url = baseUrl();
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(ClipData.newPlainText("baseUrl", url));
            }
            Toast.makeText(this, "已复制：" + url, Toast.LENGTH_SHORT).show();
        });

        findViewById(R.id.btn_checkin).setOnClickListener(v -> doCheckin());

        findViewById(R.id.btn_diag).setOnClickListener(v ->
                startActivity(new Intent(this, DiagActivity.class)));

        findViewById(R.id.btn_keys).setOnClickListener(v ->
                startActivity(new Intent(this, ApiKeysActivity.class)));

        findViewById(R.id.btn_balance).setOnClickListener(v -> fetchBalance());

        findViewById(R.id.btn_battery).setOnClickListener(v -> requestIgnoreBattery());

        findViewById(R.id.btn_settings).setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));

        requestNotificationPermission();
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.removeCallbacks(ticker);
        handler.post(ticker);
        syncFloatBall();
    }

    /** 按设置开关同步悬浮球显示（需悬浮窗权限才启动；长按隐藏后不再自动拉起）。 */
    private void syncFloatBall() {
        boolean want = store.isFloatBall() && Settings.canDrawOverlays(this)
                && !store.isFloatBallHidden();
        if (want) {
            try {
                startService(new Intent(this, FloatingBallService.class));
            } catch (Throwable ignored) {
            }
        } else {
            // 开关未开或无权限：确保服务停止（服务内部也有防御，这里双保险）
            try {
                stopService(new Intent(this, FloatingBallService.class));
            } catch (Throwable ignored) {
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(ticker);
    }

    private String baseUrl() {
        int port = GatewayService.isRunning() ? GatewayService.currentPort() : store.getPort();
        if (store.isLanEnabled()) {
            String ip = lanIp();
            if (ip != null) {
                return "http://" + ip + ":" + port + "/v1";
            }
        }
        return "http://127.0.0.1:" + port + "/v1";
    }

    private void refresh() {
        boolean running = GatewayService.isRunning();
        btnToggle.setText(running ? R.string.btn_close_gateway : R.string.btn_open_gateway);

        int port = running ? GatewayService.currentPort() : store.getPort();
        chipPort.setText("端口 " + port);

        tvState.setText(running ? R.string.status_running : R.string.status_stopped);
        dotState.setBackgroundTintList(ColorStateList.valueOf(
                getColor(running ? R.color.ok_green : R.color.err_red)));

        String cookie = store.getCookie();
        String loginInfo = cookie.isEmpty()
                ? "未登录（点「登录 / 更新会话」）"
                : "已登录 · 会话 …" + cookie.substring(Math.max(0, cookie.length() - 6));

        StringBuilder sb = new StringBuilder();
        sb.append("DevCloud：").append(loginInfo).append('\n');
        sb.append("监听：").append(store.isLanEnabled() ? "局域网开放（0.0.0.0）" : "仅本机");
        if (GatewayService.server() != null) {
            sb.append('\n').append("请求 ").append(GatewayService.server().getRequestCount())
                    .append(" · DevCloud 会话 ")
                    .append(GatewayService.server().getDevCloudSessionCount())
                    .append(" · 网关密钥 ").append(store.getApiKeys().isEmpty()
                    ? "未配置" : store.getApiKeys().size() + " 个");
        }
        tvDetail.setText(sb.toString());

        String base = baseUrl();
        tvBase.setText(base);
        tvEndpoints.setText("GET  " + base.replace("/v1", "/v1/models")
                + "\nPOST " + base.replace("/v1", "/v1/chat/completions"));
    }

    /** 端口自定义弹窗；保存后若网关在跑则自动用新端口重启。 */
    private void promptPort() {
        final com.google.android.material.textfield.TextInputEditText et =
                new com.google.android.material.textfield.TextInputEditText(this);
        et.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        et.setSingleLine(true);
        et.setText(String.valueOf(store.getPort()));
        et.setSelection(et.getText().length());
        android.widget.LinearLayout box = new android.widget.LinearLayout(this);
        box.setOrientation(android.widget.LinearLayout.VERTICAL);
        android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        lp.setMargins(pad, pad / 2, pad, 0);
        box.addView(et, lp);

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("自定义端口（1024–65535）")
                .setView(box)
                .setPositiveButton("保存并应用", (d, w) -> {
                    String v = et.getText().toString().trim();
                    int port;
                    try {
                        port = Integer.parseInt(v);
                    } catch (Exception e) {
                        Toast.makeText(this, "端口必须是数字", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (port < 1024 || port > 65535) {
                        Toast.makeText(this, "端口需在 1024–65535 之间", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    boolean wasRunning = GatewayService.isRunning();
                    store.setPort(port);
                    Toast.makeText(this, "端口已设为 " + port, Toast.LENGTH_SHORT).show();
                    if (wasRunning) {
                        // 用新端口重启
                        GatewayService.ensureStopped(this);
                        handler.postDelayed(() -> {
                            GatewayService.ensureStarted(this);
                            handler.postDelayed(this::refresh, 600);
                        }, 500);
                    } else {
                        refresh();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /** 一键签到。 */
    private void doCheckin() {
        if (store.getCookie().isEmpty()) {
            Toast.makeText(this, R.string.checkin_need_login, Toast.LENGTH_SHORT).show();
            return;
        }
        findViewById(R.id.btn_checkin).setEnabled(false);
        tvWelfare.setText(R.string.checkin_doing);
        Actions.checkin(this, r -> {
            findViewById(R.id.btn_checkin).setEnabled(true);
            StringBuilder sb = new StringBuilder();
            sb.append("签到结果：").append(r.message == null ? "完成" : r.message);
            if (r.claimedAmount > 0) {
                sb.append("（+").append(r.claimedAmount).append(" 积分）");
            }
            sb.append('\n');
            for (DevCloudClient.Welfare w : r.items) {
                sb.append("· ").append(w.title.isEmpty() ? w.type : w.title)
                        .append("  [").append(w.typeNorm).append('/')
                        .append(w.rawStatus.isEmpty() ? "?" : w.rawStatus).append(']');
                if (w.pendingAmount > 0) {
                    sb.append("  +").append(w.pendingAmount);
                }
                sb.append('\n');
            }
            tvWelfare.setText(sb.toString());
            Toast.makeText(this, r.message == null ? "完成" : r.message, Toast.LENGTH_LONG).show();
        });
    }

    /**
     * 查询额度：积分用量（官网「用量统计」同源 snap-manager）+ 免费 token 额度（benefit/balance）。
     * 1000 万那行是「免费额度」，账号实际计费用的是积分额度，两行都显示避免误解。
     */
    private void fetchBalance() {
        TextView tv = findViewById(R.id.tv_balance);
        if (store.getCookie().isEmpty()) {
            Toast.makeText(this, "请先登录 DevCloud", Toast.LENGTH_SHORT).show();
            return;
        }
        tv.setText("正在查询额度…");
        new Thread(() -> {
            final DevCloudClient.Balance b = DevCloudClient.fetchBalance(store.getCookie());
            final DevCloudClient.PackageStats p =
                    DevCloudClient.fetchPackageStats(store.getCookie());
            // 原始报文落盘，便于外部核对字段（/sdcard/Download/codearts-package.json）
            DiagDump.save(getApplicationContext(), "codearts-package.json",
                    "== 额度探针（点「查询额度」时写入）\n"
                            + "== GET /snap-manager/v1/statistics/plugin → ok=" + p.ok
                            + " package=" + p.packageName + " specCode=" + p.specCode
                            + " isCreditPackage=" + p.isCreditPackage
                            + "\n== credit used/total/remain = " + p.creditUsed + " / " + p.creditTotal
                            + " / " + p.creditRemain
                            + "\n== token used/total/unlimited = " + p.tokenUsed + " / " + p.tokenTotal
                            + " / " + p.unlimited
                            + "\n== balance(免费额度) used/total = " + b.usedAmount + " / " + b.totalQuota
                            + "\n== message: " + p.message + " | " + b.message + "\n");
            runOnUiThread(() -> {
                StringBuilder sb = new StringBuilder();
                // ① 积分额度（账号实际计费用量，官网「用量管理→积分用量」）
                if (p.ok && (p.creditUsed != null || p.creditTotal != null)) {
                    sb.append("积分：剩余 ").append(DevCloudClient.fmtNum(p.creditRemain));
                    if (p.creditTotal != null) {
                        sb.append(" / ").append(DevCloudClient.fmtNum(p.creditTotal));
                    }
                    Double cp = p.creditPercent();
                    if (cp != null) {
                        sb.append("（已用 ").append(String.format(java.util.Locale.ROOT, "%.1f", cp))
                                .append("%）");
                    }
                    sb.append('\n');
                } else if (!p.ok && !p.message.isEmpty()) {
                    sb.append("积分额度查询失败：").append(p.message).append('\n');
                }
                // ② 免费 token 额度（benefit/tokens/balance）
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
                } else {
                    sb.append("免费额度查询失败：").append(b.message);
                }
                if (p.ok && p.tokenTotal != null) {
                    sb.append("\n套餐 token：").append(DevCloudClient.fmtNum(p.tokenUsed))
                            .append(" / ").append(DevCloudClient.fmtNum(p.tokenTotal));
                } else if (p.unlimited) {
                    sb.append("\n套餐 token：∞ 无限制");
                }
                tv.setText(sb.toString());
            });
        }, "balance").start();
    }

    /** 申请加入电池优化白名单（保活前提，vivo 等尤其重要）。 */
    private void requestIgnoreBattery() {
        try {
            android.os.PowerManager pm = (android.os.PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null && pm.isIgnoringBatteryOptimizations(getPackageName())) {
                Toast.makeText(this, "已在此前加入白名单，无需重复操作", Toast.LENGTH_LONG).show();
                return;
            }
            // 缺 REQUEST_IGNORE_BATTERY_OPTIMIZATIONS 权限时，直接跳系统弹窗会被静默拒绝（实测"点了没反应"）
            // 因此先补齐权限声明再跳；部分 ROM 拦截该 action 时逐级降级。
            Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            i.setData(Uri.parse("package:" + getPackageName()));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
            LogRing.log("battery", "已发起电池优化白名单申请弹窗");
        } catch (Throwable t) {
            LogRing.log("battery", "直接弹窗失败，降级设置页: " + t);
            try {
                startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            } catch (Throwable t2) {
                LogRing.log("battery", "电池设置页也失败，降级应用详情页");
                try {
                    startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:" + getPackageName())));
                    Toast.makeText(this, "请在应用信息 → 电池 中选择「允许后台高耗电」", Toast.LENGTH_LONG).show();
                } catch (Throwable t3) {
                    Toast.makeText(this, "请手动到 系统设置→电池 中允许后台运行", Toast.LENGTH_LONG).show();
                }
            }
        }
    }

    private String lanIp() {
        try {
            Enumeration<NetworkInterface> nis = NetworkInterface.getNetworkInterfaces();
            for (NetworkInterface ni : Collections.list(nis)) {
                if (!ni.isUp() || ni.isLoopback()) {
                    continue;
                }
                for (java.net.InetAddress addr : Collections.list(ni.getInetAddresses())) {
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }
}
