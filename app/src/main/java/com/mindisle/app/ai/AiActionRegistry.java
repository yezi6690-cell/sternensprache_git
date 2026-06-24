package com.mindisle.app.ai;

import androidx.annotation.NonNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class AiActionRegistry {
    private static final Set<String> ALLOWED_TYPES = immutableSet(
            "music.openPanel",
            "music.play",
            "music.pause",
            "music.togglePlay",
            "music.next",
            "music.previous",
            "music.openList",
            "music.setPlayMode",
            "music.togglePlayMode"
    );
    private static final Set<String> NO_PARAM_ACTIONS = immutableSet(
            "music.openPanel",
            "music.play",
            "music.pause",
            "music.togglePlay",
            "music.next",
            "music.previous",
            "music.openList",
            "music.togglePlayMode"
    );
    private static final Set<String> PLAY_MODES = immutableSet(
            "sequence", "shuffle", "single"
    );

    private AiActionRegistry() {
    }

    public static boolean isAllowed(@NonNull String type) {
        return ALLOWED_TYPES.contains(type);
    }

    public static boolean requiresConfirmation(@NonNull String type) {
        return false;
    }

    @NonNull
    public static Validation validate(@NonNull AiAction action) {
        String type = action.getType();
        if (!isAllowed(type)) {
            return Validation.rejected("unknown action");
        }

        Map<String, Object> params = action.getParams();
        if (NO_PARAM_ACTIONS.contains(type)) {
            return params.isEmpty()
                    ? Validation.allowed()
                    : Validation.rejected("unexpected params");
        }
        if ("music.setPlayMode".equals(type)) {
            return validateStringParam(params, "mode", PLAY_MODES);
        }
        return Validation.rejected("unsupported action schema");
    }

    @NonNull
    private static Validation validateStringParam(
            @NonNull Map<String, Object> params,
            @NonNull String key,
            @NonNull Set<String> allowedValues
    ) {
        if (params.size() != 1) {
            return Validation.rejected("invalid params");
        }
        Object value = params.get(key);
        if (!(value instanceof String) || !allowedValues.contains(value)) {
            return Validation.rejected("invalid " + key);
        }
        return Validation.allowed();
    }

    @NonNull
    private static Set<String> immutableSet(String... values) {
        return Collections.unmodifiableSet(new HashSet<>(Arrays.asList(values)));
    }

    public static final class Validation {
        private final boolean allowed;
        private final String reason;

        private Validation(boolean allowed, @NonNull String reason) {
            this.allowed = allowed;
            this.reason = reason;
        }

        @NonNull
        public static Validation allowed() {
            return new Validation(true, "");
        }

        @NonNull
        public static Validation rejected(@NonNull String reason) {
            return new Validation(false, reason);
        }

        public boolean isAllowed() {
            return allowed;
        }

        @NonNull
        public String getReason() {
            return reason;
        }
    }
}
