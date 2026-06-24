package com.mindisle.app.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.mindisle.app.R;
import com.mindisle.app.model.PracticeItem;

import java.util.ArrayList;
import java.util.List;

public class PracticeAdapter extends RecyclerView.Adapter<PracticeAdapter.PracticeViewHolder> {
    public interface OnPracticeClickListener {
        void onPracticeClick(PracticeItem item);
    }

    private final List<PracticeItem> items = new ArrayList<>();
    private final OnPracticeClickListener listener;

    public PracticeAdapter(List<PracticeItem> items, OnPracticeClickListener listener) {
        this.items.addAll(items);
        this.listener = listener;
    }

    @NonNull
    @Override
    public PracticeViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_practice, parent, false);
        return new PracticeViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PracticeViewHolder holder, int position) {
        PracticeItem item = items.get(position);
        holder.titleText.setText(item.getTitle());
        holder.subtitleText.setText(item.getSubtitle());
        holder.itemView.setOnClickListener(v -> listener.onPracticeClick(item));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class PracticeViewHolder extends RecyclerView.ViewHolder {
        private final TextView titleText;
        private final TextView subtitleText;

        PracticeViewHolder(@NonNull View itemView) {
            super(itemView);
            titleText = itemView.findViewById(R.id.practice_title_text);
            subtitleText = itemView.findViewById(R.id.practice_subtitle_text);
        }
    }
}
