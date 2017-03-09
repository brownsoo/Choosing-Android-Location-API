package com.hansoolabs.test.locationupdate;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

/**
 *
 * Created by brownsoo on 2017. 2. 26..
 */

public class SplashActivity extends AppCompatActivity {

    private static final String TAG = "SplashActivity";
    private static final int PERMISSION_REQ_OVERLAY = 1;
    private static final int PERMISSION_REQ_LOCATION = 2;
    private static final int PERMISSION_REQ_EXTERNAL = 3;


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);
    }

    @Override
    protected void onStart() {
        super.onStart();

        if (tryOverlayPerm()) {
            if(tryLocationPerm()) {
                tryExternalPerm();
            }
        }
    }

    private boolean tryOverlayPerm() {
        if(Build.VERSION.SDK_INT >= 23) {
            if (!Settings.canDrawOverlays(this)) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, PERMISSION_REQ_OVERLAY);
                return false;
            }
        }
        return true;
    }

    private boolean tryLocationPerm() {
        if ((ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                || ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)) {
            requestPermissionLocation();
            return false;
        }
        return true;
    }

    private void requestPermissionLocation() {

        String[] perms = new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.ACCESS_COARSE_LOCATION};

        ActivityCompat.requestPermissions(this, perms, PERMISSION_REQ_LOCATION);
    }

    private void tryExternalPerm() {

        if ((ActivityCompat.checkSelfPermission(this, android.Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
                || ActivityCompat.checkSelfPermission(this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)) {
            String[] perms = new String[]{Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE};
            ActivityCompat.requestPermissions(this, perms, PERMISSION_REQ_EXTERNAL);
            return;
        }

        gotoMain();
    }

    private void gotoMain() {

        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                startActivity(new Intent(SplashActivity.this, MainActivity.class));
                finish();
            }
        }, 1000);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {

        Log.d(TAG, "onRequestPermissionsResult");

        switch(requestCode) {

            case PERMISSION_REQ_OVERLAY:
                if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    // 2nd
                    tryOverlayPerm();
                }
                break;

            case PERMISSION_REQ_LOCATION:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    tryExternalPerm();
                }
                break;
            case PERMISSION_REQ_EXTERNAL:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // 3nd
                    gotoMain();
                }
                break;
        }
    }
}
