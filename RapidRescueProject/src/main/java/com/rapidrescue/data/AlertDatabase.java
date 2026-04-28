package com.rapidrescue.data;

import com.rapidrescue.model.ResponderAlert;
import com.rapidrescue.model.ResponderAlert.Status;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * SQLite-backed store for responder dispatch alerts.
 * DB file is created at: rapid_rescue_alerts.db (next to the jar / working dir)
 */
public class AlertDatabase {

    private static final String DB_URL = "jdbc:sqlite:rapid_rescue_alerts.db";
    private static Connection conn;

    // ── Init ──────────────────────────────────────────────────────────────────

    public static void init() {
        try {
            Class.forName("org.sqlite.JDBC");
            conn = DriverManager.getConnection(DB_URL);
            createTable();
            System.out.println("[AlertDB] Connected: " + DB_URL);
        } catch (Exception e) {
            System.err.println("[AlertDB] Init failed: " + e.getMessage());
        }
    }

    private static void createTable() throws SQLException {
        String sql = """
            CREATE TABLE IF NOT EXISTS responder_alerts (
                id                INTEGER PRIMARY KEY AUTOINCREMENT,
                emergency_id      TEXT NOT NULL,
                unit_name         TEXT NOT NULL,
                unit_icon         TEXT,
                unit_type         TEXT,
                incident_location TEXT,
                emergency_type    TEXT,
                emergency_subtype TEXT,
                severity          INTEGER,
                dist_km           REAL,
                eta_min           REAL,
                dispatched_at     TEXT,
                status            TEXT DEFAULT 'PENDING'
            )
            """;
        try (Statement st = conn.createStatement()) {
            st.execute(sql);
        }
    }

    // ── Insert ────────────────────────────────────────────────────────────────

    public static void insertAlert(ResponderAlert a) {
        if (conn == null) return;
        String sql = """
            INSERT INTO responder_alerts
              (emergency_id, unit_name, unit_icon, unit_type, incident_location,
               emergency_type, emergency_subtype, severity, dist_km, eta_min,
               dispatched_at, status)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1,  a.emergencyId);
            ps.setString(2,  a.unitName);
            ps.setString(3,  a.unitIcon);
            ps.setString(4,  a.unitType);
            ps.setString(5,  a.incidentLocation);
            ps.setString(6,  a.emergencyType);
            ps.setString(7,  a.emergencySubtype);
            ps.setInt(8,     a.severity);
            ps.setDouble(9,  a.distKm);
            ps.setDouble(10, a.etaMin);
            ps.setString(11, a.dispatchedAt);
            ps.setString(12, a.status.name());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) a.id = rs.getInt(1);
            }
        } catch (SQLException e) {
            System.err.println("[AlertDB] Insert failed: " + e.getMessage());
        }
    }

    // ── Update status ─────────────────────────────────────────────────────────

    public static void updateStatus(int id, Status status) {
        if (conn == null) return;
        String sql = "UPDATE responder_alerts SET status = ? WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status.name());
            ps.setInt(2, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[AlertDB] Update failed: " + e.getMessage());
        }
    }

    // ── Query ─────────────────────────────────────────────────────────────────

    public static List<ResponderAlert> getAllAlerts() {
        List<ResponderAlert> list = new ArrayList<>();
        if (conn == null) return list;
        String sql = "SELECT * FROM responder_alerts ORDER BY id DESC";
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                ResponderAlert a = new ResponderAlert(
                    rs.getInt("id"),
                    rs.getString("emergency_id"),
                    rs.getString("unit_name"),
                    rs.getString("unit_icon"),
                    rs.getString("unit_type"),
                    rs.getString("incident_location"),
                    rs.getString("emergency_type"),
                    rs.getString("emergency_subtype"),
                    rs.getInt("severity"),
                    rs.getDouble("dist_km"),
                    rs.getDouble("eta_min"),
                    rs.getString("dispatched_at")
                );
                a.status = Status.valueOf(rs.getString("status"));
                list.add(a);
            }
        } catch (SQLException e) {
            System.err.println("[AlertDB] Query failed: " + e.getMessage());
        }
        return list;
    }

    public static List<ResponderAlert> getActiveAlerts() {
        List<ResponderAlert> list = new ArrayList<>();
        if (conn == null) return list;
        String sql = "SELECT * FROM responder_alerts WHERE status != 'ARRIVED' ORDER BY id DESC";
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                ResponderAlert a = new ResponderAlert(
                    rs.getInt("id"),
                    rs.getString("emergency_id"),
                    rs.getString("unit_name"),
                    rs.getString("unit_icon"),
                    rs.getString("unit_type"),
                    rs.getString("incident_location"),
                    rs.getString("emergency_type"),
                    rs.getString("emergency_subtype"),
                    rs.getInt("severity"),
                    rs.getDouble("dist_km"),
                    rs.getDouble("eta_min"),
                    rs.getString("dispatched_at")
                );
                a.status = Status.valueOf(rs.getString("status"));
                list.add(a);
            }
        } catch (SQLException e) {
            System.err.println("[AlertDB] Query failed: " + e.getMessage());
        }
        return list;
    }

    public static void clearAll() {
        if (conn == null) return;
        try (Statement st = conn.createStatement()) {
            st.execute("DELETE FROM responder_alerts");
        } catch (SQLException e) {
            System.err.println("[AlertDB] Clear failed: " + e.getMessage());
        }
    }

    public static void close() {
        try { if (conn != null) conn.close(); }
        catch (SQLException e) { System.err.println("[AlertDB] Close failed: " + e.getMessage()); }
    }
}
