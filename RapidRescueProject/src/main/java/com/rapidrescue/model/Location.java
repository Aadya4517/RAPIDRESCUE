package com.rapidrescue.model;

public class Location {
    public String key, name;
    public double lat, lng;

    public Location(String key, String name, double lat, double lng) {
        this.key = key; 
        this.name = name; 
        this.lat = lat; 
        this.lng = lng;
    }

    @Override 
    public String toString() { 
        return name; 
    }
}