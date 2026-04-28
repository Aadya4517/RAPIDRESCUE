package com.rapidrescue.ui;

import com.rapidrescue.model.*;
import com.rapidrescue.data.EmergencyDatabase;
import com.rapidrescue.data.AlertDatabase;
import com.rapidrescue.logic.*;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.*;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.*;
import javafx.scene.web.WebView;
import javafx.scene.web.WebEngine;
import javafx.stage.Stage;
import javafx.animation.*;
import javafx.util.Duration;
import javafx.scene.shape.Circle;
import javafx.scene.media.AudioClip;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.*;
import javafx.scene.chart.*;

public class DispatchWindow extends Application {

    private final List<EmergencyRecord> emergencyLog = new ArrayList<>();
    private final List<ResponderAlert>  responderAlerts = new ArrayList<>();
    private int dispatchCounter = 0;

    private String   selectedType    = "crime";
    private String   selectedSubtype = "Armed robbery";
    private int      selectedSev     = 3;
    private Location selectedLoc     = null;
    private List<DispatchedUnit> currentUnits = new ArrayList<>();

    // UI refs
    private VBox    subTypeBox;
    private Label   locNameLabel, locCoordsLabel, algoInfoLabel;
    private ProgressBar trafficBar;
    private Label   trafficLabel;
    private FlowPane unitGrid;
    private FlowPane summaryChips;
    private Label   logCountBadge, alertCountBadge;
    private VBox    logContainer;
    private Label   statTotal, statCritical, statUnits, statAvgSev;
    private String  activeLogFilter = "all";

    // Live clock / shift
    private Label clockLabel;
    private Label shiftLabel;

    // Severity row — kept as field so auto-severity can update it
    private HBox sevRow;
    private ToggleGroup sevToggleGroup;
    private static final String[] SEV_COLORS = {"","#22c55e","#84cc16","#eab308","#f97316","#ef4444"};

    // Map
    private WebEngine mapEngine;

    // Weather
    private Label weatherLabel;
    private Label weatherImpactLabel;

    // Responder alerts tab
    private VBox alertContainer;
    private Label alertStatPending, alertStatEnRoute, alertStatArrived;

    // Priority queue
    private final java.util.PriorityQueue<com.rapidrescue.model.PendingEmergency> pendingQueue =
        new java.util.PriorityQueue<>();
    private int queueCounter = 0;
    private VBox queueContainer;
    private Label queueCountBadge;

    // Stats
    private WebEngine statsEngine;
    private Label statsTotal, statsCritical, statsAvgEta, statsBusiestLoc;

    // Audio
    private AudioClip sirenClip;

    @Override
    public void start(Stage stage) {
        AlertDatabase.init();
        loadSirenSound();

        stage.setTitle("RAPID RESCUE — Emergency Dispatch System");
        stage.setOnCloseRequest(e -> AlertDatabase.close());

        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #0a0a0f;");

        VBox mainContent = new VBox(10);
        mainContent.setPadding(new Insets(14));
        mainContent.setStyle("-fx-background-color: #0a0a0f;");

        mainContent.getChildren().add(buildTopBar());

        TabPane tabPane = buildTabPane();
        VBox.setVgrow(tabPane, Priority.ALWAYS);
        mainContent.getChildren().add(tabPane);

        root.setCenter(mainContent);

        Scene scene = new Scene(root, 1050, 720);
        stage.setScene(scene);
        stage.setMinWidth(850);
        stage.setMinHeight(600);
        stage.show();

        refreshSubtypes();
        refreshUnitGrid();
        updateSummaryChips();
        startLiveClock();
        startCountdownTimer();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // AUDIO
    // ─────────────────────────────────────────────────────────────────────────

    void loadSirenSound() {
        try {
            // Embedded base64 short beep WAV so no external file is needed
            // This is a 440Hz beep generated inline via javax.sound as fallback
            sirenClip = null; // will use javax.sound fallback
        } catch (Exception e) {
            sirenClip = null;
        }
    }

    void playAlertSound() {
        new Thread(() -> {
            try {
                // Generate a simple siren tone using javax.sound.sampled
                int sampleRate = 44100;
                int duration   = 800; // ms
                byte[] buf = new byte[sampleRate * duration / 1000 * 2];
                for (int i = 0; i < buf.length / 2; i++) {
                    double t    = i / (double) sampleRate;
                    double freq = 880 + 440 * Math.sin(2 * Math.PI * 3 * t); // sweeping siren
                    short  val  = (short) (Short.MAX_VALUE * 0.6 * Math.sin(2 * Math.PI * freq * t));
                    buf[2 * i]     = (byte) (val & 0xff);
                    buf[2 * i + 1] = (byte) ((val >> 8) & 0xff);
                }
                javax.sound.sampled.AudioFormat fmt =
                    new javax.sound.sampled.AudioFormat(sampleRate, 16, 1, true, false);
                javax.sound.sampled.DataLine.Info info =
                    new javax.sound.sampled.DataLine.Info(javax.sound.sampled.SourceDataLine.class, fmt);
                javax.sound.sampled.SourceDataLine line =
                    (javax.sound.sampled.SourceDataLine) javax.sound.sampled.AudioSystem.getLine(info);
                line.open(fmt);
                line.start();
                line.write(buf, 0, buf.length);
                line.drain();
                line.close();
            } catch (Exception ex) {
                System.err.println("[Audio] Siren failed: " + ex.getMessage());
            }
        }).start();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // COUNTDOWN TIMER (ticks every second, updates alert cards)
    // ─────────────────────────────────────────────────────────────────────────

    void startCountdownTimer() {
        Timeline countdown = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            // Re-render alerts to update countdown labels
            if (alertContainer != null && !responderAlerts.isEmpty()) {
                renderAlerts();
            }
            // Check queue — try to dispatch pending emergencies if units freed up
            if (!pendingQueue.isEmpty()) {
                tryDispatchFromQueue();
            }
        }));
        countdown.setCycleCount(Timeline.INDEFINITE);
        countdown.play();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PRIORITY QUEUE VIEW
    // ─────────────────────────────────────────────────────────────────────────

    VBox buildQueueView() {
        VBox view = new VBox(12); view.setPadding(new Insets(14)); view.setStyle("-fx-background-color: #0a0a0f;");
        Label title = new Label("EMERGENCY PRIORITY QUEUE — Waiting for available units");
        title.setStyle("-fx-text-fill: #4444aa; -fx-font-size: 10px; -fx-font-weight: bold;");
        title.setWrapText(true);
        Label info = new Label("When all units are busy, new emergencies are queued here by severity (highest first). They auto-dispatch the moment a unit becomes available.");
        info.setWrapText(true);
        info.setStyle("-fx-text-fill: #5555aa; -fx-font-size: 11px; -fx-background-color: #111128; -fx-border-color: #2a2a4a; -fx-border-width: 0.5; -fx-border-radius: 8; -fx-background-radius: 8; -fx-padding: 10 12;");
        queueContainer = new VBox(10);
        renderQueue();
        view.getChildren().addAll(title, info, queueContainer);
        return view;
    }

    void renderQueue() {
        if (queueContainer == null) return;
        Platform.runLater(() -> {
            queueContainer.getChildren().clear();
            if (pendingQueue.isEmpty()) {
                Label e = new Label("No emergencies in queue.\nAll dispatches are being handled.");
                e.setStyle("-fx-text-fill: #3a3a5a; -fx-font-size: 13px;"); e.setAlignment(Pos.CENTER); e.setTextAlignment(TextAlignment.CENTER);
                queueContainer.getChildren().add(e); return;
            }
            // Sort copy for display
            java.util.List<com.rapidrescue.model.PendingEmergency> sorted = new java.util.ArrayList<>(pendingQueue);
            java.util.Collections.sort(sorted);
            int rank = 1;
            for (var pe : sorted) {
                String sc = pe.sev_color();
                HBox row = new HBox(12); row.setAlignment(Pos.CENTER_LEFT); row.setPadding(new Insets(12,16,12,16));
                row.setStyle("-fx-background-color: #111118; -fx-border-color: "+sc+"; -fx-border-width: 0.5; -fx-border-radius: 10; -fx-background-radius: 10;");
                Label rankLbl = new Label("#"+rank); rankLbl.setStyle("-fx-text-fill: "+sc+"; -fx-font-size: 18px; -fx-font-weight: bold; -fx-min-width: 30;");
                Label sevLbl = new Label("SEV "+pe.severity); sevLbl.setStyle("-fx-background-color: "+sc+"22; -fx-border-color: "+sc+"; -fx-border-width: 0.5; -fx-border-radius: 8; -fx-background-radius: 8; -fx-text-fill: "+sc+"; -fx-font-size: 12px; -fx-font-weight: bold; -fx-padding: 4 10;");
                Label typeLbl = new Label(capitalize(pe.type)+" - "+pe.subtype); typeLbl.setStyle("-fx-text-fill: #ccccee; -fx-font-size: 12px; -fx-font-weight: bold;");
                Label locLbl = new Label("@ "+pe.place.name); locLbl.setStyle("-fx-text-fill: #6666aa; -fx-font-size: 11px;");
                Label waitLbl = new Label("Waiting: "+pe.wait_label()); waitLbl.setStyle("-fx-text-fill: #eab308; -fx-font-size: 10px;");
                VBox info2 = new VBox(3, new HBox(8, typeLbl, sevLbl), locLbl, waitLbl); HBox.setHgrow(info2, Priority.ALWAYS);
                Button cancelBtn = new Button("Cancel");
                cancelBtn.setStyle("-fx-background-color: #1a1616; -fx-border-color: #cc3333; -fx-border-width: 0.5; -fx-border-radius: 7; -fx-background-radius: 7; -fx-text-fill: #ff6666; -fx-font-size: 10px; -fx-padding: 4 10; -fx-cursor: hand;");
                final var peFinal = pe;
                cancelBtn.setOnAction(e -> { pendingQueue.remove(peFinal); renderQueue(); updateQueueBadge(); });
                row.getChildren().addAll(rankLbl, info2, cancelBtn);
                queueContainer.getChildren().add(row);
                rank++;
            }
        });
    }

