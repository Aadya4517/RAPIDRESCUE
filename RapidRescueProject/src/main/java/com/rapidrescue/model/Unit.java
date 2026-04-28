package com.rapidrescue.model;

import java.time.LocalTime;

public class Unit {
    public String name, icon, type;
    public double lat, lng;
    public boolean available;
    public int shiftStart;
    public int shiftEnd;

    // Hospital load tracking
    public int capacity;     // max beds/capacity (0 = not a hospital)
    public int currentLoad;  // current patients/load

    public Unit(String name, String icon, String type, double lat, double lng) {
        this.name       = name;
        this.icon       = icon;
        this.type       = type;
        this.lat        = lat;
        this.lng        = lng;
        this.available  = true;
        this.shiftStart = 0;
        this.shiftEnd   = 24;
        this.capacity   = 0;
        this.currentLoad = 0;
    }

    public Unit shift(int start, int end) {
        this.shiftStart = start;
        this.shiftEnd   = end;
        return this;
    }

    /** Set hospital capacity */
    public Unit capacity(int cap) {
        this.capacity = cap;
        return this;
    }

    public boolean isOnShift() {
        int hour = LocalTime.now().getHour();
        if (shiftStart < shiftEnd) return hour >= shiftStart && hour < shiftEnd;
        return hour >= shiftStart || hour < shiftEnd;
    }

    public boolean isDeployable() {
        return available && isOnShift();
    }

    /** Hospital is overloaded if load >= 90% capacity */
    public boolean isOverloaded() {
        return capacity > 0 && currentLoad >= (int)(capacity * 0.9);
    }

    /** Load percentage 0-100 */
    public int getLoadPercent() {
        if (capacity <= 0) return 0;
        return Math.min(100, (int)((currentLoad * 100.0) / capacity));
    }

    public String getLoadLabel() {
        if (capacity <= 0) return "";
        int pct = getLoadPercent();
        if (pct >= 90) return "CRITICAL";
        if (pct >= 70) return "HIGH";
        if (pct >= 40) return "MODERATE";
        return "LOW";
    }

    public String getLoadColor() {
        int pct = getLoadPercent();
        if (pct >= 90) return "#ef4444";
        if (pct >= 70) return "#f97316";
        if (pct >= 40) return "#eab308";
        return "#22c55e";
    }

    public String getStatusLabel() {
        if (!isOnShift()) return "Off Shift";
        return available ? "Available" : "Busy";
    }

    public String getStatusColor() {
        if (!isOnShift()) return "#6b7280";
        return available ? "#22c55e" : "#ef4444";
    }

    public String getShiftLabel() {
        if (shiftStart == 0 && shiftEnd == 24) return "24/7";
        return String.format("%02d:00 - %02d:00", shiftStart, shiftEnd);
    }
}
