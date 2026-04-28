package com.rapidrescue.model;

/**
 * Represents a dispatch alert sent to a specific responder unit.
 * Status lifecycle: PENDING → ACKNOWLEDGED → EN_ROUTE → ARRIVED
 */
public class ResponderAlert {

    public enum Status { PENDING, ACKNOWLEDGED, EN_ROUTE, ARRIVED }

    public int    id;
    public String emergencyId;
    public String unitName;
    public String unitIcon;
    public String unitType;
    public String incidentLocation;
    public String emergencyType;
    public String emergencySubtype;
    public int    severity;
    public double distKm;
    public double etaMin;
    public String dispatchedAt;
    public Status status;
    public long   dispatchTimeMs;  // System.currentTimeMillis() at dispatch time

    public ResponderAlert(int id, String emergencyId, String unitName, String unitIcon,
                          String unitType, String incidentLocation, String emergencyType,
                          String emergencySubtype, int severity, double distKm,
                          double etaMin, String dispatchedAt) {
        this.id               = id;
        this.emergencyId      = emergencyId;
        this.unitName         = unitName;
        this.unitIcon         = unitIcon;
        this.unitType         = unitType;
        this.incidentLocation = incidentLocation;
        this.emergencyType    = emergencyType;
        this.emergencySubtype = emergencySubtype;
        this.severity         = severity;
        this.distKm           = distKm;
        this.etaMin           = etaMin;
        this.dispatchedAt     = dispatchedAt;
        this.status           = Status.PENDING;
        this.dispatchTimeMs   = System.currentTimeMillis();
    }

    public String getStatusLabel() {
        return switch (status) {
            case PENDING      -> "Pending";
            case ACKNOWLEDGED -> "Acknowledged";
            case EN_ROUTE     -> "En Route";
            case ARRIVED      -> "Arrived";
        };
    }

    public String getStatusColor() {
        return switch (status) {
            case PENDING      -> "#eab308";
            case ACKNOWLEDGED -> "#3b82f6";
            case EN_ROUTE     -> "#f97316";
            case ARRIVED      -> "#22c55e";
        };
    }

    /** Returns remaining seconds until ETA (negative = overdue) */
    public long getRemainingSeconds() {
        long etaMs = dispatchTimeMs + (long)(etaMin * 60 * 1000);
        return (etaMs - System.currentTimeMillis()) / 1000;
    }

    /** Formatted countdown string e.g. "4:32" or "OVERDUE" */
    public String getCountdownLabel() {
        if (status == Status.ARRIVED) return "Arrived";
        long secs = getRemainingSeconds();
        if (secs <= 0) return "OVERDUE";
        long m = secs / 60, s = secs % 60;
        return String.format("%d:%02d", m, s);
    }

    public String getCountdownColor() {
        if (status == Status.ARRIVED) return "#22c55e";
        long secs = getRemainingSeconds();
        if (secs <= 0)  return "#ef4444";
        if (secs <= 60) return "#f97316";
        return "#eab308";
    }
}
