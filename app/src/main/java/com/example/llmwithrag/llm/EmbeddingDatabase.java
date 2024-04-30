package com.example.llmwithrag.llm;

import androidx.room.Database;
import androidx.room.RoomDatabase;

@Database(entities = {Embedding.class}, version = 1, exportSchema = false)
@androidx.room.TypeConverters(TypeConverters.class)
public abstract class EmbeddingDatabase extends RoomDatabase {
    public abstract EmbeddingDao getEmbeddingDao();
}
