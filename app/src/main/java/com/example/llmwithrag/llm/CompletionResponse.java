package com.example.llmwithrag.llm;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class CompletionResponse {
    @SerializedName("id")
    public String id;

    @SerializedName("object")
    public String object;

    @SerializedName("created")
    public int created;

    @SerializedName("model")
    public String model;

    @SerializedName("choices")
    public List<Choice> choices;

    public static class Choice {
        @SerializedName("index")
        public int index;

        @SerializedName("message")
        public CompletionMessage message;

        @SerializedName("logprobs")
        public Object logprobs;

        @SerializedName("finish_reason")
        public String finishReason;
    }
}
