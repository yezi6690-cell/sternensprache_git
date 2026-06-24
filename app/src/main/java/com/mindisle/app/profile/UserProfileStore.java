package com.mindisle.app.profile;

import android.content.Context;
import android.content.SharedPreferences;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class UserProfileStore {
    private static final String PREFS = "mindisle_user_profile";

    private UserProfileStore() {
    }

    public static UserProfile load(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        UserProfile profile = new UserProfile();
        profile.avatarUri = prefs.getString("avatar_uri", "");
        profile.nickname = prefs.getString("nickname", "叶子");
        profile.userId = prefs.getString("user_id", "user_10001");
        profile.status = prefs.getString("status", "今天也要好好生活");
        profile.gender = prefs.getString("gender", "未设置");
        profile.birthday = prefs.getString("birthday", "未设置");
        profile.hometown = prefs.getString("hometown", "未设置");
        profile.signature = prefs.getString("signature", "保持开心，慢慢来");
        profile.identity = prefs.getString("identity", "未设置");
        profile.schoolOrCompany = prefs.getString("school_or_company", "未设置");
        profile.majorOrDirection = prefs.getString("major_or_direction", "未设置");
        profile.groupOrOrganization = prefs.getString("group_or_organization", "未设置");
        profile.interests = prefs.getString("interests", "陪伴、音乐、生活");
        profile.registerTime = prefs.getString("register_time", createRegisterTime());
        profile.accountStatus = prefs.getString("account_status", "正常");

        if (!prefs.contains("register_time")) {
            prefs.edit().putString("register_time", profile.registerTime).apply();
        }
        return profile;
    }

    public static void save(Context context, UserProfile profile) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString("avatar_uri", safe(profile.avatarUri))
                .putString("nickname", safe(profile.nickname))
                .putString("user_id", safe(profile.userId))
                .putString("status", safe(profile.status))
                .putString("gender", safe(profile.gender))
                .putString("birthday", safe(profile.birthday))
                .putString("hometown", safe(profile.hometown))
                .putString("signature", safe(profile.signature))
                .putString("identity", safe(profile.identity))
                .putString("school_or_company", safe(profile.schoolOrCompany))
                .putString("major_or_direction", safe(profile.majorOrDirection))
                .putString("group_or_organization", safe(profile.groupOrOrganization))
                .putString("interests", safe(profile.interests))
                .putString("register_time", safe(profile.registerTime))
                .putString("account_status", safe(profile.accountStatus))
                .apply();
    }

    private static String createRegisterTime() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(new Date());
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
