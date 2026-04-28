code = """package com.rapidrescue.logic;

import com.rapidrescue.model.DispatchedUnit;
import com.rapidrescue.model.Location;
import com.rapidrescue.data.EmergencyDatabase;

import java.util.List;

/**
 * Generates a self-contained Leaflet/OpenStreetMap HTML string for JavaFX WebView.
 * Uses HTTPS tile servers and SVG/CSS markers (no emoji) to avoid encoding issues.
 * WebView network access is enabled via system property in DispatchWindow.
 */
public class MapBuilder {

    public static String buildMapHtml(Location incident, List<DispatchedUnit> units) {
        double lat = incident != null ? incident.lat : 30.3165;
        double lng = incident != null ? incident.lng : 78.0322;

        StringBuilder markers = new StringBuilder();

        // All known unit locations (background dots)
        for (var u : EmergencyDatabase.POLICE) {
            String col = u.available ? "#3b82f6" : "#1e3a5f";
            markers.append(String.format(
                "L.circleMarker([%f,%f],{radius:6,color:'%s',fillColor:'%s',fillOpacity:0.8,weight:1.5})" +
                ".addTo(map).bindPopup('<b>%s</b><br><span style=\\"color:%s\\">%s</span>');\\n",
                u.lat, u.lng, col, col, escapeHtml(u.name), col, u.available ? "Available" : "Busy"));
        }
        for (var u : EmergencyDatabase.HOSPITALS) {
            String col = u.available ? "#22c55e" : "#14532d";
            markers.append(String.format(
                "L.circleMarker([%f,%f],{radius:6,color:'%s',fillColor:'%s',fillOpacity:0.8,weight:1.5})" +
                ".addTo(map).bindPopup('<b>%s</b><br><span style=\\"color:%s\\">%s</span>');\\n",
                u.lat, u.lng, col, col, escapeHtml(u.name), col, u.available ? "Available" : "Busy"));
        }
        for (var u : EmergencyDatabase.FIRE) {
            String col = u.available ? "#f97316" : "#7c2d12";
            markers.append(String.format(
                "L.circleMarker([%f,%f],{radius:6,color:'%s',fillColor:'%s',fillOpacity:0.8,weight:1.5})" +
                ".addTo(map).bindPopup('<b>%s</b><br><span style=\\"color:%s\\">%s</span>');\\n",
                u.lat, u.lng, col, col, escapeHtml(u.name), col, u.available ? "Available" : "Busy"));
        }

        // Incident marker (pulsing red circle)
        if (incident != null) {
            markers.append(String.format(
                "var inc = L.circleMarker([%f,%f],{radius:12,color:'#ef4444',fillColor:'#ef4444',fillOpacity:0.3,weight:3})" +
                ".addTo(map).bindPopup('<b>INCIDENT</b><br>%s').openPopup();\\n" +
                "L.circleMarker([%f,%f],{radius:5,color:'#ef4444',fillColor:'#ef4444',fillOpacity:1,weight:2}).addTo(map);\\n",
                lat, lng, escapeHtml(incident.name), lat, lng));
        }

        // Dispatched unit markers + route lines
        for (DispatchedUnit du : units) {
            String col = switch (du.unit.type) {
                case "police"   -> "#60a5fa";
                case "hospital" -> "#4ade80";
                case "fire"     -> "#fb923c";
                default         -> "#aaaacc";
            };
            String typeLabel = switch (du.unit.type) {
                case "police"   -> "Police";
                case "hospital" -> "Hospital";
                case "fire"     -> "Fire";
                default         -> "Unit";
            };
            markers.append(String.format(
                "L.circleMarker([%f,%f],{radius:9,color:'%s',fillColor:'%s',fillOpacity:0.9,weight:2})" +
                ".addTo(map).bindPopup('<b>%s</b><br>%s<br>%.2f km | ETA %.1f min');\\n" +
                "L.polyline([[%f,%f],[%f,%f]],{color:'%s',weight:2,opacity:0.7,dashArray:'6,4'}).addTo(map);\\n",
                du.unit.lat, du.unit.lng, col, col,
                escapeHtml(du.unit.name), typeLabel, du.distKm, du.etaMin,
                du.unit.lat, du.unit.lng, lat, lng, col));
        }

        String markersStr = markers.toString();
        return "<!DOCTYPE html>\\n" +
            "<html>\\n<head>\\n" +
            "<meta charset=\\"utf-8\\"/>\\n" +
            "<link rel=\\"stylesheet\\" href=\\"https://unpkg.com/leaflet@1.9.4/dist/leaflet.css\\"/>\\n" +
            "<script src=\\"https://unpkg.com/leaflet@1.9.4/dist/leaflet.js\\"></script>\\n" +
            "<style>\\n" +
            "html,body,#map{margin:0;padding:0;width:100%;height:100%;background:#0d1117;}\\n" +
            ".leaflet-popup-content-wrapper{background:#1a1a28;color:#ccccee;border:1px solid #3a3a5a;border-radius:8px;font-size:12px;}\\n" +
            ".leaflet-popup-tip{background:#1a1a28;}\\n" +
            ".leaflet-tile-pane{filter:brightness(0.85) saturate(0.7) hue-rotate(200deg);}\\n" +
            "</style>\\n</head>\\n<body>\\n" +
            "<div id=\\"map\\"></div>\\n" +
            "<script>\\n" +
            "var map=L.map('map',{center:[" + lat + "," + lng + "],zoom:13,preferCanvas:true});\\n" +
            "L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',{" +
            "attribution:'&copy; OpenStreetMap contributors',maxZoom:19,crossOrigin:true}).addTo(map);\\n" +
            markersStr +
            "</script>\\n</body>\\n</html>";
    }

    public static String buildBlankMapHtml() {
        return buildMapHtml(null, List.of());
    }

    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\\"","&quot;").replace("'","&#39;");
    }
}
"""
open("RapidRescueProject/src/main/java/com/rapidrescue/logic/MapBuilder.java","w",encoding="utf-8").write(code)
print("MapBuilder written")
