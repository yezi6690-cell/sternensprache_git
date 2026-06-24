package com.mindisle.app.activity;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.mindisle.app.R;
import com.mindisle.app.adapter.ArticleAdapter;
import com.mindisle.app.mock.MockArticleData;
import com.mindisle.app.model.ArticleItem;
import com.mindisle.app.utils.StatusBarUtils;

public class ArticleActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        StatusBarUtils.applyLightStatusBar(this);
        setContentView(R.layout.activity_article);
        setupToolbar();
        setupArticleList();
    }

    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void setupArticleList() {
        RecyclerView recyclerView = findViewById(R.id.article_recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(new ArticleAdapter(MockArticleData.list(), this::showArticleDialog));
    }

    private void showArticleDialog(ArticleItem item) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(item.getTitle())
                .setMessage(item.getContent())
                .setPositiveButton("我知道了", null)
                .show();
    }
}
