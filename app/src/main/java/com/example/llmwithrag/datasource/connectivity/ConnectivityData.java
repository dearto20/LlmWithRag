package com.example.llmwithrag.datasource.connectivity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "connectivity_data")
public class ConnectivityData {
    @PrimaryKey(autoGenerate = true)
    public int id;
    public String ssid;
    public String bssid;
    public String capabilities;
    public long timestamp;

    public ConnectivityData(String ssid, String bssid, String capabilities, long timestamp) {
        this.ssid = ssid;
        this.bssid = bssid;
        this.capabilities = capabilities;
        this.timestamp = timestamp;
    }
}
