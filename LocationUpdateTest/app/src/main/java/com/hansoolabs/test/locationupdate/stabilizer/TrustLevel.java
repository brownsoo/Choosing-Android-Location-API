package com.hansoolabs.test.locationupdate.stabilizer;

/**
 * Created by brownsoo on 2017. 3. 24..
 */

public enum TrustLevel {
    Terrible(0),
    Bad(1),
    Good(2);

    private int value;

    TrustLevel(int value) {
        this.value = value;
    }

    public int value() {
        return this.value;
    }
}