    void updateQueueBadge() {
        if (queueCountBadge == null) return;
        int sz = pendingQueue.size();
        queueCountBadge.setText(String.valueOf(sz));
        queueCountBadge.setStyle(sz > 0
            ? "-fx-background-color: #2a1616; -fx-text-fill: #ff6666; -fx-font-size: 10px; -fx-padding: 1 7 1 7; -fx-background-radius: 10;"
            : "-fx-background-color: #2a2a3a; -fx-text-fill: #7777aa; -fx-font-size: 10px; -fx-padding: 1 7 1 7; -fx-background-radius: 10;");
    }

    /** Called every second — tries to dispatch the highest-priority queued emergency */
    void tryDispatchFromQueue() {
        if (pendingQueue.isEmpty()) return;
        com.rapidrescue.model.PendingEmergency pe = pendingQueue.peek();
        java.util.List<DispatchedUnit> units = DispatchEngine.get_units(pe.severity, pe.type, pe.place);
        if (units.isEmpty()) return; // still no units
        pendingQueue.poll(); // remove from queue
        // Auto-dispatch
        dispatchCounter++;
        TrafficWeight traf = TrafficRouting.get_traffic();
        String timeStr = java.time.LocalDateTime.now().format(DateTimeFormatter.ofPattern("hh:mm a, d MMM"));
        EmergencyRecord record = new EmergencyRecord(String.format("%03d", dispatchCounter), pe.type, pe.subtype, pe.severity, pe.place, new java.util.ArrayList<>(units), timeStr, traf.label);
        emergencyLog.add(0, record);
        DispatchEngine.set_busy(units);
        for (DispatchedUnit du : units) {
            ResponderAlert alert = new ResponderAlert(0, String.format("%03d", dispatchCounter), du.unit.name, du.unit.icon, du.unit.type, pe.place.name, pe.type, pe.subtype, pe.severity, du.dist_km, du.eta_min, timeStr);
            AlertDatabase.save(alert);
            responderAlerts.add(0, alert);
        }
        Platform.runLater(() -> {
            logCountBadge.setText(String.valueOf(emergencyLog.size()));
            alertCountBadge.setText(String.valueOf(responderAlerts.stream().filter(a -> a.status != ResponderAlert.Status.ARRIVED).count()));
            updateLogStats(); renderAlerts(); refreshUnitsStatusView(); refreshUnitGrid(); updateMap(); renderQueue(); updateQueueBadge(); updateStatsCharts();
            playAlertSound();
        });
    }

