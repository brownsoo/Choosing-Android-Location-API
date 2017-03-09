package com.hansoolabs.test.locationupdate;

import android.app.IntentService;
import android.content.Intent;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.google.android.gms.location.ActivityRecognitionResult;
import com.google.android.gms.location.DetectedActivity;

/**
 *
 * Created by brownsoo on 2017. 2. 26..
 */

public class MyRecognitionIntentService extends IntentService {

    private static final String TAG = "ActivityRecognition";
    public static final String ACTION_DETECTED_ACTIVITY = "ActionDetectedActivity";
    public static final String EXTRA_DETECTED_ACTIVITY = "DetectedActivity";

    public MyRecognitionIntentService() {
        super(TAG);
    }

    @Override
    protected void onHandleIntent(Intent intent) {

        if(ActivityRecognitionResult.hasResult(intent)) {
            DetectedActivity activity = ActivityRecognitionResult.extractResult(intent).getMostProbableActivity();
            Log.d(TAG, "DetectedActivity -- " + activity.toString());

            Intent i = new Intent();
            i.setAction(ACTION_DETECTED_ACTIVITY);
            i.putExtra(EXTRA_DETECTED_ACTIVITY, activity);
            LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(i);
        }

    }
}
