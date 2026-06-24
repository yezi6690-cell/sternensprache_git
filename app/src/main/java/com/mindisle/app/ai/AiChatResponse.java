package com.mindisle.app.ai;

import androidx.annotation.NonNull;

import java.util.Collections;
import java.util.List;

public final class AiChatResponse {
    private final String reply;
    private final String role;
    private final String emotion;
    private final String expression;
    private final String motion;
    private final List<AiAction> actions;
    private final boolean needConfirm;

    public AiChatResponse(
            @NonNull String reply,
            @NonNull String role,
            @NonNull String emotion,
            @NonNull String expression,
            @NonNull String motion,
            @NonNull List<AiAction> actions,
            boolean needConfirm
    ) {
        this.reply = reply;
        this.role = role;
        this.emotion = emotion;
        this.expression = expression;
        this.motion = motion;
        this.actions = Collections.unmodifiableList(actions);
        this.needConfirm = needConfirm;
    }

    @NonNull
    public String getReply() {
        return reply;
    }

    @NonNull
    public String getRole() {
        return role;
    }

    @NonNull
    public String getEmotion() {
        return emotion;
    }

    @NonNull
    public String getExpression() {
        return expression;
    }

    @NonNull
    public String getMotion() {
        return motion;
    }

    @NonNull
    public List<AiAction> getActions() {
        return actions;
    }

    public boolean isNeedConfirm() {
        return needConfirm;
    }
}
