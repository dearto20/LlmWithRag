package com.example.llmwithrag.llm;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class EmbeddingResponse {
    @SerializedName("object")
    public String object;

    @SerializedName("usage")
    public Usage usage;

    @SerializedName("data")
    public List<EmbeddingData> data;

    public static class Usage {
        @SerializedName("total_tokens")
        public int totalTokens;

        @SerializedName("prompt_tokens")
        public int promptTokens;

        @SerializedName("completion_tokens")
        public int completionTokens;
    }

    public static class EmbeddingData {
        @SerializedName("object")
        public String object;

        @SerializedName("embedding")
        public float[] embedding;
    }
}
