package com.codearts.gateway.ui;

import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.codearts.gateway.R;
import com.codearts.gateway.core.Diagnostics;
import com.codearts.gateway.core.LogRing;
import com.codearts.gateway.core.Store;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 诊断页：一键体检（本地 Cookie + 上游三接口）+ 详细日志查看器。
 * 检测项逐条展示（绿/红点 + 说明），日志可复制、可清空。
 */
public class DiagActivity extends AppCompatActivity {

    private LinearLayout listBox;
    private TextView tvVerdict;
    private TextView tvLog;
    private MaterialButton btnDiag;
    private MaterialButton btnCopy;
    private MaterialButton btnClear;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_diag);
        Store store = Store.get(this);

        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        listBox = findViewById(R.id.diag_list);
        tvVerdict = findViewById(R.id.tv_verdict);
        tvLog = findViewById(R.id.tv_log);
        btnDiag = findViewById(R.id.btn_diag);
        btnCopy = findViewById(R.id.btn_copy);
        btnClear = findViewById(R.id.btn_clear);

        btnDiag.setOnClickListener(v -> runDiag());
        btnCopy.setOnClickListener(v -> {
            android.content.ClipboardManager cm = (android.content.ClipboardManager)
                    getSystemService(android.content.Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(android.content.ClipData.newPlainText("diag",
                        tvVerdict.getText() + "\n\n" + tvLog.getText()));
                Toast.makeText(this, "诊断+日志已复制", Toast.LENGTH_SHORT).show();
            }
        });
        btnClear.setOnClickListener(v -> {
            LogRing.clear();
            renderLog();
            Toast.makeText(this, "日志已清空", Toast.LENGTH_SHORT).show();
        });

        renderLog();

        if (getIntent().getBooleanExtra("auto_run", false)) {
            btnDiag.postDelayed(this::runDiag, 300);
        }
    }

    private void runDiag() {
        btnDiag.setEnabled(false);
        tvVerdict.setText("正在检测（本机 Cookie → 上游模型 → 上游签到 → 对话链路）…");
        listBox.removeAllViews();
        new Thread(() -> {
            final Diagnostics.Report r = Diagnostics.diagnose(Store.get(this), this);
            runOnUiThread(() -> {
                btnDiag.setEnabled(true);
                tvVerdict.setText(r.verdict);
                renderItems(r.items);
                renderLog();
            });
        }, "diag").start();
    }

    private void renderItems(List<Diagnostics.Item> items) {
        listBox.removeAllViews();
        for (Diagnostics.Item i : items) {
            MaterialCardView card = new MaterialCardView(this);
            card.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
            float d = getResources().getDisplayMetrics().density;
            LinearLayout.LayoutParams clp = (LinearLayout.LayoutParams) card.getLayoutParams();
            clp.setMargins(0, (int) (6 * d), 0, 0);
            card.setLayoutParams(clp);
            card.setRadius(14 * d);
            card.setContentPadding((int) (14 * d), (int) (12 * d),
                    (int) (14 * d), (int) (12 * d));

            LinearLayout box = new LinearLayout(this);
            box.setOrientation(LinearLayout.VERTICAL);

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);

            android.view.View dot = new android.view.View(this);
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                    (int) (10 * d), (int) (10 * d));
            dlp.rightMargin = (int) (8 * d);
            dot.setLayoutParams(dlp);
            dot.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                    getColor(i.ok ? R.color.ok_green : R.color.err_red)));
            dot.setBackgroundResource(R.drawable.bg_dot);
            row.addView(dot);

            TextView title = new TextView(this);
            title.setText(i.name);
            title.setTextSize(14);
            title.setTypeface(null, android.graphics.Typeface.BOLD);
            row.addView(title);
            box.addView(row);

            TextView detail = new TextView(this);
            detail.setText(i.detail);
            detail.setTextSize(12.5f);
            detail.setLineSpacing(2 * d, 1f);
            detail.setTextColor(getColor(R.color.brand_on_container));
            detail.setPadding(0, (int) (6 * d), 0, 0);
            box.addView(detail);

            card.addView(box);
            listBox.addView(card);
        }
    }

    private void renderLog() {
        String dump = LogRing.dump();
        if (dump.trim().isEmpty()) {
            dump = "（暂无日志）";
        }
        String shown = dump.length() > 8000 ? dump.substring(dump.length() - 8000) : dump;
        tvLog.setText(shown);
    }
}
