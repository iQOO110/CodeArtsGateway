package com.codearts.gateway.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;

import java.util.List;

import com.codearts.gateway.R;
import com.codearts.gateway.core.Actions;
import com.codearts.gateway.core.Store;

/**
 * App 内模型列表：直接向 Intern InkStone 上游拉取，支持下拉刷新、
 * 工具栏刷新、搜索过滤、点击复制模型 ID，并缓存上次结果以便秒开。
 */
public class ModelsActivity extends AppCompatActivity {

    private ModelAdapter adapter;
    private SwipeRefreshLayout swipe;
    private RecyclerView recycler;
    private View boxEmpty;
    private View progress;
    private TextView tvStatus;
    private TextView tvEmpty;
    private TextInputEditText etSearch;

    private boolean loading;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_models);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        tvStatus = findViewById(R.id.tv_status);
        tvEmpty = findViewById(R.id.tv_empty);
        boxEmpty = findViewById(R.id.box_empty);
        progress = findViewById(R.id.progress);
        swipe = findViewById(R.id.swipe);
        recycler = findViewById(R.id.recycler);
        etSearch = findViewById(R.id.et_search);

        adapter = new ModelAdapter(this::onPick);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.setAdapter(adapter);
        recycler.setHasFixedSize(false);

        swipe.setOnRefreshListener(this::refresh);
        swipe.setColorSchemeColors(getColor(R.color.brand_primary));

        findViewById(R.id.btn_empty_retry).setOnClickListener(v -> refresh());

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
                adapter.filter(s == null ? "" : s.toString());
                updateStatus(null);
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        // 先用缓存渲染，避免打开是空白
        List<Store.ModelInfo> cached = Store.get(this).cachedModels();
        if (!cached.isEmpty()) {
            adapter.submit(cached);
            showEmpty(false);
            tvStatus.setText("缓存 " + cached.size() + " 个模型，正在刷新…");
        }
        refresh();
    }

    @Override
    public boolean onCreateOptionsMenu(android.view.Menu menu) {
        getMenuInflater().inflate(R.menu.menu_models, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_refresh) {
            refresh();
            return true;
        }
        if (id == R.id.action_copy_all) {
            copyAllIds();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /** 拉取模型（可反复调用 = 刷新）。 */
    private void refresh() {
        if (loading) {
            swipe.setRefreshing(false);
            return;
        }
        loading = true;
        boolean hasData = adapter.total() > 0;
        showEmpty(false);
        if (hasData) {
            swipe.setRefreshing(true);
        } else {
            progress.setVisibility(View.VISIBLE);
            tvStatus.setText(R.string.models_loading);
        }

        Actions.fetchModels(this, new Actions.ModelCallback() {
            @Override
            public void onSuccess(List<Store.ModelInfo> models) {
                loading = false;
                progress.setVisibility(View.GONE);
                swipe.setRefreshing(false);
                adapter.submit(models);
                showEmpty(false);
                updateStatus("上游 HTTP 200");
            }

            @Override
            public void onError(String message) {
                loading = false;
                progress.setVisibility(View.GONE);
                swipe.setRefreshing(false);
                if (adapter.total() > 0) {
                    // 有旧数据就保留，只提示失败
                    updateStatus("刷新失败：" + message);
                    Toast.makeText(ModelsActivity.this, "刷新失败：" + message,
                            Toast.LENGTH_LONG).show();
                } else {
                    tvEmpty.setText(message);
                    showEmpty(true);
                    tvStatus.setText("拉取失败");
                }
            }
        });
    }

    private void showEmpty(boolean show) {
        boxEmpty.setVisibility(show ? View.VISIBLE : View.GONE);
        recycler.setVisibility(show ? View.GONE : View.VISIBLE);
    }

    private void updateStatus(String extra) {
        String base = "共 " + adapter.total() + " 个模型";
        if (adapter.getItemCount() != adapter.total()) {
            base += "，筛选出 " + adapter.getItemCount() + " 个";
        }
        if (extra != null && !extra.isEmpty()) {
            base += " · " + extra;
        }
        base += " · 点击条目复制 ID";
        tvStatus.setText(base);
    }

    private void onPick(Store.ModelInfo model, int index) {
        copyToClipboard(model.id);
        Toast.makeText(this, "已复制模型 ID：" + model.id
                + (model.name.equals(model.id) ? "" : "\n显示名：" + model.name),
                Toast.LENGTH_SHORT).show();
    }

    private void copyAllIds() {
        List<Store.ModelInfo> list = adapter.allModels();
        if (list.isEmpty()) {
            Toast.makeText(this, "还没有可复制的模型", Toast.LENGTH_SHORT).show();
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (Store.ModelInfo m : list) {
            sb.append(m.id).append('\n');
        }
        copyToClipboard(sb.toString().trim());
        Toast.makeText(this, "已复制 " + list.size() + " 个模型 ID", Toast.LENGTH_SHORT).show();
    }

    private void copyToClipboard(String text) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("inkstone", text));
        }
    }
}
