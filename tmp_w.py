f = open("RapidRescueProject/src/main/java/com/rapidrescue/logic/MapBuilder.java","w",encoding="utf-8")
f.write("""package com.rapidrescue.logic;

import com.rapidrescue.model.DispatchedUnit;
import com.rapidrescue.model.Location;
import com.rapidrescue.data.EmergencyDatabase;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

// builds Leaflet HTML for the map WebView
public class MapBuilder {

    private static String leaflet_js  = null;
    private static String leaflet_css = null;

    // try multiple paths to find the file
    private static String read_file(String rel_path) {
        String[] bases = {
            System.getProperty("user.dir"),
            System.getProperty("user.dir") + "/RapidRescueProject",
            ".",
            "RapidRescueProject"
        };
        for (String base : bases) {
            try {
                Path p = Paths.get(base, rel_path);
                if (p.toFile().exists()) return Files.readString(p);
            } catch (IOException ignored) {}
        }
        System.err.println("[Map] file not found: " + rel_path);
        return "";
    }

    private static String js() {
        if (leaflet_js == null) leaflet_js = read_file("lib/leaflet/leaflet.js");
        return leaflet_js;
    }

    private static String css() {
        if (leaflet_css == null) leaflet_css = read_file("lib/leaflet/leaflet.css");
        return leaflet_css;
    }

    public static String buildMapHtml(Location incident, List<DispatchedUnit> dispatched) {
        double lat = incident != null ? incident.lat : 30.3165;
        double lng = incident != null ? incident.lng : 78.0322;

        StringBuilder mk = new StringBuilder();

        // all unit dots
        for (var u : EmergencyDatabase.POLICE) {
            String col = !u.on_shift() ? "#4b5563" : (u.free ? "#3b82f6" : "#1e3a5f");
            String st  = !u.on_shift() ? "Off Shift" : (u.free ? "Available" : "Busy");
            mk.append(String.format(
                "L.circleMarker([%f,%f],{radius:5,color:'%s',fillColor:'%s',fillOpacity:0.85,weight:1.5})" +
                ".addTo(map).bindPopup('<b>%s</b><br>Police | <span style=color:%s>%s</span><br>Shift: %s');\\n",
                u.lat,u.lng,col,col,esc(u.name),col,st,u.shift_label()));
        }
        for (var u : EmergencyDatabase.HOSPITALS) {
            String col = !u.on_shift() ? "#4b5563" : (u.free ? "#22c55e" : "#14532d");
            String st  = !u.on_shift() ? "Off Shift" : (u.free ? "Available" : "Busy");
            String load = u.max_beds > 0 ? " | Load: " + u.load_pct() + "%" : "";
            mk.append(String.format(
                "L.circleMarker([%f,%f],{radius:5,color:'%s',fillColor:'%s',fillOpacity:0.85,weight:1.5})" +
                ".addTo(map).bindPopup('<b>%s</b><br>Hospital | <span style=color:%s>%s</span>%s<br>Shift: %s');\\n",
                u.lat,u.lng,col,col,esc(u.name),col,st,load,u.shift_label()));
        }
        for (var u : EmergencyDatabase.FIRE) {
            String col = !u.on_shift() ? "#4b5563" : (u.free ? "#f97316" : "#7c2d12");
            String st  = !u.on_shift() ? "Off Shift" : (u.free ? "Available" : "Busy");
            mk.append(String.format(
                "L.circleMarker([%f,%f],{radius:5,color:'%s',fillColor:'%s',fillOpacity:0.85,weight:1.5})" +
                ".addTo(map).bindPopup('<b>%s</b><br>Fire | <span style=color:%s>%s</span><br>Shift: %s');\\n",
                u.lat,u.lng,col,col,esc(u.name),col,st,u.shift_label()));
        }

        // incident marker
        if (incident != null) {
            mk.append(String.format(
                "L.circleMarker([%f,%f],{radius:14,color:'#ef4444',fillColor:'#ef4444',fillOpacity:0.15,weight:3}).addTo(map);\\n" +
                "L.circleMarker([%f,%f],{radius:7,color:'#ef4444',fillColor:'#ef4444',fillOpacity:1,weight:2})" +
                ".addTo(map).bindPopup('<b>INCIDENT</b><br>%s').openPopup();\\n",
                lat,lng,lat,lng,esc(incident.name)));
        }

        // dispatched units + route lines
        for (DispatchedUnit du : dispatched) {
            String col = switch (du.unit.type) {
                case "police"   -> "#60a5fa";
                case "hospital" -> "#4ade80";
                case "fire"     -> "#fb923c";
                default         -> "#aaaacc";
            };
            mk.append(String.format(
                "L.circleMarker([%f,%f],{radius:10,color:'%s',fillColor:'%s',fillOpacity:0.95,weight:2.5})" +
                ".addTo(map).bindPopup('<b>%s</b><br>%.2f km | ETA %.1f min');\\n" +
                "L.polyline([[%f,%f],[%f,%f]],{color:'%s',weight:2.5,opacity:0.8,dashArray:'8,5'}).addTo(map);\\n",
                du.unit.lat,du.unit.lng,col,col,
                esc(du.unit.name),du.dist_km,du.eta_min,
                du.unit.lat,du.unit.lng,lat,lng,col));
        }

        // legend
        mk.append("var lg=L.control({position:'bottomright'});");
        mk.append("lg.onAdd=function(){var d=L.DomUtil.create('div');");
        mk.append("d.style.cssText='background:#1a1a28;padding:8px 12px;border-radius:8px;border:1px solid #3a3a5a;color:#ccccee;font-size:11px;line-height:1.8;';");
        mk.append("d.innerHTML='<b style=color:#fff>Legend</b><br>");
        mk.append("<span style=color:#ef4444>&#9679;</span> Incident<br>");
        mk.append("<span style=color:#60a5fa>&#9679;</span> Police<br>");
        mk.append("<span style=color:#4ade80>&#9679;</span> Hospital<br>");
        mk.append("<span style=color:#fb923c>&#9679;</span> Fire<br>");
        mk.append("<span style=color:#4b5563>&#9679;</span> Off Shift';");
        mk.append("return d;};lg.addTo(map);\\n");

        String leaflet_loaded = js();
        boolean has_js = !leaflet_loaded.isEmpty();

        return "<!DOCTYPE html><html><head><meta charset=\\"utf-8\\"/>" +
            "<style>" + css() + "</style>" +
            "<style>html,body,#map{margin:0;padding:0;width:100%;height:100%;background:#0d1117;}" +
            ".leaflet-popup-content-wrapper{background:#1a1a28;color:#ccccee;border:1px solid #3a3a5a;border-radius:8px;font-size:12px;}" +
            ".leaflet-popup-tip{background:#1a1a28;}" +
            ".leaflet-tile-pane{filter:brightness(0.8) saturate(0.6) hue-rotate(195deg);}" +
            ".leaflet-control-attribution{background:#1a1a28!important;color:#555!important;}</style>" +
            (has_js ? "<script>" + leaflet_loaded + "</script>" :
             "<link rel=\\"stylesheet\\" href=\\"https://unpkg.com/leaflet@1.9.4/dist/leaflet.css\\"/>" +
             "<script src=\\"https://unpkg.com/leaflet@1.9.4/dist/leaflet.js\\"></script>") +
            "</head><body><div id=\\"map\\"></div><script>" +
            "var map=L.map('map',{center:[" + lat + "," + lng + "],zoom:13,preferCanvas:true});" +
            "L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',{attribution:'&copy; OpenStreetMap',maxZoom:19}).addTo(map);" +
            mk.toString() +
            "</script></body></html>";
    }

    public static String buildBlankMapHtml() { return buildMapHtml(null, List.of()); }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;")
                .replace("\\"","&quot;").replace("'","&#39;");
    }
}
""")
f.close()
print("MapBuilder done")
