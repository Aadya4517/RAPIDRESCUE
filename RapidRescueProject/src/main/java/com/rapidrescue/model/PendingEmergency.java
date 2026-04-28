package com.rapidrescue.model;

// queued emergency waiting for a free unit
public class PendingEmergency implements Comparable<PendingEmergency> {

    public String   type;
    public String   subtype;
    public int      severity;
    public Location place;
    public String   queued_at;
    public long     queued_ms;
    public int      queue_id;

    public PendingEmergency(int queue_id, String type, String subtype,
                            int severity, Location place, String queued_at) {
        this.queue_id  = queue_id;
        this.type      = type;
        this.subtype   = subtype;
        this.severity  = severity;
        this.place     = place;
        this.queued_at = queued_at;
        this.queued_ms = System.currentTimeMillis();
    }

    // highest severity first, then oldest first
    @Override
    public int compareTo(PendingEmergency o) {
        if (o.severity != this.severity) return o.severity - this.severity;
        return Long.compare(this.queued_ms, o.queued_ms);
    }

    // severity color
    public String sev_color() {
        return switch (severity) {
            case 1 -> "#22c55e"; case 2 -> "#84cc16";
            case 3 -> "#eab308"; case 4 -> "#f97316";
            default -> "#ef4444";
        };
    }

    // how long waiting
    public String wait_label() {
        long s = (System.currentTimeMillis() - queued_ms) / 1000;
        if (s < 60) return s + "s";
        return (s / 60) + "m " + (s % 60) + "s";
    }
}
