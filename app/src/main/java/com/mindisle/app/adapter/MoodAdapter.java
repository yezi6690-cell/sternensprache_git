package com.mindisle.app.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.mindisle.app.R;
import com.mindisle.app.model.MoodRecord;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MoodAdapter extends RecyclerView.Adapter<MoodAdapter.MoodViewHolder> {
    private final List<MoodRecord> records = new ArrayList<>();
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MM月dd日 HH:mm", Locale.CHINA);

    @NonNull
    @Override
    public MoodViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_mood_record, parent, false);
        return new MoodViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MoodViewHolder holder, int position) {
        MoodRecord record = records.get(position);
        holder.moodText.setText(record.getMood());
        holder.contentText.setText(record.getContent());
        holder.timeText.setText(dateFormat.format(new Date(record.getTimestamp())));
    }

    @Override
    public int getItemCount() {
        return records.size();
    }

    public void submitList(List<MoodRecord> newRecords) {
        records.clear();
        records.addAll(newRecords);
        notifyDataSetChanged();
    }

    static class MoodViewHolder extends RecyclerView.ViewHolder {
        private final TextView moodText;
        private final TextView contentText;
        private final TextView timeText;

        MoodViewHolder(@NonNull View itemView) {
            super(itemView);
            moodText = itemView.findViewById(R.id.record_mood_text);
            contentText = itemView.findViewById(R.id.record_content_text);
            timeText = itemView.findViewById(R.id.record_time_text);
        }
    }
}
