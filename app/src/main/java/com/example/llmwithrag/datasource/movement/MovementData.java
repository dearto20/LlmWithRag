package com.example.llmwithrag.datasource.movement;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "movement_data")
public class MovementData {
    @PrimaryKey(autoGenerate = true)
    public int id;
    public float x;
    public float y;
    public float z;
    public long timestamp;

    public MovementData(float x, float y, float z, long timestamp) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.timestamp = timestamp;
    }
}
