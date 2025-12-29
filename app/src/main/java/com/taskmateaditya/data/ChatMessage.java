package com.taskmateaditya.data;

public class ChatMessage {
    private String message;
    private boolean isUser; // true = user, false = AI

    public ChatMessage(String message, boolean isUser) {
        this.message = message;
        this.isUser = isUser;
    }

    public String getMessage() { return message; }
    public boolean isUser() { return isUser; }
}