package com.hansoolabs.test.locationupdate.utils;

import android.location.Location;
import android.support.annotation.NonNull;
import android.util.Log;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 *
 * Created by brownsoo on 2017. 3. 9..
 */

public class LocationStabilizer2 {

    private static final String TAG = "LocationStabilizer";

    /** 신뢰도 */
    private enum TrustLevel {
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

    private static final int MAX_HISTORY_COUNT = 10; // 기억하고 있는 최근 위치값 최대 갯수
    private static final int DISTANCE_THRESHOLD = 3000; // 단위 m, 정상 거리로 보는 한계값
    private static final int DISTANCE_TIME_CLUE = 30000; // 단위 millis, 이전 위치와 거리를 계산할 수 있는 시간차 한계값
    private static final int GROUPING_DISTANCE_THRESHOLD = 500; // 단위 meter, 같은 구역으로 묶을 거리
    private static final int GROUPING_MINIMUM_COUNT = 3; // 구역을 나눌 수 있는 최소 위치 갯수
    private static final int GROUPING_ASSUME_BAD_COUNT = 3; // 큰 구역은 아니지만, 정상적인 데이터로 봐야 하는 구역 내 아이템 갯수

    private LinkedList<WrapLocation> locations;

    public LocationStabilizer2() {

        locations = new LinkedList<>();

    }

    public boolean isStableLocation(@NonNull Location location) {
        return getLocationLevel(location).value > TrustLevel.Bad.value();
    }


    // 위치 신뢰 레벨 측정
    private TrustLevel getLocationLevel(Location location) {

        long startTime = System.currentTimeMillis();

        Log.d(TAG, "getLocationLevel() ac=" + location.getAccuracy());

        WrapLocation income = new WrapLocation(location, TrustLevel.Good); // 인입 기본 레벨 Good

        // 시간과 거리 비교
        if (locations.size() > 0) {

            WrapLocation last = null;
            int length = locations.size();
            for (int i=length-1; i>=0; i--) {
                // Bad 레벨 제외
                if (locations.get(i).getLevel().value() > TrustLevel.Bad.value()) {
                    last = locations.get(i);
                    break;
                }
            }
            if (last != null) {
                // 인입 데이터와 비교할 만한 시간 범위 안에 있다면,
                if (income.isComparableTime(last)) {
                    float distance = income.getDistance(last);
                    if (distance >= 0 && distance < DISTANCE_THRESHOLD) {
                        income.setLevel(TrustLevel.Good);
                    }
                    else {
                        income.setLevel(TrustLevel.Bad);
                    }
                }
                else {
                    income.setLevel(TrustLevel.Bad);
                }
            }
        }


        // # 히스토리에 담기
        locations.add(income);
        if (locations.size() > MAX_HISTORY_COUNT) { // Limit size
            locations.remove(0);
        }


        checkLocationRegion(locations);

        Log.d(TAG,
            "level=" + income.getLevel() +
            " / millis=" + (System.currentTimeMillis() - startTime)
        );

        return income.getLevel();
    }




    // 거리기반으로 위치들을 구역으로 나눠 레벨 측정
    private void checkLocationRegion(List<WrapLocation> locations) {

        // ## start grouping
        List<List<WrapLocation>> regionList = new ArrayList<>();

        if (locations.size() >= GROUPING_MINIMUM_COUNT) {
            // 첫 구역
            List<WrapLocation> regionFirst = new ArrayList<>();
            regionFirst.add(locations.get(0));
            regionList.add(regionFirst);

            int length = locations.size();
            for (int i=1; i < length; i++) {

                WrapLocation next = locations.get(i);
                boolean added = false;
                // 기존 구역 내 위치들과 비교하여, 같은 구역에 포함할지 판단한다.
                for(int j = 0; j< regionList.size(); j++) {
                    List<WrapLocation> region = regionList.get(j);
                    int groupLength = region.size();
                    for (int k=groupLength - 1; k >= 0 ; k--) { // 거꾸로 계산해서 구역이 겹친다면, 이전 데이터 그룹에 속하도록 -
                        WrapLocation m = region.get(k);
                        // 그룹 내에 어느 위치와 비교하여 라이더가 갈 수 있는 거리라면,
                        // 같은 구역으로 설정한다.
                        float distance = next.getDistance(m);
                        if (distance >= 0 && distance < GROUPING_DISTANCE_THRESHOLD) {
                            region.add(next);
                            added = true;
                            break;
                        }
                    }
                    if (added) {
                        break;
                    }
                } // <-- 기존 구역 내 검사

                if (!added) {
                    // 새 구역
                    List<WrapLocation> regionNew = new ArrayList<>();
                    regionNew.add(next);
                    regionList.add(regionNew);
                }

            } // <-- 구역 나누기


            int regionSize = regionList.size();
            for(int i=0; i<regionSize; i++) {
                List<WrapLocation> group = regionList.get(i);
                int groupSize = group.size();
                // 사이즈가 분명히 작은 구역 내 Good 위치들을 Bad 로 변경
                // 예) 만약 튀는 값이 같은 범위에서 2회 연속 발생할 경우,
                // 2번째 위치값 또한 믿을 수 없는 값으로 판단해줘야 한다.
                if(groupSize < GROUPING_ASSUME_BAD_COUNT) {
                    for(int j=0; j<groupSize; j++) {
                        group.get(j).setLevel(TrustLevel.Bad);
                    }
                }
            }

        } //<-- locations.size() >  GROUPING_MINIMUM_COUNT

    }


    private class WrapLocation {

        private long time;
        private TrustLevel level = TrustLevel.Good;
        private Location location;

        public WrapLocation(@NonNull Location location, @NonNull TrustLevel level) {
            this.location = location;
            this.time = location.getTime();
            this.level = level;
        }

        public long getTime() {
            return time;
        }

        public TrustLevel getLevel() {
            return level;
        }

        public void setLevel(TrustLevel level) {
            this.level = level;
        }

        public float getDistance(WrapLocation origin) {
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

        public boolean isComparableTime(WrapLocation old) {
            return (this.time - old.time < DISTANCE_TIME_CLUE) && (this.time > old.time);
        }

    }

}
