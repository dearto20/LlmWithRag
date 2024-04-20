package com.example.llmwithrag.llm;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "embedding")
public class Embedding {
    @PrimaryKey(autoGenerate = true)
    public int id;
    public String text;
    public float[] vector;

    public Embedding(String text, float[] vector) {
        this.text = text;
        this.vector = vector;
    }
}
