package com.hansoolabs.test.locationupdate;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.FragmentTransaction;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.PixelFormat;
import android.location.GpsSatellite;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.Editable;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.ActivityRecognition;
import com.google.android.gms.location.DetectedActivity;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.GoogleMapOptions;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.hansoolabs.test.locationupdate.events.EmailEvent;
import com.hansoolabs.test.locationupdate.events.FusedIntervalEvent;
import com.hansoolabs.test.locationupdate.events.OverlayEvent;
import com.hansoolabs.test.locationupdate.events.SourceEvent;
import com.hansoolabs.test.locationupdate.utils.ContextUtils;
import com.hansoolabs.test.locationupdate.utils.LocationStabilizer;
import com.squareup.otto.Bus;
import com.squareup.otto.Subscribe;
import com.squareup.otto.ThreadEnforcer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.zip.Adler32;
import java.util.zip.CheckedOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.android.LogcatAppender;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.FileAppender;
import ch.qos.logback.core.util.StatusPrinter;

public class MainActivity extends AppCompatActivity implements
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        OnMapReadyCallback {

    public static final String SRC_FUSED = "fused";
    public static final String SRC_PLATFORM = "plat";

    private static final String TAG = "MainActivity";
    private static final int TWO_MINUTES = 1000 * 60 * 2;
    private static final int REQUEST_RESOLVE_ERROR = 1001;

    private int fusedInterval = 1000;
    private int fusedFastestInterval = 1000;
    private static final int WHAT_MESSAGE_LOOP = 100;
    private static final int MAX_LOCATION_COUNT = 20;

    private TextView trace;
    private Logger logger;
    private Bus bus;
    private OverlayView testOverlayView;
    private LocationManager locationManager;
    private Location bestLocation = null;
    private long receiveTime = 0;
    private GoogleApiClient googleApiClient;
    private GoogleMap googleMap;
    private boolean resolvingError;
    private boolean isLocationTrackingStarted = false;
    private int movingMapDelayCount = 0;
    private Marker marker;
    private Marker markerStable;
    private String currentSource;
    private String dot = "";
    private ConnectivityManager cm;
    private int gpsTotalCount = 0;
    private int gpsUsedCount = 0;
    private Object gnssStatusCallback;
    private LatLng newLatlng;
    private List<Polyline> polyLines;
    private List<Polyline> polyLinesStabilized;
    private List<LatLng> positions;
    private List<LatLng> stablePositions;
    private ProgressBar progressBar;
    private LocationStabilizer stabilizer;


    private Handler handler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(Message message) {

            if (MainActivity.this.isFinishing()) {
                return false;
            }

            switch (message.what) {
                case WHAT_MESSAGE_LOOP:
                    dot += ".";
                    bus.post(new OverlayEvent(OverlayEvent.Field.Source, currentSource + dot));

                    if (movingMapDelayCount > 0) movingMapDelayCount--;

                    handler.sendEmptyMessageDelayed(WHAT_MESSAGE_LOOP, 1000);

                    return true;
            }

            return false;
        }
    });


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        progressBar = (ProgressBar)findViewById(R.id.progressBar);
        progressBar.setVisibility(View.GONE);

        positions = new LinkedList<>();
        stablePositions = new LinkedList<>();
        polyLines = new LinkedList<>();
        polyLinesStabilized = new LinkedList<>();
        stabilizer = new LocationStabilizer();
        stabilizer.setUseSpeedCheck(true);
        stabilizer.setUseDistanceCheck(false);

        bus = new Bus(ThreadEnforcer.ANY);
        bus.register(this);

        configureLogback();
        logger = LoggerFactory.getLogger("LocationTest");

        trace = (TextView) findViewById(R.id.trace_tv);
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        currentSource = getCurrentSource();
        cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);

        initMapView();

        handler.sendEmptyMessageDelayed(WHAT_MESSAGE_LOOP, 1000);

        logger.debug("----------------------------------------------------------------------");
        logger.debug("----------onCreate() " + Build.MODEL);


    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.d(TAG, "onStart()");

        if (tryStartOverlay()) {
            tryLocationUpdate();
        }
    }

    @Override
    protected void onDestroy() {
        stopGpsStatusListener();
        stopFusedLocationUpdate();
        stopPlatformLocationUpdate();
        destroyTestOverlay();
        stopActivityRecognition();
        releaseGoogleApiClient();
        bus.unregister(this);
        handler.removeMessages(WHAT_MESSAGE_LOOP);

        logger.debug("----------onDestroy()");
        logger.debug("----------------------------------------------------------------------");
        super.onDestroy();
    }


    private boolean tryStartOverlay() {
        if (Build.VERSION.SDK_INT >= 23) {
            if (!Settings.canDrawOverlays(this)) {
                return false;
            }
        }

        initOverlay();
        return true;
    }

    private void tryLocationUpdate() {
        if ((ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                || ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)) {
            return;
        }

        startGpsStatusListener();
        if(!checkConnectionGoogleApiClient()) {
            googleApiClient.connect();
        }
        else {
            doGrantedLocationUpdate();
        }
    }

    private void doGrantedLocationUpdate() {
        logger.info("doGrantedLocationUpdate -- " + currentSource);

        bus.post(new SourceEvent(currentSource));
        if (currentSource.equals(SRC_FUSED)) {
            startFusedLocationUpdate();
        }
        else {
            startPlatformLocationUpdate();
        }
    }


    private String getCurrentSource() {
        SharedPreferences preferences = getSharedPreferences("setting", MODE_PRIVATE);
        return preferences.getString("source", SRC_FUSED);
    }

    private void setCurrentSource(String src) {
        currentSource = src;
        SharedPreferences preferences = getSharedPreferences("setting", MODE_PRIVATE);
        preferences.edit().putString("source", src).apply();
    }

    @Subscribe
    public void toggleCurrentSource(ToggleSourceEvent event) {

        if (!isLocationTrackingStarted) return;

        trace.setText("");

        if (currentSource.equals(SRC_FUSED)) {
            stopFusedLocationUpdate();
            setCurrentSource(SRC_PLATFORM);
        }
        else {
            stopPlatformLocationUpdate();
            setCurrentSource(SRC_FUSED);
        }


        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                tryLocationUpdate();
            }
        }, 500);
    }


    @Subscribe
    public void changeFusedInterval(FusedIntervalEvent event) {

        if (currentSource.equals(SRC_FUSED)) {
            stopFusedLocationUpdate();
            fusedInterval = event.milis;
            fusedFastestInterval = event.milis;
            startFusedLocationUpdate();
        }

    }

    @Subscribe
    public void sendEmail(EmailEvent event) {
        sendEmail();
    }



    private void initMapView() {

        GoogleMapOptions options = new GoogleMapOptions();
        options.mapType(GoogleMap.MAP_TYPE_NORMAL);

        MapFragment mapFragment = MapFragment.newInstance(options);
        FragmentTransaction fragmentTransaction =
                getFragmentManager().beginTransaction();
        fragmentTransaction.add(R.id.map_container, mapFragment);
        fragmentTransaction.commit();
        mapFragment.getMapAsync(this);

    }

    @Override
    public void onMapReady(GoogleMap googleMap) {

        LatLng seoul = new LatLng(37.532600, 127.024612);//first location
        this.googleMap = googleMap;
        this.marker = googleMap.addMarker(new MarkerOptions()
                .position(seoul));
        this.markerStable = googleMap.addMarker(new MarkerOptions()
                .position(seoul)
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
        );

        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(seoul, 18));
        googleMap.setOnMapClickListener(new GoogleMap.OnMapClickListener() {
            @Override
            public void onMapClick(LatLng latLng) {
                movingMapDelayCount = 6;
            }
        });

    }

    private void drawMarker(double lat, double lon, boolean stable) {

        if (movingMapDelayCount <= 0 && stable) {
            LatLngBounds bounds = new LatLngBounds(
                    new LatLng(lat - 1, lon - 1),
                    new LatLng(lat + 1, lon + 1));
            googleMap.animateCamera(CameraUpdateFactory.newLatLng(bounds.getCenter()));
        }

        if (stable) {
            markerStable.setPosition(new LatLng(lat, lon));
        }
        else {
            marker.setPosition(new LatLng(lat, lon));
        }
    }

    private void drawLine(boolean stable) {

        List<LatLng> pos;
        List<Polyline> lines;
        int color;

        if (stable) {
            pos = stablePositions;
            lines = polyLinesStabilized;
            color = 0x00ff00;
        }
        else {
            pos = positions;
            lines = polyLines;
            color = 0xff0000;
        }

        if (pos.size() > MAX_LOCATION_COUNT) {
            pos.remove(0);
        }

        if (lines.size() > MAX_LOCATION_COUNT - 1) {
            lines.remove(0);
        }

        if (pos.size() > 1) {
            int length = pos.size() - 1;
            int delta = 255 / length;
            int alpha;
            int lineColor;

            for (int i = 0; i < length; i++) {
                alpha = delta * (i+1);
                lineColor = (alpha << 24) | color;
                if (lines.size() < i+1) {
                    PolylineOptions options = new PolylineOptions()
                            .color(lineColor)
                            .width(5f);
                    options.add(pos.get(i));
                    options.add(pos.get(i+1));
                    Polyline line = googleMap.addPolyline(options);
                    lines.add(line);
                }
                else {
                    List<LatLng> list = new ArrayList<>();
                    list.add(pos.get(i));
                    list.add(pos.get(i+1));
                    lines.get(i).setPoints(list);
                    lines.get(i).setColor(lineColor);
                }
            }
        }

    }

    private void scrollToBottom(TextView textView) {
        int top = textView.getLayout().getLineTop(textView.getLineCount());
        int scrollY = top - textView.getHeight();
        if (scrollY > 0) {
            textView.scrollTo(0, scrollY);
        }
    }


    private boolean checkConnectionGoogleApiClient() {

        if (googleApiClient == null) {

            googleApiClient = new GoogleApiClient.Builder(this)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .addApi(LocationServices.API)
                    .addApi(ActivityRecognition.API)
                    .build();
            return false;
        }

        return googleApiClient.isConnected() || googleApiClient.isConnecting();
    }

    private void releaseGoogleApiClient() {
        if (googleApiClient.isConnected()) {
            googleApiClient.disconnect();
        }
        googleApiClient = null;
    }

    private void onConnectedAPIClient() {
        doGrantedLocationUpdate();
        startActivityRecognition();
    }


    private void startGpsStatusListener() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                locationManager.registerGnssStatusCallback((android.location.GnssStatus.Callback)getGnssStatusCallback());
                logger.info("registerGnssStatusCallback");
            }
            else {
                //noinspection deprecation
                locationManager.addGpsStatusListener(gpsStatusListener);
                logger.info("addGpsStatusListener");
            }
        }
        catch (SecurityException ex) {
            logger.error("startGpsStatusListener", ex);
        }
    }

    private void stopGpsStatusListener() {

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                locationManager.unregisterGnssStatusCallback((android.location.GnssStatus.Callback)getGnssStatusCallback());
                logger.info("unregisterGnssStatusCallback");
            }
            else {
                //noinspection deprecation
                locationManager.removeGpsStatusListener(gpsStatusListener);
                logger.info("removeGpsStatusListener");
            }
        }
        catch (SecurityException ex) {
            logger.error("stopGpsStatusListener", ex);
        }
    }

    @SuppressWarnings("deprecation")
    private android.location.GpsStatus.Listener gpsStatusListener = new GpsStatus.Listener() {
        @SuppressWarnings("MissingPermission")
        @Override
        public void onGpsStatusChanged(int event) {
            switch (event) {
                case GpsStatus.GPS_EVENT_SATELLITE_STATUS:
                    int satellites = 0;
                    int satellitesInFix = 0;
                    for (GpsSatellite sat : locationManager.getGpsStatus(null).getSatellites()) {
                        if(sat.usedInFix()) {
                            satellitesInFix++;
                        }
                        satellites++;
                    }
                    reportGpsCount(satellites, satellitesInFix, false);
                    break;
            }
        }
    };


    @SuppressLint("NewApi")
    private Object getGnssStatusCallback() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            if (gnssStatusCallback == null) {
                gnssStatusCallback = new android.location.GnssStatus.Callback() {
                    @Override
                    public void onFirstFix(int ttffMillis) {
                        super.onFirstFix(ttffMillis);
                    }

                    @Override
                    public void onSatelliteStatusChanged(android.location.GnssStatus status) {
                        super.onSatelliteStatusChanged(status);
                        int total = status.getSatelliteCount();
                        int used = 0;
                        for (int i = 0; i < total; i++) {
                            if (status.usedInFix(i)) used++;
                        }
                        reportGpsCount(total, used, true);
                    }
                };
            }
        }
        return gnssStatusCallback;
    }


    private void reportGpsCount(int total, int used, boolean useGnss) {

        if (gpsTotalCount != total || gpsUsedCount != used) {
            gpsTotalCount = total;
            gpsUsedCount = used;
            String gnss = useGnss ? "" : "gnss";
            String text = "total=" + total + "/used=" + used + " - -" + gnss;

            bus.post(new OverlayEvent(OverlayEvent.Field.GPS, text));
            logger.info("Gps count = " + text);
        }
    }

    @SuppressLint("RtlHardcoded")
    private void initOverlay() {

        if (testOverlayView == null) {
            testOverlayView = new OverlayView(this, bus);
            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_PHONE,//항상 최 상위. 터치 이벤트 받을 수 있음.
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,  //포커스를 가지지 않음
                    PixelFormat.TRANSLUCENT
            );
            params.gravity = Gravity.TOP|Gravity.START|Gravity.LEFT;
            params.setTitle("Test Overlay");
            WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
            wm.addView(testOverlayView, params);
        }

    }

    private void destroyTestOverlay() {
        if (testOverlayView != null) {
            WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
            wm.removeView(testOverlayView);
            testOverlayView = null;
        }
    }


    private void setFirstLocation(double latitude, double longitude) {
        drawMarker(latitude, longitude, true);
        drawMarker(latitude, longitude, false);
    }


    @Override
    public void onConnected(@Nullable Bundle bundle) {

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        Location location = LocationServices.FusedLocationApi.getLastLocation(googleApiClient);
        if (location != null) {
            setFirstLocation(location.getLatitude(), location.getLongitude());
        }

        onConnectedAPIClient();
    }


    @Override
    public void onConnectionSuspended(int i) {}

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        if (!resolvingError) {
            if (connectionResult.hasResolution()) {
                try {
                    resolvingError = true;
                    connectionResult.startResolutionForResult(this, REQUEST_RESOLVE_ERROR);
                } catch (IntentSender.SendIntentException ignored) {
                    googleApiClient.connect();
                }
            } else {
                showErrorDialog(connectionResult.getErrorCode(), connectionResult.getErrorMessage());
                resolvingError = true;
            }
        }
    }

    private void showErrorDialog(int errorCode, String message ) {

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(String.valueOf(errorCode))
            .setMessage(message)
            .create().show();
    }





    ////////////
    // FUSED PROVIDER


    private void startFusedLocationUpdate() {

        logger.info(TAG, "startFusedLocationUpdate");
        if (isLocationTrackingStarted) {
            return;
        }
        logger.debug(TAG, "startFusedLocationUpdate -- ");

        LocationRequest request = new LocationRequest()
                .setInterval(fusedInterval)
                .setFastestInterval(fusedFastestInterval)
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        String text = "normal:" + fusedInterval + " / fastest:" + fusedFastestInterval;
        bus.post(new OverlayEvent(OverlayEvent.Field.Setting, text));
        logger.info(text);

        receiveTime = System.currentTimeMillis();

        LocationServices.FusedLocationApi.requestLocationUpdates(
                googleApiClient,
                request,
                fusedLocationListener);

        isLocationTrackingStarted = true;

    }

    private LocationListener fusedLocationListener = new LocationListener() {
        @Override
        public void onLocationChanged(Location location) {
            if (location != null) {
                traceLocation(location);
            }
        }
    };


    private void stopFusedLocationUpdate() {

        logger.info("stopFusedLocationUpdate");
        if (googleApiClient != null) {
            LocationServices.FusedLocationApi.removeLocationUpdates(googleApiClient, fusedLocationListener);
        }
        isLocationTrackingStarted = false;
    }


    private void startActivityRecognition() {
        logger.info("startActivityRecognition");
        if (googleApiClient != null) {
            Intent activityIntent = new Intent(getApplicationContext(), MyRecognitionIntentService.class);
            PendingIntent pendingActivityIntent = PendingIntent.getService(this, 1, activityIntent, 0);
            ActivityRecognition.ActivityRecognitionApi.requestActivityUpdates(
                    googleApiClient,
                    1000,
                    pendingActivityIntent
            );
        }

        LocalBroadcastManager.getInstance(getApplicationContext())
                .registerReceiver(activityBroadcastReceiver, new IntentFilter(MyRecognitionIntentService.ACTION_DETECTED_ACTIVITY));
    }

    private void stopActivityRecognition() {
        logger.info("stopActivityRecognition");
        if (googleApiClient != null) {
            Intent activityIntent = new Intent(getApplicationContext(), MyRecognitionIntentService.class);
            PendingIntent pendingIntent = PendingIntent.getService(this, 1, activityIntent, 0);
            if(pendingIntent != null) {
                pendingIntent.cancel();
            }
            ActivityRecognition.ActivityRecognitionApi.removeActivityUpdates(googleApiClient, pendingIntent);
        }

        LocalBroadcastManager.getInstance(getApplicationContext())
                .unregisterReceiver(activityBroadcastReceiver);
    }

    private BroadcastReceiver activityBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.hasExtra(MyRecognitionIntentService.EXTRA_DETECTED_ACTIVITY)) {

                DetectedActivity detected = intent.getParcelableExtra(MyRecognitionIntentService.EXTRA_DETECTED_ACTIVITY);
                logger.debug(detected.toString());
                bus.post(new OverlayEvent(OverlayEvent.Field.Activity,
                        "Type=" + DetectedActivity.zzkf(detected.getType())
                        + "/Confidence="+ detected.getConfidence()));
            }
        }
    };

    /** Determines whether one Location reading is better than the current Location fix
     * @param location  The new Location that you want to evaluate
     * @param currentBestLocation  The current Location fix, to which you want to compare the new one
     */
    protected boolean isBetterLocation(Location location, Location currentBestLocation) {
        if (currentBestLocation == null) {
            // A new location is always better than no location
            return true;
        }

        // Check whether the new location fix is newer or older
        long timeDelta = location.getTime() - currentBestLocation.getTime();
        boolean isSignificantlyNewer = timeDelta > TWO_MINUTES;
        boolean isSignificantlyOlder = timeDelta < -TWO_MINUTES;
        boolean isNewer = timeDelta > 0;

        // If it's been more than two minutes since the current location, use the new location
        // because the user has likely moved
        if (isSignificantlyNewer) {
            return true;
            // If the new location is more than two minutes older, it must be worse
        } else if (isSignificantlyOlder) {
            return false;
        }

        // Check whether the new location fix is more or less accurate
        int accuracyDelta = (int) (location.getAccuracy() - currentBestLocation.getAccuracy());
        boolean isLessAccurate = accuracyDelta > 0;
        boolean isMoreAccurate = accuracyDelta < 0;
        boolean isSignificantlyLessAccurate = accuracyDelta > 200;

        // Check if the old and new location are from the same provider
        boolean isFromSameProvider = isSameProvider(location.getProvider(),
                currentBestLocation.getProvider());

        // Determine location quality using a combination of timeliness and accuracy
        if (isMoreAccurate) {
            return true;
        } else if (isNewer && !isLessAccurate) {
            return true;
        } else if (isNewer && !isSignificantlyLessAccurate && isFromSameProvider) {
            return true;
        }
        return false;
    }

    /** Checks whether two providers are the same */
    private boolean isSameProvider(String provider1, String provider2) {
        if (provider1 == null) {
            return provider2 == null;
        }
        return provider1.equals(provider2);
    }


    @SuppressWarnings("MissingPermission")
    private void startPlatformLocationUpdate() {

        logger.info(TAG, "startPlatformLocationUpdate");
        if (isLocationTrackingStarted) return;
        logger.debug(TAG, "startPlatformLocationUpdate -- ");

        receiveTime = System.currentTimeMillis();
        
        long minTime = 1000;
        float minDis = 1;
        long minTimeNetwork = 1000;
        float minDisNetwork = 1;
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, minTime, minDis, locationListener);
        locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, minTime, minDis, locationListener);

        logger.info("startPlatformLocationUpdate");
        String text = "GPS: minTime=" + minTime + "/ minDistance:" + minDis + " --- Network: minTime="+minTimeNetwork + "/ minDistance:" + minDisNetwork;
        logger.info(text);
        bus.post(new OverlayEvent(OverlayEvent.Field.Setting, text));

        isLocationTrackingStarted = true;
    }

    @SuppressWarnings("MissingPermission")
    private void stopPlatformLocationUpdate() {
        locationManager.removeUpdates(locationListener);
        isLocationTrackingStarted = false;
    }


    android.location.LocationListener locationListener = new android.location.LocationListener() {
        public void onLocationChanged(Location location) {
            if (bestLocation == null) {
                if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                        && ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    return;
                }
                bestLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            }

            if (isBetterLocation(location, bestLocation)) {
                bestLocation = location;
            }
            traceLocation(bestLocation);
        }

        public void onStatusChanged(String provider, int status, Bundle extras) {}

        public void onProviderEnabled(String provider) {}

        public void onProviderDisabled(String provider) {}
    };


    private float distanceBetween(double lat0, double lon0, double lat1, double lon1) {
        try {
            float[] results = new float[3];
            Location.distanceBetween(lat0, lon0, lat1, lon1, results);
            return results[0];
        }
        catch (Throwable e) {
            // pass
        }
        return -1;
    }


    private void traceLocation(final Location location) {
        final long elapsed = System.currentTimeMillis() - receiveTime;
        receiveTime = System.currentTimeMillis();
        dot = "";
        bus.post(new OverlayEvent(OverlayEvent.Field.Provider, String.valueOf(location.getProvider())));
        bus.post(new OverlayEvent(OverlayEvent.Field.Accuracy, String.valueOf(location.getAccuracy())));
        bus.post(new OverlayEvent(OverlayEvent.Field.Elapsed, String.valueOf(elapsed)));

        runOnUiThread(new Runnable() {
            @Override
            public void run() {

                float distance = -1;
                if (newLatlng != null) {
                    distance = distanceBetween(newLatlng.latitude, newLatlng.longitude,
                            location.getLatitude(), location.getLongitude());
                }

                newLatlng = new LatLng(location.getLatitude(), location.getLongitude());
                String distanceString = String.format(Locale.US, "%.1f", distance);

                SimpleDateFormat dateFormat = new SimpleDateFormat("MM/dd HH:mm:ss", Locale.US);
                String text = dateFormat.format(
                        new Date(receiveTime)) + "  "
                        + elapsed + " /"
                        + location.getAccuracy() + "ac /"
                        + distanceString + "m /"
                        + currentSource + " /"
                        + activeNetworkTypeName();
                logger.debug(text);
                logger.debug(String.format(Locale.US, "lat:%f / lon:%f", location.getLatitude(), location.getLongitude()));
                trace.append(text + "\n");

                if (trace.getLineCount() > 30) {
                    Editable editable = trace.getEditableText();
                    int start = trace.getLayout().getLineStart(0);
                    int end = trace.getLayout().getLineEnd(0);
                    editable.delete(start, end);
                }

                scrollToBottom(trace);

                // Draw raw data
                positions.add(newLatlng);
                drawLine(false);
                drawMarker(location.getLatitude(), location.getLongitude(), false);


                // Stabilize data
                if(stabilizer.isStableLocation(location)) {
                    stablePositions.add(new LatLng(location.getLatitude()+0.00003, location.getLongitude()+0.00003));
                    drawLine(true);
                    drawMarker(location.getLatitude()+0.00003, location.getLongitude()+0.00003, true);
                }
                else {
                    logger.debug("------> ------> No Stable data");

                }
            }
        });


    }






    private String activeNetworkTypeName() {
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        return (activeNetwork != null) ? activeNetwork.getTypeName() : "null";
    }


    /////

    private File logDir() {
        File downloadFolder =
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);

        return new File(downloadFolder, "logs");
    }

    private void configureLogback() {

        LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();
        lc.reset();

        /*
        // Rolling
        RollingFileAppender<ILoggingEvent> rollingFileAppender = new RollingFileAppender<>();
        rollingFileAppender.setAppend(true);
        rollingFileAppender.setContext(lc);
        rollingFileAppender.setFile(new File(logDir, "active_log.txt").getAbsolutePath());

        TimeBasedRollingPolicy<ILoggingEvent> rollingPolicy = new TimeBasedRollingPolicy<>();
        rollingPolicy.setFileNamePattern(logDir + "/log.%d.txt");
        rollingPolicy.setMaxHistory(7);
        rollingPolicy.setParent(rollingFileAppender);  // parent and context required!
        rollingPolicy.setContext(lc);
        rollingPolicy.start();

        rollingFileAppender.setRollingPolicy(rollingPolicy);

        // FileAppender
        PatternLayoutEncoder encoder1 = new PatternLayoutEncoder();
        encoder1.setContext(lc);
        encoder1.setPattern("%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n");
        encoder1.start();


        //rollingFileAppender.setFile(getFileStreamPath("loc.log").getAbsolutePath());
        rollingFileAppender.setEncoder(encoder1);
        rollingFileAppender.start();
        */



        // Single FileAppender
        PatternLayoutEncoder encoder1 = new PatternLayoutEncoder();
        encoder1.setContext(lc);
        encoder1.setPattern("%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n");
        encoder1.start();

        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy_MM_dd-HH:mm:ss", Locale.US);
        String logFileName = String.format("log-%s.txt", dateFormat.format(new Date()));
        FileAppender<ILoggingEvent> fileAppender = new FileAppender<>();
        fileAppender.setContext(lc);
        fileAppender.setFile(new File(logDir(), logFileName).getAbsolutePath());
        fileAppender.setEncoder(encoder1);
        fileAppender.start();

        // Logcat
        PatternLayoutEncoder encoder2 = new PatternLayoutEncoder();
        encoder2.setContext(lc);
        encoder2.setPattern("[%thread] %msg%n");
        encoder2.start();

        LogcatAppender logcatAppender = new LogcatAppender();
        logcatAppender.setContext(lc);
        logcatAppender.setEncoder(encoder2);
        logcatAppender.start();

        // add the newly created appenders to the root logger;
        // qualify Logger to disambiguate from org.slf4j.Logger
        ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        root.addAppender(fileAppender);
        root.addAppender(logcatAppender);

        StatusPrinter.print(lc);

    }


    private void sendEmail() {
        progressBar.setVisibility(View.VISIBLE);
        zipFiles();
    }

    private void zipFiles() {

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {

                    final int BUFFER = 2048;

                    BufferedInputStream origin;
                    File downloadFolder =
                            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                    File zipDir = new File(downloadFolder, "log-archive");
                    if (!zipDir.exists()) {
                        //noinspection ResultOfMethodCallIgnored
                        zipDir.mkdir();
                    }
                    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy_MM_dd-HH:mm:ss", Locale.US);
                    String zipFileName = String.format("log-%s.zip", dateFormat.format(new Date()));
                    final File zipFile = new File(zipDir, zipFileName);
                    FileOutputStream dest = new FileOutputStream(zipFile);
                    CheckedOutputStream checksum = new CheckedOutputStream(dest, new Adler32());
                    ZipOutputStream out = new ZipOutputStream(new BufferedOutputStream(checksum));
                    //out.setMethod(ZipOutputStream.STORED); // not compressed
                    byte data[] = new byte[BUFFER];

                    File[] files  = logDir().listFiles();

                    for (File file : files) {
                        Log.d(TAG, "Zip Adding: " + file);
                        FileInputStream fi = new FileInputStream(file);
                        origin = new BufferedInputStream(fi, BUFFER);
                        ZipEntry entry = new ZipEntry(file.getPath());
                        out.putNextEntry(entry);
                        int count;
                        while ((count = origin.read(data, 0, BUFFER)) != -1) {
                            out.write(data, 0, count);
                        }
                        origin.close();


                    }
                    out.close();
                    Log.d(TAG, "checksum: "+checksum.getChecksum().getValue());

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            progressBar.setVisibility(View.GONE);
                            handleWithEmailDialog(MainActivity.this, zipFile);
                        }
                    });


                } catch(Exception e) {
                    e.printStackTrace();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(MainActivity.this,"Error on making a zip.", Toast.LENGTH_LONG).show();
                            progressBar.setVisibility(View.GONE);
                        }
                    });
                }
            }
        }).start();
    }


    private void handleWithEmailDialog(final Context context, final File file) {

        if(context == null) return;

        final StringBuffer sb = new StringBuffer();

        sb.append("\n");
        sb.append("\n");
        sb.append("os:").append(ContextUtils.getOsVersion()).append("\n");
        sb.append("operator name:").append(ContextUtils.getCellOperatorName(context)).append("\n");
        sb.append("device model:").append(ContextUtils.getDeviceModel()).append("\n");
        sb.append("connection:").append(ContextUtils.getConnectionInfo(context)).append("\n");



        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(context);
        builder.setMessage("로그를 메일로 보내주시겠습니까?")
                .setPositiveButton("메일보내기", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        Intent intent = new Intent(Intent.ACTION_SENDTO, Uri.fromParts(
                                "mailto", "hyunsoo.han@meshkorea.net", null));
                        intent.putExtra(Intent.EXTRA_SUBJECT, "로그 보냄");
                        intent.putExtra(Intent.EXTRA_TEXT, ("\n\n" + sb.toString()));
                        Uri uri = Uri.fromFile(file);
                        intent.putExtra(Intent.EXTRA_STREAM, uri);
                        context.startActivity(Intent.createChooser(intent, "Send email..."));
                    }
                })
                .setNegativeButton("닫기", null)
                .create()
                .show();

    }

}
