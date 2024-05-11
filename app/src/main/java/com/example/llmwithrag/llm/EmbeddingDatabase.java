package com.example.llmwithrag.llm;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.example.llmwithrag.datasource.DataSourceDatabase;

@Database(entities = {Embedding.class}, version = 1, exportSchema = false)
@androidx.room.TypeConverters(TypeConverters.class)
public abstract class EmbeddingDatabase extends RoomDatabase {
    public abstract EmbeddingDao getEmbeddingDao();

    private static volatile EmbeddingDatabase sInstance = null;

    public static EmbeddingDatabase getInstance(Context context) {
        if (sInstance == null) {
            synchronized (EmbeddingDatabase.class) {
                if (sInstance == null) {
                    sInstance = Room.databaseBuilder(context.getApplicationContext(),
                                    EmbeddingDatabase.class, "vector_db")
                            .build();
                }
            }
        }
        return sInstance;
    }
}
