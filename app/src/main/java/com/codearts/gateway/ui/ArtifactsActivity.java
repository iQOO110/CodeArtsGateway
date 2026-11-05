package com.codearts.gateway.ui;

import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.codearts.gateway.R;
import com.codearts.gateway.core.ArtifactIndex;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

/**
 * 码道产物页：展示本地已落盘的产物，支持打开、另存和复制完整存储路径。
 */
public class ArtifactsActivity extends AppCompatActivity {

    private static final int REQ_SAVE_AS = 4101;

    private ArtifactAdapter adapter;
    private SwipeRefreshLayout swipe;
    private View boxEmpty;
    private TextView tvEmpty;
    private ArtifactIndex.Item pendingSave;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_artifacts);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        swipe = findViewById(R.id.swipe);
        boxEmpty = findViewById(R.id.box_empty);
        tvEmpty = findViewById(R.id.tv_empty);
        RecyclerView recycler = findViewById(R.id.recycler);

        adapter = new ArtifactAdapter(new ArtifactAdapter.Listener() {
            @Override
            public void onOpen(ArtifactIndex.Item item) {
                open(item);
            }

            @Override
            public void onSaveAs(ArtifactIndex.Item item) {
                saveAs(item);
            }

            @Override
            public void onCopy(ArtifactIndex.Item item) {
                copyPath(item);
            }
        });
        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.setAdapter(adapter);

        swipe.setColorSchemeColors(getColor(R.color.brand_primary));
        swipe.setOnRefreshListener(this::reload);
        findViewById(R.id.btn_empty_retry).setOnClickListener(v -> reload());

        reload();
    }

    private void reload() {
        List<ArtifactIndex.Item> items = ArtifactIndex.list(this);
        adapter.setItems(items);
        swipe.setRefreshing(false);
        boolean empty = items.isEmpty();
        boxEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        if (empty) {
            tvEmpty.setText(getString(R.string.artifacts_empty));
        }
    }

    private void copyPath(ArtifactIndex.Item item) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("artifact_path", item.path));
            Toast.makeText(this, R.string.artifacts_copied, Toast.LENGTH_SHORT).show();
        }
    }

    private void open(ArtifactIndex.Item item) {
        Uri uri = contentUri(item);
        if (uri == null) {
            Toast.makeText(this, R.string.artifacts_missing, Toast.LENGTH_LONG).show();
            return;
        }
        String mime = item.mime == null || item.mime.isEmpty()
                ? "application/octet-stream" : item.mime;
        Intent i = new Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, mime)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivity(i);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.artifacts_no_app, Toast.LENGTH_LONG).show();
        }
    }

    private void saveAs(ArtifactIndex.Item item) {
        pendingSave = item;
        String mime = item.mime == null || item.mime.isEmpty()
                ? "application/octet-stream" : item.mime;
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType(mime)
                .putExtra(Intent.EXTRA_TITLE, item.name);
        try {
            startActivityForResult(i, REQ_SAVE_AS);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.artifacts_no_app, Toast.LENGTH_LONG).show();
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
            Toast.makeText(this, R.string.artifacts_saved, Toast.LENGTH_SHORT).show();
        } catch (Throwable t) {
            Toast.makeText(this, getString(R.string.artifacts_save_failed) + ": " + t,
                    Toast.LENGTH_LONG).show();
        }
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
            return FileProvider.getUriForFile(this,
                    getPackageName() + ".fileprovider", f);
        } catch (Throwable t) {
            return null;
        }
    }

    private InputStream openInput(ArtifactIndex.Item item) throws Exception {
        if (item.uri != null && !item.uri.isEmpty()) {
            return getContentResolver().openInputStream(Uri.parse(item.uri));
        }
        return new FileInputStream(new File(item.path));
    }
}