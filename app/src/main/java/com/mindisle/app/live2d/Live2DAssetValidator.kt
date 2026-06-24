package com.mindisle.app.live2d

import android.content.Context
import android.util.Log
import org.json.JSONObject

object Live2DAssetValidator {
    private const val TAG = "Live2DAssets"
    private val XINGYUE_CORE_ASSETS = listOf(
        "live2d/xingyue_shuimu_safe/xingyue_shuimu_safe.model3.json",
        "live2d/xingyue_shuimu_safe/xingyue_shuimu.moc3",
        "live2d/xingyue_shuimu_safe/textures/texture_00.png",
        "live2d/xingyue_shuimu_safe/textures/texture_01.png",
        "live2d/xingyue_shuimu_safe/xingyue_shuimu.physics3.json"
    )

    fun validate(context: Context, model: Live2DModel): Boolean {
        if (model.id == "xingyue_shuimu") {
            logXingyueCoreAssets(context)
        }

        val modelAssetPath = resolveAssetPath(model.assetModelPath)
        val modelEntryExists = assetExists(context, modelAssetPath)
        Log.d(TAG, "当前模型 id：${model.id}")
        Log.d(TAG, "当前模型路径：$modelAssetPath")
        Log.d(TAG, "模型入口文件存在：$modelEntryExists")
        if (!modelEntryExists) {
            Log.e(TAG, "${model.name}模型入口文件不存在：$modelAssetPath")
            return false
        }

        val modelJson = runCatching {
            context.assets.open(modelAssetPath).bufferedReader(Charsets.UTF_8).use { it.readText() }
        }.getOrElse { error ->
            Log.e(TAG, "${model.name}模型入口文件读取失败：$modelAssetPath", error)
            return false
        }

        val root = runCatching { JSONObject(modelJson) }.getOrElse { error ->
            Log.e(TAG, "${model.name}模型入口 JSON 解析失败：$modelAssetPath", error)
            return false
        }

        val baseDir = modelAssetPath.substringBeforeLast('/', missingDelimiterValue = "")
        val references = root.optJSONObject("FileReferences") ?: return true
        logResourceScan(context, baseDir, modelAssetPath, references)
        var isComplete = true

        fun checkReference(relativePath: String?) {
            if (relativePath.isNullOrBlank()) return
            val assetPath = joinAssetPath(baseDir, relativePath)
            if (!assetExists(context, assetPath)) {
                Log.e(TAG, "${model.name}模型缺失文件：$assetPath")
                isComplete = false
            }
        }

        checkReference(references.optionalString("Moc"))
        checkReference(references.optionalString("Physics"))
        checkReference(references.optionalString("Pose"))
        checkReference(references.optionalString("DisplayInfo"))

        references.optJSONArray("Textures")?.let { textures ->
            for (index in 0 until textures.length()) {
                checkReference(textures.optionalString(index))
            }
        }

        references.optJSONArray("Expressions")?.let { expressions ->
            for (index in 0 until expressions.length()) {
                checkReference(expressions.optJSONObject(index)?.optionalString("File"))
            }
        }

        references.optJSONObject("Motions")?.let { motions ->
            val keys = motions.keys()
            while (keys.hasNext()) {
                val group = motions.optJSONArray(keys.next()) ?: continue
                for (index in 0 until group.length()) {
                    checkReference(group.optJSONObject(index)?.optionalString("File"))
                }
            }
        }

        return isComplete
    }

    private fun logXingyueCoreAssets(context: Context) {
        XINGYUE_CORE_ASSETS.forEach { assetPath ->
            Log.d(TAG, "XingyueLive2D asset exists: $assetPath = ${assetExists(context, assetPath)}")
        }
    }

    private fun logResourceScan(
        context: Context,
        baseDir: String,
        modelAssetPath: String,
        references: JSONObject
    ) {
        val motions = references.optJSONObject("Motions")
        val motionGroups = mutableListOf<String>()
        motions?.keys()?.let { keys ->
            while (keys.hasNext()) {
                motionGroups.add(keys.next())
            }
        }
        val looseMotionCount = listAssetsRecursive(context, baseDir).count { it.endsWith(".motion3.json") }
        if (motionGroups.isEmpty() && looseMotionCount > 0) {
            motionGroups.add("loose:$looseMotionCount")
        }
        val expressionsCount = maxOf(
            references.optJSONArray("Expressions")?.length() ?: 0,
            listAssetsRecursive(context, baseDir).count { it.endsWith(".exp3.json") }
        )
        val textureCount = references.optJSONArray("Textures")?.length() ?: 0
        Log.d(TAG, "模型资源扫描 model3=$modelAssetPath")
        Log.d(TAG, "模型资源扫描 motions=${motionGroups.joinToString().ifBlank { "none" }}")
        Log.d(TAG, "模型资源扫描 expressions=$expressionsCount")
        Log.d(TAG, "模型资源扫描 physics=${references.has("Physics") && !references.isNull("Physics")}")
        Log.d(TAG, "模型资源扫描 pose=${references.has("Pose") && !references.isNull("Pose")}")
        Log.d(TAG, "模型资源扫描 textures=$textureCount")
    }

    private fun listAssetsRecursive(context: Context, baseDir: String): List<String> {
        val result = mutableListOf<String>()

        fun walk(path: String) {
            val children = runCatching { context.assets.list(path).orEmpty() }.getOrDefault(emptyArray())
            if (children.isEmpty()) {
                result.add(path)
                return
            }
            children.forEach { child ->
                walk("$path/$child")
            }
        }

        walk(baseDir)
        return result
    }

    fun resolveAssetPath(assetModelPath: String): String {
        val normalizedPath = assetModelPath.replace('\\', '/')
        if (normalizedPath.startsWith("live2d/")) {
            return normalizedPath
        }

        val parts = mutableListOf("live2d", "viewer")
        normalizedPath
            .split('/')
            .filter { it.isNotBlank() && it != "." }
            .forEach { segment ->
                if (segment == "..") {
                    if (parts.isNotEmpty()) parts.removeAt(parts.lastIndex)
                } else {
                    parts.add(segment)
                }
            }
        return parts.joinToString("/")
    }

    fun assetExists(context: Context, assetPath: String): Boolean {
        return runCatching {
            context.assets.open(assetPath).close()
            true
        }.getOrDefault(false)
    }

    private fun joinAssetPath(baseDir: String, relativePath: String): String {
        val parts = baseDir.split('/').filter { it.isNotBlank() }.toMutableList()
        relativePath.replace('\\', '/')
            .split('/')
            .filter { it.isNotBlank() && it != "." }
            .forEach { segment ->
                if (segment == "..") {
                    if (parts.isNotEmpty()) parts.removeAt(parts.lastIndex)
                } else {
                    parts.add(segment)
                }
            }
        return parts.joinToString("/")
    }

    private fun JSONObject.optionalString(key: String): String? {
        return if (has(key) && !isNull(key)) optString(key) else null
    }

    private fun org.json.JSONArray.optionalString(index: Int): String? {
        return if (!isNull(index)) optString(index) else null
    }
}
