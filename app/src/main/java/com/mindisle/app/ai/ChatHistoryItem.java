package com.mindisle.app.ai;

import androidx.annotation.NonNull;

public final class ChatHistoryItem {
    private final String role;
    private final String content;

    public ChatHistoryItem(@NonNull String role, @NonNull String content) {
        this.role = role;
        this.content = content;
    }

    @NonNull
    public String getRole() {
        return role;
    }

    @NonNull
    public String getContent() {
        return content;
    }
}
