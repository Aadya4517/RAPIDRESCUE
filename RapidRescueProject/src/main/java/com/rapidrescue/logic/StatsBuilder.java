package com.rapidrescue.logic;

import com.rapidrescue.model.EmergencyRecord;
import com.rapidrescue.model.ResponderAlert;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.*;

public class StatsBuilder {

    private static String chartJs = null;

    private static String getChartJs() {
        if (chartJs == null) {
            try { chartJs = Files.readString(Paths.get("RapidRescueProject/lib/chart.min.js")); }
            catch (IOException e) { chartJs = ""; System.err.println("[StatsBuilder] chart.min.js not found"); }
        }
        return chartJs;
    }

    public static String buildStatsHtml(List<EmergencyRecord> log, List<ResponderAlert> alerts) {
        int total = log.size();
        long critical = log.stream().filter(r -> r.severity >= 4).count();
        double avgEta = alerts.stream().mapToDouble(a -> a.eta_min).average().orElse(0);
        String busiest = log.stream()
            .collect(Collectors.groupingBy(r -> r.place.name, Collectors.counting()))
            .entrySet().stream().max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey).orElse("-");
        long arrived = alerts.stream().filter(a -> a.status == ResponderAlert.Status.ARRIVED).count();

        long crime    = log.stream().filter(r -> r.type.equals("crime")).count();
        long fire     = log.stream().filter(r -> r.type.equals("fire")).count();
        long accident = log.stream().filter(r -> r.type.equals("accident")).count();
        long medical  = log.stream().filter(r -> r.type.equals("medical")).count();

        long[] sev = new long[6];
        for (int i = 1; i <= 5; i++) { final int s = i; sev[i] = log.stream().filter(r -> r.severity == s).count(); }

        long[] hour = new long[24];
        for (EmergencyRecord r : log) {
            try {
                java.time.LocalTime lt = java.time.LocalTime.parse(
                    r.time_str.split(",")[0].trim(), DateTimeFormatter.ofPattern("hh:mm a"));
                hour[lt.getHour()]++;
            } catch (Exception ignored) {}
        }

        long policeA   = alerts.stream().filter(a -> a.unit_type.equals("police")).count();
        long hospitalA = alerts.stream().filter(a -> a.unit_type.equals("hospital")).count();
        long fireA     = alerts.stream().filter(a -> a.unit_type.equals("fire")).count();

        String[] types = {"crime","fire","accident","medical"};
        String[] cols  = {"#ee88cc","#ffaa55","#7799ee","#44cc88"};
        StringBuilder rows = new StringBuilder();
        for (int i = 0; i < types.length; i++) {
            String t = types[i]; String c = cols[i];
            List<EmergencyRecord> sub = log.stream().filter(r -> r.type.equals(t)).collect(Collectors.toList());
            int cnt = sub.size();
            double eta = alerts.stream().filter(a -> a.emg_type.equals(t)).mapToDouble(a -> a.eta_min).average().orElse(0);
            double avgSev = sub.stream().mapToInt(r -> r.severity).average().orElse(0);
            long crit = sub.stream().filter(r -> r.severity >= 4).count();
            double critPct = cnt > 0 ? (crit * 100.0 / cnt) : 0;
            rows.append(String.format(
                "<tr><td style='color:%s;font-weight:700'>%s</td><td>%d</td><td>%.1f</td><td>%.1f</td><td>%.0f%%</td></tr>",
                c, cap(t), cnt, eta, avgSev, critPct));
        }

        StringBuilder hourLabels = new StringBuilder();
        StringBuilder hourData   = new StringBuilder();
        for (int i = 0; i < 24; i++) {
            if (i > 0) { hourLabels.append(","); hourData.append(","); }
            hourLabels.append("'").append(String.format("%02d:00", i)).append("'");
            hourData.append(hour[i]);
        }

        String js = getChartJs();
        String busiestShort = busiest.length() > 14 ? busiest.substring(0,14)+"..." : busiest;

        // Hospital load data
        StringBuilder hospRows = new StringBuilder();
        for (var h : com.rapidrescue.data.EmergencyDatabase.HOSPITALS) {
            int pct = h.load_pct();
            String col = h.load_color();
            String barStyle = "background:linear-gradient(to right," + col + " " + pct + "%,#1a1a28 " + pct + "%)";
            hospRows.append(String.format(
                "<tr><td style='color:#ccccee;font-weight:600'>%s</td>" +
                "<td>%d/%d</td>" +
                "<td><div style='height:8px;border-radius:4px;%s;min-width:100px'></div></td>" +
                "<td style='color:%s;font-weight:700'>%d%%</td>" +
                "<td style='color:%s'>%s</td>" +
                "<td style='color:%s'>%s</td></tr>",
                h.name, h.cur_load, h.max_beds, barStyle,
                col, pct, col, h.load_label(),
                h.status_color(), h.status_label()));
        }

