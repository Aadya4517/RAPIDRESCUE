package com.rapidrescue.data;

import com.rapidrescue.model.ResponderAlert;
import com.rapidrescue.model.ResponderAlert.Status;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

// SQLite store for responder alerts
public class AlertDatabase {

    private static final String DB = "jdbc:sqlite:rapid_rescue_alerts.db";
    private static Connection conn;

    // connect and create table
    public static void init() {
        try {
            Class.forName("org.sqlite.JDBC");
            conn = DriverManager.getConnection(DB);
            String sql = "CREATE TABLE IF NOT EXISTS alerts ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "emg_id TEXT, unit_name TEXT, unit_icon TEXT, unit_type TEXT,"
                + "place TEXT, emg_type TEXT, emg_subtype TEXT, severity INTEGER,"
                + "dist_km REAL, eta_min REAL, sent_at TEXT, status TEXT DEFAULT 'PENDING')";
            try (Statement st = conn.createStatement()) { st.execute(sql); }
            System.out.println("[DB] connected");
        } catch (Exception e) { System.err.println("[DB] init failed: " + e.getMessage()); }
    }

    // save new alert
    public static void save(ResponderAlert a) {
        if (conn == null) return;
        String sql = "INSERT INTO alerts (emg_id,unit_name,unit_icon,unit_type,place,"
            + "emg_type,emg_subtype,severity,dist_km,eta_min,sent_at,status) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1,a.emergency_id); ps.setString(2,a.unit_name);
            ps.setString(3,a.unit_icon);    ps.setString(4,a.unit_type);
            ps.setString(5,a.place);        ps.setString(6,a.emg_type);
            ps.setString(7,a.emg_subtype);  ps.setInt(8,a.severity);
            ps.setDouble(9,a.dist_km);      ps.setDouble(10,a.eta_min);
            ps.setString(11,a.sent_at);     ps.setString(12,a.status.name());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) { if (rs.next()) a.id = rs.getInt(1); }
        } catch (SQLException e) { System.err.println("[DB] save failed: " + e.getMessage()); }
    }

    // update alert status
    public static void update_status(int id, Status s) {
        if (conn == null) return;
        try (PreparedStatement ps = conn.prepareStatement("UPDATE alerts SET status=? WHERE id=?")) {
            ps.setString(1,s.name()); ps.setInt(2,id); ps.executeUpdate();
        } catch (SQLException e) { System.err.println("[DB] update failed: " + e.getMessage()); }
    }

    // load all alerts
    public static List<ResponderAlert> load_all() {
        List<ResponderAlert> list = new ArrayList<>();
        if (conn == null) return list;
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM alerts ORDER BY id DESC")) {
            while (rs.next()) {
                ResponderAlert a = new ResponderAlert(
                    rs.getInt("id"), rs.getString("emg_id"),
                    rs.getString("unit_name"), rs.getString("unit_icon"),
                    rs.getString("unit_type"), rs.getString("place"),
                    rs.getString("emg_type"), rs.getString("emg_subtype"),
                    rs.getInt("severity"), rs.getDouble("dist_km"),
                    rs.getDouble("eta_min"), rs.getString("sent_at"));
                a.status = Status.valueOf(rs.getString("status"));
                list.add(a);
            }
        } catch (SQLException e) { System.err.println("[DB] load failed: " + e.getMessage()); }
        return list;
    }

    // delete all records
    public static void clear() {
        if (conn == null) return;
        try (Statement st = conn.createStatement()) { st.execute("DELETE FROM alerts"); }
        catch (SQLException e) { System.err.println("[DB] clear failed: " + e.getMessage()); }
    }

    public static void close() {
        try { if (conn != null) conn.close(); }
        catch (SQLException e) { System.err.println("[DB] close failed: " + e.getMessage()); }
    }
}
