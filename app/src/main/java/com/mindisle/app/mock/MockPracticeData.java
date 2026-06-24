package com.mindisle.app.mock;

import com.mindisle.app.model.PracticeItem;

import java.util.ArrayList;
import java.util.List;

public class MockPracticeData {
    private MockPracticeData() {
    }

    public static List<PracticeItem> list() {
        List<PracticeItem> items = new ArrayList<>();
        items.add(new PracticeItem(
                "3分钟呼吸放松",
                "用一个短节奏把注意力带回当下",
                "跟随节奏：\n吸气 4 秒\n停留 2 秒\n呼气 6 秒\n\n不用追求完全放松，只要慢慢把注意力带回呼吸。"
        ));
        items.add(new PracticeItem(
                "负面想法改写",
                "把脑海里的绝对化想法写得更温和",
                "先写下让你难受的一句话，再问自己：有没有别的解释？如果朋友这样想，我会怎么安慰 TA？最后把它改写成更具体、更可承受的版本。"
        ));
        items.add(new PracticeItem(
                "考前焦虑缓解",
                "把复习压力拆成可完成的小块",
                "选择最重要的一门课，列出一个 25 分钟内能完成的小任务。完成后短暂休息，再决定下一步。焦虑不需要立刻消失，行动可以先变小。"
        ));
        items.add(new PracticeItem(
                "睡前放松",
                "给身体一个准备休息的信号",
                "放下手机，慢慢扫描从额头到脚趾的紧绷感。每注意到一个部位，就轻轻呼气。今晚只需要让身体知道：现在可以暂停了。"
        ));
        return items;
    }
}
