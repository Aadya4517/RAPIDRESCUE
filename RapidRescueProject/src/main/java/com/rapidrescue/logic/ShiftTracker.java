package com.rapidrescue.logic;

import java.time.LocalTime;

// current work shift
public class ShiftTracker {

    public record Shift(String name, String icon, String color, String time_range) {}

    public static Shift get_shift() {
        int h = LocalTime.now().getHour();
        if (h >= 6  && h < 14) return new Shift("Morning Shift", "sunrise", "#eab308", "06:00 - 14:00");
        if (h >= 14 && h < 22) return new Shift("Evening Shift", "sunset",  "#f97316", "14:00 - 22:00");
        return new Shift("Night Shift", "moon", "#3b82f6", "22:00 - 06:00");
    }
}
