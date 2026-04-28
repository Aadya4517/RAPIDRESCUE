package com.rapidrescue.logic;

import com.rapidrescue.model.DispatchedUnit;
import com.rapidrescue.model.Location;
import com.rapidrescue.data.EmergencyDatabase;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

/**
 * Generates a self-contained Leaflet map HTML for JavaFX WebView.
 * Leaflet JS/CSS are loaded from local files (no internet needed).
 * Tiles still load from OpenStreetMap (requires internet for tiles only).
 */
public class MapBuilder {

    private static String leafletJs  = null;
    private static String leafletCss = null;

    private static String getLeafletJs() {
        if (leafletJs == null) {
            try {
                leafletJs = Files.readString(Paths.get("RapidRescueProject/lib/leaflet/leaflet.js"));
            } catch (IOException e) {
                leafletJs = "";
                System.err.println("[MapBuilder] Could not load leaflet.js: " + e.getMessage());
            }
        }
        return leafletJs;
    }

    private static String getLeafletCss() {
        if (leafletCss == null) {
            try {
                leafletCss = Files.readString(Paths.get("RapidRescueProject/lib/leaflet/leaflet.css"));
            } catch (IOException e) {
                leafletCss = "";
                System.err.println("[MapBuilder] Could not load leaflet.css: " + e.getMessage());
            }
        }
        return leafletCss;
    }

    public static String buildMapHtml(Location incident, List<DispatchedUnit> dispatched) {
        double lat = incident != null ? incident.lat : 30.3165;
        double lng = incident != null ? incident.lng : 78.0322;

        StringBuilder js = new StringBuilder();

        // All unit background dots
        for (var u : EmergencyDatabase.POLICE) {
            String col = !u.on_shift() ? "#4b5563" : (u.free ? "#3b82f6" : "#1e3a5f");
            String status = !u.on_shift() ? "Off Shift" : (u.free ? "Available" : "Busy");
            js.append(String.format(
                "L.circleMarker([%f,%f],{radius:5,color:'%s',fillColor:'%s',fillOpacity:0.85,weight:1.5})" +
                ".addTo(map).bindPopup('<b>%s</b><br>Police<br><span style=color:%s>%s</span><br>Shift: %s');\n",
                u.lat, u.lng, col, col, esc(u.name), col, status, u.shift_label()));
        }
        for (var u : EmergencyDatabase.HOSPITALS) {
            String col = !u.on_shift() ? "#4b5563" : (u.free ? "#22c55e" : "#14532d");
            String status = !u.on_shift() ? "Off Shift" : (u.free ? "Available" : "Busy");
            js.append(String.format(
                "L.circleMarker([%f,%f],{radius:5,color:'%s',fillColor:'%s',fillOpacity:0.85,weight:1.5})" +
                ".addTo(map).bindPopup('<b>%s</b><br>Hospital<br><span style=color:%s>%s</span><br>Shift: %s');\n",
                u.lat, u.lng, col, col, esc(u.name), col, status, u.shift_label()));
        }
        for (var u : EmergencyDatabase.FIRE) {
            String col = !u.on_shift() ? "#4b5563" : (u.free ? "#f97316" : "#7c2d12");
            String status = !u.on_shift() ? "Off Shift" : (u.free ? "Available" : "Busy");
            js.append(String.format(
                "L.circleMarker([%f,%f],{radius:5,color:'%s',fillColor:'%s',fillOpacity:0.85,weight:1.5})" +
                ".addTo(map).bindPopup('<b>%s</b><br>Fire<br><span style=color:%s>%s</span><br>Shift: %s');\n",
                u.lat, u.lng, col, col, esc(u.name), col, status, u.shift_label()));
        }

        // Incident marker
        if (incident != null) {
            js.append(String.format(
                "L.circleMarker([%f,%f],{radius:14,color:'#ef4444',fillColor:'#ef4444',fillOpacity:0.15,weight:3}).addTo(map);\n" +
                "L.circleMarker([%f,%f],{radius:7,color:'#ef4444',fillColor:'#ef4444',fillOpacity:1,weight:2})" +
                ".addTo(map).bindPopup('<b>INCIDENT</b><br>%s').openPopup();\n",
                lat, lng, lat, lng, esc(incident.name)));
        }

        // Dispatched units + route lines
        for (DispatchedUnit du : dispatched) {
            String col = switch (du.unit.type) {
                case "police"   -> "#60a5fa";
                case "hospital" -> "#4ade80";
                case "fire"     -> "#fb923c";
                default         -> "#aaaacc";
            };
            js.append(String.format(
                "L.circleMarker([%f,%f],{radius:10,color:'%s',fillColor:'%s',fillOpacity:0.95,weight:2.5})" +
                ".addTo(map).bindPopup('<b>%s</b><br>%.2f km | ETA %.1f min');\n" +
                "L.polyline([[%f,%f],[%f,%f]],{color:'%s',weight:2.5,opacity:0.8,dashArray:'8,5'}).addTo(map);\n",
                du.unit.lat, du.unit.lng, col, col,
                esc(du.unit.name), du.dist_km, du.eta_min,
                du.unit.lat, du.unit.lng, lat, lng, col));
        }

        // Legend
        js.append("var legend=L.control({position:'bottomright'});");
        js.append("legend.onAdd=function(){var d=L.DomUtil.create('div');");
        js.append("d.style.cssText='background:#1a1a28;padding:8px 12px;border-radius:8px;border:1px solid #3a3a5a;color:#ccccee;font-size:11px;line-height:1.8;';");
        js.append("d.innerHTML='<b style=color:#ffffff>Legend</b><br>");
        js.append("<span style=color:#ef4444>&#9679;</span> Incident<br>");
        js.append("<span style=color:#60a5fa>&#9679;</span> Police<br>");
        js.append("<span style=color:#4ade80>&#9679;</span> Hospital<br>");
        js.append("<span style=color:#fb923c>&#9679;</span> Fire<br>");
        js.append("<span style=color:#4b5563>&#9679;</span> Off Shift';");
        js.append("return d;};legend.addTo(map);\n");

        String css = getLeafletCss();
        String jsLib = getLeafletJs();

        return "<!DOCTYPE html><html><head><meta charset=\"utf-8\"/>" +
            "<style>" + css + "</style>" +
            "<style>" +
            "html,body,#map{margin:0;padding:0;width:100%;height:100%;background:#0d1117;}" +
            ".leaflet-popup-content-wrapper{background:#1a1a28;color:#ccccee;border:1px solid #3a3a5a;border-radius:8px;font-size:12px;}" +
            ".leaflet-popup-tip{background:#1a1a28;}" +
            ".leaflet-tile-pane{filter:brightness(0.8) saturate(0.6) hue-rotate(195deg);}" +
            ".leaflet-control-attribution{background:#1a1a28!important;color:#555!important;}" +
            "</style>" +
            "<script>" + jsLib + "</script>" +
            "</head><body><div id=\"map\"></div><script>" +
            "var map=L.map('map',{center:[" + lat + "," + lng + "],zoom:13,preferCanvas:true});" +
            "L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png'," +
            "{attribution:'&copy; OpenStreetMap',maxZoom:19}).addTo(map);" +
            js.toString() +
            "</script></body></html>";
    }

    public static String buildBlankMapHtml() {
        return buildMapHtml(null, List.of());
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;")
                .replace("\"","&quot;").replace("'","&#39;");
    }
}
