package com.rapidrescue.logic;

import java.time.LocalTime;

/**
 * Determines the current operational shift based on time of day.
 */
public class ShiftTracker {

    public record Shift(String name, String icon, String color, String timeRange) {}

    public static Shift getCurrentShift() {
        int hour = LocalTime.now().getHour();
        if (hour >= 6  && hour < 14) return new Shift("Morning Shift", "🌅", "#eab308", "06:00 – 14:00");
        if (hour >= 14 && hour < 22) return new Shift("Evening Shift", "🌆", "#f97316", "14:00 – 22:00");
        return new Shift("Night Shift", "🌙", "#3b82f6", "22:00 – 06:00");
    }
}
