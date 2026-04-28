package com.rapidrescue.logic;

import com.rapidrescue.model.TrafficWeight;
import java.time.LocalDateTime;

// traffic and ETA calc
public class TrafficRouting {

    // traffic level by hour
    public static TrafficWeight get_traffic() {
        int h = LocalDateTime.now().getHour();
        if (h >= 8  && h <= 10) return new TrafficWeight(1.6, "Heavy (rush hour)", 80);
        if (h >= 17 && h <= 19) return new TrafficWeight(1.5, "Heavy (evening)",   75);
        if (h >= 11 && h <= 16) return new TrafficWeight(1.2, "Moderate",          55);
        if (h >= 20 && h <= 22) return new TrafficWeight(1.1, "Light",             35);
        return new TrafficWeight(1.0, "Clear (night)", 15);
    }

    // ETA with traffic only
    public static double calc_eta(double dist_km, double traffic) {
        return (dist_km / (40.0 / traffic)) * 60.0;
    }

    // ETA with traffic + weather
    public static double calc_eta(double dist_km, double traffic, double weather) {
        return (dist_km / (40.0 / traffic)) * 60.0 * weather;
    }
}
