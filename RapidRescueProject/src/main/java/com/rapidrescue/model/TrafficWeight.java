package com.rapidrescue.model;

// traffic level at current hour
public class TrafficWeight {
    public double weight;
    public String label;
    public int    percent;

    public TrafficWeight(double weight, String label, int percent) {
        this.weight  = weight;
        this.label   = label;
        this.percent = percent;
    }
}
