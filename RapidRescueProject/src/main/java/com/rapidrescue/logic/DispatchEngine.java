package com.rapidrescue.logic;

import com.rapidrescue.model.*;
import com.rapidrescue.data.EmergencyDatabase;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class DispatchEngine {

    public static List<DispatchedUnit> getUnitsForSeverity(int sev, String type, Location loc) {
        TrafficWeight traf = TrafficRouting.getTrafficWeight();
        WeatherEngine.WeatherCondition weather = WeatherEngine.getCurrentWeather();
        double wm = weather.etaMultiplier();

        List<DispatchedUnit> pl = EmergencyDatabase.POLICE.stream()
            .filter(u -> u.isDeployable())
            .map(u -> { double d = GeoUtils.haversine(loc.lat,loc.lng,u.lat,u.lng); return new DispatchedUnit(u,d,TrafficRouting.dijkstraETA(d,traf.weight,wm)); })
            .sorted(Comparator.comparingDouble(du -> du.distKm)).collect(Collectors.toList());

        List<DispatchedUnit> hl = EmergencyDatabase.HOSPITALS.stream()
            .filter(u -> u.isDeployable())
            .map(u -> { double d = GeoUtils.haversine(loc.lat,loc.lng,u.lat,u.lng); return new DispatchedUnit(u,d,TrafficRouting.dijkstraETA(d,traf.weight,wm)); })
            .sorted(Comparator.comparingDouble(du -> du.distKm)).collect(Collectors.toList());

        List<DispatchedUnit> fl = EmergencyDatabase.FIRE.stream()
            .filter(u -> u.isDeployable())
            .map(u -> { double d = GeoUtils.haversine(loc.lat,loc.lng,u.lat,u.lng); return new DispatchedUnit(u,d,TrafficRouting.dijkstraETA(d,traf.weight,wm)); })
            .sorted(Comparator.comparingDouble(du -> du.distKm)).collect(Collectors.toList());

        List<DispatchedUnit> result = new ArrayList<>();
        switch (type) {
            case "crime":
                safeAdd(result,pl,0);
                if (sev>=4) safeAdd(result,pl,1);
                if (sev>=4) safeAdd(result,hl,0);
                if (sev==5) { safeAdd(result,hl,1); safeAdd(result,pl,2); }
                break;
            case "fire":
                safeAdd(result,fl,0);
                if (sev>=3) safeAdd(result,fl,1);
                if (sev>=4) { safeAdd(result,hl,0); safeAdd(result,pl,0); }
                if (sev==5) safeAdd(result,fl,2);
                break;
            case "accident":
                safeAdd(result,hl,0);
                safeAdd(result,pl,0);
                if (sev>=3) safeAdd(result,hl,1);
                if (sev>=4) safeAdd(result,fl,0);
                if (sev==5) safeAdd(result,pl,1);
                break;
            case "medical":
                safeAdd(result,hl,0);
                if (sev>=3) safeAdd(result,hl,1);
                if (sev>=4) safeAdd(result,pl,0);
                if (sev==5) safeAdd(result,hl,2);
                break;
        }
        return result;
    }

    /** Mark dispatched units as busy and increase hospital load */
    public static void markBusy(List<DispatchedUnit> units) {
        for (DispatchedUnit du : units) {
            du.unit.available = false;
            if (du.unit.type.equals("hospital") && du.unit.capacity > 0) {
                du.unit.currentLoad = Math.min(du.unit.capacity, du.unit.currentLoad + 1);
            }
        }
    }

    /** Mark a unit as available again and decrease hospital load */
    public static void markAvailable(String unitName) {
        setAvailability(unitName, true);
    }

    private static void setAvailability(String unitName, boolean available) {
        EmergencyDatabase.POLICE.stream().filter(u -> u.name.equals(unitName)).findFirst()
            .ifPresent(u -> u.available = available);
        EmergencyDatabase.HOSPITALS.stream().filter(u -> u.name.equals(unitName)).findFirst()
            .ifPresent(u -> {
                u.available = available;
                if (available && u.capacity > 0) {
                    u.currentLoad = Math.max(0, u.currentLoad - 1);
                }
            });
        EmergencyDatabase.FIRE.stream().filter(u -> u.name.equals(unitName)).findFirst()
            .ifPresent(u -> u.available = available);
    }

    private static void safeAdd(List<DispatchedUnit> result, List<DispatchedUnit> src, int idx) {
        if (idx < src.size()) result.add(src.get(idx));
    }
}
