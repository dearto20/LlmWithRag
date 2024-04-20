package com.example.llmwithrag.llm;

public class CompletionMessage {
    public String role;
    public String content;

    public CompletionMessage(String role, String content) {
        this.role = role;
        this.content = content;
    }
}
