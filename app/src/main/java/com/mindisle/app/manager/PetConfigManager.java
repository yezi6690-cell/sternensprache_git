package com.mindisle.app.manager;

import android.content.Context;
import android.content.SharedPreferences;

import com.mindisle.app.model.PetConfig;

public class PetConfigManager {
    private static final String PREF_NAME = "mindisle_pet_config";
    private static final String KEY_SELECTED_PET_ID = "selected_pet_id";
    private static final String KEY_PET_NAME = "pet_name";
    private static final String KEY_COMPANION_STYLE = "companion_style";
    private static final String KEY_MODEL_PATH = "model_path";
    private static final String KEY_VOICE_ENABLED = "voice_enabled";

    private PetConfigManager() {
    }

    public static PetConfig load(Context context) {
        SharedPreferences preferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        PetConfig defaultConfig = new PetConfig();
        return new PetConfig(
                preferences.getString(KEY_SELECTED_PET_ID, defaultConfig.getSelectedPetId()),
                preferences.getString(KEY_PET_NAME, defaultConfig.getPetName()),
                preferences.getString(KEY_COMPANION_STYLE, defaultConfig.getCompanionStyle()),
                preferences.getString(KEY_MODEL_PATH, defaultConfig.getModelPath()),
                preferences.getBoolean(KEY_VOICE_ENABLED, defaultConfig.isVoiceEnabled())
        );
    }

    public static void save(Context context, PetConfig config) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_SELECTED_PET_ID, config.getSelectedPetId())
                .putString(KEY_PET_NAME, config.getPetName())
                .putString(KEY_COMPANION_STYLE, config.getCompanionStyle())
                .putString(KEY_MODEL_PATH, config.getModelPath())
                .putBoolean(KEY_VOICE_ENABLED, config.isVoiceEnabled())
                .apply();
    }

    public static void saveCompanionStyle(Context context, String companionStyle) {
        PetConfig config = load(context);
        config.setCompanionStyle(companionStyle);
        save(context, config);
    }
}
