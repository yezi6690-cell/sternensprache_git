package com.mindisle.app.mock;

import com.mindisle.app.model.MoodRecord;

import java.util.ArrayList;
import java.util.List;

public class MockMoodData {
    private MockMoodData() {
    }

    public static List<MoodRecord> recentRecords() {
        List<MoodRecord> records = new ArrayList<>();
        long now = System.currentTimeMillis();
        records.add(new MoodRecord("平静", "今天给自己留了一点独处时间，感觉节奏慢下来了。", now - 24L * 60L * 60L * 1000L));
        records.add(new MoodRecord("疲惫", "课程和作业有点满，但晚上散步后舒服了一些。", now - 2L * 24L * 60L * 60L * 1000L));
        return records;
    }
}