        // Weather info
        WeatherEngine.Weather wx = WeatherEngine.get_weather();
        String wxColor = wx.impact() >= 2 ? "#ef4444" : wx.impact() == 1 ? "#eab308" : "#22c55e";
        String wxHtml = "<div style='background:#111118;border:0.5px solid " + wxColor + ";border-radius:12px;padding:14px;margin-bottom:14px;'>" +
            "<h3 style='font-size:10px;color:#7777bb;margin-bottom:10px;font-weight:700;letter-spacing:0.8px;'>CURRENT WEATHER CONDITIONS</h3>" +
            "<div style='display:flex;gap:20px;align-items:center;flex-wrap:wrap;'>" +
            "<div style='font-size:22px;font-weight:800;color:" + wxColor + "'>" + wx.name() + "</div>" +
            "<div><div style='color:#ccccee;font-size:12px'>" + wx.desc() + "</div>" +
            "<div style='color:" + wxColor + ";font-size:11px;margin-top:4px'>ETA Multiplier: x" + String.format("%.1f", wx.eta_mult()) + "  |  " + WeatherEngine.impact_label(wx.impact()) + "</div></div>" +
            "</div></div>";

        return "<!DOCTYPE html><html><head><meta charset='utf-8'/><style>" +
            "*{margin:0;padding:0;box-sizing:border-box;}" +
            "body{background:#0a0a0f;color:#ccccee;font-family:system-ui,sans-serif;padding:14px;overflow-y:auto;}" +
            "h2{color:#4444aa;font-size:11px;font-weight:700;letter-spacing:1.5px;margin-bottom:12px;}" +
            ".kpi-row{display:grid;grid-template-columns:repeat(5,1fr);gap:10px;margin-bottom:14px;}" +
            ".kpi{background:#111118;border:0.5px solid #2a2a3a;border-radius:12px;padding:14px;text-align:center;}" +
            ".kpi .val{font-size:24px;font-weight:800;margin-bottom:4px;}" +
            ".kpi .lbl{font-size:10px;color:#5555aa;letter-spacing:0.5px;}" +
            ".row2{display:grid;grid-template-columns:1fr 1fr;gap:10px;margin-bottom:14px;}" +
            ".box{background:#111118;border:0.5px solid #2a2a3a;border-radius:12px;padding:14px;}" +
            ".box h3{font-size:10px;color:#7777bb;margin-bottom:10px;font-weight:700;letter-spacing:0.8px;}" +
            ".box canvas{max-height:210px;}" +
            ".full{background:#111118;border:0.5px solid #2a2a3a;border-radius:12px;padding:14px;margin-bottom:14px;}" +
            ".full h3{font-size:10px;color:#7777bb;margin-bottom:10px;font-weight:700;letter-spacing:0.8px;}" +
            ".full canvas{max-height:160px;}" +
            "table{width:100%;border-collapse:collapse;font-size:12px;}" +
            "th{color:#5555aa;font-size:10px;font-weight:700;padding:8px 14px;text-align:left;border-bottom:1px solid #2a2a3a;}" +
            "td{padding:9px 14px;border-bottom:0.5px solid #1a1a28;}" +
            "tr:hover td{background:#1a1a28;}" +
            "</style><script>" + js + "</script></head><body>" +
            "<h2>STATISTICS DASHBOARD</h2>" +
            "<div class='kpi-row'>" +
            kpi(String.valueOf(total), "Total Dispatches", "#ffffff") +
            kpi(String.valueOf(critical), "Critical (Sev 4-5)", "#ef4444") +
            kpi(total > 0 ? String.format("%.1f", avgEta) : "-", "Avg ETA (min)", "#3b82f6") +
            kpi(busiestShort, "Busiest Location", "#f97316") +
            kpi(String.valueOf(arrived), "Units Arrived", "#22c55e") +
            "</div>" +
            "<div class='row2'>" +
            "<div class='box'><h3>EMERGENCIES BY TYPE</h3><canvas id='pie'></canvas></div>" +
            "<div class='box'><h3>UNITS DEPLOYED BY TYPE</h3><canvas id='doughnut'></canvas></div>" +
            "</div>" +
            "<div class='row2'>" +
            "<div class='box'><h3>DISPATCHES BY SEVERITY</h3><canvas id='bar'></canvas></div>" +
            "<div class='box'><h3>SEVERITY AREA CHART</h3><canvas id='area'></canvas></div>" +
            "</div>" +
            "<div class='full'><h3>DISPATCH ACTIVITY BY HOUR OF DAY</h3><canvas id='line'></canvas></div>" +
            "<div class='full'><h3>RESPONSE PERFORMANCE BY TYPE</h3>" +
            "<table><tr><th>Type</th><th>Count</th><th>Avg ETA (min)</th><th>Avg Severity</th><th>Critical %</th></tr>" +
            rows + "</table></div>" +
            wxHtml +
            "<div class='full'><h3>HOSPITAL LOAD STATUS</h3>" +
            "<table><tr><th>Hospital</th><th>Patients</th><th>Load Bar</th><th>Load %</th><th>Status</th><th>Availability</th></tr>" +
            hospRows + "</table></div>" +
            "<script>" +
            "Chart.defaults.color='#7777aa';Chart.defaults.borderColor='#2a2a3a';Chart.defaults.font.family='system-ui';" +
            "new Chart(document.getElementById('pie'),{type:'pie',data:{labels:['Crime','Fire','Accident','Medical']," +
            "datasets:[{data:[" + crime + "," + fire + "," + accident + "," + medical + "]," +
            "backgroundColor:['#cc33aa','#f97316','#3b82f6','#22c55e'],borderColor:'#0a0a0f',borderWidth:2}]}," +
            "options:{plugins:{legend:{labels:{color:'#aaaacc',font:{size:11}}}}}});" +
            "new Chart(document.getElementById('doughnut'),{type:'doughnut',data:{labels:['Police','Hospital','Fire']," +
            "datasets:[{data:[" + policeA + "," + hospitalA + "," + fireA + "]," +
            "backgroundColor:['#3b82f6','#22c55e','#f97316'],borderColor:'#0a0a0f',borderWidth:2}]}," +
            "options:{plugins:{legend:{labels:{color:'#aaaacc',font:{size:11}}}}}});" +
            "new Chart(document.getElementById('bar'),{type:'bar',data:{labels:['Sev 1','Sev 2','Sev 3','Sev 4','Sev 5']," +
            "datasets:[{data:[" + sev[1] + "," + sev[2] + "," + sev[3] + "," + sev[4] + "," + sev[5] + "]," +
            "backgroundColor:['#22c55e','#84cc16','#eab308','#f97316','#ef4444'],borderRadius:6,borderSkipped:false}]}," +
            "options:{plugins:{legend:{display:false}},scales:{x:{grid:{color:'#1a1a28'}},y:{grid:{color:'#1a1a28'},beginAtZero:true,ticks:{stepSize:1}}}}});" +
            "new Chart(document.getElementById('area'),{type:'line',data:{labels:['Sev 1','Sev 2','Sev 3','Sev 4','Sev 5']," +
            "datasets:[{data:[" + sev[1] + "," + sev[2] + "," + sev[3] + "," + sev[4] + "," + sev[5] + "]," +
            "fill:true,backgroundColor:'rgba(239,68,68,0.15)',borderColor:'#ef4444',pointBackgroundColor:'#ef4444',tension:0.4}]}," +
            "options:{plugins:{legend:{display:false}},scales:{x:{grid:{color:'#1a1a28'}},y:{grid:{color:'#1a1a28'},beginAtZero:true,ticks:{stepSize:1}}}}});" +
            "new Chart(document.getElementById('line'),{type:'line',data:{labels:[" + hourLabels + "]," +
            "datasets:[{label:'Dispatches',data:[" + hourData + "]," +
            "fill:true,backgroundColor:'rgba(59,130,246,0.12)',borderColor:'#3b82f6'," +
            "pointBackgroundColor:'#3b82f6',pointRadius:3,tension:0.3}]}," +
            "options:{plugins:{legend:{display:false}},scales:{x:{grid:{color:'#1a1a28'},ticks:{maxRotation:45,font:{size:9}}}," +
            "y:{grid:{color:'#1a1a28'},beginAtZero:true,ticks:{stepSize:1}}}}});" +
            "</script></body></html>";
    }

    private static String kpi(String val, String label, String color) {
        return "<div class='kpi'><div class='val' style='color:" + color + "'>" + val +
               "</div><div class='lbl'>" + label + "</div></div>";
    }

    private static String cap(String s) {
        return s == null || s.isEmpty() ? s : s.substring(0,1).toUpperCase() + s.substring(1);
    }
}
