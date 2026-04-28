package com.rapidrescue.model;

// alert sent to a responder unit
public class ResponderAlert {

    // status flow
    public enum Status { PENDING, ACKNOWLEDGED, EN_ROUTE, ARRIVED }

    public int    id;
    public String emergency_id;
    public String unit_name;
    public String unit_icon;
    public String unit_type;
    public String place;
    public String emg_type;
    public String emg_subtype;
    public int    severity;
    public double dist_km;
    public double eta_min;
    public String sent_at;
    public Status status;
    public long   sent_ms;   // epoch ms at dispatch

    public ResponderAlert(int id, String emergency_id, String unit_name, String unit_icon,
                          String unit_type, String place, String emg_type,
                          String emg_subtype, int severity, double dist_km,
                          double eta_min, String sent_at) {
        this.id           = id;
        this.emergency_id = emergency_id;
        this.unit_name    = unit_name;
        this.unit_icon    = unit_icon;
        this.unit_type    = unit_type;
        this.place        = place;
        this.emg_type     = emg_type;
        this.emg_subtype  = emg_subtype;
        this.severity     = severity;
        this.dist_km      = dist_km;
        this.eta_min      = eta_min;
        this.sent_at      = sent_at;
        this.status       = Status.PENDING;
        this.sent_ms      = System.currentTimeMillis();
    }

    // status display text
    public String status_label() {
        return switch (status) {
            case PENDING      -> "Pending";
            case ACKNOWLEDGED -> "Acknowledged";
            case EN_ROUTE     -> "En Route";
            case ARRIVED      -> "Arrived";
        };
    }

    // status color
    public String status_color() {
        return switch (status) {
            case PENDING      -> "#eab308";
            case ACKNOWLEDGED -> "#3b82f6";
            case EN_ROUTE     -> "#f97316";
            case ARRIVED      -> "#22c55e";
        };
    }

    // seconds left until ETA
    public long secs_left() {
        long eta_ms = sent_ms + (long)(eta_min * 60 * 1000);
        return (eta_ms - System.currentTimeMillis()) / 1000;
    }

    // countdown text e.g. 4:32 or OVERDUE
    public String countdown() {
        if (status == Status.ARRIVED) return "Arrived";
        long s = secs_left();
        if (s <= 0) return "OVERDUE";
        return String.format("%d:%02d", s / 60, s % 60);
    }

    // countdown color
    public String countdown_color() {
        if (status == Status.ARRIVED) return "#22c55e";
        long s = secs_left();
        if (s <= 0)  return "#ef4444";
        if (s <= 60) return "#f97316";
        return "#eab308";
    }
}
