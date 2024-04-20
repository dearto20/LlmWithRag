package com.example.llmwithrag.llm;

import com.google.gson.annotations.SerializedName;

public class EmbeddingRequest {
    @SerializedName("input")
    private String input;

    @SerializedName("model")
    private String model;

    @SerializedName("encoding_format")
    private String encodingFormat;

    public EmbeddingRequest(String input, String model, String encodingFormat) {
        this.input = input;
        this.model = model;
        this.encodingFormat = encodingFormat;
    }
}
