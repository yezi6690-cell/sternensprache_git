package com.mindisle.app.chat

object LocalReplyEngine {
    fun reply(userText: String): String {
        return when {
            userText.contains("专注") || userText.contains("学习") ->
                "好呀，我陪你。我们先从 25 分钟开始，目标只选一个小任务。"
            userText.contains("累") || userText.contains("疲惫") ->
                "辛苦啦。先把肩膀放松一下，喝点水，我们不急着立刻变好。"
            userText.contains("休息") ->
                "可以的。现在先离开屏幕一分钟，看看远处，让眼睛和脑袋都缓一缓。"
            userText.contains("焦虑") ->
                "听起来你有些焦虑。我们先把最担心的一件事写下来，再慢慢拆开。"
            userText.contains("难过") || userText.contains("低落") ->
                "我能感觉到你现在不太好受。先不用急着振作，我会在这里陪你。"
            else ->
                "我听到了。你可以慢慢说，我会陪你把这件事一点点理清楚。"
        }
    }
}
