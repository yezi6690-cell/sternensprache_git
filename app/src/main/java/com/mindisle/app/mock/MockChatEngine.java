package com.mindisle.app.mock;

public class MockChatEngine {
    public static final String MODE_FRIEND = "知心朋友";
    public static final String MODE_SUPPORT = "专业支持";

    private MockChatEngine() {
    }

    public static String reply(String userText, String mode) {
        if (userText == null) {
            return "我听到了，你可以慢慢说。这里是一个不用逞强的地方。";
        }
        if (userText.contains("焦虑")) {
            return "听起来你最近有些焦虑，这种感觉确实会让人很累。我们可以先把最担心的一件事写下来，再慢慢拆开。";
        }
        if (userText.contains("难过") || userText.contains("低落")) {
            return "我能感觉到你现在不太好受。先不用急着振作，我会在这里陪你。你愿意说说今天发生了什么吗？";
        }
        if (userText.contains("压力")) {
            return "压力大的时候，大脑会把很多事情混在一起。我们可以先选一件最重要的小事，给自己一个可完成的开始。";
        }
        if (MODE_SUPPORT.equals(mode)) {
            return "我理解你正在整理自己的感受。我们可以先识别此刻最明显的情绪，再看看它和哪件具体事情有关，最后选一个今天能完成的小步骤。";
        }
        return "我听到了，你可以慢慢说。这里是一个不用逞强的地方。";
    }
}
