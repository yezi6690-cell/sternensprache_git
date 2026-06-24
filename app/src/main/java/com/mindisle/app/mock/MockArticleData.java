package com.mindisle.app.mock;

import com.mindisle.app.model.ArticleItem;

import java.util.ArrayList;
import java.util.List;

public class MockArticleData {
    private MockArticleData() {
    }

    public static List<ArticleItem> list() {
        List<ArticleItem> articles = new ArrayList<>();
        articles.add(new ArticleItem(
                "什么是焦虑？",
                "了解焦虑的提醒功能，也看见它带来的负担。",
                "焦虑是人在面对不确定、压力或可能的风险时产生的身心反应。适度焦虑会提醒我们准备考试、完成任务，但当它持续很久、影响睡眠和学习时，就需要停下来照顾自己。可以先记录触发焦虑的情境，再把任务拆小，必要时寻求学校心理中心的支持。"
        ));
        articles.add(new ArticleItem(
                "为什么大学生容易内耗？",
                "学习、关系和未来选择常常同时挤在一起。",
                "大学阶段充满比较、选择和身份变化，很多人会在“应该更努力”和“我已经很累”之间反复拉扯。内耗并不代表你不够好，它常常说明你把太多评价放在了心里。试着把模糊的担心写成具体问题，再分清哪些能行动，哪些需要暂时放下。"
        ));
        articles.add(new ArticleItem(
                "如何缓解考试压力？",
                "把复习从巨大目标拆成今天能做的一步。",
                "考试压力常来自未知感和任务堆积。你可以先列出科目和薄弱点，再选出最影响分数的一小块内容。用 25 分钟专注复习，之后休息 5 分钟，比长时间硬撑更稳定。睡眠、饮水和规律进食也会直接影响记忆和情绪。"
        ));
        articles.add(new ArticleItem(
                "如何处理宿舍关系？",
                "边界清楚一点，关系反而更轻松。",
                "宿舍关系密度高，作息、卫生和声音都容易变成压力源。沟通时尽量描述具体事实和自己的感受，例如“晚上十二点后声音会影响我睡觉”，比指责更容易被听见。如果一次沟通无效，可以请辅导员或宿管协助制定共同规则。"
        ));
        articles.add(new ArticleItem(
                "睡不着时可以做什么？",
                "先降低对睡眠的用力感。",
                "睡不着时越急着入睡，大脑越容易保持警觉。可以把灯光调暗，离开床做一件安静的小事，比如听舒缓音频或读几页轻松内容。避免反复看时间。若失眠持续多周，并明显影响白天状态，建议主动联系专业支持。"
        ));
        articles.add(new ArticleItem(
                "什么时候应该主动求助？",
                "求助是照顾自己，不是失败。",
                "如果低落、焦虑、失眠或无力感持续两周以上，已经影响学习、人际和日常生活，或者出现伤害自己的念头，就值得尽快求助。可以先联系可信任的人、辅导员或学校心理中心。越早被支持，越容易把困难从一个人扛变成一起面对。"
        ));
        return articles;
    }
}
