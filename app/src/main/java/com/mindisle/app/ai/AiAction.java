package com.mindisle.app.ai;

import androidx.annotation.NonNull;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class AiAction {
    private final String type;
    private final Map<String, Object> params;
    private final boolean needConfirm;
    private final String displayText;

    public AiAction(
            @NonNull String type,
            @NonNull Map<String, Object> params,
            boolean needConfirm,
            @NonNull String displayText
    ) {
        this.type = type;
        this.params = Collections.unmodifiableMap(new LinkedHashMap<>(params));
        this.needConfirm = needConfirm;
        this.displayText = displayText;
    }

    @NonNull
    public String getType() {
        return type;
    }

    @NonNull
    public Map<String, Object> getParams() {
        return params;
    }

    public boolean isNeedConfirm() {
        return needConfirm;
    }

    @NonNull
    public String getDisplayText() {
        return displayText;
    }
}
