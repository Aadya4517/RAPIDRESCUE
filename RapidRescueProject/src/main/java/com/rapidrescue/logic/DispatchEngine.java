package com.rapidrescue.logic;

import com.rapidrescue.model.*;
import com.rapidrescue.data.EmergencyDatabase;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

// picks and manages units for emergencies
public class DispatchEngine {

    // get best units for this emergency
    public static List<DispatchedUnit> get_units(int sev, String type, Location loc) {
        TrafficWeight traf    = TrafficRouting.get_traffic();
        WeatherEngine.Weather wx = WeatherEngine.get_weather();
        double wm = wx.eta_mult();

        // sort each group by distance, ready units only
        List<DispatchedUnit> police = EmergencyDatabase.POLICE.stream()
            .filter(u -> u.ready())
            .map(u -> { double d = GeoUtils.dist(loc.lat,loc.lng,u.lat,u.lng); return new DispatchedUnit(u,d,TrafficRouting.calc_eta(d,traf.weight,wm)); })
            .sorted(Comparator.comparingDouble(du -> du.dist_km)).collect(Collectors.toList());

        List<DispatchedUnit> hosp = EmergencyDatabase.HOSPITALS.stream()
            .filter(u -> u.ready())
            .map(u -> { double d = GeoUtils.dist(loc.lat,loc.lng,u.lat,u.lng); return new DispatchedUnit(u,d,TrafficRouting.calc_eta(d,traf.weight,wm)); })
            .sorted(Comparator.comparingDouble(du -> du.dist_km)).collect(Collectors.toList());

        List<DispatchedUnit> fire = EmergencyDatabase.FIRE.stream()
            .filter(u -> u.ready())
            .map(u -> { double d = GeoUtils.dist(loc.lat,loc.lng,u.lat,u.lng); return new DispatchedUnit(u,d,TrafficRouting.calc_eta(d,traf.weight,wm)); })
            .sorted(Comparator.comparingDouble(du -> du.dist_km)).collect(Collectors.toList());

        List<DispatchedUnit> out = new ArrayList<>();
        // greedy allocation by type and severity
        switch (type) {
            case "crime":
                add(out,police,0);
                if (sev>=4) add(out,police,1);
                if (sev>=4) add(out,hosp,0);
                if (sev==5) { add(out,hosp,1); add(out,police,2); }
                break;
            case "fire":
                add(out,fire,0);
                if (sev>=3) add(out,fire,1);
                if (sev>=4) { add(out,hosp,0); add(out,police,0); }
                if (sev==5) add(out,fire,2);
                break;
            case "accident":
                add(out,hosp,0);
                add(out,police,0);
                if (sev>=3) add(out,hosp,1);
                if (sev>=4) add(out,fire,0);
                if (sev==5) add(out,police,1);
                break;
            case "medical":
                add(out,hosp,0);
                if (sev>=3) add(out,hosp,1);
                if (sev>=4) add(out,police,0);
                if (sev==5) add(out,hosp,2);
                break;
        }
        return out;
    }

    // mark units busy, increase hospital load
    public static void set_busy(List<DispatchedUnit> units) {
        for (DispatchedUnit du : units) {
            du.unit.free = false;
            if (du.unit.type.equals("hospital") && du.unit.max_beds > 0)
                du.unit.cur_load = Math.min(du.unit.max_beds, du.unit.cur_load + 1);
        }
    }

    // free a unit by name, decrease hospital load
    public static void set_free(String name) {
        find_and_set(name, true);
    }

    private static void find_and_set(String name, boolean free) {
        EmergencyDatabase.POLICE.stream().filter(u -> u.name.equals(name)).findFirst()
            .ifPresent(u -> u.free = free);
        EmergencyDatabase.HOSPITALS.stream().filter(u -> u.name.equals(name)).findFirst()
            .ifPresent(u -> {
                u.free = free;
                if (free && u.max_beds > 0)
                    u.cur_load = Math.max(0, u.cur_load - 1);
            });
        EmergencyDatabase.FIRE.stream().filter(u -> u.name.equals(name)).findFirst()
            .ifPresent(u -> u.free = free);
    }

    // safe list add by index
    private static void add(List<DispatchedUnit> out, List<DispatchedUnit> src, int i) {
        if (i < src.size()) out.add(src.get(i));
    }
}
