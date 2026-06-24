package com.mindisle.app.profile

import android.content.Context
import androidx.annotation.DrawableRes
import com.mindisle.app.R

data class StatusItem(
    val id: String,
    val name: String,
    val category: String,
    @DrawableRes val iconRes: Int,
    val defaultText: String
)

data class ActiveProfileStatus(
    val statusId: String,
    val statusName: String,
    val statusText: String,
    val iconName: String,
    val updatedAt: Long,
    val expireAt: Long
)

object ProfileStatusCatalog {
    val categories = linkedMapOf(
        "心情想法" to listOf(
            StatusItem("happy", "美滋滋", "心情想法", R.drawable.ic_status_star, "今天的心情亮晶晶"),
            StatusItem("emo", "有点emo", "心情想法", R.drawable.ic_status_moon, "需要一点点陪伴"),
            StatusItem("thinking", "胡思乱想", "心情想法", R.drawable.ic_status_cloud, "脑海里飘着很多小念头"),
            StatusItem("energetic", "元气满满", "心情想法", R.drawable.ic_status_sparkle, "今天也要闪闪发光"),
            StatusItem("need_company", "想被陪伴", "心情想法", R.drawable.ic_status_healing, "想在心屿靠一会儿"),
            StatusItem("daydreaming", "发呆中", "心情想法", R.drawable.ic_status_jellyfish, "正在安静地漂一会儿")
        ),
        "学习工作" to listOf(
            StatusItem("studying", "学习中", "学习工作", R.drawable.ic_status_study, "正在悄悄变厉害"),
            StatusItem("busy", "忙碌中", "学习工作", R.drawable.ic_status_focus, "认真处理今天的事情"),
            StatusItem("slacking", "摸鱼中", "学习工作", R.drawable.ic_status_jellyfish, "短暂漂流，马上回来"),
            StatusItem("focus", "专注模式", "学习工作", R.drawable.ic_status_focus, "先专心完成这一件事"),
            StatusItem("ddl", "赶ddl", "学习工作", R.drawable.ic_status_study, "正在和时间一起冲刺"),
            StatusItem("rest_day", "今日摆烂", "学习工作", R.drawable.ic_status_cloud, "允许自己慢一点")
        ),
        "活动" to listOf(
            StatusItem("exercise", "运动中", "活动", R.drawable.ic_status_activity, "让身体也轻快起来"),
            StatusItem("milk_tea", "喝奶茶", "活动", R.drawable.ic_status_cup, "补充一杯甜甜能量"),
            StatusItem("eating", "吃饭中", "活动", R.drawable.ic_status_cup, "认真吃饭也是大事"),
            StatusItem("walking", "散步中", "活动", R.drawable.ic_status_activity, "去接住一点晚风"),
            StatusItem("photo", "拍照中", "活动", R.drawable.ic_status_sparkle, "收藏此刻的微光"),
            StatusItem("streaming", "配信中", "活动", R.drawable.ic_status_moon, "正在心屿小岛发光中")
        ),
        "休息" to listOf(
            StatusItem("resting", "休息中", "休息", R.drawable.ic_status_cloud, "正在补充温柔能量"),
            StatusItem("sleeping", "睡觉中", "休息", R.drawable.ic_status_sleep, "晚安，梦里也要轻轻的"),
            StatusItem("music", "听歌中", "休息", R.drawable.ic_status_music, "让旋律陪我待一会儿"),
            StatusItem("do_not_disturb", "勿扰模式", "休息", R.drawable.ic_status_moon, "暂时安静一下"),
            StatusItem("home", "宅家中", "休息", R.drawable.ic_status_cloud, "在自己的小岛充电"),
            StatusItem("charging", "充电中", "休息", R.drawable.ic_status_healing, "慢慢恢复能量")
        ),
        "心屿专属" to listOf(
            StatusItem("moon_jellyfish", "星月水母", "心屿专属", R.drawable.ic_status_jellyfish, "和星月水母一起漂流"),
            StatusItem("healing", "被治愈中", "心屿专属", R.drawable.ic_status_healing, "正在被温柔接住"),
            StatusItem("companionship", "陪伴中", "心屿专属", R.drawable.ic_status_star, "此刻有人陪着我"),
            StatusItem("island_drift", "小岛漂流", "心屿专属", R.drawable.ic_status_jellyfish, "在心屿小岛慢慢漂流"),
            StatusItem("sunny_mood", "心情晴朗", "心屿专属", R.drawable.ic_status_sparkle, "心里有一小片晴天"),
            StatusItem("recovering", "慢慢恢复", "心屿专属", R.drawable.ic_status_healing, "不用着急，慢慢来")
        )
    )

    val all: List<StatusItem> = categories.values.flatten()

    fun find(id: String?): StatusItem? = all.firstOrNull { it.id == id }
}

class ProfileStatusStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun loadActive(): ActiveProfileStatus? {
        val id = prefs.getString(KEY_ID, null) ?: return null
        val expireAt = prefs.getLong(KEY_EXPIRE_AT, 0L)
        if (expireAt in 1 until System.currentTimeMillis()) {
            clear()
            return null
        }
        val item = ProfileStatusCatalog.find(id) ?: run {
            clear()
            return null
        }
        return ActiveProfileStatus(
            statusId = id,
            statusName = prefs.getString(KEY_NAME, item.name).orEmpty(),
            statusText = prefs.getString(KEY_TEXT, item.defaultText).orEmpty(),
            iconName = prefs.getString(KEY_ICON, item.id).orEmpty(),
            updatedAt = prefs.getLong(KEY_UPDATED_AT, 0L),
            expireAt = expireAt
        )
    }

    fun save(item: StatusItem, customText: String) {
        val now = System.currentTimeMillis()
        prefs.edit()
            .putString(KEY_ID, item.id)
            .putString(KEY_NAME, item.name)
            .putString(KEY_TEXT, customText.trim().ifBlank { item.defaultText })
            .putString(KEY_ICON, item.id)
            .putLong(KEY_UPDATED_AT, now)
            .putLong(KEY_EXPIRE_AT, now + STATUS_DURATION_MS)
            .apply()
    }

    fun clear() {
        prefs.edit()
            .remove(KEY_ID)
            .remove(KEY_NAME)
            .remove(KEY_TEXT)
            .remove(KEY_ICON)
            .remove(KEY_UPDATED_AT)
            .remove(KEY_EXPIRE_AT)
            .apply()
    }

    private companion object {
        const val PREFS = "mindisle_profile_status"
        const val KEY_ID = "current_status_id"
        const val KEY_NAME = "current_status_name"
        const val KEY_TEXT = "current_status_text"
        const val KEY_ICON = "current_status_icon_name"
        const val KEY_UPDATED_AT = "current_status_updated_at"
        const val KEY_EXPIRE_AT = "current_status_expire_at"
        const val STATUS_DURATION_MS = 24L * 60L * 60L * 1000L
    }
}
