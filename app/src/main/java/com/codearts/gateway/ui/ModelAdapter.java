package com.codearts.gateway.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.codearts.gateway.R;
import com.codearts.gateway.core.Store;

/** 模型列表适配器：卡片 + 标签 Chip + 本地搜索过滤。 */
public class ModelAdapter extends RecyclerView.Adapter<ModelAdapter.VH> {

    public interface OnPick {
        void onPick(Store.ModelInfo model, int indexInShown);
    }

    private final List<Store.ModelInfo> all = new ArrayList<>();
    private final List<Store.ModelInfo> shown = new ArrayList<>();
    private final OnPick onPick;
    private String query = "";

    public ModelAdapter(OnPick onPick) {
        this.onPick = onPick;
    }

    public void submit(List<Store.ModelInfo> models) {
        all.clear();
        if (models != null) {
            all.addAll(models);
        }
        applyFilter();
    }

    public void filter(String q) {
        query = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        applyFilter();
    }

    public int total() {
        return all.size();
    }

    public List<Store.ModelInfo> allModels() {
        return new ArrayList<>(all);
    }

    private void applyFilter() {
        shown.clear();
        for (Store.ModelInfo m : all) {
            if (query.isEmpty()
                    || m.id.toLowerCase(Locale.ROOT).contains(query)
                    || m.name.toLowerCase(Locale.ROOT).contains(query)) {
                shown.add(m);
            }
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_model, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        final Store.ModelInfo m = shown.get(position);
        h.name.setText(m.name);
        h.id.setText(m.id);
        h.index.setText("#" + (position + 1));

        h.chips.removeAllViews();
        for (String tag : m.tags) {
            Chip c = new Chip(h.itemView.getContext());
            c.setText(tag);
            c.setCheckable(false);
            c.setClickable(false);
            c.setChipMinHeight(0f);
            h.chips.addView(c);
        }
        h.chips.setVisibility(m.tags.isEmpty() ? View.GONE : View.VISIBLE);

        h.itemView.setOnClickListener(v -> onPick.onPick(m, position));
    }

    @Override
    public int getItemCount() {
        return shown.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView name;
        final TextView id;
        final TextView index;
        final ChipGroup chips;

        VH(View v) {
            super(v);
            name = v.findViewById(R.id.tv_name);
            id = v.findViewById(R.id.tv_id);
            index = v.findViewById(R.id.tv_index);
            chips = v.findViewById(R.id.chips);
        }
    }
}
