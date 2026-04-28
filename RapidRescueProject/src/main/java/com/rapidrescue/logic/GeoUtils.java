package com.rapidrescue.logic;

// earth distance formula
public class GeoUtils {
    public static double dist(double lat1, double lng1, double lat2, double lng2) {
        final double R = 6371.0;
        double d_lat = Math.toRadians(lat2 - lat1);
        double d_lng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(d_lat/2) * Math.sin(d_lat/2)
                 + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                 * Math.sin(d_lng/2) * Math.sin(d_lng/2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
