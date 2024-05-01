package com.example.llmwithrag.datasource.connectivity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "connectivity_data")
public class ConnectivityData {
    @PrimaryKey(autoGenerate = true)
    public int id;
    public boolean connected;
    public boolean enterprise;
    public long timestamp;

    public ConnectivityData(boolean connected, boolean enterprise, long timestamp) {
        this.connected = connected;
        this.enterprise = enterprise;
        this.timestamp = timestamp;
    }
}
