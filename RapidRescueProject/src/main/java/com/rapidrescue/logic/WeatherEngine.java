package com.rapidrescue.logic;

import java.time.LocalDateTime;
import java.time.Month;

/**
 * Simulates weather conditions for Dehradun based on time of day and season.
 * Applies a realistic ETA multiplier to account for road conditions.
 *
 * Dehradun climate:
 *  - Jun-Sep: Monsoon (heavy rain, fog)
 *  - Oct-Nov: Post-monsoon (mild, occasional fog)
 *  - Dec-Feb: Winter (fog, cold, occasional rain)
 *  - Mar-May: Summer (clear, hot, dust)
 */
public class WeatherEngine {

    public record WeatherCondition(
        String name,
        String icon,
        String color,
        String description,
        double etaMultiplier,
        int    severity   // 0=fine, 1=minor, 2=moderate, 3=severe
    ) {}

    public static WeatherCondition getCurrentWeather() {
        LocalDateTime now = LocalDateTime.now();
        int hour  = now.getHour();
        Month mon = now.getMonth();
        boolean isNight = hour < 6 || hour >= 20;
        boolean isMonsoon   = mon == Month.JUNE || mon == Month.JULY || mon == Month.AUGUST || mon == Month.SEPTEMBER;
        boolean isWinter    = mon == Month.DECEMBER || mon == Month.JANUARY || mon == Month.FEBRUARY;
        boolean isSummer    = mon == Month.MARCH || mon == Month.APRIL || mon == Month.MAY;
        boolean isPostMon   = mon == Month.OCTOBER || mon == Month.NOVEMBER;

        // Monsoon season
        if (isMonsoon) {
            if (hour >= 14 && hour <= 18) // afternoon thunderstorms
                return new WeatherCondition("Heavy Rain", "RAIN", "#3b82f6",
                    "Monsoon thunderstorm — roads flooded, visibility poor", 1.8, 3);
            if (isNight)
                return new WeatherCondition("Rain + Fog", "FOG", "#6b7280",
                    "Night rain with fog — severely reduced visibility", 2.0, 3);
            return new WeatherCondition("Moderate Rain", "RAIN", "#60a5fa",
                "Monsoon rain — wet roads, reduced speed", 1.5, 2);
        }

        // Winter
        if (isWinter) {
            if (hour >= 5 && hour <= 9)
                return new WeatherCondition("Dense Fog", "FOG", "#9ca3af",
                    "Winter morning fog — near-zero visibility", 2.2, 3);
            if (isNight)
                return new WeatherCondition("Cold + Fog", "FOG", "#6b7280",
                    "Cold night with fog patches", 1.6, 2);
            if (mon == Month.JANUARY && hour >= 10 && hour <= 15)
                return new WeatherCondition("Light Rain", "RAIN", "#93c5fd",
                    "Winter drizzle — slightly slippery roads", 1.3, 1);
            return new WeatherCondition("Cold & Clear", "CLEAR", "#a5b4fc",
                "Cold but clear — normal driving conditions", 1.1, 0);
        }

        // Summer
        if (isSummer) {
            if (hour >= 12 && hour <= 16)
                return new WeatherCondition("Heatwave", "HOT", "#f97316",
                    "Extreme heat — tyre blowouts risk, engine stress", 1.2, 1);
            if (hour >= 17 && hour <= 19 && mon == Month.MAY)
                return new WeatherCondition("Dust Storm", "DUST", "#d97706",
                    "Pre-monsoon dust storm — poor visibility", 1.7, 2);
            return new WeatherCondition("Clear & Hot", "CLEAR", "#fbbf24",
                "Clear skies — good driving conditions", 1.0, 0);
        }

        // Post-monsoon
        if (isPostMon) {
            if (isNight)
                return new WeatherCondition("Mild Fog", "FOG", "#9ca3af",
                    "Post-monsoon night fog — moderate visibility", 1.3, 1);
            return new WeatherCondition("Clear", "CLEAR", "#22c55e",
                "Post-monsoon clear weather — excellent conditions", 1.0, 0);
        }

        // Default: clear
        return new WeatherCondition("Clear", "CLEAR", "#22c55e",
            "Clear conditions — normal response times", 1.0, 0);
    }

    /** Apply weather multiplier on top of traffic ETA */
    public static double applyWeather(double trafficEta, WeatherCondition w) {
        return trafficEta * w.etaMultiplier();
    }

    public static String getSeverityLabel(int sev) {
        return switch (sev) {
            case 0 -> "No impact";
            case 1 -> "Minor impact";
            case 2 -> "Moderate impact";
            case 3 -> "Severe impact";
            default -> "Unknown";
        };
    }
}
