package com.example.llmwithrag;

import static android.Manifest.permission.ACCESS_COARSE_LOCATION;
import static android.Manifest.permission.ACCESS_FINE_LOCATION;
import static android.Manifest.permission.CHANGE_WIFI_STATE;
import static android.Manifest.permission.FOREGROUND_SERVICE_LOCATION;
import static android.Manifest.permission.RECORD_AUDIO;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.Map;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "LlmWithRag";
    private ServiceViewModel mViewModel;
    private ActivityResultLauncher<String[]> mRequestPermissionLauncher;

    @SuppressLint("NonConstantResourceId")
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mViewModel = new ViewModelProvider(this).get(ServiceViewModel.class);
        BottomNavigationView navigationView = findViewById(R.id.navigation);
        if (savedInstanceState == null) {
            loadFragment(new StoreEmbeddingsFragment());
            navigationView.setSelectedItemId(R.id.navigation_store_embeddings);
        }

        navigationView.setOnItemSelectedListener(item -> {
            switch (item.getItemId()) {
                case R.id.navigation_store_embeddings:
                    if (!(getCurrentFragment() instanceof StoreEmbeddingsFragment)) {
                        loadFragment(new StoreEmbeddingsFragment());
                    }
                    return true;
                case R.id.navigation_perform_query:
                    if (!(getCurrentFragment() instanceof PerformQueryFragment)) {
                        loadFragment(new PerformQueryFragment());
                    }
                    return true;
                default:
                    return false;
            }
        });

        mRequestPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                    boolean allPermissionsGranted = true;
                    for (Map.Entry<String, Boolean> entry : result.entrySet()) {
                        if (!entry.getValue()) {
                            allPermissionsGranted = false;
                            break;
                        }
                    }

                    if (allPermissionsGranted) {
                        Context context = getApplicationContext();
                        Intent serviceIntent = new Intent(context, MonitoringService.class);
                        ContextCompat.startForegroundService(context, serviceIntent);

                        bindToMonitoringService();
                    } else {
                        Toast.makeText(getApplicationContext(),
                                "Permission Denied", Toast.LENGTH_SHORT).show();
                    }
                });
        updateService();
    }

    private void loadFragment(Fragment fragment) {
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.replace(R.id.fragment_container, fragment);
        transaction.commit();
    }

    private Fragment getCurrentFragment() {
        return getSupportFragmentManager().findFragmentById(R.id.fragment_container);
    }

    private void updateService() {
        try {
            String[] permissions = getPermissions();
            if (isPermissionGranted(permissions)) {
                bindToMonitoringService();
            } else {
                mRequestPermissionLauncher.launch(permissions);
            }
        } catch (Throwable e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
        }
    }

    @NonNull
    private String[] getPermissions() {
        String[] permissions;
        if (Build.VERSION.SDK_INT >= 34) {
            permissions = new String[]{
                    ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION, CHANGE_WIFI_STATE, RECORD_AUDIO,
                    FOREGROUND_SERVICE_LOCATION
            };
        } else {
            permissions = new String[]{
                    ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION, CHANGE_WIFI_STATE, RECORD_AUDIO
            };
        }
        return permissions;
    }

    private boolean isPermissionGranted(String[] permissions) {
        if (permissions == null || permissions.length == 0) return false;
        Context context = getApplicationContext();
        if (context == null) return false;
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(context, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private void bindToMonitoringService() {
        if (mViewModel.getService().getValue() != null) return;
        Context context = getApplicationContext();
        Intent intent = new Intent(context, MonitoringService.class);
        context.bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
    }

    private final ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder binder) {
            Log.i(TAG, "connected to the service");
            mViewModel.setService(((MonitoringService.LocalBinder) binder).getService());
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            Log.i(TAG, "disconnected from the service");
            mViewModel.setService(null);
            updateService();
        }
    };
}