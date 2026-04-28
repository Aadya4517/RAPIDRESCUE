package com.rapidrescue.model;

import java.util.List;

// one dispatched emergency
public class EmergencyRecord {
    public String id, type, subtype;
    public int    severity;
    public Location  place;
    public List<DispatchedUnit> units;
    public String time_str;
    public String traffic_label;

    public EmergencyRecord(String id, String type, String subtype, int severity,
                           Location place, List<DispatchedUnit> units,
                           String time_str, String traffic_label) {
        this.id            = id;
        this.type          = type;
        this.subtype       = subtype;
        this.severity      = severity;
        this.place         = place;
        this.units         = units;
        this.time_str      = time_str;
        this.traffic_label = traffic_label;
    }
}
