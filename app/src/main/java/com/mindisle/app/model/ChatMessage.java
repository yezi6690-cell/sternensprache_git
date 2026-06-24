package com.mindisle.app.model;

import androidx.annotation.Nullable;

public class ChatMessage {
    public static final int ROLE_USER = 1;
    public static final int ROLE_ASSISTANT = 2;

    public static final int TYPE_TEXT = 0;
    public static final int TYPE_IMAGE = 1;
    public static final int TYPE_IMAGE_WITH_TEXT = 2;

    private final int type;
    @Nullable private final String text;
    private final int role;
    private final long timestamp;
    @Nullable private final String imageUri;

    /** Text-only message. */
    public ChatMessage(String text, int role) {
        this.type = TYPE_TEXT;
        this.text = text;
        this.role = role;
        this.imageUri = null;
        this.timestamp = System.currentTimeMillis();
    }

    /** Image-only message. */
    public static ChatMessage image(String imageUri, int role) {
        return new ChatMessage(imageUri, null, role, TYPE_IMAGE);
    }

    /** Image + text combined message. */
    public static ChatMessage imageWithText(String imageUri, String text, int role) {
        return new ChatMessage(imageUri, text, role, TYPE_IMAGE_WITH_TEXT);
    }

    private ChatMessage(String imageUri, String text, int role, int type) {
        this.type = type;
        this.role = role;
        this.timestamp = System.currentTimeMillis();
        this.imageUri = imageUri;
        this.text = text;
    }

    public int getType() { return type; }

    @Nullable public String getText() { return text; }

    public int getRole() { return role; }

    public long getTimestamp() { return timestamp; }

    @Nullable public String getImageUri() { return imageUri; }

    public boolean isUser() { return role == ROLE_USER; }
}
