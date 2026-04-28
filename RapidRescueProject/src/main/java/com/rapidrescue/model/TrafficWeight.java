package com.rapidrescue.model;

public class TrafficWeight {
    public double weight;
    public String label;
    public int percent;

    public TrafficWeight(double weight, String label, int percent) {
        this.weight = weight; 
        this.label = label; 
        this.percent = percent;
    }
}