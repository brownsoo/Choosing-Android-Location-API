package com.hansoolabs.test.locationupdate.utils;

import android.location.Location;
import android.support.annotation.NonNull;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * Location Stabilizer
 * Created by brownsoo on 2017. 3. 9..
 */

public class LocationStabilizer {

    private static final String TAG = "LocationStabilizer";



    /** 신뢰도 */
    private enum TrustLevel {
        Terrible(0),
        Bad(1),
        Good(2);

        private int value;

        TrustLevel(int value) {
            this.value = value;
        }

        int value() {
            return this.value;
        }
    }

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

    private LinkedList<WrapLocation> locations;
    private boolean useSpeedCheck = true;
    private boolean useDistanceCheck = false;

    public LocationStabilizer() {

        locations = new LinkedList<>();

    }

    @SuppressWarnings("unused")
    public boolean isStableLocation(@NonNull Location location) {
        return getLocationLevel(location).value > TrustLevel.Bad.value();
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

        WrapLocation income = new WrapLocation(location, TrustLevel.Good); // 인입 기본 레벨 Good

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

    private void setLevelWithSpeed(WrapLocation location, List<WrapLocation> history) {
        if(history.size() >= SPEED_CHECKABLE_COUNT) { // ## 속력으로 레벨 측정
            WrapLocation last = null;
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


    private void setLevelWithDistance(WrapLocation location, List<WrapLocation> history) {
        if (history.size() > 0) {

            WrapLocation last = null;
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
            List<List<WrapLocation>> regionList = new ArrayList<>();
            // 첫 구역
            regionList.add(Collections.singletonList(locations.get(0)));

            int length = locations.size();
            for (int i=1; i < length; i++) {

                WrapLocation next = locations.get(i);
                boolean added = false;
                // 기존 구역 내 위치들과 비교하여, 같은 구역에 포함할지 판단한다.
                for(List<WrapLocation> region :regionList) {
                    if (isLocationBelongToRegion(next, region)) {
                        region.add(next);
                        added = true;
                        break;
                    }
                }

                if (!added) {
                    // 새 구역
                    regionList.add(Collections.singletonList(next));
                }

            } // <-- 구역 나누기

            setBadLevelInSmallRegion(regionList);

        } //<-- locations.size() >  GROUPING_MINIMUM_COUNT

    }

    private boolean isLocationBelongToRegion(WrapLocation origin, List<WrapLocation> region) {

        for (int k=region.size() - 1; k >= 0 ; k--) { // 구역 내 최근 위치부터
            WrapLocation target = region.get(k);
            // 그룹 내에 어느 위치와 비교하여 라이더가 갈 수 있는 거리라면,
            // 같은 구역으로 설정한다.
            float distance = origin.getDistance(target);
            if (distance >= 0 && distance < GROUPING_DISTANCE_THRESHOLD) {
                return true;
            }
        }

        return false;

    }

    private void setBadLevelInSmallRegion(List<List<WrapLocation>> regionList) {
        for(List<WrapLocation> group :regionList) {
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


    private class WrapLocation {

        private long time;
        private TrustLevel level = TrustLevel.Good;
        private Location location;

        WrapLocation(@NonNull Location location, @NonNull TrustLevel level) {
            this.location = location;
            this.time = location.getTime();
            this.level = level;
        }

        TrustLevel getLevel() {
            return level;
        }

        void setLevel(TrustLevel level) {
            this.level = level;
        }

        float getDistance(WrapLocation origin) {
            try {
                float[] results = new float[3];
                Location.distanceBetween(
                        origin.location.getLatitude(), origin.location.getLongitude(),
                        location.getLatitude(), location.getLongitude(),
                        results);

                return results[0];
            }
            catch (Throwable e) {
                //
            }
            return -1;
        }

        float getSpeed(WrapLocation old) {
            float distance = getDistance(old);
            long interval = this.time - old.time;
            float sec = interval / 1000f;

            return distance / sec;
        }

        boolean isComparableSpeed(WrapLocation old) {
            return (this.time - old.time < SPEED_TIME_CLUE) && (this.time > old.time);
        }

        boolean isComparableTime(WrapLocation old) {
            return (this.time - old.time < DISTANCE_TIME_CLUE) && (this.time > old.time);
        }

    }

}
