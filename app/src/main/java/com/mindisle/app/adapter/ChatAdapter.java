package com.mindisle.app.adapter;

import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.mindisle.app.R;
import com.mindisle.app.model.ChatMessage;

import java.util.ArrayList;
import java.util.List;

public class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.ChatViewHolder> {
    private final List<ChatMessage> messages = new ArrayList<>();

    @NonNull
    @Override
    public ChatViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat_message, parent, false);
        return new ChatViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ChatViewHolder holder, int position) {
        ChatMessage message = messages.get(position);
        holder.messageText.setText(message.getText());
        holder.messageRoot.setGravity(message.isUser() ? Gravity.END : Gravity.START);
        holder.messageText.setBackgroundResource(message.isUser() ? R.drawable.bg_chat_user : R.drawable.bg_chat_assistant);
        holder.messageText.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.mi_text_main));
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    public void addMessage(ChatMessage message) {
        messages.add(message);
        notifyItemInserted(messages.size() - 1);
    }

    public int lastPosition() {
        return Math.max(0, messages.size() - 1);
    }

    static class ChatViewHolder extends RecyclerView.ViewHolder {
        private final LinearLayout messageRoot;
        private final TextView messageText;

        ChatViewHolder(@NonNull View itemView) {
            super(itemView);
            messageRoot = itemView.findViewById(R.id.message_root);
            messageText = itemView.findViewById(R.id.message_text);
        }
    }
}
