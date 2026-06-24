package com.mindisle.app.ai;

import androidx.annotation.NonNull;

public final class AiActionResult {
    private final boolean success;
    private final String message;
    private final String actionType;

    public AiActionResult(
            boolean success,
            @NonNull String message,
            @NonNull String actionType
    ) {
        this.success = success;
        this.message = message;
        this.actionType = actionType;
    }

    public boolean isSuccess() {
        return success;
    }

    @NonNull
    public String getMessage() {
        return message;
    }

    @NonNull
    public String getActionType() {
        return actionType;
    }

    @NonNull
    public static AiActionResult success(@NonNull String type, @NonNull String message) {
        return new AiActionResult(true, message, type);
    }

    @NonNull
    public static AiActionResult failure(@NonNull String type, @NonNull String message) {
        return new AiActionResult(false, message, type);
    }
}
