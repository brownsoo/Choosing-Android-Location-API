package com.hansoolabs.test.locationupdate.stabilizer;

import android.location.Location;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

/**
 * Location Stabilizer
 * Created by brownsoo on 2017. 3. 9..
 */

public class LocationStabilizer {

    private static final String TAG = "LocationStabilizer";

    private static final int BAD_ACCURACY_THRESHOLD = 350; // Terrible 레벨을 정하기 위한 Accuracy
    private static final int MAX_HISTORY_COUNT = 10; // 기억하고 있는 최근 위치값 최대 갯수
    private static final int DISTANCE_THRESHOLD = 3000; // 단위 m, 정상 거리로 보는 한계값
    private static final int DISTANCE_TIME_CLUE = 30000; // 단위 millis, 이전 위치와 거리를 계산할 수 있는 시간차 한계값
    private static final int SPEED_THRESHOLD = 30; // 단위 m/s, 정상 속도로 보는 한계값
    private static final int SPEED_TIME_CLUE = 10000; // 단위 millis, 이전 위치와 속도를 계산할 수 있는 시간차 한계값
    private static final int SPEED_CHECKABLE_COUNT = 2; // 속도를 계산할 수 있는 최소 위치 갯수
    private static final int GROUPING_DISTANCE_THRESHOLD = 500; // 단위 meter, 같은 구역으로 묶을 거리
    private static final int GROUPING_MINIMUM_COUNT = 3; // 구역을 나눌 수 있는 최소 위치 갯수
    private static final int GROUPING_ASSUME_BAD_COUNT = 3; // 큰 구역은 아니지만, 정상적인 데이터로 봐야 하는 구역 내 아이템 갯수

    private LinkedList<LevelledLocation> locations;
    private boolean useSpeedCheck = true;
    private boolean useDistanceCheck = false;

    public LocationStabilizer() {

        locations = new LinkedList<>();

    }

    @SuppressWarnings("unused")
    public boolean isStableLocation(@NonNull Location location) {
        return getLocationLevel(location).value() > TrustLevel.Bad.value();
    }

    @SuppressWarnings("unused")
    public boolean isUseSpeedCheck() {
        return useSpeedCheck;
    }

    @SuppressWarnings("unused")
    public void setUseSpeedCheck(boolean value) {
        useDistanceCheck = value;
    }

    @SuppressWarnings("unused")
    public boolean isUseDistanceCheck() {
        return useDistanceCheck;
    }

    @SuppressWarnings("unused")
    public void setUseDistanceCheck(boolean value) {
        useDistanceCheck = value;
    }




    // 위치 신뢰 레벨 측정
    private TrustLevel getLocationLevel(Location location) {

        long startTime = System.currentTimeMillis();

        Log.d(TAG, "getLocationLevel() ac=" + location.getAccuracy());

        LevelledLocation income = new LevelledLocation(location, TrustLevel.Good); // 인입 기본 레벨 Good

        if (location.getAccuracy() > BAD_ACCURACY_THRESHOLD) { // 분명히 나쁜 데이터
            income.setLevel(TrustLevel.Terrible);
        }
        else if(useSpeedCheck) { // ## 속력으로 레벨 측정
            setLevelWithSpeed(income, locations);
        }
        else if(useDistanceCheck) {
            setLevelWithDistance(income, locations);
        }


        // # 히스토리에 담기
        locations.add(income);
        if (locations.size() > MAX_HISTORY_COUNT) { // Limit size
            locations.remove(0);
        }


        checkLevelWithRegions();

        Log.d(TAG, "level=" + income.getLevel() +
                " / millis=" + (System.currentTimeMillis() - startTime)
        );

        return income.getLevel();
    }

    private void setLevelWithSpeed(LevelledLocation location, List<LevelledLocation> history) {
        if(history.size() >= SPEED_CHECKABLE_COUNT) { // ## 속력으로 레벨 측정
            LevelledLocation last = null;
            int length = history.size();
            for (int i=length-1; i>=0; i--) {
                // Terrible 레벨 제외
                if (history.get(i).getLevel().value() > TrustLevel.Terrible.value()) {
                    last = history.get(i);
                    break;
                }
            }

            if (last != null) {
                // 인입 데이터와 비교할 만한 시간 범위 안에 있다면,
                if (location.isComparableSpeed(last)) {
                    float speed = location.getSpeed(last);
                    if (speed >= 0 && speed < SPEED_THRESHOLD) {
                        location.setLevel(TrustLevel.Good);
                    }
                    else {
                        location.setLevel(TrustLevel.Bad);
                    }
                }
                else {
                    location.setLevel(TrustLevel.Bad);
                }
            }
        } // <-- 속력 측정
    }


