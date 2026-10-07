package hyperdrive.ui;

import hyperdrive.enums.FaultType;
import hyperdrive.model.Car;
import hyperdrive.model.Fault;
import hyperdrive.telemetry.TelemetrySnapshot;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The Fault Simulator screen: one row per FaultType, each with an INJECT/CLEAR button and a
 * live OK/ACTIVE status. Deliberately simple - Car.injectFault()/clearFault() are unconditional
 * (no OperationDeniedException; faults are meant to be injectable at will), so button clicks
 * call Car directly with no try/catch needed, unlike every key action on the Cockpit screen.
 *
 * Button clicks run on the JavaFX Application Thread; the SimulationEngine ticks Car on its own
 * background thread. Both go through Car's synchronized methods, so this is safe for the exact
 * reason explained in the README's "Thread safety" section - nothing new needed here.
 */
public class FaultSimulatorScreen extends BorderPane implements Screen {
    private final Car car;
    private final Label summaryLabel = new Label();
    private final Map<FaultType, Label> statusLabels = new EnumMap<>(FaultType.class);
    private final Map<FaultType, Button> toggleButtons = new EnumMap<>(FaultType.class);

    public FaultSimulatorScreen(Car car) {
        this.car = car;
        getStyleClass().add("fault-screen");
        setPadding(new Insets(20));

        summaryLabel.getStyleClass().add("fault-summary");
        BorderPane.setMargin(summaryLabel, new Insets(0, 0, 16, 0));
        setTop(summaryLabel);
        ScrollPane scroll = new ScrollPane(buildFaultGrid());
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("log-scroll");
        setCenter(scroll);
        setBottom(buildConditionBar());
    }

    private GridPane buildFaultGrid() {
        GridPane grid = new GridPane();
        grid.setHgap(20);
        grid.setVgap(14);

        int row = 0;
        for (FaultType type : FaultType.values()) {
            Label nameLabel = new Label(type.getDescription());
            nameLabel.getStyleClass().add("fault-name");

            Label severityLabel = new Label(type.getDefaultSeverity().name());
            severityLabel.getStyleClass().add("fault-severity-" + type.getDefaultSeverity().name().toLowerCase());

            Label statusLabel = new Label("OK");
            statusLabel.getStyleClass().add("fault-status-ok");
            statusLabels.put(type, statusLabel);

            Button toggle = new Button("INJECT");
            toggle.getStyleClass().add("fault-toggle-button");
            toggle.setOnAction(e -> toggleFault(type));
            toggleButtons.put(type, toggle);

            grid.add(nameLabel, 0, row);
            grid.add(severityLabel, 1, row);
            grid.add(statusLabel, 2, row);
            grid.add(toggle, 3, row);
            row++;
        }
        return grid;
    }

    /** Non-fault conditions that drive warnings and launch preconditions: door, seatbelt, road slope, warm-up. */
    private HBox buildConditionBar() {
        Button door = new Button("TOGGLE DOOR");
        door.getStyleClass().add("fault-toggle-button");
        door.setOnAction(e -> car.setDoorOpen(!car.getTelemetry().isDoorOpen()));
        Button belt = new Button("TOGGLE SEATBELT");
        belt.getStyleClass().add("fault-toggle-button");
        belt.setOnAction(e -> car.setSeatBeltFastened(!car.getTelemetry().isSeatBeltFastened()));
        Button slope = new Button("TOGGLE 8% SLOPE");
        slope.getStyleClass().add("fault-toggle-button");
        slope.setOnAction(e -> car.setRoadSlope(car.getRoadSlope() == 0 ? 8.0 : 0.0));
        Button warm = new Button("PRE-WARM (SIM)");
        warm.getStyleClass().add("fault-toggle-button");
        warm.setOnAction(e -> car.preWarm());
        HBox bar = new HBox(12, door, belt, slope, warm);
        bar.setPadding(new Insets(14, 0, 0, 0));
        return bar;
    }

    private void toggleFault(FaultType type) {
        if (isActive(type)) {
            car.clearFault(type);
        } else {
            car.injectFault(type);
        }
        // No need to refresh() here - the AnimationTimer redraws every screen every frame anyway,
        // so the button/label will catch up within one frame regardless.
    }

    private boolean isActive(FaultType type) {
        for (Fault f : car.getActiveFaults()) {
            if (f.getType() == type) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void refresh(TelemetrySnapshot telemetry) {
        // Read live faults straight from Car, not the snapshot - TelemetrySnapshot only carries a
        // COUNT (getActiveFaultCount()), because a dashboard doesn't need to know WHICH ones are
        // active. This screen's whole job is showing which, so it goes to Car directly.
        List<Fault> active = car.getActiveFaults();
        Set<FaultType> activeTypes = EnumSet.noneOf(FaultType.class);
        for (Fault f : active) {
            activeTypes.add(f.getType());
        }

        for (FaultType type : FaultType.values()) {
            boolean isActive = activeTypes.contains(type);
            Label statusLabel = statusLabels.get(type);
            Button button = toggleButtons.get(type);

            statusLabel.setText(isActive ? "ACTIVE" : "OK");
            statusLabel.getStyleClass().removeAll("fault-status-ok", "fault-status-active");
            statusLabel.getStyleClass().add(isActive ? "fault-status-active" : "fault-status-ok");
            button.setText(isActive ? "CLEAR" : "INJECT");
        }

        summaryLabel.setText(active.size() + " of " + FaultType.values().length + " faults active");
    }
}
