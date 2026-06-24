package com.mindisle.app.model;

public class MoodRecord {
    private final String mood;
    private final String content;
    private final long timestamp;

    public MoodRecord(String mood, String content, long timestamp) {
        this.mood = mood;
        this.content = content;
        this.timestamp = timestamp;
    }

    public String getMood() {
        return mood;
    }

    public String getContent() {
        return content;
    }

    public long getTimestamp() {
        return timestamp;
    }
}
