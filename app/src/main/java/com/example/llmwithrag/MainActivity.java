package com.example.llmwithrag;

import static android.Manifest.permission.ACCESS_COARSE_LOCATION;
import static android.Manifest.permission.ACCESS_FINE_LOCATION;
import static android.Manifest.permission.CHANGE_WIFI_STATE;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigation.NavigationBarView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "LlmWithRag";
    private static final int PERMISSIONS_REQUEST_CODE = 1024;

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
        checkAndRequestPermissions();
    }

    private void checkAndRequestPermissions() {
        String[] permissions = {
                ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION, CHANGE_WIFI_STATE,
        };
        List<String> missingPermissions = new ArrayList<>();
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PERMISSION_GRANTED) {
                missingPermissions.add(permission);
            }
        }

        if (!missingPermissions.isEmpty()) {
            ActivityCompat.requestPermissions(this, missingPermissions.toArray(new String[0]),
                    PERMISSIONS_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            Map<String, Integer> perms = new HashMap<>();
            perms.put(ACCESS_FINE_LOCATION, PERMISSION_GRANTED);
            perms.put(ACCESS_COARSE_LOCATION, PERMISSION_GRANTED);
            perms.put(CHANGE_WIFI_STATE, PERMISSION_GRANTED);

            if (grantResults.length > 0) {
                for (int i = 0; i < permissions.length; i++) {
                    perms.put(permissions[i], grantResults[i]);
                }

                if (perms.get(ACCESS_FINE_LOCATION) != PERMISSION_GRANTED ||
                        perms.get(ACCESS_COARSE_LOCATION) != PERMISSION_GRANTED) {
                    Toast.makeText(getApplicationContext(), "permission request failed",
                            Toast.LENGTH_SHORT).show();
                }
            }
        }
    }
}