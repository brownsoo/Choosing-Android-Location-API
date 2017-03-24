package com.hansoolabs.test.locationupdate;

import android.location.Location;
import android.support.test.runner.AndroidJUnit4;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.hansoolabs.test.locationupdate.stabilizer.LocationStabilizer;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;

/**
 *
 * Created by brownsoo on 2017. 3. 15..
 */
@RunWith(AndroidJUnit4.class)
public class LocationStabilizerTest {

    private static final String TAG = "LocationStabilizerTest";

    @Test
    public void locationStabilizer_isFixingDataSR380() {

        LocationStabilizer.LevelledLocation levelledLocation;

        LocationStabilizer stabilizer = new LocationStabilizer();
        stabilizer.setUseSpeedCheck(false);
        stabilizer.setUseDistanceCheck(true);

        BufferedReader reader = new BufferedReader(new StringReader("RAW DATA")); //TODO raw data
        String line;
        Gson gson = new GsonBuilder()
                .setDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
                .create();
        try {
            while ((line = reader.readLine()) != null) {
                try {
                    LocationTestData data = gson.fromJson(line, LocationTestData.class);
                    Location location = new Location("fused");
                    location.setLatitude(data.getLatitude());
                    location.setLongitude(data.getLongitude());
                    location.setTime(data.getTimestamp().getTime());

                    if (!stabilizer.isStableLocation(location)) {
                        Log.d(TAG, location.getLatitude() + "/" + location.getLongitude());
                    }

                } catch (JsonSyntaxException e) {
                    e.printStackTrace();
                    break;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

    }





}
