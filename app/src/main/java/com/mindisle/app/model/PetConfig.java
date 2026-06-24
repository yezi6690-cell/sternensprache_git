package com.mindisle.app.model;

public class PetConfig {
    private String selectedPetId;
    private String petName;
    private String companionStyle;
    private String modelPath;
    private boolean voiceEnabled;

    public PetConfig() {
        selectedPetId = "cloud";
        petName = "小云朵";
        companionStyle = "知心朋友";
        modelPath = "";
        voiceEnabled = true;
    }

    public PetConfig(String selectedPetId, String petName, String companionStyle, String modelPath, boolean voiceEnabled) {
        this.selectedPetId = selectedPetId;
        this.petName = petName;
        this.companionStyle = companionStyle;
        this.modelPath = modelPath;
        this.voiceEnabled = voiceEnabled;
    }

    public String getSelectedPetId() {
        return selectedPetId;
    }

    public void setSelectedPetId(String selectedPetId) {
        this.selectedPetId = selectedPetId;
    }

    public String getPetName() {
        return petName;
    }

    public void setPetName(String petName) {
        this.petName = petName;
    }

    public String getCompanionStyle() {
        return companionStyle;
    }

    public void setCompanionStyle(String companionStyle) {
        this.companionStyle = companionStyle;
    }

    public String getModelPath() {
        return modelPath;
    }

    public void setModelPath(String modelPath) {
        this.modelPath = modelPath;
    }

    public boolean isVoiceEnabled() {
        return voiceEnabled;
    }

    public void setVoiceEnabled(boolean voiceEnabled) {
        this.voiceEnabled = voiceEnabled;
    }
}
