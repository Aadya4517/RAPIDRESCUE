package com.rapidrescue.model;

import java.util.List;

public class EmergencyRecord {
    public String id, type, subtype;
    public int severity;
    public Location location;
    public List<DispatchedUnit> units;
    public String timestamp;
    public String trafficLabel;

    public EmergencyRecord(String id, String type, String subtype, int severity,
                           Location location, List<DispatchedUnit> units,
                           String timestamp, String trafficLabel) {
        this.id = id; 
        this.type = type; 
        this.subtype = subtype;
        this.severity = severity; 
        this.location = location;
        this.units = units; 
        this.timestamp = timestamp;
        this.trafficLabel = trafficLabel;
    }
}