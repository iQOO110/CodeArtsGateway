package com.codearts.gateway.ui;

import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.codearts.gateway.R;
import com.codearts.gateway.core.ArtifactIndex;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** 产物列表适配器：文件名、完整路径、大小与时间，并提供打开/另存/复制。 */
public class ArtifactAdapter extends RecyclerView.Adapter<ArtifactAdapter.Holder> {

    public interface Listener {
        void onOpen(ArtifactIndex.Item item);

        void onSaveAs(ArtifactIndex.Item item);

        void onCopy(ArtifactIndex.Item item);
    }

    private final Listener listener;
    private final List<ArtifactIndex.Item> items = new ArrayList<>();
    private final SimpleDateFormat timeFormat =
            new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT);

    public ArtifactAdapter(Listener listener) {
        this.listener = listener;
    }

    public void setItems(List<ArtifactIndex.Item> data) {
        items.clear();
        if (data != null) {
            items.addAll(data);
        }
        notifyDataSetChanged();
    }

    public int size() {
        return items.size();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_artifact, parent, false);
        return new Holder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        ArtifactIndex.Item it = items.get(position);
        h.tvName.setText(it.name == null || it.name.isEmpty() ? "未命名产物" : it.name);
        h.tvPath.setText(it.path);
        String size = it.size > 0 ? Formatter.formatShortFileSize(h.itemView.getContext(), it.size) : "大小未知";
        String time = it.time > 0 ? timeFormat.format(new Date(it.time)) : "时间未知";
        h.tvMeta.setText(size + " · " + time);
        h.itemView.setOnClickListener(v -> listener.onOpen(it));
        h.btnOpen.setOnClickListener(v -> listener.onOpen(it));
        h.btnSave.setOnClickListener(v -> listener.onSaveAs(it));
        h.btnCopy.setOnClickListener(v -> listener.onCopy(it));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final TextView tvName;
        final TextView tvPath;
        final TextView tvMeta;
        final View btnOpen;
        final View btnSave;
        final View btnCopy;

        Holder(View v) {
            super(v);
            tvName = v.findViewById(R.id.tv_name);
            tvPath = v.findViewById(R.id.tv_path);
            tvMeta = v.findViewById(R.id.tv_meta);
            btnOpen = v.findViewById(R.id.btn_open);
            btnSave = v.findViewById(R.id.btn_save);
            btnCopy = v.findViewById(R.id.btn_copy);
        }
    }
}