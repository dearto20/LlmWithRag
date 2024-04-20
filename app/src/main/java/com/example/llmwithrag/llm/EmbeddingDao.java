package com.example.llmwithrag.llm;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface EmbeddingDao {
    @Insert
    void insertAll(Embedding... embeddings);

    @Delete
    void delete(Embedding embedding);

    @Query("DELETE FROM embedding")
    void deleteAll();

    @Query("SELECT * FROM embedding")
    List<Embedding> getAll();
}
