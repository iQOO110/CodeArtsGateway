package com.codearts.gateway.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.codearts.gateway.R;
import com.codearts.gateway.core.ArtifactIndex;
import com.codearts.gateway.core.DevCloudClient;
import com.codearts.gateway.core.LogRing;
import com.codearts.gateway.core.Store;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;

/** 新版 Web 控制台：加载 mobile/ 前端，所有能力经 WebBridge 调原生。 */
public class WebConsoleActivity extends AppCompatActivity {

    private static final int REQ_SAVE_AS = 6101;

    private WebView webView;
    private ArtifactIndex.Item pendingSave;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_web_console);
        LogRing.attach(this);
        DevCloudClient.setCookieSink(cookies ->
                Store.get(getApplicationContext()).mergeSetCookies(cookies, true));

        webView = findViewById(R.id.webview);
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(false);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);

        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient());
        webView.addJavascriptInterface(new WebBridge(this, webView), "CodeArtsNative");
        webView.loadUrl("file:///android_asset/mobile/index.html?native=1");

        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) {
            webView.post(() -> webView.evaluateJavascript(
                    "window.__nativeRefresh && window.__nativeRefresh()", null));
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
            return;
        }
        super.onBackPressed();
    }

    public void startSaveArtifact(String path) {
        ArtifactIndex.Item item = null;
        for (ArtifactIndex.Item it : ArtifactIndex.list(this)) {
            if (it.path.equals(path)) {
                item = it;
                break;
            }
        }
        if (item == null) {
            Toast.makeText(this, "产物不存在或已被移动", Toast.LENGTH_LONG).show();
            return;
        }
        pendingSave = item;
        String mime = item.mime == null || item.mime.isEmpty()
                ? "application/octet-stream" : item.mime;
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType(mime)
                .putExtra(Intent.EXTRA_TITLE, item.name);
        try {
            startActivityForResult(i, REQ_SAVE_AS);
        } catch (Throwable t) {
            Toast.makeText(this, "没有可用的文件选择器", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_SAVE_AS || resultCode != RESULT_OK
                || data == null || data.getData() == null || pendingSave == null) {
            return;
        }
        ArtifactIndex.Item item = pendingSave;
        pendingSave = null;
        try (InputStream in = openInput(item);
             OutputStream out = getContentResolver().openOutputStream(data.getData())) {
            if (in == null || out == null) {
                throw new IllegalStateException("无法打开输入或输出流");
            }
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            out.flush();
            Toast.makeText(this, "已另存", Toast.LENGTH_SHORT).show();
        } catch (Throwable t) {
            Toast.makeText(this, "另存失败: " + t, Toast.LENGTH_LONG).show();
        }
    }

    private InputStream openInput(ArtifactIndex.Item item) throws Exception {
        if (item.uri != null && !item.uri.isEmpty()) {
            return getContentResolver().openInputStream(Uri.parse(item.uri));
        }
        return new FileInputStream(new File(item.path));
    }
}