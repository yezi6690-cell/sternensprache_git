package com.mindisle.app.assessment

import com.mindisle.app.R

object AssessmentCatalog {
    val definitions: List<AssessmentDefinition> = listOf(
        AssessmentDefinition(
            id = "comprehensive",
            title = "今日综合评估",
            description = "从六个维度温柔了解今天的整体状态",
            estimatedMinutes = 3,
            iconRes = R.drawable.ic_assess_emotion,
            questions = listOf(
                AssessmentQuestion("今天我的情绪整体比较平稳。", "情绪稳定度", reverse = true),
                AssessmentQuestion("我容易因为小事产生明显的情绪波动。", "情绪稳定度"),
                AssessmentQuestion("我能从一些小事中感到放松或安慰。", "情绪稳定度", reverse = true),
                AssessmentQuestion("今天我感觉有很多事情压在心上。", "压力负荷"),
                AssessmentQuestion("我能给自己留出一点喘息时间。", "压力负荷", reverse = true),
                AssessmentQuestion("最近的任务或责任让我有些紧绷。", "压力负荷"),
                AssessmentQuestion("我醒来后感觉精力有所恢复。", "睡眠恢复", reverse = true),
                AssessmentQuestion("最近的睡眠质量让我满意。", "睡眠恢复", reverse = true),
                AssessmentQuestion("我白天容易感到困倦或疲惫。", "睡眠恢复"),
                AssessmentQuestion("我会反复担心还没有发生的事情。", "焦虑紧张"),
                AssessmentQuestion("我很难让自己真正放松下来。", "焦虑紧张"),
                AssessmentQuestion("我能把注意力拉回当下。", "焦虑紧张", reverse = true),
                AssessmentQuestion("我对平时喜欢的事情兴趣有所下降。", "低落体验"),
                AssessmentQuestion("我会觉得自己没有什么动力。", "低落体验"),
                AssessmentQuestion("我对接下来的一天仍有一点期待。", "低落体验", reverse = true),
                AssessmentQuestion("我希望有人能听我说说话。", "陪伴需求"),
                AssessmentQuestion("我更想一个人安静待着。", "陪伴需求"),
                AssessmentQuestion("我觉得自己需要一点理解和支持。", "陪伴需求")
            )
        ),
        AssessmentDefinition(
            id = "emotion",
            title = "情绪状态",
            description = "看看今天的情绪起伏",
            estimatedMinutes = 2,
            iconRes = R.drawable.ic_assess_emotion,
            questions = listOf(
                AssessmentQuestion("最近几天，我能感受到愉快或放松。", "愉悦感", reverse = true),
                AssessmentQuestion("我容易因为小事产生情绪波动。", "情绪波动"),
                AssessmentQuestion("我感觉自己的情绪比较稳定。", "情绪稳定", reverse = true),
                AssessmentQuestion("我会突然觉得很累或提不起劲。", "疲惫感"),
                AssessmentQuestion("我愿意和别人交流自己的感受。", "陪伴连接", reverse = true),
                AssessmentQuestion("我觉得自己需要被理解和陪伴。", "陪伴需求"),
                AssessmentQuestion("我能从一些小事里获得安慰。", "恢复能力", reverse = true),
                AssessmentQuestion("我对今天或明天还有一点期待。", "期待感", reverse = true)
            )
        ),
        AssessmentDefinition(
            id = "stress",
            title = "压力负荷",
            description = "了解最近的压力与恢复感",
            estimatedMinutes = 3,
            iconRes = R.drawable.ic_assess_stress,
            questions = listOf(
                AssessmentQuestion("最近我经常感觉事情很多、来不及处理。", "时间压力"),
                AssessmentQuestion("我会因为学习、工作或生活安排感到紧绷。", "身体紧绷"),
                AssessmentQuestion("我觉得自己很难真正放松下来。", "恢复能力"),
                AssessmentQuestion("我会反复想着还没完成的事情。", "反复思考"),
                AssessmentQuestion("我觉得休息后仍然没有恢复。", "恢复能力"),
                AssessmentQuestion("我最近容易烦躁或没有耐心。", "情绪负荷"),
                AssessmentQuestion("我能合理安排自己的任务。", "任务掌控", reverse = true),
                AssessmentQuestion("我感觉自己被很多要求推着走。", "任务压力"),
                AssessmentQuestion("我有给自己留下喘息的时间。", "恢复空间", reverse = true),
                AssessmentQuestion("我最近的压力已经影响到日常状态。", "日常影响")
            )
        ),
        AssessmentDefinition(
            id = "sleep",
            title = "睡眠质量",
            description = "记录睡眠和精力恢复",
            estimatedMinutes = 2,
            iconRes = R.drawable.ic_assess_sleep,
            questions = listOf(
                AssessmentQuestion("我最近入睡比较困难。", "入睡难度"),
                AssessmentQuestion("我睡眠中容易醒来。", "睡眠连续性"),
                AssessmentQuestion("我醒来后仍然觉得疲惫。", "醒后恢复"),
                AssessmentQuestion("我白天容易困倦或注意力下降。", "白天状态"),
                AssessmentQuestion("我睡前会反复思考很多事情。", "睡前思绪"),
                AssessmentQuestion("我最近的睡眠能让我恢复精力。", "恢复能力", reverse = true)
            )
        ),
        AssessmentDefinition(
            id = "anxiety",
            title = "焦虑倾向",
            description = "观察紧张、担忧和放松程度",
            estimatedMinutes = 2,
            iconRes = R.drawable.ic_assess_anxiety,
            questions = listOf(
                AssessmentQuestion("我最近经常担心还没发生的事情。", "担忧频率"),
                AssessmentQuestion("我会感到紧张、坐立不安。", "紧张感"),
                AssessmentQuestion("我很难让自己停止反复思考。", "反复思考"),
                AssessmentQuestion("我容易预想糟糕结果。", "预期压力"),
                AssessmentQuestion("我身体上会有紧绷、心慌或不适感。", "身体紧绷"),
                AssessmentQuestion("我觉得自己难以放松。", "放松能力"),
                AssessmentQuestion("我能够把注意力拉回当下。", "当下感", reverse = true)
            )
        ),
        AssessmentDefinition(
            id = "low_mood",
            title = "低落倾向",
            description = "关注兴趣、能量和低落体验",
            estimatedMinutes = 2,
            iconRes = R.drawable.ic_assess_low_mood,
            questions = listOf(
                AssessmentQuestion("我最近对平时喜欢的事情兴趣下降。", "兴趣下降"),
                AssessmentQuestion("我容易感到低落或空空的。", "情绪低落"),
                AssessmentQuestion("我觉得自己做什么都没有动力。", "能量下降"),
                AssessmentQuestion("我会对自己产生否定想法。", "自我否定"),
                AssessmentQuestion("我感觉和外界有些疏远。", "连接感"),
                AssessmentQuestion("我能从别人或事物中感到支持。", "支持感", reverse = true),
                AssessmentQuestion("我觉得最近的生活还有一些值得期待的部分。", "期待感", reverse = true)
            )
        ),
        AssessmentDefinition(
            id = "social_energy",
            title = "社交能量",
            description = "看看今天更想独处还是交流",
            estimatedMinutes = 1,
            iconRes = R.drawable.ic_assess_social_energy,
            questions = listOf(
                AssessmentQuestion("我最近愿意和别人聊天或互动。", "交流意愿", reverse = true),
                AssessmentQuestion("我更想一个人待着。", "独处需求"),
                AssessmentQuestion("社交后我会觉得被消耗。", "交流消耗"),
                AssessmentQuestion("我希望有人陪我，但又不知道怎么开口。", "陪伴需求"),
                AssessmentQuestion("我能清楚表达自己的边界。", "边界感", reverse = true),
                AssessmentQuestion("我感觉自己和他人仍有连接。", "连接感", reverse = true)
            )
        )
    )

    fun find(type: String): AssessmentDefinition? =
        definitions.firstOrNull { it.id == type }
}
