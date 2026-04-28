package com.rapidrescue.data;

import com.rapidrescue.model.Location;
import com.rapidrescue.model.Unit;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class EmergencyDatabase {
    
    // 📍 PRECISE DEHRADUN INCIDENT LOCATIONS
    public static final List<Location> LOCATIONS = Arrays.asList(
        new Location("isbt",        "ISBT Bus Stand",              30.2885, 78.0015),
        new Location("paltan",      "Paltan Bazaar",               30.3201, 78.0366),
        new Location("clock",       "Clock Tower (Ghanta Ghar)",   30.3241, 78.0339),
        new Location("rajpur",      "Rajpur Road",                 30.3479, 78.0667),
        new Location("dehradun_rly","Dehradun Railway Station",    30.3167, 78.0333),
        new Location("clement",     "Clement Town",                30.2666, 78.0100),
        new Location("prem",        "Prem Nagar",                  30.3342, 77.9625),
        new Location("saharanpur",  "Saharanpur Road",             30.2650, 78.0000),
        new Location("rispana",     "Rispana Bridge",              30.3015, 78.0312),
        new Location("danda",       "Danda Lakhaund",              30.3600, 78.0800),
        new Location("raipur",      "Raipur Road",                 30.3115, 78.0850),
        new Location("niranjanpur", "Niranjanpur",                 30.3060, 78.0100),
        new Location("bharuwala",   "Bharuwala Grant",             30.2850, 78.0200)
    );

    // 🚔 POLICE STATION LOCATIONS + SHIFTS
    public static final List<Unit> POLICE = Arrays.asList(
        new Unit("ISBT Police Chowki",       "P", "police", 30.2880, 78.0010).shift(0,  24), // 24/7
        new Unit("Kotwali Paltan Bazaar",    "P", "police", 30.3195, 78.0350).shift(0,  24), // 24/7
        new Unit("Rajpur Police Station",    "P", "police", 30.3837, 78.0907).shift(8,  20), // day
        new Unit("Clement Town Police Stn",  "P", "police", 30.2655, 78.0110).shift(8,  20), // day
        new Unit("Prem Nagar Police Post",   "P", "police", 30.3350, 77.9630).shift(6,  22), // extended
        new Unit("Nehru Colony Police",      "P", "police", 30.3005, 78.0375).shift(0,  24), // 24/7
        new Unit("Raipur Police Station",    "P", "police", 30.3120, 78.0860).shift(8,  20)  // day
    );

    // 🚑 HOSPITAL LOCATIONS + SHIFTS + CAPACITY
    public static final List<Unit> HOSPITALS = Arrays.asList(
        new Unit("Doon Medical Hospital",  "H", "hospital", 30.3180, 78.0350).shift(0,24).capacity(300),
        new Unit("Max Super Speciality",   "H", "hospital", 30.3758, 78.0736).shift(0,24).capacity(200),
        new Unit("Synergy Hospital",       "H", "hospital", 30.3342, 78.0125).shift(0,24).capacity(150),
        new Unit("Shri Mahant Indiresh",   "H", "hospital", 30.3060, 78.0197).shift(0,24).capacity(400),
        new Unit("Velmed Hospital",        "H", "hospital", 30.3055, 78.0135).shift(8,20).capacity(80),
        new Unit("Jolly Grant Hospital",   "H", "hospital", 30.1915, 78.1755).shift(0,24).capacity(250)
    );

    // 🚒 FIRE STATION LOCATIONS + SHIFTS
    public static final List<Unit> FIRE = Arrays.asList(
        new Unit("Fire Station HQ (Gandhi Park)", "F", "fire", 30.3245, 78.0380).shift(0,  24), // 24/7
        new Unit("Fire Station Clement Town",     "F", "fire", 30.2670, 78.0120).shift(6,  22), // extended
        new Unit("Fire Station Sahastradhara",    "F", "fire", 30.3500, 78.0650).shift(8,  20)  // day
    );

    // 🔴 EMERGENCY SUBTYPES
    public static final Map<String, List<String>> SUBTYPES = new LinkedHashMap<>();
    static {
        SUBTYPES.put("crime",    Arrays.asList("Armed robbery","Chain snatching","Vehicle theft","Burglary","Assault","Mugging"));
        SUBTYPES.put("fire",     Arrays.asList("Industrial fire","House fire","Forest fire","Gas leak","Explosion","Electrical fire"));
        SUBTYPES.put("accident", Arrays.asList("Road accident","Bike collision","Pedestrian hit","Bus accident","Site accident","Auto collision"));
        SUBTYPES.put("medical",  Arrays.asList("Cardiac arrest","Stroke","Trauma/injury","Critical illness","Snake bite","Obstetric emergency"));
    }

    // 🎯 AUTO-SEVERITY: default severity per subtype (1=Minor .. 5=Critical)
    public static final Map<String, Integer> SUBTYPE_SEVERITY = new LinkedHashMap<>();
    static {
        // Crime
        SUBTYPE_SEVERITY.put("Armed robbery",    5);
        SUBTYPE_SEVERITY.put("Chain snatching",  3);
        SUBTYPE_SEVERITY.put("Vehicle theft",    2);
        SUBTYPE_SEVERITY.put("Burglary",         3);
        SUBTYPE_SEVERITY.put("Assault",          4);
        SUBTYPE_SEVERITY.put("Mugging",          3);
        // Fire
        SUBTYPE_SEVERITY.put("Industrial fire",  5);
        SUBTYPE_SEVERITY.put("House fire",       4);
        SUBTYPE_SEVERITY.put("Forest fire",      4);
        SUBTYPE_SEVERITY.put("Gas leak",         5);
        SUBTYPE_SEVERITY.put("Explosion",        5);
        SUBTYPE_SEVERITY.put("Electrical fire",  3);
        // Accident
        SUBTYPE_SEVERITY.put("Road accident",    3);
        SUBTYPE_SEVERITY.put("Bike collision",   2);
        SUBTYPE_SEVERITY.put("Pedestrian hit",   4);
        SUBTYPE_SEVERITY.put("Bus accident",     5);
        SUBTYPE_SEVERITY.put("Site accident",    4);
        SUBTYPE_SEVERITY.put("Auto collision",   3);
        // Medical
        SUBTYPE_SEVERITY.put("Cardiac arrest",   5);
        SUBTYPE_SEVERITY.put("Stroke",           5);
        SUBTYPE_SEVERITY.put("Trauma/injury",    4);
        SUBTYPE_SEVERITY.put("Critical illness", 4);
        SUBTYPE_SEVERITY.put("Snake bite",       3);
        SUBTYPE_SEVERITY.put("Obstetric emergency", 4);
    }

    /** Returns the recommended severity for a given subtype (default 3 if unknown) */
    public static int getAutoSeverity(String subtype) {
        return SUBTYPE_SEVERITY.getOrDefault(subtype, 3);
    }
}