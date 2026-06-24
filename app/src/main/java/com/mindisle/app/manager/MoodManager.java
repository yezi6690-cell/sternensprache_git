package com.mindisle.app.manager;

import android.content.Context;
import android.content.SharedPreferences;

import com.mindisle.app.model.MoodRecord;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class MoodManager {
    private static final String PREF_NAME = "mindisle_mood_records";
    private static final String KEY_RECORDS = "records";
    private static final int MAX_RECORDS = 30;

    private MoodManager() {
    }

    public static void saveRecord(Context context, MoodRecord record) {
        List<MoodRecord> records = loadRecords(context);
        records.add(0, record);
        if (records.size() > MAX_RECORDS) {
            records = records.subList(0, MAX_RECORDS);
        }
        saveRecords(context, records);
    }

    public static List<MoodRecord> loadRecords(Context context) {
        SharedPreferences preferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String raw = preferences.getString(KEY_RECORDS, "[]");
        List<MoodRecord> records = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.getJSONObject(i);
                records.add(new MoodRecord(
                        item.optString("mood"),
                        item.optString("content"),
                        item.optLong("timestamp")
                ));
            }
        } catch (JSONException ignored) {
            return new ArrayList<>();
        }
        return records;
    }

    private static void saveRecords(Context context, List<MoodRecord> records) {
        JSONArray array = new JSONArray();
        for (MoodRecord record : records) {
            JSONObject item = new JSONObject();
            try {
                item.put("mood", record.getMood());
                item.put("content", record.getContent());
                item.put("timestamp", record.getTimestamp());
                array.put(item);
            } catch (JSONException ignored) {
                // Skip malformed local records.
            }
        }
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_RECORDS, array.toString())
                .apply();
    }
}
