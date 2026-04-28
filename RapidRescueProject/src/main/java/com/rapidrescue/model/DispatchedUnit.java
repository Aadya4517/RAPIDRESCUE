package com.rapidrescue.model;

// unit assigned to an emergency
public class DispatchedUnit {
    public Unit   unit;
    public double dist_km;
    public double eta_min;

    public DispatchedUnit(Unit unit, double dist_km, double eta_min) {
        this.unit    = unit;
        this.dist_km = dist_km;
        this.eta_min = eta_min;
    }
}
