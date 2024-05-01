package com.example.llmwithrag;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigation.NavigationBarView;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "LlmWithRag";

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        BottomNavigationView navigationView = findViewById(R.id.navigation);
        navigationView.setOnItemSelectedListener(new NavigationBarView.OnItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                Fragment selectedFragment = null;
                if (item.getItemId() == R.id.navigation_store_embeddings) {
                    selectedFragment = new StoreEmbeddingsFragment();
                } else if (item.getItemId() == R.id.navigation_perform_query) {
                    selectedFragment = new PerformQueryFragment();
                } else {
                    Log.e(TAG, "invalid item id: " + item.getItemId());
                    return false;
                }

                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, selectedFragment)
                        .commit();
                return true;
            }
        });

        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, new StoreEmbeddingsFragment())
                    .commit();
            navigationView.setSelectedItemId(R.id.navigation_store_embeddings);
        }

        Intent serviceIntent = new Intent(this, MonitoringService.class);
        ContextCompat.startForegroundService(this, serviceIntent);
    }
}