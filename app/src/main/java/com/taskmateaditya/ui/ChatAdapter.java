package com.taskmateaditya.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.taskmateaditya.R;
import com.taskmateaditya.data.ChatMessage;
import io.noties.markwon.Markwon;
import java.util.ArrayList;
import java.util.List;

public class ChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private final List<ChatMessage> chatList = new ArrayList<>();
    private Markwon markwon;
    private static final int TYPE_USER = 1;
    private static final int TYPE_AI = 2;

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (markwon == null) markwon = Markwon.create(parent.getContext());

        if (viewType == TYPE_USER) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat_user, parent, false);
            return new UserViewHolder(view);
        } else {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat_ai, parent, false);
            return new AiViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ChatMessage chat = chatList.get(position);
        if (holder instanceof UserViewHolder) {
            ((UserViewHolder) holder).tvMessage.setText(chat.getMessage());
        } else {
            markwon.setMarkdown(((AiViewHolder) holder).tvMessage, chat.getMessage());
        }
    }

    @Override
    public int getItemCount() { return chatList.size(); }

    @Override
    public int getItemViewType(int position) {
        return chatList.get(position).isUser() ? TYPE_USER : TYPE_AI;
    }

    public void addMessage(ChatMessage message) {
        chatList.add(message);
        notifyItemInserted(chatList.size() - 1);
    }

    public void removeLastMessage() {
        if (!chatList.isEmpty()) {
            int pos = chatList.size() - 1;
            chatList.remove(pos);
            notifyItemRemoved(pos);
        }
    }

    static class UserViewHolder extends RecyclerView.ViewHolder {
        TextView tvMessage;
        UserViewHolder(View itemView) {
            super(itemView);
            tvMessage = itemView.findViewById(R.id.tvMessage);
        }
    }

    static class AiViewHolder extends RecyclerView.ViewHolder {
        TextView tvMessage;
        AiViewHolder(View itemView) {
            super(itemView);
            tvMessage = itemView.findViewById(R.id.tvMessage);
        }
    }
}