package com.codearts.gateway.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.codearts.gateway.R;
import com.codearts.gateway.core.Store;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;

import java.util.List;

/** 网关 API Key 管理：生成随机密钥、手动添加、删除、复制。 */
public class ApiKeysActivity extends AppCompatActivity {

    private LinearLayout listBox;
    private TextInputEditText etNew;
    private Store store;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_apikeys);
        store = Store.get(this);

        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        listBox = findViewById(R.id.box_keys);
        etNew = findViewById(R.id.et_new);

        findViewById(R.id.btn_add).setOnClickListener(v -> {
            String k = etNew.getText() == null ? "" : etNew.getText().toString().trim();
            if (k.isEmpty()) {
                k = Store.newApiKey();
            }
            store.addApiKey(k);
            etNew.setText("");
            render();
            Toast.makeText(this, "已添加密钥", Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
    }

    private void render() {
        listBox.removeAllViews();
        List<String> keys = store.getApiKeys();
        if (keys.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("暂无密钥 —— 客户端可不填 API Key（不校验）。\n添加后客户端必须携带 Authorization: Bearer <密钥>。");
            empty.setTextSize(12.5f);
            empty.setPadding(0, dp(6), 0, dp(6));
            listBox.addView(empty);
            return;
        }
        float d = getResources().getDisplayMetrics().density;
        for (String k : keys) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(6), 0, dp(6));

            TextView tv = new TextView(this);
            tv.setText(k);
            tv.setTextIsSelectable(true);
            tv.setTextSize(13f);
            tv.setHorizontallyScrolling(false);
            tv.setSingleLine(false);
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            tlp.rightMargin = dp(8);
            row.addView(tv, tlp);

            MaterialButton copy = new MaterialButton(this);
            copy.setText("复制");
            copy.setTextSize(11);
            copy.setMinWidth(0);
            copy.setMinimumWidth(0);
            copy.setInsetTop(0);
            copy.setInsetBottom(0);
            copy.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
            copy.setOnClickListener(v -> {
                ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null) {
                    cm.setPrimaryClip(ClipData.newPlainText("key", k));
                }
                Toast.makeText(this, "已复制", Toast.LENGTH_SHORT).show();
            });
            row.addView(copy);

            MaterialButton del = new MaterialButton(this);
            del.setText("删除");
            del.setTextSize(11);
            del.setMinWidth(0);
            del.setMinimumWidth(0);
            del.setInsetTop(0);
            del.setInsetBottom(0);
            del.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
            del.setOnClickListener(v -> new MaterialAlertDialogBuilder(this)
                    .setTitle("删除密钥")
                    .setMessage(k)
                    .setPositiveButton("删除", (dg, w) -> {
                        store.removeApiKey(k);
                        render();
                    })
                    .setNegativeButton("取消", null)
                    .show());
            row.addView(del);

            LinearLayout.LayoutParams dlp = (LinearLayout.LayoutParams) del.getLayoutParams();
            dlp.leftMargin = dp(8);
            del.setLayoutParams(dlp);

            listBox.addView(row);
        }
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }
}
