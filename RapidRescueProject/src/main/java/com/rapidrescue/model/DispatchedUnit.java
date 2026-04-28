package com.rapidrescue.model;

public class DispatchedUnit {
    public Unit unit;
    public double distKm;
    public double etaMin;

    public DispatchedUnit(Unit unit, double distKm, double etaMin) {
        this.unit = unit; 
        this.distKm = distKm; 
        this.etaMin = etaMin;
    }
}