package com.mindisle.app.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.mindisle.app.R;
import com.mindisle.app.model.ArticleItem;

import java.util.ArrayList;
import java.util.List;

public class ArticleAdapter extends RecyclerView.Adapter<ArticleAdapter.ArticleViewHolder> {
    public interface OnArticleClickListener {
        void onArticleClick(ArticleItem item);
    }

    private final List<ArticleItem> items = new ArrayList<>();
    private final OnArticleClickListener listener;

    public ArticleAdapter(List<ArticleItem> items, OnArticleClickListener listener) {
        this.items.addAll(items);
        this.listener = listener;
    }

    @NonNull
    @Override
    public ArticleViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_article, parent, false);
        return new ArticleViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ArticleViewHolder holder, int position) {
        ArticleItem item = items.get(position);
        holder.titleText.setText(item.getTitle());
        holder.summaryText.setText(item.getSummary());
        holder.itemView.setOnClickListener(v -> listener.onArticleClick(item));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ArticleViewHolder extends RecyclerView.ViewHolder {
        private final TextView titleText;
        private final TextView summaryText;

        ArticleViewHolder(@NonNull View itemView) {
            super(itemView);
            titleText = itemView.findViewById(R.id.article_title_text);
            summaryText = itemView.findViewById(R.id.article_summary_text);
        }
    }
}
