package hyperdrive.ui;

import hyperdrive.sensors.SpeedSensor;
import hyperdrive.systems.Engine;
import hyperdrive.systems.Tyre;
import hyperdrive.telemetry.TelemetrySnapshot;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/**
 * The Cockpit/Dashboard screen: speed, RPM, gear, mode, and system status.
 * Pure display - it never calls a Car method itself. HyperDriveApp's key handlers call Car;
 * this screen only shows the result, via refresh(TelemetrySnapshot).
 */
public class CockpitScreen extends BorderPane implements Screen {
    private static final double SPEED_GAUGE_MAX = SpeedSensor.MAX_SPEED_KMH;
    private static final double SPEED_GAUGE_REDZONE = 280;

    private final GaugeView speedGauge;
    private final GaugeView rpmGauge;
    private final Label gearLabel;
    private final Label modeLabel;
    private final Label fuelLabel;
    private final Label coolantLabel;
    private final Label oilLabel;
    private final Label batteryLabel;
    private final Label brakeLabel;
    private final Label driftLabel;
    private final Label launchLabel;
    private final Label faultCountLabel;
    private final Label airbrakeLabel;
    private final Label liftLabel;
    private final Label shutdownWarningLabel;
    private final Label[] tyreLabels;

    public CockpitScreen() {
        getStyleClass().add("cockpit-screen");

        rpmGauge = new GaugeView("RPM x1000", 0, Engine.REDLINE_RPM, Engine.REDLINE_RPM * 0.875, "rpm");
        speedGauge = new GaugeView("SPEED", 0, SPEED_GAUGE_MAX, SPEED_GAUGE_REDZONE, "km/h");
        gearLabel = bigLabel("P");
        VBox gearBox = labeledColumn(gearLabel, "GEAR");

        HBox centerRow = new HBox(20, rpmGauge, gearBox, speedGauge);
        centerRow.setAlignment(Pos.CENTER);

        modeLabel = new Label("Comfort");
        modeLabel.getStyleClass().add("mode-label");
        driftLabel = new Label("drift 0%");
        driftLabel.getStyleClass().add("info-label");
        launchLabel = new Label("launch: IDLE");
        launchLabel.getStyleClass().add("info-label");
        faultCountLabel = new Label("faults: 0");
        faultCountLabel.getStyleClass().add("info-label");
        HBox modeRow = new HBox(20, modeLabel, driftLabel, launchLabel, faultCountLabel);
        modeRow.setAlignment(Pos.CENTER);

        shutdownWarningLabel = new Label("");
        shutdownWarningLabel.getStyleClass().add("shutdown-warning");
        shutdownWarningLabel.setVisible(false);
        shutdownWarningLabel.setManaged(false);   // takes no layout space while hidden

        VBox center = new VBox(18, centerRow, modeRow, shutdownWarningLabel);
        center.setAlignment(Pos.CENTER);
        center.getStyleClass().add("center-pane");
        setCenter(center);

        fuelLabel = new Label("Fuel: --");
        coolantLabel = new Label("Coolant: --");
        oilLabel = new Label("Oil: --");
        batteryLabel = new Label("Battery: --");
        brakeLabel = new Label("Brakes: --");
        airbrakeLabel = new Label("Airbrake: --");
        liftLabel = new Label("Lift: --");
        VBox statusColumn = new VBox(8,
                fuelLabel, coolantLabel, oilLabel, batteryLabel, brakeLabel, airbrakeLabel, liftLabel);
        statusColumn.getStyleClass().add("side-panel");
        statusColumn.setPadding(new Insets(16));

        GridPane tyreGrid = new GridPane();
        tyreGrid.setHgap(10);
        tyreGrid.setVgap(6);
        tyreLabels = new Label[4];
        String[] positions = {"FL", "FR", "RL", "RR"};
        for (int i = 0; i < 4; i++) {
            tyreLabels[i] = new Label(positions[i] + ": --");
            tyreGrid.add(tyreLabels[i], i % 2, i / 2);
        }
        VBox tyrePanel = new VBox(8, new Label("TYRES"), tyreGrid);
        tyrePanel.getStyleClass().add("side-panel");
        tyrePanel.setPadding(new Insets(16));

        VBox rightPane = new VBox(12, statusColumn, tyrePanel);
        setRight(rightPane);
    }

    private Label bigLabel(String initial) {
        Label label = new Label(initial);
        label.getStyleClass().add("big-number");
        return label;
    }

    private VBox labeledColumn(Label valueLabel, String unit) {
        Label unitLabel = new Label(unit);
        unitLabel.getStyleClass().add("unit-label");
        VBox box = new VBox(valueLabel, unitLabel);
        box.setAlignment(Pos.CENTER);
        return box;
    }

    @Override
    public void refresh(TelemetrySnapshot t) {
        speedGauge.setValue(t.getSpeedKmh());
        rpmGauge.setValue(t.getRpm());
        gearLabel.setText(t.getGear().getLabel());
        modeLabel.setText(t.getMode().getName());
        fuelLabel.setText(String.format("Fuel: %.0f%%", t.getFuelLevelPercent()));
        coolantLabel.setText(String.format("Coolant: %.0f C", t.getCoolantTemp()));
        oilLabel.setText(String.format("Oil: %.0f C", t.getOilTemp()));
        batteryLabel.setText(String.format("Battery: %.1f V", t.getBatteryVoltage()));
        brakeLabel.setText(String.format("Brakes: %.0f C%s", t.getBrakeDiscTemp(), t.isAbsActive() ? " | ABS" : ""));
        brakeLabel.getStyleClass().removeAll("info-label", "abs-active");
        brakeLabel.getStyleClass().add(t.isAbsActive() ? "abs-active" : "info-label");
        airbrakeLabel.setText("Airbrake: " + t.getAirbrakeState());
        liftLabel.setText("Lift: " + t.getLiftState());
        driftLabel.setText(String.format("drift %.0f%%", t.getDriftLevel()));
        launchLabel.setText(String.format("launch: %s (%.0f%%)", t.getLaunchState(), t.getLaunchBoostPercent()));
        faultCountLabel.setText("faults: " + t.getActiveFaultCount());
        faultCountLabel.getStyleClass().removeAll("info-label", "fault-count-active");
        faultCountLabel.getStyleClass().add(t.getActiveFaultCount() > 0 ? "fault-count-active" : "info-label");

        Tyre[] tyres = t.getTyres();
        String[] positions = {"FL", "FR", "RL", "RR"};
        for (int i = 0; i < tyres.length && i < tyreLabels.length; i++) {
            tyreLabels[i].setText(String.format("%s: %.1f bar / %.0f C", positions[i],
                    tyres[i].getPressure(), tyres[i].getTemperature()));
        }

        boolean shuttingDown = t.getCriticalFaultCountdown() > 0;
        shutdownWarningLabel.setVisible(shuttingDown);
        shutdownWarningLabel.setManaged(shuttingDown);
        if (shuttingDown) {
            shutdownWarningLabel.setText(String.format(
                    "CRITICAL FAULT - ENGINE SHUTDOWN IN %.0fs", t.getCriticalFaultCountdown()));
        }
    }
}
