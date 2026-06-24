package com.mindisle.app.live2d

import android.content.Context
import android.net.Uri

object Live2DModels {
    private const val ASSET_LOADER_BASE_URL = "https://appassets.androidplatform.net/assets"
    private const val PREFS_NAME = "live2d_model_prefs"
    private const val KEY_SELECTED_MODEL_ID = "selected_live2d_model_id"
    private const val LEGACY_KEY_SELECTED_MODEL_ID = "selected_model_id"
    private const val XINGYUE_SHUIMU_MODEL_FILE = "xingyue_shuimu_safe.model3.json"
    private const val XIAOTUJI_MODEL_FILE = "小兔叽 (1).model3.json"
    private const val HEHUA_XIAOJIANGSHI_MODEL_FILE = "荷花小僵尸.model3.json"
    private const val TIANSHI_XIAOXIAOYANG_MODEL_FILE = "天使小小羊.model3.json"
    private const val XIAOXIONG_MODEL_FILE = "女仆小熊.model3.json"

    val availableModels = listOf(
        Live2DModel(
            id = "baimao",
            name = "白猫",
            assetModelPath = "../models/whitecat/sdwhite cat free.model3.json",
            previewImagePath = "../models/whitecat/SDwhite cat free.2048/texture_00.png",
            description = "白猫 Live2D 模型"
        ),
        Live2DModel(
            id = "xiaoyu",
            name = "小屿",
            assetModelPath = "../models/qq/1-qq.model3.json",
            previewImagePath = "../models/qq/1-qq.4096/texture_00.png",
            description = "默认陪伴角色"
        ),
        Live2DModel(
            id = "xingyue_shuimu",
            name = "星月水母",
            assetModelPath = "live2d/xingyue_shuimu_safe/$XINGYUE_SHUIMU_MODEL_FILE",
            previewImagePath = "live2d/xingyue_shuimu/ICON.png",
            description = "星月水母 Live2D 主模型",
            runtimeVersion = "cubism5"
        ),
        Live2DModel(
            id = "xiaotuji",
            name = "小兔姬",
            assetModelPath = "../xiaotuji/$XIAOTUJI_MODEL_FILE",
            previewImagePath = "../xiaotuji/小兔叽 (1).4096/texture_00.png",
            description = "小兔叽 Live2D 模型"
        ),
        Live2DModel(
            id = "hehua_xiaojiangshi",
            name = "荷花小僵尸",
            assetModelPath = "../hehua_xiaojiangshi/$HEHUA_XIAOJIANGSHI_MODEL_FILE",
            previewImagePath = "",
            description = "荷花小僵尸 Live2D 模型"
        ),
        Live2DModel(
            id = "tianshi_xiaoxiaoyang",
            name = "天使小小羊",
            assetModelPath = "../tianshi_xiaoxiaoyang/$TIANSHI_XIAOXIAOYANG_MODEL_FILE",
            previewImagePath = "",
            description = "天使小小羊 Live2D 模型"
        ),
        Live2DModel(
            id = "xiaoxiong",
            name = "小熊",
            assetModelPath = "../xiaoxiong/$XIAOXIONG_MODEL_FILE",
            previewImagePath = "",
            description = "小熊 Live2D 模型"
        ),
        Live2DModel(
            id = "yumi",
            name = "Yumi",
            assetModelPath = "../yumi/yumi.model3.json",
            previewImagePath = "../yumi/yumi.8192/texture_00.png",
            description = "EchoBot Yumi 模型"
        ),
        Live2DModel(
            id = "xiaohushi",
            name = "小护士",
            assetModelPath = "live2d/cubism5_models/xiaohushi/model.model3.json",
            previewImagePath = "live2d/cubism5_models/xiaohushi/preview.png",
            description = "Cubism5 小护士模型",
            runtimeVersion = "cubism5"
        ),
    )

    fun getSelectedModel(context: Context): Live2DModel {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val storedModelId = prefs.getString(KEY_SELECTED_MODEL_ID, null)
            ?: prefs.getString(LEGACY_KEY_SELECTED_MODEL_ID, null)
        val modelId = migrateLegacyModelId(storedModelId)
        if (modelId != null && modelId != storedModelId) {
            prefs.edit()
                .putString(KEY_SELECTED_MODEL_ID, modelId)
                .remove(LEGACY_KEY_SELECTED_MODEL_ID)
                .apply()
        }
        return availableModels.firstOrNull { it.id == modelId } ?: availableModels.first()
    }

    fun saveSelectedModel(context: Context, modelId: String): Live2DModel? {
        val model = availableModels.firstOrNull { it.id == modelId } ?: return null
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SELECTED_MODEL_ID, model.id)
            .remove(LEGACY_KEY_SELECTED_MODEL_ID)
            .apply()
        return model
    }

    fun viewerUrl(
        model: Live2DModel,
        floating: Boolean = false,
        variant: String? = null
    ): String {
        return viewerUrl(
            model.assetModelPath,
            model.id,
            model.runtimeVersion,
            floating,
            variant
        )
    }

    fun viewerUrl(
        assetModelPath: String,
        modelId: String = "",
        runtimeVersion: String = "cubism4",
        floating: Boolean = false,
        variant: String? = null
    ): String {
        val floatingParam = if (floating) "&floating=1&mode=float" else ""
        val modelIdParam = if (modelId.isNotBlank()) "&modelId=${Uri.encode(modelId)}" else ""
        val variantParam = variant?.takeIf { it.isNotBlank() }
            ?.let { "&variant=${Uri.encode(it)}" }
            .orEmpty()
        val viewerModelPath = toViewerModelPath(assetModelPath)
        val viewerDir = if (runtimeVersion == "cubism5") "viewer5" else "viewer"
        return "$ASSET_LOADER_BASE_URL/live2d/$viewerDir/index.html?model=${Uri.encode(viewerModelPath)}$modelIdParam$floatingParam$variantParam"
    }

    private fun toViewerModelPath(assetModelPath: String): String {
        val normalizedPath = assetModelPath.replace('\\', '/')
        return if (normalizedPath.startsWith("live2d/")) {
            "../${normalizedPath.removePrefix("live2d/")}"
        } else {
            normalizedPath
        }
    }

    private fun migrateLegacyModelId(modelId: String?): String? {
        return when (modelId) {
            "whitecat" -> "baimao"
            "qq" -> "xiaoyu"
            else -> modelId
        }
    }
}
