package com.rapidrescue.model;

import java.util.List;

/**
 * An emergency waiting in the priority queue because no units were available.
 * Sorted by severity (highest first), then by time queued (oldest first).
 */
public class PendingEmergency implements Comparable<PendingEmergency> {

    public String   type;
    public String   subtype;
    public int      severity;
    public Location location;
    public String   queuedAt;
    public long     queuedTimeMs;
    public int      queueId;

    public PendingEmergency(int queueId, String type, String subtype,
                             int severity, Location location, String queuedAt) {
        this.queueId      = queueId;
        this.type         = type;
        this.subtype      = subtype;
        this.severity     = severity;
        this.location     = location;
        this.queuedAt     = queuedAt;
        this.queuedTimeMs = System.currentTimeMillis();
    }

    @Override
    public int compareTo(PendingEmergency other) {
        // Higher severity first
        if (other.severity != this.severity) return other.severity - this.severity;
        // Older first (lower timestamp)
        return Long.compare(this.queuedTimeMs, other.queuedTimeMs);
    }

    public String getSeverityColor() {
        return switch (severity) {
            case 1 -> "#22c55e"; case 2 -> "#84cc16";
            case 3 -> "#eab308"; case 4 -> "#f97316";
            default -> "#ef4444";
        };
    }

    public String getTypeIcon() {
        return switch (type) {
            case "crime"    -> "[CRIME]";
            case "fire"     -> "[FIRE]";
            case "accident" -> "[ACCIDENT]";
            default         -> "[MEDICAL]";
        };
    }

    /** How long this emergency has been waiting */
    public String getWaitLabel() {
        long secs = (System.currentTimeMillis() - queuedTimeMs) / 1000;
        if (secs < 60) return secs + "s";
        return (secs / 60) + "m " + (secs % 60) + "s";
    }
}
