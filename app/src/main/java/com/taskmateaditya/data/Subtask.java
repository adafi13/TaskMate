package com.taskmateaditya.data;

import java.io.Serializable;
import java.util.UUID;

public class Subtask implements Serializable {
    private String id;
    private String title;
    private boolean isCompleted;

    public Subtask() {
        this.id = UUID.randomUUID().toString();
        this.isCompleted = false;
    }

    public Subtask(String title) {
        this.id = UUID.randomUUID().toString();
        this.title = title;
        this.isCompleted = false;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public boolean isCompleted() {
        return isCompleted;
    }

    public void setCompleted(boolean completed) {
        isCompleted = completed;
    }
}
