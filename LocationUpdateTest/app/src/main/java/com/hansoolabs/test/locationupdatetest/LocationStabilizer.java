package com.hansoolabs.test.locationupdatetest;

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

public class LocationStabilizer {

    private static final String TAG = "LocationStabilizer";

    /** 신뢰도 */
    private enum TrustLevel {
        Terrible(0),
        Bad(1),
        Good(2),
        Best(3);

        private int value;

        TrustLevel(int value) {
            this.value = value;
        }

        int value() {
            return this.value;
        }
    }

    private static final int FINE_ACCURACY_THRESHOLD = 10; // Best 레벨을 정하기 위한 Accuracy
    private static final int BAD_ACCURACY_THRESHOLD = 350; // Terrible 레벨을 정하기 위한 Accuracy
    private static final int MAX_HISTORY_COUNT = 10; // 기억하고 있는 최근 위치값 최대 갯수
    private static final int SPEED_THRESHOLD = 30; // 단위 m/s, 정상 속도로 보는 한계값
    private static final int SPEED_TIME_CLUE = 10000; // 단위 millis, 이전 위치와 속도를 계산할 수 있는 시간차 한계값
    private static final int SPEED_CHECKABLE_COUNT = 3; // 속도를 계산할 수 있는 최소 위치값
    private static final int GROUPING_DISTANCE_THRESHOLD = 500; // 단위 meter, 같은 구역으로 묶을 거리
    private static final int GROUPING_MINIMUM_COUNT = 3; // 구역을 나눌 수 있는 최소 위치 갯수
    private static final int GROUPING_ASSUME_BAD_COUNT = 3; // 큰 구역은 아니지만, 정상적인 데이터로 봐야 하는 구역 내 아이템 갯수

    private LinkedList<WrapLocation> locations;
    private List<List<WrapLocation>> regionList;

    public LocationStabilizer() {

        locations = new LinkedList<>();
        regionList = new ArrayList<>();

    }

    public void reset() {
        Log.d(TAG, "reset()");
        locations.clear();
        regionList.clear();

    }

    public boolean verifyNewLocation(@NonNull Location location) {
        return checkLocationLevel(location).value > TrustLevel.Bad.value();
    }


    // 위치 신뢰 레벨 측정
    private TrustLevel checkLocationLevel(Location location) {

        long startTime = System.currentTimeMillis();

        Log.d(TAG, "checkLocationLevel() ac=" + location.getAccuracy());

        WrapLocation income = new WrapLocation(location, TrustLevel.Good); // 인입 기본 레벨 Good

        if (location.getAccuracy() < FINE_ACCURACY_THRESHOLD) { // 분명히 좋은 데이터
            income.level = TrustLevel.Best;
        }
        else if (location.getAccuracy() > BAD_ACCURACY_THRESHOLD) { // 분명히 나쁜 데이터
            income.level = TrustLevel.Terrible;
        }
        else if(locations.size() + 1 >= SPEED_CHECKABLE_COUNT) { // ## 속력으로 레벨 측정
            WrapLocation last = null;
            int length = locations.size();
            for (int i=length-1; i>=0; i--) {
                // Terrible 레벨 제외
                if (locations.get(i).level.value() > TrustLevel.Terrible.value()) {
                    last = locations.get(i);
                    break;
                }
            }

            if (last != null) {
                // 인입 데이터와 비교할 만한 시간 범위 안에 있다면,
                if (income.speedComparable(last)) {
                    income.speed = income.speedFrom(last);
                    if (income.speed >= 0 && income.speed < SPEED_THRESHOLD) {
                        income.level = TrustLevel.Good;
                    }
                    else {
                        income.level = TrustLevel.Bad;
                    }
                }
                else {
                    income.level = TrustLevel.Bad;
                }
            }
        } // <-- 속력 측정


        // # 히스토리에 담기
        locations.add(income);
        if (locations.size() > MAX_HISTORY_COUNT) { // Limit size
            locations.remove(0);
        }


        checkLocationRegion();

        Log.d(TAG,
                "level=" + income.level +
                        " / millis=" + (System.currentTimeMillis() - startTime) +
                        " / regions=" + regionList.size()
        );

        return income.level;
    }




    // 거리기반으로 위치들을 구역으로 나눠 레벨 측정
    private void checkLocationRegion() {

        // ## start grouping
        regionList.clear();

        if (locations.size() >= GROUPING_MINIMUM_COUNT) {
            // 첫 구역
            List<WrapLocation> region = new ArrayList<>();
            region.add(locations.get(0));
            regionList.add(region);

            int length = locations.size();
            for (int i=1; i < length; i++) {

                WrapLocation next = locations.get(i);
                boolean added = false;
                // 기존 구역 내 위치들과 비교하여, 같은 구역에 포함할지 판단한다.
                for(int j = 0; j< regionList.size(); j++) {
                    region = regionList.get(j);
                    int groupLength = region.size();
                    for (int k=groupLength - 1; k >= 0 ; k--) { // 거꾸로 계산해서 구역이 겹친다면, 이전 데이터 그룹에 속하도록 -
                        WrapLocation m = region.get(k);
                        // 그룹 내에 어느 위치와 비교하여 라이더가 갈 수 있는 거리라면,
                        // 같은 구역으로 설정한다.
                        if (next.distanceFrom(m) < GROUPING_DISTANCE_THRESHOLD) {
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
                    region = new ArrayList<>();
                    region.add(next);
                    regionList.add(region);
                }

            } // <-- 구역 나누기

            // 큰 사이즈 찾기
            int maxSize = 0;
            int regionSize = regionList.size();
            for(int i=0; i<regionSize; i++) {
                int size = regionList.get(i).size();
                if (size > maxSize) {
                    maxSize = size;
                }
            }

            // 큰 사이즈 값을 기준으로 Good 와 Bad 레벨을 조정한다.
            for(int i=0; i<regionSize; i++) {
                List<WrapLocation> group = regionList.get(i);
                int groupSize = group.size();
                if(groupSize >= maxSize) { // 사이즈가 큰 구역의 Bad 위치들을 Good 으로 변경
                    for(int j=0; j<groupSize; j++) {
                        if(group.get(j).level == TrustLevel.Bad) group.get(j).level = TrustLevel.Good;
                    }
                }
                else if(groupSize < GROUPING_ASSUME_BAD_COUNT) { // 사이즈가 분명히 작은 구역 내 Good 위치들을 Bad 로 변경
                    for(int j=0; j<groupSize; j++) {
                        if(group.get(j).level == TrustLevel.Good) group.get(j).level = TrustLevel.Bad;
                    }
                }
            }

        } //<-- locations.size() >  GROUPING_MINIMUM_COUNT

    }


    private class WrapLocation {

        long time;
        float speed = 0;
        TrustLevel level = TrustLevel.Good;
        Location location;

        WrapLocation(@NonNull Location location, @NonNull TrustLevel level) {
            this.location = location;
            this.time = location.getTime();
            this.level = level;
        }

        float distanceFrom(WrapLocation origin) {
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

        float speedFrom(WrapLocation old) {
            float distance = distanceFrom(old);
            long interval = this.time - old.time;
            float sec = interval / 1000f;

            return distance / sec;
        }

        boolean speedComparable(WrapLocation old) {
            return (this.time - old.time < SPEED_TIME_CLUE) && (this.time > old.time);
        }

    }

}