    void startLiveClock() {
        Timeline clock = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
            clockLabel.setText(time);
            ShiftTracker.Shift shift = ShiftTracker.get_shift();
            shiftLabel.setText(shift.icon() + "  " + shift.name() + "  (" + shift.time_range() + ")");
            shiftLabel.setStyle("-fx-text-fill: " + shift.color() + "; -fx-font-size: 11px;");
            // Update weather every 30 seconds
            if (LocalDateTime.now().getSecond() % 30 == 0) updateWeatherDisplay();
        }));
        clock.setCycleCount(Timeline.INDEFINITE);
        clock.play();
        updateWeatherDisplay(); // initial
    }

    void updateWeatherDisplay() {
        WeatherEngine.Weather w = WeatherEngine.get_weather();
        if (weatherLabel != null) {
            weatherLabel.setText(w.name());
            weatherLabel.setStyle("-fx-text-fill: " + w.color() + "; -fx-font-size: 11px; -fx-font-weight: bold;");
        }
        if (weatherImpactLabel != null) {
            String impact = w.eta_mult() > 1.0
                ? String.format("ETA x%.1f  |  %s", w.eta_mult(), WeatherEngine.impact_label(w.impact()))
                : "No ETA impact";
            weatherImpactLabel.setText(impact);
            weatherImpactLabel.setStyle("-fx-text-fill: " + (w.impact() >= 2 ? "#ef4444" : w.impact() == 1 ? "#eab308" : "#22c55e") + "; -fx-font-size: 10px;");
        }
        if (algoInfoLabel != null && selectedLoc != null) updateLocationInfo();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TOP BAR
    // ─────────────────────────────────────────────────────────────────────────

    HBox buildTopBar() {
        HBox bar = new HBox(16);
        bar.setPadding(new Insets(10, 16, 10, 16));
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setStyle("-fx-background-color: #111118; -fx-border-color: #2a2a3a; "
                   + "-fx-border-width: 1; -fx-border-radius: 10; -fx-background-radius: 10;");

        Label logo    = new Label("RAPID ");
        logo.setStyle("-fx-font-size: 17px; -fx-font-weight: bold; -fx-text-fill: #ffffff;");
        Label logoRed = new Label("RESCUE");
        logoRed.setStyle("-fx-font-size: 17px; -fx-font-weight: bold; -fx-text-fill: #ff3c3c;");
        HBox logoBox  = new HBox(0, logo, logoRed);
        logoBox.setAlignment(Pos.CENTER_LEFT);

        HBox chips = new HBox(6,
            algoChip("Dijkstra",    "#5588ff"),
            algoChip("Haversine",   "#22c55e"),
            algoChip("Dyn.Weights", "#eab308"),
            algoChip("Multi-Unit",  "#f97316")
        );

        // Live clock
        clockLabel = new Label("--:--:--");
        clockLabel.setStyle("-fx-text-fill: #ccccee; -fx-font-size: 14px; -fx-font-weight: bold; -fx-font-family: monospace;");

        // Shift label
        ShiftTracker.Shift shift = ShiftTracker.get_shift();
        shiftLabel = new Label(shift.icon() + "  " + shift.name());
        shiftLabel.setStyle("-fx-text-fill: " + shift.color() + "; -fx-font-size: 11px;");

        VBox clockBox = new VBox(2, clockLabel, shiftLabel);
        clockBox.setAlignment(Pos.CENTER_RIGHT);
        clockBox.setPadding(new Insets(0, 8, 0, 8));
        clockBox.setStyle("-fx-background-color: #1a1a28; -fx-border-color: #2a2a3a; "
                        + "-fx-border-width: 0.5; -fx-border-radius: 8; -fx-background-radius: 8;");

        Circle pulseDot = new Circle(4, Color.web("#22c55e"));
        animatePulse(pulseDot);
        Label liveLabel = new Label("Live");
        liveLabel.setStyle("-fx-text-fill: #22c55e; -fx-font-size: 11px;");
        HBox statusBox = new HBox(6, pulseDot, liveLabel);
        statusBox.setAlignment(Pos.CENTER);

        // Weather widget
        WeatherEngine.Weather w = WeatherEngine.get_weather();
        weatherLabel = new Label(w.name());
        weatherLabel.setStyle("-fx-text-fill: " + w.color() + "; -fx-font-size: 11px; -fx-font-weight: bold;");
        weatherImpactLabel = new Label(w.eta_mult() > 1.0
            ? String.format("ETA x%.1f", w.eta_mult()) : "No ETA impact");
        weatherImpactLabel.setStyle("-fx-text-fill: " + (w.impact() >= 2 ? "#ef4444" : "#22c55e") + "; -fx-font-size: 10px;");
        VBox weatherBox = new VBox(2, weatherLabel, weatherImpactLabel);
        weatherBox.setAlignment(Pos.CENTER_RIGHT);
        weatherBox.setPadding(new Insets(0, 8, 0, 8));
        weatherBox.setStyle("-fx-background-color: #1a1a28; -fx-border-color: #2a2a3a; -fx-border-width: 0.5; -fx-border-radius: 8; -fx-background-radius: 8;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        bar.getChildren().addAll(logoBox, chips, spacer, weatherBox, clockBox, statusBox);
        return bar;
    }

    HBox algoChip(String label, String color) {
        Circle dot = new Circle(3, Color.web(color));
        Label lbl  = new Label(label);
        lbl.setStyle("-fx-text-fill: #7777bb; -fx-font-size: 10px;");
        HBox chip  = new HBox(5, dot, lbl);
        chip.setAlignment(Pos.CENTER);
        chip.setPadding(new Insets(3, 9, 3, 9));
        chip.setStyle("-fx-background-color: #1a1a28; -fx-border-color: #3a3a5a; "
                    + "-fx-border-width: 0.5; -fx-border-radius: 20; -fx-background-radius: 20;");
        return chip;
    }

    void animatePulse(Circle dot) {
        FadeTransition ft = new FadeTransition(Duration.seconds(1.5), dot);
        ft.setFromValue(1.0); ft.setToValue(0.3);
        ft.setCycleCount(FadeTransition.INDEFINITE);
        ft.setAutoReverse(true);
        ft.play();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TAB PANE
    // ─────────────────────────────────────────────────────────────────────────

    TabPane buildTabPane() {
        TabPane tp = new TabPane();
        tp.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tp.setStyle("-fx-background-color: #111118;");

        // Dispatch tab
        ScrollPane dispatchScroll = new ScrollPane(buildDispatchView());
        dispatchScroll.setStyle("-fx-background: #0a0a0f; -fx-background-color: #0a0a0f;");
        dispatchScroll.setFitToWidth(true);
        dispatchScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        Tab dispatchTab = new Tab("🚨 Dispatch", dispatchScroll);

        // Map tab
        Tab mapTab = new Tab("🗺 Live Map", buildMapView());

        // Units tab
        Tab unitsTab = new Tab("🚔 Unit Status", buildUnitsStatusView());

        // Log tab
        logCountBadge = new Label("0");
        logCountBadge.setStyle("-fx-background-color: #2a2a3a; -fx-text-fill: #7777aa; "
                              + "-fx-font-size: 10px; -fx-padding: 1 7 1 7; -fx-background-radius: 10;");
        Label logTabLabel  = new Label("📋 Emergency Log  ");
        HBox  logTabHeader = new HBox(4, logTabLabel, logCountBadge);
        logTabHeader.setAlignment(Pos.CENTER);
        ScrollPane logScroll = new ScrollPane(buildLogView());
        logScroll.setStyle("-fx-background: #0a0a0f; -fx-background-color: #0a0a0f;");
        logScroll.setFitToWidth(true);
        logScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        Tab logTab = new Tab();
        logTab.setGraphic(logTabHeader);
        logTab.setContent(logScroll);

        // Responder Alerts tab
        alertCountBadge = new Label("0");
        alertCountBadge.setStyle("-fx-background-color: #2a1616; -fx-text-fill: #ff6666; "
                                + "-fx-font-size: 10px; -fx-padding: 1 7 1 7; -fx-background-radius: 10;");
        Label alertTabLabel  = new Label("🔔 Responder Alerts  ");
        HBox  alertTabHeader = new HBox(4, alertTabLabel, alertCountBadge);
        alertTabHeader.setAlignment(Pos.CENTER);
        ScrollPane alertScroll = new ScrollPane(buildResponderAlertsView());
        alertScroll.setStyle("-fx-background: #0a0a0f; -fx-background-color: #0a0a0f;");
        alertScroll.setFitToWidth(true);
        alertScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        Tab alertTab = new Tab();
        alertTab.setGraphic(alertTabHeader);
        alertTab.setContent(alertScroll);

        tp.getTabs().addAll(dispatchTab, mapTab, unitsTab, logTab, alertTab);

        // Priority Queue tab
        queueCountBadge = new Label("0");
        queueCountBadge.setStyle("-fx-background-color: #2a2a3a; -fx-text-fill: #7777aa; -fx-font-size: 10px; -fx-padding: 1 7 1 7; -fx-background-radius: 10;");
        Label queueTabLabel = new Label("⏳ Queue  ");
        HBox queueTabHeader = new HBox(4, queueTabLabel, queueCountBadge);
        queueTabHeader.setAlignment(Pos.CENTER);
        ScrollPane queueScroll = new ScrollPane(buildQueueView());
        queueScroll.setStyle("-fx-background: #0a0a0f; -fx-background-color: #0a0a0f;");
        queueScroll.setFitToWidth(true); queueScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        Tab queueTab = new Tab(); queueTab.setGraphic(queueTabHeader); queueTab.setContent(queueScroll);

        // Stats tab
        Tab statsTab = new Tab("📊 Statistics", buildStatsView());

        tp.getTabs().addAll(queueTab, statsTab);
        return tp;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DISPATCH VIEW
    // ─────────────────────────────────────────────────────────────────────────

    VBox buildDispatchView() {
        VBox view = new VBox(12);
        view.setPadding(new Insets(12));
        view.setStyle("-fx-background-color: #0a0a0f;");

        HBox mainGrid = new HBox(12);
        VBox leftPanel = buildPanel("Emergency Type + Subtype", buildTypeAndSubtype());
        HBox.setHgrow(leftPanel, Priority.ALWAYS);
        VBox rightPanel = buildPanel("Severity + Location", buildSeverityAndLocation());
        HBox.setHgrow(rightPanel, Priority.ALWAYS);
        mainGrid.getChildren().addAll(leftPanel, rightPanel);

        VBox unitsPanel = buildPanel("Nearest Available Units  —  Haversine + Dijkstra", buildUnitGrid());
        summaryChips = new FlowPane(7, 7);
        VBox summaryPanel = buildPanel("Dispatch Summary", summaryChips);

        Button dispatchBtn = new Button("\uD83D\uDE80  DISPATCH EMERGENCY UNITS");
        dispatchBtn.setMaxWidth(Double.MAX_VALUE);
        dispatchBtn.setStyle("-fx-background-color: #cc1111; -fx-text-fill: white; -fx-font-size: 15px; -fx-font-weight: bold; -fx-padding: 16; -fx-background-radius: 11; -fx-cursor: hand;");
        dispatchBtn.setOnAction(e -> showDispatchModal());
        dispatchBtn.setOnMouseEntered(e -> dispatchBtn.setStyle("-fx-background-color: #ee1111; -fx-text-fill: white; -fx-font-size: 15px; -fx-font-weight: bold; -fx-padding: 16; -fx-background-radius: 11; -fx-cursor: hand;"));
        dispatchBtn.setOnMouseExited(e -> dispatchBtn.setStyle("-fx-background-color: #cc1111; -fx-text-fill: white; -fx-font-size: 15px; -fx-font-weight: bold; -fx-padding: 16; -fx-background-radius: 11; -fx-cursor: hand;"));

        view.getChildren().addAll(mainGrid, unitsPanel, summaryPanel, dispatchBtn);
        return view;
    }

    VBox buildPanel(String title, javafx.scene.Node content) {
        VBox panel = new VBox(10);
        panel.setPadding(new Insets(14));
        panel.setStyle("-fx-background-color: #111118; -fx-border-color: #2a2a3a; -fx-border-width: 0.5; -fx-border-radius: 12; -fx-background-radius: 12;");
        Label lbl = new Label(title.toUpperCase());
        lbl.setStyle("-fx-text-fill: #4444aa; -fx-font-size: 10px; -fx-font-weight: bold; -fx-letter-spacing: 1.5;");
        panel.getChildren().addAll(lbl, content);
        return panel;
    }

    VBox buildTypeAndSubtype() {
        VBox box = new VBox(10);
        GridPane typeGrid = new GridPane();
        typeGrid.setHgap(8); typeGrid.setVgap(8);
        String[][] types = {{"crime","\uD83D\uDEA8","Crime"},{"fire","\uD83D\uDD25","Fire"},{"accident","\uD83D\uDE97","Accident"},{"medical","\uD83C\uDFE5","Medical"}};
        ToggleGroup tg = new ToggleGroup();
        int r = 0, c = 0;
        for (String[] t : types) {
            ToggleButton btn = new ToggleButton(t[1] + "\n" + t[2]);
            btn.setToggleGroup(tg);
            btn.setMaxWidth(Double.MAX_VALUE);
            btn.setMinHeight(60);
            btn.setStyle(typeStyle(false));
            btn.setWrapText(true);
            btn.setTextAlignment(TextAlignment.CENTER);
            if (t[0].equals("crime")) { btn.setSelected(true); btn.setStyle(typeStyle(true)); }
            final String typeName = t[0];
            btn.setOnAction(e -> {
                for (var n : typeGrid.getChildren()) if (n instanceof ToggleButton tb) tb.setStyle(typeStyle(tb.isSelected()));
                selectedType = typeName;
                selectedSubtype = EmergencyDatabase.SUBTYPES.get(typeName).get(0);
                refreshSubtypes(); refreshUnitGrid(); updateSummaryChips();
            });
            GridPane.setHgrow(btn, Priority.ALWAYS);
            typeGrid.add(btn, c, r);
            c++; if (c == 2) { c = 0; r++; }
        }
        subTypeBox = new VBox(5);
        ScrollPane sp = new ScrollPane(subTypeBox);
        sp.setPrefHeight(140);
        sp.setStyle("-fx-background: #0a0a0f; -fx-background-color: #0a0a0f;");
        sp.setFitToWidth(true);
        sp.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        Label subLbl = new Label("SUB-TYPE");
        subLbl.setStyle("-fx-text-fill: #4444aa; -fx-font-size: 10px; -fx-font-weight: bold;");
        box.getChildren().addAll(typeGrid, subLbl, sp);
        return box;
    }

    String typeStyle(boolean active) {
        if (active) return "-fx-background-color: #18183a; -fx-border-color: #7777ff; -fx-border-width: 1.5; -fx-border-radius: 9; -fx-background-radius: 9; -fx-text-fill: #8888ff; -fx-font-size: 12px; -fx-cursor: hand;";
        return "-fx-background-color: #1a1a28; -fx-border-color: #2a2a3a; -fx-border-width: 1.5; -fx-border-radius: 9; -fx-background-radius: 9; -fx-text-fill: #aaaacc; -fx-font-size: 12px; -fx-cursor: hand;";
    }

    void refreshSubtypes() {
        subTypeBox.getChildren().clear();
        List<String> subs = EmergencyDatabase.SUBTYPES.get(selectedType);
        ToggleGroup stg = new ToggleGroup();
        for (String s : subs) {
            ToggleButton tb = new ToggleButton("\u25B6  " + s);
            tb.setToggleGroup(stg);
            tb.setMaxWidth(Double.MAX_VALUE);
            if (s.equals(subs.get(0)) || s.equals(selectedSubtype)) { tb.setSelected(true); tb.setStyle(subStyle(true)); selectedSubtype = s; }
            else tb.setStyle(subStyle(false));
            tb.setOnAction(e -> {
                for (var n : subTypeBox.getChildren()) if (n instanceof ToggleButton b) b.setStyle(subStyle(b.isSelected()));
                selectedSubtype = s;
                // Auto-set severity based on subtype
                int autoSev = EmergencyDatabase.auto_sev(s);
                applyAutoSeverity(autoSev);
                updateSummaryChips();
            });
            subTypeBox.getChildren().add(tb);
        }
        // Auto-set severity for the initially selected subtype too
        int autoSev = EmergencyDatabase.auto_sev(selectedSubtype);
        applyAutoSeverity(autoSev);
    }

    /** Programmatically select a severity button and update selectedSev */
    void applyAutoSeverity(int sev) {
        selectedSev = sev;
        if (sevRow == null) return;
        for (var n : sevRow.getChildren()) {
            if (n instanceof ToggleButton b) {
                int idx = sevRow.getChildren().indexOf(b) + 1;
                b.setSelected(idx == sev);
                b.setStyle(sevStyle(idx == sev, SEV_COLORS[idx]));
            }
        }
        refreshUnitGrid();
    }

    String subStyle(boolean active) {
        if (active) return "-fx-background-color: #18183a; -fx-border-color: #5555ff; -fx-border-width: 0.5; -fx-border-radius: 7; -fx-background-radius: 7; -fx-text-fill: #8888ff; -fx-font-size: 12px; -fx-alignment: center-left; -fx-cursor: hand;";
        return "-fx-background-color: #1a1a28; -fx-border-color: #2a2a3a; -fx-border-width: 0.5; -fx-border-radius: 7; -fx-background-radius: 7; -fx-text-fill: #aaaacc; -fx-font-size: 12px; -fx-alignment: center-left; -fx-cursor: hand;";
    }

    VBox buildSeverityAndLocation() {
        VBox box = new VBox(12);
        sevRow = new HBox(7);
        sevToggleGroup = new ToggleGroup();
        for (int i = 1; i <= 5; i++) {
            ToggleButton sb = new ToggleButton(String.valueOf(i));
            sb.setToggleGroup(sevToggleGroup);
            sb.setPrefSize(50, 50);
            final int sev = i; final String col = SEV_COLORS[i];
            if (i == 3) { sb.setSelected(true); sb.setStyle(sevStyle(true, col)); } else sb.setStyle(sevStyle(false, col));
            sb.setOnAction(e -> {
                selectedSev = sev;
                for (var n : sevRow.getChildren()) if (n instanceof ToggleButton b) { int idx = sevRow.getChildren().indexOf(b)+1; b.setStyle(sevStyle(b.isSelected(), SEV_COLORS[idx])); }
                refreshUnitGrid(); updateSummaryChips();
            });
            HBox.setHgrow(sb, Priority.ALWAYS);
            sevRow.getChildren().add(sb);
        }
        HBox sevLbl = new HBox();
        Label minorLbl = new Label("Minor"); minorLbl.setStyle("-fx-text-fill: #3a3a6a; -fx-font-size: 10px;");
        Label critLbl  = new Label("Critical"); critLbl.setStyle("-fx-text-fill: #3a3a6a; -fx-font-size: 10px;");
        Region sp2 = new Region(); HBox.setHgrow(sp2, Priority.ALWAYS);
        sevLbl.getChildren().addAll(minorLbl, sp2, critLbl);

        Label locLbl = new Label("INCIDENT LOCATION — DEHRADUN");
        locLbl.setStyle("-fx-text-fill: #4444aa; -fx-font-size: 10px; -fx-font-weight: bold;");
        ComboBox<Location> locCombo = new ComboBox<>();
        locCombo.setMaxWidth(Double.MAX_VALUE);
        locCombo.setStyle("-fx-background-color: #1a1a28; -fx-border-color: #2a2a3a; -fx-text-fill: #ccccee; -fx-font-size: 12px;");
        locCombo.setPromptText("— Select location —");
        locCombo.getItems().addAll(EmergencyDatabase.LOCATIONS);
        locCombo.setOnAction(e -> { selectedLoc = locCombo.getValue(); updateLocationInfo(); refreshUnitGrid(); updateSummaryChips(); updateMap(); });

        locNameLabel   = new Label("Select a location above"); locNameLabel.setStyle("-fx-text-fill: #5555aa; -fx-font-size: 11px;");
        locCoordsLabel = new Label(""); locCoordsLabel.setStyle("-fx-text-fill: #3a3a6a; -fx-font-size: 11px;");
        HBox locInfoBox = new HBox(10, locNameLabel, locCoordsLabel);
        locInfoBox.setPadding(new Insets(8,12,8,12));
        locInfoBox.setStyle("-fx-background-color: #111120; -fx-border-color: #2a2a3a; -fx-border-width: 0.5; -fx-border-radius: 8; -fx-background-radius: 8;");

        trafficLabel = new Label("—"); trafficLabel.setStyle("-fx-text-fill: #eab308; -fx-font-size: 10px;");
        Label trafHead = new Label("Dynamic Traffic Weight  "); trafHead.setStyle("-fx-text-fill: #5555aa; -fx-font-size: 10px;");
        HBox trafLblRow = new HBox(0, trafHead, trafficLabel);
        trafficBar = new ProgressBar(0); trafficBar.setMaxWidth(Double.MAX_VALUE); trafficBar.setPrefHeight(6); trafficBar.setStyle("-fx-accent: #eab308;");
        VBox trafBox = new VBox(5, trafLblRow, trafficBar);
        trafBox.setPadding(new Insets(10,12,10,12));
        trafBox.setStyle("-fx-background-color: #1a1a28; -fx-border-color: #2a2a3a; -fx-border-width: 0.5; -fx-border-radius: 8; -fx-background-radius: 8;");

        algoInfoLabel = new Label("Select a location to see Haversine + Dijkstra calculations.");
        algoInfoLabel.setWrapText(true); algoInfoLabel.setStyle("-fx-text-fill: #6666aa; -fx-font-size: 11px;");
        VBox algoBox = new VBox(algoInfoLabel);
        algoBox.setPadding(new Insets(10,12,10,12));
        algoBox.setStyle("-fx-background-color: #0e0e1a; -fx-border-color: #2a2a3a; -fx-border-width: 0.5; -fx-border-radius: 8; -fx-background-radius: 8;");

        box.getChildren().addAll(sevRow, sevLbl, locLbl, locCombo, locInfoBox, trafBox, algoBox);
        return box;
    }

    String sevStyle(boolean active, String color) {
        if (active) return "-fx-background-color: "+color+"22; -fx-border-color: "+color+"; -fx-border-width: 1.5; -fx-border-radius: 8; -fx-background-radius: 8; -fx-text-fill: "+color+"; -fx-font-size: 15px; -fx-font-weight: bold; -fx-cursor: hand;";
        return "-fx-background-color: #1a1a28; -fx-border-color: #2a2a3a; -fx-border-width: 1.5; -fx-border-radius: 8; -fx-background-radius: 8; -fx-text-fill: #555555; -fx-font-size: 15px; -fx-font-weight: bold; -fx-cursor: hand;";
    }

    void updateLocationInfo() {
        if (selectedLoc == null) return;
        locNameLabel.setText(selectedLoc.name); locNameLabel.setStyle("-fx-text-fill: #22c55e; -fx-font-size: 11px;");
        locCoordsLabel.setText(String.format("%.4f N, %.4f E", selectedLoc.lat, selectedLoc.lng)); locCoordsLabel.setStyle("-fx-text-fill: #22c55e; -fx-font-size: 11px;");
        TrafficWeight traf = TrafficRouting.get_traffic();
        WeatherEngine.Weather weather = WeatherEngine.get_weather();
        trafficLabel.setText(traf.label); trafficBar.setProgress(traf.percent / 100.0);
        double nearPol = EmergencyDatabase.POLICE.stream().mapToDouble(p -> GeoUtils.dist(selectedLoc.lat, selectedLoc.lng, p.lat, p.lng)).min().orElse(0);
        double eta = TrafficRouting.calc_eta(nearPol, traf.weight, weather.eta_mult());
        algoInfoLabel.setText(String.format(
            "Haversine nearest police: %.2f km | Dijkstra ETA x%.1f traffic x%.1f weather = %.1f min | %s",
            nearPol, traf.weight, weather.eta_mult(), eta, weather.name()));
    }

    FlowPane buildUnitGrid() {
        unitGrid = new FlowPane(8, 8); unitGrid.setPrefWrapLength(600);
        Label ph = new Label("\uD83D\uDCCD Select a location to compute nearest units"); ph.setStyle("-fx-text-fill: #3a3a6a; -fx-font-size: 12px;");
        unitGrid.getChildren().add(ph);
        return unitGrid;
    }

    void refreshUnitGrid() {
        if (unitGrid == null) return;
        unitGrid.getChildren().clear();
        if (selectedLoc == null) { Label ph = new Label("\uD83D\uDCCD Select a location to compute nearest units"); ph.setStyle("-fx-text-fill: #3a3a6a; -fx-font-size: 12px;"); unitGrid.getChildren().add(ph); return; }
        currentUnits = DispatchEngine.get_units(selectedSev, selectedType, selectedLoc);
        if (currentUnits.isEmpty()) { Label ph = new Label("No available units"); ph.setStyle("-fx-text-fill: #ef4444; -fx-font-size: 12px;"); unitGrid.getChildren().add(ph); return; }
        for (DispatchedUnit du : currentUnits) {
            String color = unitColor(du.unit.type);
            VBox card = new VBox(3); card.setAlignment(Pos.CENTER); card.setPadding(new Insets(10,8,10,8)); card.setPrefWidth(130);
            card.setStyle("-fx-background-color: #1a1a28; -fx-border-color: #2a2a3a; -fx-border-width: 0.5; -fx-border-radius: 8; -fx-background-radius: 8;");
            Label icon = new Label(du.unit.icon); icon.setStyle("-fx-font-size: 18px;");
            Label name = new Label(du.unit.name.split(",")[0].split("\\(")[0].trim()); name.setStyle("-fx-text-fill: #aaaacc; -fx-font-size: 10px; -fx-font-weight: bold;"); name.setWrapText(true); name.setTextAlignment(TextAlignment.CENTER);
            Label dist = new Label(String.format("%.2f km", du.dist_km)); dist.setStyle("-fx-text-fill: "+color+"; -fx-font-size: 10px;");
            Label eta  = new Label(String.format("ETA %.1f min", du.eta_min)); eta.setStyle("-fx-background-color: "+color+"22; -fx-text-fill: "+color+"; -fx-font-size: 9px; -fx-padding: 2 6 2 6; -fx-background-radius: 10;");
            Label avail = new Label(du.unit.free ? "\u2705 Available" : "\uD83D\uDD34 Busy"); avail.setStyle("-fx-text-fill: "+(du.unit.free?"#22c55e":"#ef4444")+"; -fx-font-size: 9px;");
            card.getChildren().addAll(icon, name, dist, eta, avail);
            unitGrid.getChildren().add(card);
        }
        updateSummaryChips();
    }

    String unitColor(String type) {
        return switch (type) { case "police" -> "#3b82f6"; case "hospital" -> "#22c55e"; case "fire" -> "#f97316"; default -> "#aaaacc"; };
    }

    void updateSummaryChips() {
        if (summaryChips == null) return;
        summaryChips.getChildren().clear();
        summaryChips.getChildren().add(chip(capitalize(selectedType), "#554499", "#1a1535", "#aa88ee"));
        summaryChips.getChildren().add(chip(selectedSubtype, "#3355aa", "#141e35", "#7799dd"));
        String sevColor = selectedSev >= 4 ? "#cc3333" : "#886622";
        String sevBg    = selectedSev >= 4 ? "#2a1616" : "#261e10";
        String sevTx    = selectedSev >= 4 ? "#ff7777" : "#ddaa44";
        summaryChips.getChildren().add(chip("Severity " + selectedSev, sevColor, sevBg, sevTx));
        if (selectedLoc != null) summaryChips.getChildren().add(chip("\uD83D\uDCCD " + selectedLoc.name, "#226622", "#142214", "#55cc55"));
        if (!currentUnits.isEmpty()) summaryChips.getChildren().add(chip(currentUnits.size() + " unit(s) dispatching", "#cc3333", "#2a1616", "#ff7777"));
    }

    Label chip(String text, String border, String bg, String color) {
        Label l = new Label(text);
        l.setStyle("-fx-background-color: "+bg+"; -fx-border-color: "+border+"; -fx-border-width: 0.5; -fx-border-radius: 16; -fx-background-radius: 16; -fx-text-fill: "+color+"; -fx-font-size: 11px; -fx-padding: 4 11 4 11;");
        return l;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DISPATCH MODAL
    // ─────────────────────────────────────────────────────────────────────────

    void showDispatchModal() {
        if (selectedLoc == null) { new Alert(Alert.AlertType.WARNING, "Please select an incident location first!").showAndWait(); return; }

        // If no units available — add to priority queue instead
        if (currentUnits.isEmpty()) {
            queueCounter++;
            String timeStr = java.time.LocalDateTime.now().format(DateTimeFormatter.ofPattern("hh:mm a, d MMM"));
            com.rapidrescue.model.PendingEmergency pe = new com.rapidrescue.model.PendingEmergency(queueCounter, selectedType, selectedSubtype, selectedSev, selectedLoc, timeStr);
            pendingQueue.add(pe);
            updateQueueBadge(); renderQueue();
            Alert info = new Alert(Alert.AlertType.INFORMATION,
                "No units available right now.\nEmergency added to Priority Queue (Severity " + selectedSev + ").\nIt will auto-dispatch when a unit is freed.");
            info.setTitle("Added to Queue"); info.showAndWait();
            return;
        }

        playAlertSound();

        dispatchCounter++;
        TrafficWeight traf = TrafficRouting.get_traffic();
        WeatherEngine.Weather weather = WeatherEngine.get_weather();
        String timeStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("hh:mm a, d MMM"));

        EmergencyRecord record = new EmergencyRecord(String.format("%03d", dispatchCounter), selectedType, selectedSubtype, selectedSev, selectedLoc, new ArrayList<>(currentUnits), timeStr, traf.label);
        emergencyLog.add(0, record);

        // Mark units busy
        DispatchEngine.set_busy(currentUnits);

        // Save to SQLite and in-memory alert list
        for (DispatchedUnit du : currentUnits) {
            ResponderAlert alert = new ResponderAlert(0, String.format("%03d", dispatchCounter), du.unit.name, du.unit.icon, du.unit.type, selectedLoc.name, selectedType, selectedSubtype, selectedSev, du.dist_km, du.eta_min, timeStr);
            AlertDatabase.save(alert);
            responderAlerts.add(0, alert);
        }

        logCountBadge.setText(String.valueOf(emergencyLog.size()));
        logCountBadge.setStyle("-fx-background-color: #2a1616; -fx-text-fill: #ff6666; -fx-font-size: 10px; -fx-padding: 1 7 1 7; -fx-background-radius: 10;");
        alertCountBadge.setText(String.valueOf(responderAlerts.stream().filter(a -> a.status != ResponderAlert.Status.ARRIVED).count()));
        alertCountBadge.setStyle("-fx-background-color: #2a1616; -fx-text-fill: #ff6666; -fx-font-size: 10px; -fx-padding: 1 7 1 7; -fx-background-radius: 10;");
        updateLogStats();
        renderLog();
        renderAlerts();
        refreshUnitGrid();
        refreshUnitsStatusView();
        updateMap();
        updateStatsCharts();

        Stage dialog = new Stage();
        dialog.setTitle("Units Dispatched!");
        VBox content = new VBox(12); content.setPadding(new Insets(20)); content.setStyle("-fx-background-color: #14141f;"); content.setPrefWidth(460);

        String typeIcon = switch (selectedType) { case "crime" -> "\uD83D\uDEA8"; case "fire" -> "\uD83D\uDD25"; case "accident" -> "\uD83D\uDE97"; default -> "\uD83C\uDFE5"; };
        Label iconLbl = new Label(typeIcon); iconLbl.setStyle("-fx-font-size: 22px; -fx-background-color: #2a1616; -fx-padding: 8; -fx-background-radius: 10;");
        Label titleLbl = new Label("Units Dispatched!"); titleLbl.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: white;");
        Label subLbl2 = new Label(capitalize(selectedType) + " \u00B7 " + selectedSubtype + " \u00B7 Severity " + selectedSev); subLbl2.setStyle("-fx-text-fill: #ef4444; -fx-font-size: 11px;");
        VBox titleBox = new VBox(3, titleLbl, subLbl2);
        HBox header = new HBox(12, iconLbl, titleBox); header.setAlignment(Pos.CENTER_LEFT);

        Label algoBadge = new Label("Algorithms: Haversine + Dijkstra ETA x" + traf.weight + " traffic x" + String.format("%.1f", weather.eta_mult()) + " weather (" + weather.name() + ") + Greedy " + currentUnits.size() + " unit(s) Sev " + selectedSev);
        algoBadge.setStyle("-fx-background-color: #111128; -fx-border-color: #3344aa; -fx-border-width: 0.5; -fx-border-radius: 7; -fx-background-radius: 7; -fx-text-fill: #7788cc; -fx-font-size: 11px; -fx-padding: 8 12;");
        algoBadge.setWrapText(true);
        String wCol = weather.impact() >= 2 ? "#ef4444" : weather.impact() == 1 ? "#eab308" : "#22c55e";
        Label weatherBadge = new Label(weather.name() + "  |  " + weather.desc() + (weather.eta_mult() > 1.0 ? "  |  ETA +" + String.format("%.0f%%", (weather.eta_mult()-1)*100) + " slower" : "  |  No ETA impact"));
        weatherBadge.setWrapText(true);
        weatherBadge.setStyle("-fx-background-color: "+wCol+"11; -fx-border-color: "+wCol+"; -fx-border-width: 0.5; -fx-border-radius: 7; -fx-background-radius: 7; -fx-text-fill: "+wCol+"; -fx-font-size: 11px; -fx-padding: 8 12;");

        Label dispMsg = new Label("Emergency at " + selectedLoc.name + ". " + currentUnits.size() + " unit(s) dispatched. Responders have been alerted in the database.");
        dispMsg.setStyle("-fx-background-color: #1a1a28; -fx-border-radius: 9; -fx-background-radius: 9; -fx-text-fill: #ccccee; -fx-font-size: 12px; -fx-padding: 12 14; -fx-line-spacing: 3;");
        dispMsg.setWrapText(true);

        VBox unitRows = new VBox(8);
        for (DispatchedUnit du : currentUnits) {
            String col = unitColor(du.unit.type);
            Label uIcon = new Label(du.unit.icon); uIcon.setStyle("-fx-font-size: 18px;");
            Label uName = new Label(du.unit.name); uName.setStyle("-fx-text-fill: #eeeeee; -fx-font-size: 12px; -fx-font-weight: bold;");
            Label uDetail = new Label(String.format("%.2f km  \u00B7  ETA %.1f min", du.dist_km, du.eta_min)); uDetail.setStyle("-fx-text-fill: "+col+"; -fx-font-size: 10px;");
            Label alertSent = new Label("\uD83D\uDD14 Alert sent to responder database"); alertSent.setStyle("-fx-text-fill: #eab308; -fx-font-size: 10px;");
            String origin = URLEncoder.encode(du.unit.name + ", Dehradun", StandardCharsets.UTF_8);
            String dest   = URLEncoder.encode(selectedLoc.name + ", Dehradun", StandardCharsets.UTF_8);
            String mapsUrl = "https://www.google.com/maps/dir/?api=1&origin=" + origin + "&destination=" + dest;
            Button routeBtn = new Button("\uD83D\uDDFA  Open Route \u2192 " + selectedLoc.name.split("\\(")[0].trim());
            routeBtn.setMaxWidth(Double.MAX_VALUE);
            routeBtn.setStyle("-fx-background-color: transparent; -fx-border-color: "+col+"; -fx-border-width: 0.5; -fx-border-radius: 6; -fx-background-radius: 6; -fx-text-fill: "+col+"; -fx-font-size: 11px; -fx-cursor: hand; -fx-padding: 5;");
            routeBtn.setOnAction(e -> { try { java.awt.Desktop.getDesktop().browse(new java.net.URI(mapsUrl)); } catch (Exception ex) { ex.printStackTrace(); } });
            VBox info = new VBox(2, uName, uDetail, alertSent, routeBtn);
            HBox row = new HBox(10, uIcon, info); row.setAlignment(Pos.CENTER_LEFT); row.setPadding(new Insets(10,14,10,14));
            row.setStyle("-fx-background-color: #1a1a28; -fx-border-color: "+col+"; -fx-border-width: 0.5; -fx-border-radius: 9; -fx-background-radius: 9;");
            HBox.setHgrow(info, Priority.ALWAYS);
            unitRows.getChildren().add(row);
        }

        Button openAllBtn = new Button("\uD83D\uDDFA  Open All Routes Simultaneously");
        openAllBtn.setMaxWidth(Double.MAX_VALUE);
        openAllBtn.setStyle("-fx-background-color: #1a1a2e; -fx-border-color: #5555ff; -fx-border-width: 1.5; -fx-border-radius: 9; -fx-background-radius: 9; -fx-text-fill: #8888ff; -fx-font-size: 13px; -fx-font-weight: bold; -fx-padding: 12; -fx-cursor: hand;");
        openAllBtn.setOnAction(e -> {
            for (DispatchedUnit du : currentUnits) {
                String origin = URLEncoder.encode(du.unit.name + ", Dehradun", StandardCharsets.UTF_8);
                String dest   = URLEncoder.encode(selectedLoc.name + ", Dehradun", StandardCharsets.UTF_8);
                String url    = "https://www.google.com/maps/dir/?api=1&origin=" + origin + "&destination=" + dest;
                try { java.awt.Desktop.getDesktop().browse(new java.net.URI(url)); } catch (Exception ex) { ex.printStackTrace(); }
            }
        });

        Label savedBadge = new Label("\u2705  Emergency #" + String.format("%03d", dispatchCounter) + " saved to log + responder alerts DB");
        savedBadge.setMaxWidth(Double.MAX_VALUE); savedBadge.setAlignment(Pos.CENTER);
        savedBadge.setStyle("-fx-background-color: #142214; -fx-border-color: #226622; -fx-border-width: 0.5; -fx-border-radius: 8; -fx-background-radius: 8; -fx-text-fill: #44cc66; -fx-font-size: 11px; -fx-padding: 6 12;");

        Button closeBtn = new Button("Close");
        closeBtn.setMaxWidth(Double.MAX_VALUE);
        closeBtn.setStyle("-fx-background-color: #1a1a28; -fx-border-color: #2a2a3a; -fx-text-fill: #aaaacc; -fx-font-size: 12px; -fx-padding: 8; -fx-border-radius: 8; -fx-background-radius: 8; -fx-cursor: hand;");
        closeBtn.setOnAction(e -> dialog.close());

        content.getChildren().addAll(header, algoBadge, weatherBadge, dispMsg, unitRows, openAllBtn, savedBadge, closeBtn);
        ScrollPane sc = new ScrollPane(content); sc.setStyle("-fx-background: #14141f; -fx-background-color: #14141f;"); sc.setFitToWidth(true);
        dialog.setScene(new Scene(sc, 480, 620)); dialog.show();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // MAP VIEW
    // ─────────────────────────────────────────────────────────────────────────

    private WebView mapWebView;

    VBox buildMapView() {
        VBox box = new VBox(0);
        box.setStyle("-fx-background-color: #0a0a0f;");
        mapWebView = new WebView();
        mapEngine  = mapWebView.getEngine();
        // Set a real browser user-agent so tile servers respond
        mapEngine.setUserAgent(
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
        mapEngine.loadContent(MapBuilder.buildBlankMapHtml());
        VBox.setVgrow(mapWebView, Priority.ALWAYS);
        box.getChildren().add(mapWebView);
        return box;
    }

    void updateMap() {
        if (mapEngine == null) return;
        Platform.runLater(() -> mapEngine.loadContent(MapBuilder.buildMapHtml(selectedLoc, currentUnits)));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // UNIT STATUS VIEW
    // ─────────────────────────────────────────────────────────────────────────

    private VBox unitsStatusContainer;

    VBox buildUnitsStatusView() {
        VBox view = new VBox(12);
        view.setPadding(new Insets(14));
        view.setStyle("-fx-background-color: #0a0a0f;");

        Label title = new Label("UNIT AVAILABILITY STATUS");
        title.setStyle("-fx-text-fill: #4444aa; -fx-font-size: 11px; -fx-font-weight: bold;");

        HBox legend = new HBox(16,
            chipSmall("\u2705 Available", "#22c55e"),
            chipSmall("\uD83D\uDD34 Busy / Dispatched", "#ef4444")
        );

        unitsStatusContainer = new VBox(10);
        renderUnitsStatus();

        ScrollPane sp = new ScrollPane(unitsStatusContainer);
        sp.setStyle("-fx-background: #0a0a0f; -fx-background-color: #0a0a0f;");
        sp.setFitToWidth(true); sp.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        VBox.setVgrow(sp, Priority.ALWAYS);

        view.getChildren().addAll(title, legend, sp);
        return view;
    }

    void renderUnitsStatus() {
        if (unitsStatusContainer == null) return;
        unitsStatusContainer.getChildren().clear();
        renderUnitGroup("\uD83D\uDE94 Police Stations", EmergencyDatabase.POLICE, "#3b82f6");
        renderUnitGroup("\uD83D\uDE91 Hospitals", EmergencyDatabase.HOSPITALS, "#22c55e");
        renderUnitGroup("\uD83D\uDE92 Fire Stations", EmergencyDatabase.FIRE, "#f97316");
    }

    void renderUnitGroup(String groupTitle, java.util.List<com.rapidrescue.model.Unit> units, String color) {
        Label grpLbl = new Label(groupTitle);
        grpLbl.setStyle("-fx-text-fill: "+color+"; -fx-font-size: 12px; -fx-font-weight: bold;");
        unitsStatusContainer.getChildren().add(grpLbl);
        for (com.rapidrescue.model.Unit u : units) {
            HBox row = new HBox(12); row.setAlignment(Pos.CENTER_LEFT); row.setPadding(new Insets(10,14,10,14));
            String borderCol = u.status_color();
            row.setStyle("-fx-background-color: #111118; -fx-border-color: "+borderCol+"; -fx-border-width: 0.5; -fx-border-radius: 9; -fx-background-radius: 9;");
            Label icon = new Label(u.icon); icon.setStyle("-fx-font-size: 18px;");
            Label name = new Label(u.name); name.setStyle("-fx-text-fill: #ccccee; -fx-font-size: 12px; -fx-font-weight: bold;");
            Label shiftLbl = new Label("Shift: " + u.shift_label()); shiftLbl.setStyle("-fx-text-fill: #5555aa; -fx-font-size: 10px;");
            VBox info = new VBox(2, name, shiftLbl);
            if (u.type.equals("hospital") && u.max_beds > 0) {
                int pct = u.load_pct();
                String loadCol = u.load_color();
                Label loadLbl = new Label(String.format("Load: %d/%d (%d%%) — %s", u.cur_load, u.max_beds, pct, u.load_label()));
                loadLbl.setStyle("-fx-text-fill: "+loadCol+"; -fx-font-size: 10px;");
                ProgressBar loadBar = new ProgressBar(pct / 100.0);
                loadBar.setPrefWidth(140); loadBar.setPrefHeight(5);
                loadBar.setStyle("-fx-accent: "+loadCol+";");
                info.getChildren().addAll(loadLbl, loadBar);
            }
            HBox.setHgrow(info, Priority.ALWAYS);
            Label statusLbl = new Label(u.status_label());
            statusLbl.setStyle("-fx-background-color: "+borderCol+"22; -fx-border-color: "+borderCol+"; -fx-border-width: 0.5; -fx-border-radius: 10; -fx-background-radius: 10; -fx-text-fill: "+borderCol+"; -fx-font-size: 11px; -fx-padding: 4 10;");
            Button freeBtn = new Button("Mark Available");
            freeBtn.setStyle("-fx-background-color: #1a1a28; -fx-border-color: #3b82f6; -fx-border-width: 0.5; -fx-border-radius: 7; -fx-background-radius: 7; -fx-text-fill: #3b82f6; -fx-font-size: 10px; -fx-padding: 4 10; -fx-cursor: hand;");
            freeBtn.setVisible(!u.free && u.on_shift());
            freeBtn.setOnAction(e -> { DispatchEngine.set_free(u.name); renderUnitsStatus(); refreshUnitGrid(); });
            row.getChildren().addAll(icon, info, statusLbl, freeBtn);
            unitsStatusContainer.getChildren().add(row);
        }
    }

    void refreshUnitsStatusView() {
        Platform.runLater(this::renderUnitsStatus);
    }

    Label chipSmall(String text, String color) {
        Label l = new Label(text);
        l.setStyle("-fx-text-fill: "+color+"; -fx-font-size: 11px; -fx-background-color: "+color+"22; -fx-padding: 3 10; -fx-background-radius: 10;");
        return l;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // LOG VIEW
    // ─────────────────────────────────────────────────────────────────────────

    VBox buildLogView() {
        VBox view = new VBox(12); view.setPadding(new Insets(12)); view.setStyle("-fx-background-color: #0a0a0f;");
        HBox statsRow = new HBox(10);
        statTotal    = statCard("0", "#ffffff", "Total Dispatches");
        statCritical = statCard("0", "#ef4444", "Critical (4-5)");
        statUnits    = statCard("0", "#3b82f6", "Units Deployed");
        statAvgSev   = statCard("-",  "#eab308", "Avg Severity");
        statsRow.getChildren().addAll(statTotal.getParent(), statCritical.getParent(), statUnits.getParent(), statAvgSev.getParent());
        for (var n : statsRow.getChildren()) HBox.setHgrow(n, Priority.ALWAYS);

        HBox filterRow = new HBox(8); filterRow.setAlignment(Pos.CENTER_LEFT);
        String[] filterTypes  = {"all","crime","fire","accident","medical"};
        String[] filterLabels = {"All","\uD83D\uDEA8 Crime","\uD83D\uDD25 Fire","\uD83D\uDE97 Accident","\uD83C\uDFE5 Medical"};
        ToggleGroup fg = new ToggleGroup();
        for (int i = 0; i < filterTypes.length; i++) {
            ToggleButton fb = new ToggleButton(filterLabels[i]); fb.setToggleGroup(fg);
            if (i == 0) { fb.setSelected(true); fb.setStyle(filterStyle(true)); } else fb.setStyle(filterStyle(false));
            final String ft = filterTypes[i];
            fb.setOnAction(e -> { for (var n : filterRow.getChildren()) if (n instanceof ToggleButton b) b.setStyle(filterStyle(b.isSelected())); activeLogFilter = ft; renderLog(); });
            filterRow.getChildren().add(fb);
        }
        Region sp3 = new Region(); HBox.setHgrow(sp3, Priority.ALWAYS);
        Button clearBtn = new Button("Clear all");
        clearBtn.setStyle("-fx-background-color: #1a1616; -fx-border-color: #cc3333; -fx-border-width: 0.5; -fx-border-radius: 8; -fx-background-radius: 8; -fx-text-fill: #ff6666; -fx-font-size: 11px; -fx-padding: 6 14; -fx-cursor: hand;");
        clearBtn.setOnAction(e -> {
            if (emergencyLog.isEmpty()) return;
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "Clear all " + emergencyLog.size() + " records?", ButtonType.YES, ButtonType.NO);
            confirm.showAndWait().ifPresent(bt -> { if (bt == ButtonType.YES) { emergencyLog.clear(); logCountBadge.setText("0"); logCountBadge.setStyle("-fx-background-color: #2a2a3a; -fx-text-fill: #7777aa; -fx-font-size: 10px; -fx-padding: 1 7 1 7; -fx-background-radius: 10;"); updateLogStats(); renderLog(); } });
        });
        filterRow.getChildren().addAll(sp3, clearBtn);

        logContainer = new VBox(10);
        Label emptyLbl = new Label("\uD83D\uDCCB\n\nNo emergencies dispatched yet."); emptyLbl.setStyle("-fx-text-fill: #3a3a5a; -fx-font-size: 13px;"); emptyLbl.setAlignment(Pos.CENTER); emptyLbl.setTextAlignment(TextAlignment.CENTER);
        logContainer.getChildren().add(emptyLbl);
        view.getChildren().addAll(statsRow, filterRow, logContainer);
        return view;
    }

    Label statCard(String val, String color, String lbl) {
        VBox card = new VBox(4); card.setAlignment(Pos.CENTER); card.setPadding(new Insets(12));
        card.setStyle("-fx-background-color: #111118; -fx-border-color: #2a2a3a; -fx-border-width: 0.5; -fx-border-radius: 10; -fx-background-radius: 10;");
        Label valLbl = new Label(val); valLbl.setStyle("-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: "+color+";");
        Label lblLbl = new Label(lbl); lblLbl.setStyle("-fx-text-fill: #5555aa; -fx-font-size: 10px;");
        card.getChildren().addAll(valLbl, lblLbl);
        return valLbl;
    }

    String filterStyle(boolean active) {
        if (active) return "-fx-background-color: #18183a; -fx-border-color: #5555ff; -fx-border-width: 0.5; -fx-border-radius: 20; -fx-background-radius: 20; -fx-text-fill: #aaaaff; -fx-font-size: 11px; -fx-padding: 5 12; -fx-cursor: hand;";
        return "-fx-background-color: #1a1a28; -fx-border-color: #2a2a3a; -fx-border-width: 0.5; -fx-border-radius: 20; -fx-background-radius: 20; -fx-text-fill: #7777aa; -fx-font-size: 11px; -fx-padding: 5 12; -fx-cursor: hand;";
    }

    void renderLog() {
        if (logContainer == null) return;
        logContainer.getChildren().clear();
        List<EmergencyRecord> filtered = emergencyLog.stream().filter(r -> activeLogFilter.equals("all") || r.type.equals(activeLogFilter)).collect(Collectors.toList());
        if (filtered.isEmpty()) { Label e = new Label("\uD83D\uDCCB\n\nNo emergencies " + (activeLogFilter.equals("all") ? "dispatched yet." : "of this type.")); e.setStyle("-fx-text-fill: #3a3a5a; -fx-font-size: 13px;"); e.setAlignment(Pos.CENTER); e.setTextAlignment(TextAlignment.CENTER); logContainer.getChildren().add(e); return; }
        for (EmergencyRecord r : filtered) {
            String[] tc = getTypeColor(r.type); String sc = getSevColor(r.severity);
            HBox hdr = new HBox(8); hdr.setAlignment(Pos.CENTER_LEFT);
            Label idLbl = new Label("#"+r.id); idLbl.setStyle("-fx-text-fill: #3a3a6a; -fx-font-size: 11px; -fx-font-weight: bold;");
            Label typeBadge = new Label(capitalize(r.type)); typeBadge.setStyle("-fx-background-color: "+tc[0]+"; -fx-border-color: "+tc[1]+"; -fx-border-width: 0.5; -fx-border-radius: 16; -fx-background-radius: 16; -fx-text-fill: "+tc[2]+"; -fx-font-size: 11px; -fx-padding: 3 10;");
            Label sevBadge = new Label("Sev "+r.severity); sevBadge.setStyle("-fx-background-color: "+sc+"22; -fx-border-color: "+sc+"80; -fx-border-width: 0.5; -fx-border-radius: 10; -fx-background-radius: 10; -fx-text-fill: "+sc+"; -fx-font-size: 11px; -fx-padding: 2 8;");
            Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);
            Label timeLbl = new Label(r.time_str); timeLbl.setStyle("-fx-text-fill: #3a3a6a; -fx-font-size: 10px;");
            hdr.getChildren().addAll(idLbl, typeBadge, sevBadge, sp, timeLbl);
            HBox body = new HBox(8); body.setAlignment(Pos.CENTER_LEFT);
            Label locLbl2 = new Label("\uD83D\uDCCD "+r.place.name); locLbl2.setStyle("-fx-text-fill: #aaaacc; -fx-font-size: 12px;");
            Label subLbl3 = new Label(r.subtype); subLbl3.setStyle("-fx-text-fill: #6666aa; -fx-font-size: 11px;");
            Label uCount = new Label(r.units.size()+" unit"+(r.units.size()>1?"s":"")); uCount.setStyle("-fx-background-color: #1a1a28; -fx-text-fill: #7777aa; -fx-font-size: 11px; -fx-padding: 2 8; -fx-background-radius: 10;");
            body.getChildren().addAll(locLbl2, subLbl3, uCount);
            FlowPane footer = new FlowPane(6, 4);
            for (DispatchedUnit du : r.units) {
                String col = unitColor(du.unit.type);
                String origin = URLEncoder.encode(du.unit.name+", Dehradun", StandardCharsets.UTF_8);
                String dest   = URLEncoder.encode(r.place.name+", Dehradun", StandardCharsets.UTF_8);
                String mapsUrl = "https://www.google.com/maps/dir/?api=1&origin="+origin+"&destination="+dest;
                Button pill = new Button(du.unit.icon+" "+du.unit.name.split(" ")[0]+" \u2197");
                pill.setStyle("-fx-background-color: #1a1a28; -fx-border-color: "+col+"80; -fx-border-width: 0.5; -fx-border-radius: 6; -fx-background-radius: 6; -fx-text-fill: "+col+"; -fx-font-size: 10px; -fx-padding: 2 8; -fx-cursor: hand;");
                pill.setOnAction(e -> { try { java.awt.Desktop.getDesktop().browse(new java.net.URI(mapsUrl)); } catch (Exception ex) { ex.printStackTrace(); } });
                footer.getChildren().add(pill);
            }
            Label trafLbl2 = new Label(r.traffic_label); trafLbl2.setStyle("-fx-text-fill: #3a3a5a; -fx-font-size: 10px;");
            footer.getChildren().add(trafLbl2);
            VBox card = new VBox(8, hdr, body, footer); card.setPadding(new Insets(14,16,14,16));
            card.setStyle("-fx-background-color: #111118; -fx-border-color: #2a2a3a; -fx-border-width: 0.5; -fx-border-radius: 11; -fx-background-radius: 11;");
            logContainer.getChildren().add(card);
        }
    }

    String[] getTypeColor(String type) {
        return switch (type) { case "crime" -> new String[]{"#2a1628","#cc33aa","#ee88cc"}; case "fire" -> new String[]{"#2a1a10","#cc6622","#ffaa55"}; case "accident" -> new String[]{"#1a1a2a","#3355cc","#7799ee"}; default -> new String[]{"#102a1a","#226644","#44cc88"}; };
    }

    String getSevColor(int sev) {
        return switch (sev) { case 1 -> "#22c55e"; case 2 -> "#84cc16"; case 3 -> "#eab308"; case 4 -> "#f97316"; default -> "#ef4444"; };
    }

    void updateLogStats() {
        int total = emergencyLog.size();
        long critical = emergencyLog.stream().filter(r -> r.severity >= 4).count();
        int units = emergencyLog.stream().mapToInt(r -> r.units.size()).sum();
        double avg = total > 0 ? emergencyLog.stream().mapToInt(r -> r.severity).average().orElse(0) : 0;
        if (statTotal    != null) statTotal.setText(String.valueOf(total));
        if (statCritical != null) statCritical.setText(String.valueOf(critical));
        if (statUnits    != null) statUnits.setText(String.valueOf(units));
        if (statAvgSev   != null) statAvgSev.setText(total > 0 ? String.format("%.1f", avg) : "-");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // RESPONDER ALERTS VIEW
    // ─────────────────────────────────────────────────────────────────────────

    VBox buildResponderAlertsView() {
        VBox view = new VBox(12); view.setPadding(new Insets(12)); view.setStyle("-fx-background-color: #0a0a0f;");

        Label title = new Label("RESPONDER ALERT DATABASE — Live dispatch notifications sent to each unit");
        title.setStyle("-fx-text-fill: #4444aa; -fx-font-size: 10px; -fx-font-weight: bold;");
        title.setWrapText(true);

        // Stats row
        HBox statsRow = new HBox(10);
        alertStatPending  = statCard("0", "#eab308", "Pending");
        alertStatEnRoute  = statCard("0", "#f97316", "En Route");
        alertStatArrived  = statCard("0", "#22c55e", "Arrived");
        statsRow.getChildren().addAll(alertStatPending.getParent(), alertStatEnRoute.getParent(), alertStatArrived.getParent());
        for (var n : statsRow.getChildren()) HBox.setHgrow(n, Priority.ALWAYS);

        Label howItWorks = new Label("\uD83D\uDCA1 How it works: When you dispatch units, each responder gets an alert record saved to SQLite. Responders update their status below (Acknowledge \u2192 En Route \u2192 Arrived). When Arrived, the unit is automatically freed.");
        howItWorks.setWrapText(true);
        howItWorks.setStyle("-fx-text-fill: #5555aa; -fx-font-size: 11px; -fx-background-color: #111128; -fx-border-color: #2a2a4a; -fx-border-width: 0.5; -fx-border-radius: 8; -fx-background-radius: 8; -fx-padding: 10 12;");

        alertContainer = new VBox(10);
        Label emptyLbl = new Label("\uD83D\uDD14\n\nNo responder alerts yet. Dispatch an emergency to send alerts."); emptyLbl.setStyle("-fx-text-fill: #3a3a5a; -fx-font-size: 13px;"); emptyLbl.setAlignment(Pos.CENTER); emptyLbl.setTextAlignment(TextAlignment.CENTER);
        alertContainer.getChildren().add(emptyLbl);

        // Load from DB on startup
        List<ResponderAlert> dbAlerts = AlertDatabase.load_all();
        if (!dbAlerts.isEmpty()) { responderAlerts.addAll(dbAlerts); renderAlerts(); }

        view.getChildren().addAll(title, statsRow, howItWorks, alertContainer);
        return view;
    }

    void renderAlerts() {
        if (alertContainer == null) return;
        Platform.runLater(() -> {
            alertContainer.getChildren().clear();
            if (responderAlerts.isEmpty()) {
                Label e = new Label("\uD83D\uDD14\n\nNo responder alerts yet."); e.setStyle("-fx-text-fill: #3a3a5a; -fx-font-size: 13px;"); e.setAlignment(Pos.CENTER); e.setTextAlignment(TextAlignment.CENTER);
                alertContainer.getChildren().add(e); return;
            }
            for (ResponderAlert a : responderAlerts) {
                String col = switch (a.unit_type) { case "police" -> "#3b82f6"; case "hospital" -> "#22c55e"; case "fire" -> "#f97316"; default -> "#aaaacc"; };
                String statusColor = a.status_color();

                HBox hdr = new HBox(8); hdr.setAlignment(Pos.CENTER_LEFT);
                Label idLbl = new Label("Alert #"+a.id+" | Emergency #"+a.emergency_id); idLbl.setStyle("-fx-text-fill: #3a3a6a; -fx-font-size: 10px;");
                Label statusBadge = new Label(a.status_label()); statusBadge.setStyle("-fx-background-color: "+statusColor+"22; -fx-border-color: "+statusColor+"; -fx-border-width: 0.5; -fx-border-radius: 10; -fx-background-radius: 10; -fx-text-fill: "+statusColor+"; -fx-font-size: 11px; -fx-padding: 3 10;");
                // ETA Countdown
                Label countdownLbl = new Label(a.countdown());
                countdownLbl.setStyle("-fx-background-color: "+a.countdown_color()+"22; -fx-border-color: "+a.countdown_color()+"; -fx-border-width: 0.5; -fx-border-radius: 10; -fx-background-radius: 10; -fx-text-fill: "+a.countdown_color()+"; -fx-font-size: 11px; -fx-font-weight: bold; -fx-padding: 3 10; -fx-font-family: monospace;");
                Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);
                Label timeLbl = new Label(a.sent_at); timeLbl.setStyle("-fx-text-fill: #3a3a6a; -fx-font-size: 10px;");
                hdr.getChildren().addAll(idLbl, statusBadge, countdownLbl, sp, timeLbl);

                HBox body = new HBox(10); body.setAlignment(Pos.CENTER_LEFT);
                Label unitIcon = new Label(a.unit_icon); unitIcon.setStyle("-fx-font-size: 20px;");
                Label unitName = new Label(a.unit_name); unitName.setStyle("-fx-text-fill: #eeeeee; -fx-font-size: 13px; -fx-font-weight: bold;");
                Label dest = new Label("\u2192 " + a.place); dest.setStyle("-fx-text-fill: #ef4444; -fx-font-size: 12px; -fx-font-weight: bold;");
                Label detail = new Label(String.format("%.2f km  |  ETA %.1f min  |  %s - %s  |  Sev %d", a.dist_km, a.eta_min, capitalize(a.emg_type), a.emg_subtype, a.severity)); detail.setStyle("-fx-text-fill: #6666aa; -fx-font-size: 10px;");
                VBox info = new VBox(3, new HBox(8, unitName, dest), detail); HBox.setHgrow(info, Priority.ALWAYS);
                body.getChildren().addAll(unitIcon, info);

                // Action buttons — next status step
                HBox actions = new HBox(8);
                if (a.status == ResponderAlert.Status.PENDING) {
                    Button ackBtn = new Button("\u2705 Acknowledge");
                    ackBtn.setStyle("-fx-background-color: #141e35; -fx-border-color: #3b82f6; -fx-border-width: 0.5; -fx-border-radius: 7; -fx-background-radius: 7; -fx-text-fill: #3b82f6; -fx-font-size: 11px; -fx-padding: 5 14; -fx-cursor: hand;");
                    ackBtn.setOnAction(e -> { a.status = ResponderAlert.Status.ACKNOWLEDGED; AlertDatabase.update_status(a.id, a.status); renderAlerts(); updateAlertStats(); });
                    actions.getChildren().add(ackBtn);
                } else if (a.status == ResponderAlert.Status.ACKNOWLEDGED) {
                    Button enRouteBtn = new Button("\uD83D\uDE80 En Route");
                    enRouteBtn.setStyle("-fx-background-color: #1e1a14; -fx-border-color: #f97316; -fx-border-width: 0.5; -fx-border-radius: 7; -fx-background-radius: 7; -fx-text-fill: #f97316; -fx-font-size: 11px; -fx-padding: 5 14; -fx-cursor: hand;");
                    enRouteBtn.setOnAction(e -> { a.status = ResponderAlert.Status.EN_ROUTE; AlertDatabase.update_status(a.id, a.status); renderAlerts(); updateAlertStats(); });
                    actions.getChildren().add(enRouteBtn);
                } else if (a.status == ResponderAlert.Status.EN_ROUTE) {
                    Button arrivedBtn = new Button("\uD83D\uDCCD Arrived");
                    arrivedBtn.setStyle("-fx-background-color: #142214; -fx-border-color: #22c55e; -fx-border-width: 0.5; -fx-border-radius: 7; -fx-background-radius: 7; -fx-text-fill: #22c55e; -fx-font-size: 11px; -fx-padding: 5 14; -fx-cursor: hand;");
                    arrivedBtn.setOnAction(e -> { a.status = ResponderAlert.Status.ARRIVED; AlertDatabase.update_status(a.id, a.status); DispatchEngine.set_free(a.unit_name); renderAlerts(); updateAlertStats(); refreshUnitsStatusView(); refreshUnitGrid(); updateStatsCharts(); });
                    actions.getChildren().add(arrivedBtn);
                } else {
                    Label done = new Label("\uD83C\uDFC1 Mission Complete — Unit freed"); done.setStyle("-fx-text-fill: #22c55e; -fx-font-size: 11px;");
                    actions.getChildren().add(done);
                }

                VBox card = new VBox(8, hdr, body, actions); card.setPadding(new Insets(14,16,14,16));
                card.setStyle("-fx-background-color: #111118; -fx-border-color: "+col+"; -fx-border-width: 0.5; -fx-border-radius: 11; -fx-background-radius: 11;");
                alertContainer.getChildren().add(card);
            }
            updateAlertStats();
        });
    }

    void updateAlertStats() {
        long pending  = responderAlerts.stream().filter(a -> a.status == ResponderAlert.Status.PENDING).count();
        long enRoute  = responderAlerts.stream().filter(a -> a.status == ResponderAlert.Status.EN_ROUTE || a.status == ResponderAlert.Status.ACKNOWLEDGED).count();
        long arrived  = responderAlerts.stream().filter(a -> a.status == ResponderAlert.Status.ARRIVED).count();
        long active   = responderAlerts.stream().filter(a -> a.status != ResponderAlert.Status.ARRIVED).count();
        if (alertStatPending != null) alertStatPending.setText(String.valueOf(pending));
        if (alertStatEnRoute != null) alertStatEnRoute.setText(String.valueOf(enRoute));
        if (alertStatArrived != null) alertStatArrived.setText(String.valueOf(arrived));
        if (alertCountBadge  != null) { alertCountBadge.setText(String.valueOf(active)); alertCountBadge.setStyle(active > 0 ? "-fx-background-color: #2a1616; -fx-text-fill: #ff6666; -fx-font-size: 10px; -fx-padding: 1 7 1 7; -fx-background-radius: 10;" : "-fx-background-color: #2a2a3a; -fx-text-fill: #7777aa; -fx-font-size: 10px; -fx-padding: 1 7 1 7; -fx-background-radius: 10;"); }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // STATISTICS DASHBOARD
    // ─────────────────────────────────────────────────────────────────────────

    VBox buildStatsView() {
        VBox view = new VBox(0);
        view.setStyle("-fx-background-color: #0a0a0f;");
        WebView statsWebView = new WebView();
        statsEngine = statsWebView.getEngine();
        statsEngine.setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
        statsEngine.loadContent(StatsBuilder.buildStatsHtml(emergencyLog, responderAlerts));
        VBox.setVgrow(statsWebView, Priority.ALWAYS);
        view.getChildren().add(statsWebView);
        return view;
    }

    void updateStatsCharts() {
        if (statsEngine == null) return;
        Platform.runLater(() ->
            statsEngine.loadContent(StatsBuilder.buildStatsHtml(emergencyLog, responderAlerts)));
    }

    // HELPERS
    // ─────────────────────────────────────────────────────────────────────────

    String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.substring(0,1).toUpperCase() + s.substring(1);
    }
}
