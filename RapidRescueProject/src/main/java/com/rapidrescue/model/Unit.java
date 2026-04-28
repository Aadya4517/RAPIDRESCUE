package com.rapidrescue.model;

import java.time.LocalTime;

// police / hospital / fire unit
public class Unit {
    public String  name, icon, type;
    public double  lat, lng;
    public boolean free;        // available to dispatch
    public int     shift_start;
    public int     shift_end;
    public int     max_beds;    // hospital only, 0 = not hospital
    public int     cur_load;    // current patients

    public Unit(String name, String icon, String type, double lat, double lng) {
        this.name        = name;
        this.icon        = icon;
        this.type        = type;
        this.lat         = lat;
        this.lng         = lng;
        this.free        = true;
        this.shift_start = 0;
        this.shift_end   = 24;
        this.max_beds    = 0;
        this.cur_load    = 0;
    }

    // set shift hours
    public Unit shift(int start, int end) {
        this.shift_start = start;
        this.shift_end   = end;
        return this;
    }

    // set hospital bed count
    public Unit beds(int n) {
        this.max_beds = n;
        return this;
    }

    // is unit within shift hours
    public boolean on_shift() {
        int h = LocalTime.now().getHour();
        if (shift_start < shift_end) return h >= shift_start && h < shift_end;
        return h >= shift_start || h < shift_end;
    }

    // free and on shift
    public boolean ready() { return free && on_shift(); }

    // load >= 90%
    public boolean overloaded() { return max_beds > 0 && cur_load >= (int)(max_beds * 0.9); }

    // load as 0-100
    public int load_pct() {
        if (max_beds <= 0) return 0;
        return Math.min(100, (int)((cur_load * 100.0) / max_beds));
    }

    // LOW / MODERATE / HIGH / CRITICAL
    public String load_label() {
        if (max_beds <= 0) return "";
        int p = load_pct();
        if (p >= 90) return "CRITICAL";
        if (p >= 70) return "HIGH";
        if (p >= 40) return "MODERATE";
        return "LOW";
    }

    // color for load bar
    public String load_color() {
        int p = load_pct();
        if (p >= 90) return "#ef4444";
        if (p >= 70) return "#f97316";
        if (p >= 40) return "#eab308";
        return "#22c55e";
    }

    // status text
    public String status_label() {
        if (!on_shift()) return "Off Shift";
        return free ? "Available" : "Busy";
    }

    // status color
    public String status_color() {
        if (!on_shift()) return "#6b7280";
        return free ? "#22c55e" : "#ef4444";
    }

    // shift time range text
    public String shift_label() {
        if (shift_start == 0 && shift_end == 24) return "24/7";
        return String.format("%02d:00 - %02d:00", shift_start, shift_end);
    }
}
