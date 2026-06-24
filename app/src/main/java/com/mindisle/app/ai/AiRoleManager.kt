package com.mindisle.app.ai

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

class AiRoleManager(private val context: Context) {
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val roles: List<AiRole> by lazy { loadRoles() }

    fun getEnabledRoles(): List<AiRole> = roles.filter { it.enabled }

    fun getSelectedRole(preferredModelId: String? = null): AiRole {
        val enabledRoles = getEnabledRoles()
        val storedRoleId = preferences.getString(KEY_SELECTED_ROLE_ID, null)
        val savedRoleId = migrateLegacyRoleId(storedRoleId)
        if (savedRoleId != null && savedRoleId != storedRoleId) {
            preferences.edit().putString(KEY_SELECTED_ROLE_ID, savedRoleId).apply()
        }
        return enabledRoles.firstOrNull { it.roleId == savedRoleId }
            ?: enabledRoles.firstOrNull { it.modelId == preferredModelId }
            ?: enabledRoles.firstOrNull { it.roleId == DEFAULT_ROLE_ID }
            ?: enabledRoles.firstOrNull()
            ?: defaultRole()
    }

    fun findByRoleId(roleId: String?): AiRole? {
        return getEnabledRoles().firstOrNull { it.roleId == roleId }
    }

    fun findByModelId(modelId: String?): AiRole? {
        return getEnabledRoles().firstOrNull { it.modelId == modelId }
    }

    fun saveSelectedRole(roleId: String): AiRole? {
        val role = findByRoleId(roleId) ?: return null
        preferences.edit().putString(KEY_SELECTED_ROLE_ID, role.roleId).apply()
        return role
    }

    private fun loadRoles(): List<AiRole> {
        return try {
            val json = context.assets.open(ROLES_ASSET_PATH).bufferedReader().use { it.readText() }
            val root = JSONObject(json)
            val array = root.optJSONArray("roles") ?: JSONArray()
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    parseRole(item)?.let(::add)
                }
            }.ifEmpty { listOf(defaultRole()) }
        } catch (error: Exception) {
            Log.e(TAG, "Unable to load AI role configuration", error)
            listOf(defaultRole())
        }
    }

    private fun parseRole(item: JSONObject): AiRole? {
        val roleId = item.optString("roleId").trim()
        val modelId = item.optString("modelId").trim()
        val displayName = item.optString("displayName").trim()
        if (roleId.isBlank() || modelId.isBlank() || displayName.isBlank()) return null

        val live2dJson = item.optJSONObject("live2d") ?: JSONObject()
        return AiRole(
            roleId = roleId,
            modelId = modelId,
            displayName = displayName,
            personaFileName = item.optString("personaFileName", "$modelId.txt"),
            live2dModelPath = item.optString("live2dModelPath", live2dJson.optString("modelPath")),
            enabled = item.optBoolean("enabled", true),
            description = item.optString("description"),
            personalityPrompt = item.optString("personalityPrompt"),
            speakingStyle = item.optString("speakingStyle"),
            relationshipWithUser = item.optString("relationshipWithUser"),
            welcomeMessage = item.optString("welcomeMessage"),
            live2d = Live2DRoleConfig(
                modelPath = live2dJson.optString("modelPath"),
                defaultExpression = live2dJson.optString("defaultExpression"),
                defaultMotion = live2dJson.optString("defaultMotion"),
                expressions = live2dJson.optJSONArray("expressions").toStringList(),
                motions = live2dJson.optJSONArray("motions").toStringList()
            )
        )
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return buildList {
            for (index in 0 until length()) {
                optString(index).takeIf { it.isNotBlank() }?.let(::add)
            }
        }
    }

    private fun defaultRole(): AiRole {
        return AiRole(
            roleId = DEFAULT_ROLE_ID,
            modelId = DEFAULT_ROLE_ID,
            displayName = "白猫",
            personaFileName = "baimao.txt",
            live2dModelPath = "",
            description = "默认陪伴角色"
        )
    }

    private fun migrateLegacyRoleId(roleId: String?): String? {
        return when (roleId) {
            "mindisle_default", "whitecat" -> "baimao"
            "role_qq", "qq" -> "xiaoyu"
            "role_xingyue_shuimu" -> "xingyue_shuimu"
            "role_xiaotuji" -> "xiaotuji"
            "role_hehua_xiaojiangshi" -> "hehua_xiaojiangshi"
            "role_tianshi_xiaoxiaoyang" -> "tianshi_xiaoxiaoyang"
            "role_xiaoxiong" -> "xiaoxiong"
            "role_yumi" -> "yumi"
            "role_xiaohushi" -> "xiaohushi"
            else -> roleId
        }
    }

    companion object {
        private const val TAG = "MindIsleAiRole"
        private const val PREFS_NAME = "mindisle_ai_role_preferences"
        private const val KEY_SELECTED_ROLE_ID = "selected_ai_role_id"
        private const val ROLES_ASSET_PATH = "ai_roles/roles.json"
        const val DEFAULT_ROLE_ID = "baimao"
    }
}
