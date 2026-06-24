package com.mindisle.app.model;

public class PracticeItem {
    private final String title;
    private final String subtitle;
    private final String detail;

    public PracticeItem(String title, String subtitle, String detail) {
        this.title = title;
        this.subtitle = subtitle;
        this.detail = detail;
    }

    public String getTitle() {
        return title;
    }

    public String getSubtitle() {
        return subtitle;
    }

    public String getDetail() {
        return detail;
    }
}
