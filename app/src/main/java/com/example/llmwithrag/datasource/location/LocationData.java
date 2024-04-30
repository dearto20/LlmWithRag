package com.example.llmwithrag.datasource.location;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "location_data")
public class LocationData {
    @PrimaryKey(autoGenerate = true)
    public int id;
    public double latitude;
    public double longitude;
    public long timestamp;

    public LocationData(double latitude, double longitude, long timestamp) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.timestamp = timestamp;
    }
}
