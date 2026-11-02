package com.codearts.gateway.ui;

import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.codearts.gateway.R;
import com.codearts.gateway.core.DevCloudClient;
import com.codearts.gateway.core.LogRing;
import com.codearts.gateway.core.Store;

/**
 * DevCloud 登录页：WebView 打开 chat/home，用户完成登录后点「保存会话」，
 * 从 CookieManager 抓取含 cftk 的会话 Cookie 存入 Store。
 */
public class LoginActivity extends AppCompatActivity {

    private Store store;
    private WebView webView;
    private TextView tvState;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        store = Store.get(this);

        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        tvState = findViewById(R.id.tv_state);
        webView = findViewById(R.id.webview);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.loadUrl(DevCloudClient.LOGIN_URL);
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                updateState("已加载：" + url);
            }
        });

        findViewById(R.id.btn_save).setOnClickListener(v -> save());
        findViewById(R.id.btn_copy).setOnClickListener(v -> {
            String c = currentCookie();
            if (c.isEmpty()) {
                Toast.makeText(this, "还没有可复制的 Cookie", Toast.LENGTH_SHORT).show();
                return;
            }
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(ClipData.newPlainText("devcloud-cookie", c));
                Toast.makeText(this, "Cookie 已复制", Toast.LENGTH_SHORT).show();
            }
        });

        updateState(store.getCookie().isEmpty()
                ? getString(R.string.login_hint)
                : "已有保存的会话（可重新登录更新）");
    }

    private String currentCookie() {
        // 先 flush（把 WebView 内存中的会话 Cookie 同步到 CookieManager 持久层）再读
        CookieManager.getInstance().flush();
        String c = CookieManager.getInstance().getCookie(DevCloudClient.ORIGIN);
        return c == null ? "" : c;
    }

    private void updateState(String s) {
        tvState.setText(s);
    }

    /** 校验并保存 Cookie。 */
    private void save() {
        // 登录跳转后 Cookie 可能刚写完，多 flush 一次
        CookieManager.getInstance().flush();
        String all = currentCookie();
        if (all.isEmpty()) {
            Toast.makeText(this, "未取到任何 Cookie，请先完成登录", Toast.LENGTH_LONG).show();
            return;
        }
        boolean hasSession = false;
        for (String pair : all.split(";")) {
            String name = pair.trim();
            int eq = name.indexOf('=');
            if (eq > 0) {
                name = name.substring(0, eq);
            }
            String low = name.toLowerCase();
            if (low.contains("cftk") || low.contains("session") || low.contains("hwwafses")) {
                hasSession = true;
                break;
            }
        }
        if (!hasSession) {
            Toast.makeText(this, "Cookie 中未见会话键（cftk），请确认已在页面里登录成功",
                    Toast.LENGTH_LONG).show();
            return;
        }
        store.setCookie(all);
        LogRing.log("login", "DevCloud Cookie 已保存，长度 " + all.length());
        Toast.makeText(this, R.string.login_saved, Toast.LENGTH_SHORT).show();
        setResult(RESULT_OK);
        finish();
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            ((ViewGroup) webView.getParent()).removeView(webView);
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