    private void setLevelWithDistance(LevelledLocation location, List<LevelledLocation> history) {
        if (history.size() > 0) {

            LevelledLocation last = null;
            int length = history.size();
            for (int i=length-1; i>=0; i--) {
                // Bad 레벨 제외
                if (history.get(i).getLevel().value() > TrustLevel.Bad.value()) {
                    last = history.get(i);
                    break;
                }
            }
            if (last != null) {
                // 인입 데이터와 비교할 만한 시간 범위 안에 있다면,
                if (location.isComparableTime(last)) {
                    float distance = location.getDistance(last);
                    if (distance >= 0 && distance < DISTANCE_THRESHOLD) {
                        location.setLevel(TrustLevel.Good);
                    }
                    else {
                        location.setLevel(TrustLevel.Bad);
                    }
                }
                else {
                    location.setLevel(TrustLevel.Bad);
                }
            }
        }
    }




    // 거리기반으로 위치들을 구역으로 나눠 레벨 측정
    private void checkLevelWithRegions() {


        if (locations.size() >= GROUPING_MINIMUM_COUNT) {
            // ## start grouping
            List<List<LevelledLocation>> regionList = new ArrayList<>();
            // 첫 구역
            regionList.add(new ArrayList<>(Arrays.asList(locations.get(0))));

            int length = locations.size();
            for (int i=1; i < length; i++) {

                LevelledLocation next = locations.get(i);
                boolean added = false;
                // 기존 구역 내 위치들과 비교하여, 같은 구역에 포함할지 판단한다.
                for(List<LevelledLocation> region :regionList) {
                    if (isLocationBelongToRegion(next, region)) {
                        region.add(next);
                        added = true;
                        break;
                    }
                }

                if (!added) {
                    // 새 구역
                    regionList.add(new ArrayList<>(Arrays.asList(next)));
                }

            } // <-- 구역 나누기

            setBadLevelInSmallRegion(regionList);

        } //<-- locations.size() >  GROUPING_MINIMUM_COUNT

    }

    private boolean isLocationBelongToRegion(LevelledLocation origin, List<LevelledLocation> region) {

        for (int k=region.size() - 1; k >= 0 ; k--) { // 구역 내 최근 위치부터
            LevelledLocation target = region.get(k);
            // 그룹 내에 어느 위치와 비교하여 라이더가 갈 수 있는 거리라면,
            // 같은 구역으로 설정한다.
            float distance = origin.getDistance(target);
            if (distance >= 0 && distance < GROUPING_DISTANCE_THRESHOLD) {
                return true;
            }
        }

        return false;

    }

    private void setBadLevelInSmallRegion(List<List<LevelledLocation>> regionList) {
        for(List<LevelledLocation> group :regionList) {
            // 사이즈가 분명히 작은 구역 내 Good 위치들을 Bad 로 변경
            // 예) 만약 튀는 값이 같은 범위에서 2회 연속 발생할 경우,
            // 2번째 위치값 또한 믿을 수 없는 값으로 판단해줘야 한다.
            if(group.size() < GROUPING_ASSUME_BAD_COUNT) {
                for(int j=0; j<group.size(); j++) {
                    group.get(j).setLevel(TrustLevel.Bad);
                }
            }
        }
    }


    public static class LevelledLocation {


        private Location location;

        @SerializedName("level")
        private TrustLevel level = TrustLevel.Good;
        @SerializedName("latitude")
        private Double latitude = null;
        @SerializedName("longitude")
        private Double longitude = null;
        @SerializedName("time")
        private long time;

        LevelledLocation(@Nullable Location location, @NonNull TrustLevel level) {
            this.location = location;
            this.level = level;

            if (location != null) {
                this.time = location.getTime();
                this.latitude = location.getLatitude();
                this.longitude = location.getLongitude();
            }
        }

        TrustLevel getLevel() {
            return level;
        }

        void setLevel(TrustLevel level) {
            this.level = level;
        }

        Double getLatitude() {
            return latitude;
        }
        public void setLatitude(Double latitude) {
            this.latitude = latitude;
        }

        Double getLongitude() {
            return longitude;
        }
        public void setLongitude(Double longitude) {
            this.longitude = longitude;
        }


        float getDistance(LevelledLocation origin) {
            try {
                float[] results = new float[3];
                Location.distanceBetween(
                        origin.getLocation().getLatitude(), origin.getLocation().getLongitude(),
                        getLatitude(), getLongitude(),
                        results);

                return results[0];
            }
            catch (Throwable e) {
                //
            }
            return -1;
        }

        Location getLocation() {
            return this.location;
        }

        float getSpeed(LevelledLocation old) {
            float distance = getDistance(old);
            long interval = this.time - old.time;
            float sec = interval / 1000f;

            return distance / sec;
        }

        boolean isComparableSpeed(LevelledLocation old) {
            return (this.time - old.time < SPEED_TIME_CLUE) && (this.time > old.time);
        }

        boolean isComparableTime(LevelledLocation old) {
            return (this.time - old.time < DISTANCE_TIME_CLUE) && (this.time > old.time);
        }



    }

}
