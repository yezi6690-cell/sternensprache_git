package com.mindisle.app.activity;

import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.mindisle.app.R;
import com.mindisle.app.manager.Live2DController;
import com.mindisle.app.manager.PetConfigManager;
import com.mindisle.app.model.PetConfig;
import com.mindisle.app.utils.StatusBarUtils;
import com.mindisle.app.utils.ToastUtils;

public class PetCustomizeActivity extends AppCompatActivity {
    private final Live2DController live2DController = new Live2DController();
    private ImageView previewImageView;
    private TextView petNameTextView;
    private MaterialButtonToggleGroup styleGroup;
    private String selectedPetId = "cloud";
    private String selectedPetName = "小云朵";
    private String selectedStyle = "知心朋友";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        StatusBarUtils.applyLightStatusBar(this);
        setContentView(R.layout.activity_pet_customize);
        setupToolbar();
        bindViews();
        loadSavedConfig();
        setupPetButtons();
        setupStyleButtons();
        setupTestButtons();
        setupSaveButton();
    }

    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void bindViews() {
        previewImageView = findViewById(R.id.pet_preview_image);
        petNameTextView = findViewById(R.id.pet_name_text);
        styleGroup = findViewById(R.id.style_group);
    }

    private void loadSavedConfig() {
        PetConfig config = PetConfigManager.load(this);
        selectedPetId = config.getSelectedPetId();
        selectedPetName = config.getPetName();
        selectedStyle = config.getCompanionStyle();
        updatePetPreview(selectedPetId, selectedPetName);
    }

    private void setupPetButtons() {
        findViewById(R.id.pet_cloud_button).setOnClickListener(v -> updatePetPreview("cloud", "小云朵"));
        findViewById(R.id.pet_whale_button).setOnClickListener(v -> updatePetPreview("whale", "小鲸鱼"));
        findViewById(R.id.pet_senior_button).setOnClickListener(v -> updatePetPreview("senior", "温柔学姐"));
        findViewById(R.id.pet_import_button).setOnClickListener(v -> new MaterialAlertDialogBuilder(this)
                .setTitle("导入模型")
                .setMessage("Live2D 导入功能将在第二阶段接入。")
                .setPositiveButton("好的", null)
                .show());
    }

    private void updatePetPreview(String petId, String petName) {
        selectedPetId = petId;
        selectedPetName = petName;
        petNameTextView.setText(petName);
        if ("whale".equals(petId)) {
            previewImageView.setImageResource(R.drawable.ic_pet_whale);
        } else if ("senior".equals(petId)) {
            previewImageView.setImageResource(R.drawable.ic_pet_senior);
        } else {
            previewImageView.setImageResource(R.drawable.ic_pet_cloud);
        }
        live2DController.switchModel(petId);
    }

    private void setupStyleButtons() {
        checkStyleButton(selectedStyle);
        styleGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            selectedStyle = styleFromButtonId(checkedId);
            PetConfigManager.saveCompanionStyle(this, selectedStyle);
            ToastUtils.show(this, "已选择陪伴风格：" + selectedStyle);
        });
    }

    private void checkStyleButton(String style) {
        if ("专业支持".equals(style)) {
            styleGroup.check(R.id.style_support_button);
        } else if ("活泼鼓励".equals(style)) {
            styleGroup.check(R.id.style_energy_button);
        } else if ("安静倾听".equals(style)) {
            styleGroup.check(R.id.style_quiet_button);
        } else {
            styleGroup.check(R.id.style_friend_button);
        }
    }

    private String styleFromButtonId(int checkedId) {
        if (checkedId == R.id.style_support_button) return "专业支持";
        if (checkedId == R.id.style_energy_button) return "活泼鼓励";
        if (checkedId == R.id.style_quiet_button) return "安静倾听";
        return "知心朋友";
    }

    private void setupTestButtons() {
        bindTestButton(R.id.test_happy_button, "开心", "播放开心表情");
        bindTestButton(R.id.test_worry_button, "担心", "播放担心表情");
        bindTestButton(R.id.test_comfort_button, "安慰", "播放安慰动作");
        bindTestButton(R.id.test_cheer_button, "鼓励", "播放鼓励动作");
    }

    private void bindTestButton(int buttonId, String motionName, String toastText) {
        MaterialButton button = findViewById(buttonId);
        button.setOnClickListener(v -> {
            live2DController.setExpression(motionName);
            live2DController.playMotion(motionName);
            ToastUtils.show(this, toastText);
        });
    }

    private void setupSaveButton() {
        findViewById(R.id.save_pet_button).setOnClickListener(v -> {
            PetConfig config = new PetConfig(selectedPetId, selectedPetName, selectedStyle, "", true);
            PetConfigManager.save(this, config);
            ToastUtils.show(this, "设置已保存");
        });
    }
}
