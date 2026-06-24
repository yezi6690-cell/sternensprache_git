package com.mindisle.app.manager;

public class CrisisKeywordManager {
    private static final String[] CRISIS_KEYWORDS = {
            "不想活了",
            "想死",
            "自杀",
            "伤害自己",
            "撑不下去",
            "想消失"
    };

    public static boolean containsCrisisKeyword(String text) {
        if (text == null) return false;
        for (String keyword : CRISIS_KEYWORDS) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
