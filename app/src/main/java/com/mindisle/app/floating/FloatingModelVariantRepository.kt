package com.mindisle.app.floating

import android.content.Context
import com.mindisle.app.live2d.Live2DModel
import org.json.JSONObject

data class FloatingModelVariant(
    val id: String,
    val displayName: String,
    val assetModelPath: String,
    val widthDp: Int,
    val heightDp: Int,
    val displayPreset: String = "FULL_BODY",
    val initialExpression: String? = null,
    val renderScale: Float = 1.0f
)

object FloatingModelVariantRepository {
    const val XINGYUE_SHUIMU_MODEL_ID = "xingyue_shuimu"
    private const val CONFIG_PATH =
        "live2d/xingyue_shuimu_safe/xingyue_shuimu_config.json"
    private const val MODEL_DIRECTORY = "live2d/xingyue_shuimu_safe"

    fun supportedVariants(context: Context, model: Live2DModel): List<FloatingModelVariant> {
        if (model.id != XINGYUE_SHUIMU_MODEL_ID) return emptyList()
        return listOfNotNull(
            readVariant(context, model, "full"),
            readVariant(context, model, "small")
        )
    }

    fun resolve(
        context: Context,
        model: Live2DModel,
        variantId: String?
    ): FloatingModelVariant? {
        if (model.id != XINGYUE_SHUIMU_MODEL_ID) return null
        val requestedId = variantId ?: defaultFloatingVariant(context)
        return readVariant(context, model, requestedId)
            ?: readVariant(context, model, "full")
            ?: fallbackVariant(model, "full")
    }

    private fun defaultFloatingVariant(context: Context): String {
        return "full"
    }

    private fun readVariant(
        context: Context,
        model: Live2DModel,
        variantId: String
    ): FloatingModelVariant? {
        val variant = readConfig(context)
            ?.optJSONObject("variants")
            ?.optJSONObject(variantId)
            ?: return fallbackVariant(model, variantId)
        if (!variant.optBoolean("supportFloating", false)) return null

        val model3 = variant.optString("model3")
        val floating = variant.optJSONObject("floating") ?: JSONObject()
        return FloatingModelVariant(
            id = variantId,
            displayName = variant.optString("displayName", variantId),
            assetModelPath = if (model3.isBlank()) {
                model.assetModelPath
            } else {
                "$MODEL_DIRECTORY/$model3"
            },
            widthDp = floating.optInt("widthDp", if (variantId == "small") 160 else 220),
            heightDp = floating.optInt("heightDp", if (variantId == "small") 200 else 320),
            displayPreset = variant.optString("displayPreset", "FULL_BODY"),
            initialExpression = variant.optString("initialExpression")
                .takeIf { it.isNotBlank() }
        )
    }

    private fun fallbackVariant(
        model: Live2DModel,
        variantId: String
    ): FloatingModelVariant? {
        return when (variantId) {
            "full" -> FloatingModelVariant(
                id = "full",
                displayName = "全身悬浮",
                assetModelPath = model.assetModelPath,
                widthDp = 220,
                heightDp = 320
            )
            "small" -> FloatingModelVariant(
                id = "small",
                displayName = "缩小悬浮",
                assetModelPath = model.assetModelPath,
                widthDp = 230,
                heightDp = 420,
                initialExpression = "变小.exp3.json",
                renderScale = 3.0f
            )
            else -> null
        }
    }

    private fun readConfig(context: Context): JSONObject? {
        return runCatching {
            context.assets.open(CONFIG_PATH).bufferedReader(Charsets.UTF_8).use {
                JSONObject(it.readText())
            }
        }.getOrNull()
    }
}
