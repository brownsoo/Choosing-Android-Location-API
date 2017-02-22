package com.hansoolabs.test.locationupdatetest.events;

/**
 *
 * Created by brownsoo on 2017. 2. 17..
 */

public class OverlayEvent {

    public enum Field {
        Source,
        Setting,
        GPS,
        Provider,
        Elapsed,
        Accuracy,
        Activity
    }

    public Field field;
    public String value;

    public OverlayEvent(Field field, String value) {
        this.field = field;
        this.value = value;
    }

}
