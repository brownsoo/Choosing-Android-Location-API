package com.hansoolabs.test.locationupdate;

import com.google.gson.annotations.SerializedName;

import java.util.Date;

/**
 *
 * Created by brownsoo on 2017. 3. 15..
 */

public class LocationTestData {

    @SerializedName("latitude")
    private Double latitude = null;
    @SerializedName("longitude")
    private Double longitude = null;
    @SerializedName("timestamp")
    private Date timestamp = null;

    public Double getLatitude() {
        return latitude;
    }
    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public Double getLongitude() {
        return longitude;
    }
    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    public Date getTimestamp() {
        return timestamp;
    }
    public void setTimestamp(Date timestamp) {
        this.timestamp = timestamp;
    }


    @Override
    public String toString()  {
        StringBuilder sb = new StringBuilder();
        sb.append("class LocationTestData {\n");

        sb.append("  latitude: ").append(latitude).append("\n");
        sb.append("  longitude: ").append(longitude).append("\n");
        sb.append("  timestamp: ").append(timestamp).append("\n");
        sb.append("}\n");
        return sb.toString();
    }
}
