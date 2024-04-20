package com.example.llmwithrag.llm;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class CompletionRequest {
    @SerializedName("model")
    private String model;

    @SerializedName("messages")
    private List<CompletionMessage> messages;

    public CompletionRequest(String model, List<CompletionMessage> messages) {
        this.model = model;
        this.messages = messages;
    }
}
