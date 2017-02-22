package com.hansoolabs.test.locationupdatetest.utils;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.telephony.TelephonyManager;

public class ContextUtils {

    private static final String CLASS = "ContextUtils";

    public static String getCellOperatorName(Context context) {
        TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        return tm.getNetworkOperatorName();
    }

    public static String getDeviceModel() {
        String manufacturer = Build.MANUFACTURER;
        String model = Build.MODEL;
        if (model.startsWith(manufacturer)) {
            return capitalizeFirstChar(model);
        } else {
            return capitalizeFirstChar(manufacturer) + " " + model;
        }
    }

    private static String capitalizeFirstChar(String s) {
        if (s == null || s.length() == 0) {
            return "";
        }
        char first = s.charAt(0);
        if (Character.isUpperCase(first)) {
            return s;
        } else {
            return Character.toUpperCase(first) + s.substring(1);
        }
    }

    public static String getConnectionInfo(Context context) {

        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);

        if(cm.getActiveNetworkInfo() != null && cm.getActiveNetworkInfo().isConnected()) {
            if (cm.getActiveNetworkInfo().getType() == ConnectivityManager.TYPE_MOBILE) {
                return "mobile";
            }
            else if(cm.getActiveNetworkInfo().getType() == ConnectivityManager.TYPE_WIFI) {
                WifiInfo wifiInfo = wifiManager.getConnectionInfo();
                String info = "";
                if(wifiInfo != null) {
                    info = wifiInfo.getSSID() + "|" +
                            wifiInfo.getBSSID() + "|" +
                            getIPStringFromInteger(wifiInfo.getIpAddress());
                }
                return info;
            }
            else {
                return "unknown";
            }
        }

        return "NO connection";
    }

    private static String getIPStringFromInteger(int ipAddress) {
        byte[] buffer = new byte[4];
        buffer[0] = (byte)(ipAddress & 0x000000FF);
        buffer[1] = (byte) Integer.rotateRight(ipAddress & 0x0000FF00, 8);
        buffer[2] = (byte) Integer.rotateRight(ipAddress & 0x00FF0000, 16);
        buffer[3] = (byte) Integer.rotateRight(ipAddress & 0xFF000000, 24);

        return getIPStringFromByteArray(buffer);
    }

    private static String getIPStringFromByteArray(byte[] address) {
        if ( address != null ) {
            String text = "";
            for(int n = 0; n < address.length; n++) {
                text += byteToInt(address[n]);
                if(n < address.length - 1) text += ".";
            }
            return text;
        } else {
            return null;
        }
    }

    private static int byteToInt(byte num) {
        return (num < 0? 256 + num: num);
    }

    public static int getOsVersion() {
        return Build.VERSION.SDK_INT;
    }
}
