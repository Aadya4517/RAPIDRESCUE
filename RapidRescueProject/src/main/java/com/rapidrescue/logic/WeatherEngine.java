package com.rapidrescue.logic;

import java.time.LocalDateTime;
import java.time.Month;

// Dehradun weather by season and hour
public class WeatherEngine {

    // weather data record
    public record Weather(
        String name,
        String icon,
        String color,
        String desc,
        double eta_mult,  // ETA slowdown factor
        int    impact     // 0=none 1=minor 2=moderate 3=severe
    ) {}

    // get current weather condition
    public static Weather get_weather() {
        LocalDateTime now = LocalDateTime.now();
        int h   = now.getHour();
        Month m = now.getMonth();
        boolean night    = h < 6 || h >= 20;
        boolean monsoon  = m == Month.JUNE || m == Month.JULY || m == Month.AUGUST || m == Month.SEPTEMBER;
        boolean winter   = m == Month.DECEMBER || m == Month.JANUARY || m == Month.FEBRUARY;
        boolean summer   = m == Month.MARCH || m == Month.APRIL || m == Month.MAY;
        boolean post_mon = m == Month.OCTOBER || m == Month.NOVEMBER;

        // monsoon Jun-Sep
        if (monsoon) {
            if (h >= 14 && h <= 18)
                return new Weather("Heavy Rain", "RAIN", "#3b82f6",
                    "Monsoon thunderstorm, roads flooded", 1.8, 3);
            if (night)
                return new Weather("Rain + Fog", "FOG", "#6b7280",
                    "Night rain with fog, poor visibility", 2.0, 3);
            return new Weather("Moderate Rain", "RAIN", "#60a5fa",
                "Monsoon rain, wet roads", 1.5, 2);
        }

        // winter Dec-Feb
        if (winter) {
            if (h >= 5 && h <= 9)
                return new Weather("Dense Fog", "FOG", "#9ca3af",
                    "Winter morning fog, near-zero visibility", 2.2, 3);
            if (night)
                return new Weather("Cold + Fog", "FOG", "#6b7280",
                    "Cold night with fog patches", 1.6, 2);
            if (m == Month.JANUARY && h >= 10 && h <= 15)
                return new Weather("Light Rain", "RAIN", "#93c5fd",
                    "Winter drizzle, slippery roads", 1.3, 1);
            return new Weather("Cold & Clear", "CLEAR", "#a5b4fc",
                "Cold but clear, normal driving", 1.1, 0);
        }

        // summer Mar-May
        if (summer) {
            if (h >= 12 && h <= 16)
                return new Weather("Heatwave", "HOT", "#f97316",
                    "Extreme heat, engine stress", 1.2, 1);
            if (h >= 17 && h <= 19 && m == Month.MAY)
                return new Weather("Dust Storm", "DUST", "#d97706",
                    "Pre-monsoon dust, poor visibility", 1.7, 2);
            return new Weather("Clear & Hot", "CLEAR", "#fbbf24",
                "Clear skies, good driving", 1.0, 0);
        }

        // post-monsoon Oct-Nov
        if (post_mon) {
            if (night)
                return new Weather("Mild Fog", "FOG", "#9ca3af",
                    "Post-monsoon night fog", 1.3, 1);
            return new Weather("Clear", "CLEAR", "#22c55e",
                "Clear weather, excellent conditions", 1.0, 0);
        }

        return new Weather("Clear", "CLEAR", "#22c55e",
            "Clear, normal response times", 1.0, 0);
    }

    // impact text
    public static String impact_label(int level) {
        return switch (level) {
            case 0 -> "No impact";
            case 1 -> "Minor impact";
            case 2 -> "Moderate impact";
            case 3 -> "Severe impact";
            default -> "Unknown";
        };
    }
}
