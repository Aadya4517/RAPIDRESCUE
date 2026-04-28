package com.rapidrescue.logic;

import com.rapidrescue.model.TrafficWeight;
import java.time.LocalDateTime;

public class TrafficRouting {

    public static TrafficWeight getTrafficWeight() {
        int hour = LocalDateTime.now().getHour();
        if (hour >= 8  && hour <= 10) return new TrafficWeight(1.6, "Heavy (rush hour)", 80);
        if (hour >= 17 && hour <= 19) return new TrafficWeight(1.5, "Heavy (evening)",   75);
        if (hour >= 11 && hour <= 16) return new TrafficWeight(1.2, "Moderate",          55);
        if (hour >= 20 && hour <= 22) return new TrafficWeight(1.1, "Light",             35);
        return new TrafficWeight(1.0, "Clear (night)", 15);
    }

    /** Base ETA using traffic weight only */
    public static double dijkstraETA(double distKm, double trafficWeight) {
        double speedKmh = 40.0 / trafficWeight;
        return (distKm / speedKmh) * 60.0;
    }

    /** ETA with both traffic and weather multipliers applied */
    public static double dijkstraETA(double distKm, double trafficWeight, double weatherMultiplier) {
        double speedKmh = 40.0 / trafficWeight;
        return (distKm / speedKmh) * 60.0 * weatherMultiplier;
    }
}
