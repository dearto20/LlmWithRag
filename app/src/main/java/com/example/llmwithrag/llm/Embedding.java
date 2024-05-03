package com.example.llmwithrag.llm;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "embedding")
public class Embedding {
    @PrimaryKey(autoGenerate = true)
    public int id;
    public String text;
    public String description;
    public String category;
    public float[] vector;

    public Embedding(String text, String description, String category, float[] vector) {
        this.text = text;
        this.description = description;
        this.category = category;
        this.vector = vector;
    }
}
