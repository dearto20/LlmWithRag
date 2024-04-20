package com.example.llmwithrag.llm;

import com.example.llmwithrag.BuildConfig;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Headers;
import retrofit2.http.POST;

public interface OpenAiService {
    @Headers("Authorization: Bearer " + BuildConfig.OPENAI_API_KEY)
    @POST("v1/embeddings")
    Call<EmbeddingResponse> getEmbedding(@Body EmbeddingRequest body);

    @Headers("Authorization: Bearer " + BuildConfig.OPENAI_API_KEY)
    @POST("v1/chat/completions")
    Call<CompletionResponse> getCompletions(@Body CompletionRequest body);
}
