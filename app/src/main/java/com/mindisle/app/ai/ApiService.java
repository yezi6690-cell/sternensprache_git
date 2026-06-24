package com.mindisle.app.ai;

import androidx.annotation.NonNull;

import java.util.List;

public interface ApiService {
    void sendMessage(
            @NonNull String message,
            @NonNull String roleId,
            @NonNull String modelId,
            @NonNull List<ChatHistoryItem> history,
            @NonNull Callback callback
    );

    void cancelAll();

    interface Callback {
        void onSuccess(@NonNull AiChatResponse response);

        void onFailure();
    }
}
