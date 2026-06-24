package com.mindisle.app.model;

public class ArticleItem {
    private final String title;
    private final String summary;
    private final String content;

    public ArticleItem(String title, String summary, String content) {
        this.title = title;
        this.summary = summary;
        this.content = content;
    }

    public String getTitle() {
        return title;
    }

    public String getSummary() {
        return summary;
    }

    public String getContent() {
        return content;
    }
}
