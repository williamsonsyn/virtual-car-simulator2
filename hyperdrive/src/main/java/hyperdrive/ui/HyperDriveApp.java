package hyperdrive.ui;

import hyperdrive.enums.AirbrakeState;
import hyperdrive.enums.GearPosition;
import hyperdrive.enums.LiftState;
import hyperdrive.exceptions.OperationDeniedException;
import hyperdrive.io.Logger;
import hyperdrive.model.Car;
import hyperdrive.modes.ComfortMode;
import hyperdrive.modes.SportMode;
import hyperdrive.modes.TrackMode;
import hyperdrive.sim.SimulationEngine;
import hyperdrive.systems.Tyre;
import hyperdrive.telemetry.TelemetrySnapshot;
import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * The JavaFX Cockpit/Dashboard screen - screen 1 of 3 from the original plan (Diagnostics and
 * Fault Simulator come in a later pass). This class ONLY builds UI and calls Car's public
 * methods - it holds no vehicle logic of its own. Every key press either calls a Car method
 * directly (throttle/brake are immediate, no exception) or wraps a Car method in try/catch and
 * shows OperationDeniedException's reasons in the status bar. Car decides; this class displays.
 *
 * Keys implemented here: I (power on), ENTER (start engine), O (stop engine / power off),
 * W/S (throttle/brake, held), Q/E (downshift/upshift), P/N/R (park/neutral/reverse - added
 * beyond the original W/S/A/D/Q/E/I/ENTER/1/2/3/L/B/V/F/T list, since the model supports them),
 * 1/2/3 (Comfort/Sport/Track), B (airbrake deploy/retract), V (lift raise/lower), L (launch).
 * NOT implemented yet: A/D (steering - there is no steering system in the OOP model to bind to),
 * F and T (Fault Simulator and Diagnostics screens - they don't exist yet).
 *
 * Run with: mvn clean javafx:run   (see README "Run the JavaFX UI" for why plain java/javac
 * will not launch this class, and for troubleshooting).
 */
public class HyperDriveApp extends Application {

    private Car car;
    private Logger logger;
    private SimulationEngine simulationEngine;
    private Thread simulationThread;

    private Label speedLabel;
    private Label rpmLabel;
    private Label gearLabel;
    private Label modeLabel;
    private Label powerStateLabel;
    private Label fuelLabel;
    private Label coolantLabel;
    private Label oilLabel;
    private Label batteryLabel;
    private Label driftLabel;
    private Label launchLabel;
    private Label airbrakeLabel;
    private Label liftLabel;
    private Label statusLabel;   // shows the result of the last key action
    private Label[] tyreLabels;

    @Override
    public void start(Stage stage) {
        logger = new Logger("logs/hyperdrive-session.log");
        logger.open();
        car = new Car(logger);

        BorderPane root = buildLayout();
        Scene scene = new Scene(root, 900, 560);
        scene.getStylesheets().add(getClass().getResource("/hyperdrive/ui/dashboard.css").toExternalForm());
        scene.setOnKeyPressed(this::handleKeyPressed);
        scene.setOnKeyReleased(this::handleKeyReleased);

        stage.setTitle("HYPERDRIVE X-01");
        stage.setScene(scene);
        stage.setOnCloseRequest(e -> shutdown());
        stage.show();

        simulationEngine = new SimulationEngine(car);
        simulationThread = new Thread(simulationEngine, "Simulation-Thread");
        simulationThread.setDaemon(true);   // a safety net - shutdown() is the real cleanup path
        simulationThread.start();

        AnimationTimer refreshLoop = new AnimationTimer() {
            @Override
            public void handle(long now) {
                refreshDashboard();   // read-only: this timer never changes Car, only displays it
            }
        };
        refreshLoop.start();

        setStatus("HYPERDRIVE X-01 ready. Press I to power on, then ENTER to start the engine.");
    }

    // ------------------------------------------------------------------ layout

    private BorderPane buildLayout() {
        BorderPane root = new BorderPane();
        root.getStyleClass().add("root-pane");

        Label title = new Label("HYPERDRIVE X-01");
        title.getStyleClass().add("app-title");
        powerStateLabel = new Label("SLEEP");
        powerStateLabel.getStyleClass().add("power-badge");
        HBox top = new HBox(16, title, powerStateLabel);
        top.setAlignment(Pos.CENTER_LEFT);
        top.setPadding(new Insets(12, 20, 12, 20));
        top.getStyleClass().add("top-bar");
        root.setTop(top);

        speedLabel = bigLabel("0");
        VBox speedBox = labeledColumn(speedLabel, "km/h");
        rpmLabel = bigLabel("0");
        VBox rpmBox = labeledColumn(rpmLabel, "RPM");
        gearLabel = bigLabel("P");
        VBox gearBox = labeledColumn(gearLabel, "GEAR");

        HBox centerRow = new HBox(40, speedBox, rpmBox, gearBox);
        centerRow.setAlignment(Pos.CENTER);

        modeLabel = new Label("Comfort");
        modeLabel.getStyleClass().add("mode-label");
        driftLabel = new Label("drift 0%");
        driftLabel.getStyleClass().add("info-label");
        launchLabel = new Label("launch: IDLE");
        launchLabel.getStyleClass().add("info-label");
        HBox modeRow = new HBox(20, modeLabel, driftLabel, launchLabel);
        modeRow.setAlignment(Pos.CENTER);

        VBox center = new VBox(18, centerRow, modeRow);
        center.setAlignment(Pos.CENTER);
        center.getStyleClass().add("center-pane");
        root.setCenter(center);

        fuelLabel = new Label("Fuel: --");
        coolantLabel = new Label("Coolant: --");
        oilLabel = new Label("Oil: --");
        batteryLabel = new Label("Battery: --");
        airbrakeLabel = new Label("Airbrake: --");
        liftLabel = new Label("Lift: --");
        VBox statusColumn = new VBox(8, fuelLabel, coolantLabel, oilLabel, batteryLabel, airbrakeLabel, liftLabel);
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
        root.setRight(rightPane);

        statusLabel = new Label("");
        statusLabel.getStyleClass().add("status-label");
        Label controlsHint = new Label(
                "I power on | ENTER start | O stop/power off | W/S throttle/brake | Q/E gear down/up | "
                        + "P/N/R park/neutral/reverse | 1/2/3 Comfort/Sport/Track | B airbrake | V lift | L launch");
        controlsHint.getStyleClass().add("hint-label");
        VBox bottom = new VBox(4, statusLabel, controlsHint);
        bottom.setPadding(new Insets(10, 20, 14, 20));
        bottom.getStyleClass().add("bottom-bar");
        root.setBottom(bottom);

        return root;
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

    // ------------------------------------------------------------------ input

    private void handleKeyPressed(KeyEvent event) {
        switch (event.getCode()) {
            case I -> attempt("POWER ON", car::powerOn);
            case ENTER -> attempt("START ENGINE", car::startEngine);
            case O -> attempt("STOP / POWER OFF", this::powerDown);
            case W -> car.setThrottle(1.0);
            case S -> car.pressBrake(1.0);
            case Q -> attempt("DOWNSHIFT", car::shiftDown);
            case E -> attempt("UPSHIFT", car::shiftUp);
            case P -> attempt("PARK", () -> car.shiftGear(GearPosition.P));
            case N -> attempt("NEUTRAL", () -> car.shiftGear(GearPosition.N));
            case R -> attempt("REVERSE", () -> car.shiftGear(GearPosition.R));
            case DIGIT1 -> attempt("COMFORT MODE", () -> car.selectMode(new ComfortMode()));
            case DIGIT2 -> attempt("SPORT MODE", () -> car.selectMode(new SportMode()));
            case DIGIT3 -> attempt("TRACK MODE", () -> car.selectMode(new TrackMode()));
            case B -> attempt(car.getAirbrakeState() == AirbrakeState.DEPLOYED ? "RETRACT AIRBRAKE" : "DEPLOY AIRBRAKE",
                    this::toggleAirbrake);
            case V -> attempt(car.getLiftState() == LiftState.DOWN ? "RAISE LIFT" : "LOWER LIFT",
                    this::toggleLift);
            case L -> attempt("REQUEST LAUNCH", car::requestLaunch);
            default -> { }
        }
    }

    private void handleKeyReleased(KeyEvent event) {
        switch (event.getCode()) {
            case W -> car.setThrottle(0.0);
            case S -> car.releaseBrake();
            default -> { }
        }
    }

    private void toggleAirbrake() throws OperationDeniedException {
        if (car.getAirbrakeState() == AirbrakeState.DEPLOYED) {
            car.retractAirbrake();
        } else {
            car.deployAirbrake();
        }
    }

    private void toggleLift() throws OperationDeniedException {
        if (car.getLiftState() == LiftState.DOWN) {
            car.raiseLift();
        } else {
            car.lowerLift();
        }
    }

    private void powerDown() throws OperationDeniedException {
        if (car.getPowerState() == hyperdrive.enums.PowerState.ENGINE_RUNNING) {
            car.stopEngine();
        } else {
            car.powerOff();
        }
    }

    @FunctionalInterface
    private interface CarAction {
        void run() throws OperationDeniedException;
    }

    private void attempt(String label, CarAction action) {
        try {
            action.run();
            setStatus(label + " - OK");
        } catch (OperationDeniedException e) {
            setStatus(label + " DENIED: " + String.join("; ", e.getReasons()));
        }
    }

    private void setStatus(String message) {
        statusLabel.setText(message);
    }

    // ------------------------------------------------------------------ dashboard refresh

    private void refreshDashboard() {
        TelemetrySnapshot t = car.getTelemetry();   // one atomic read - see hyperdrive.telemetry

        speedLabel.setText(String.format("%.0f", t.getSpeedKmh()));
        rpmLabel.setText(String.format("%.0f", t.getRpm()));
        gearLabel.setText(t.getGear().getLabel());
        modeLabel.setText(t.getMode().getName());
        powerStateLabel.setText(t.getPowerState().name());
        fuelLabel.setText(String.format("Fuel: %.0f%%", t.getFuelLevelPercent()));
        coolantLabel.setText(String.format("Coolant: %.0f C", t.getCoolantTemp()));
        oilLabel.setText(String.format("Oil: %.0f C", t.getOilTemp()));
        batteryLabel.setText(String.format("Battery: %.1f V", t.getBatteryVoltage()));
        airbrakeLabel.setText("Airbrake: " + t.getAirbrakeState());
        liftLabel.setText("Lift: " + t.getLiftState());
        driftLabel.setText(String.format("drift %.0f%%", t.getDriftLevel()));
        launchLabel.setText(String.format("launch: %s (%.0f%%)", t.getLaunchState(), t.getLaunchBoostPercent()));

        Tyre[] tyres = t.getTyres();
        String[] positions = {"FL", "FR", "RL", "RR"};
        for (int i = 0; i < tyres.length && i < tyreLabels.length; i++) {
            tyreLabels[i].setText(String.format("%s: %.1f bar / %.0f C", positions[i],
                    tyres[i].getPressure(), tyres[i].getTemperature()));
        }

        powerStateLabel.getStyleClass().removeAll("power-badge", "power-badge-fault");
        powerStateLabel.getStyleClass().add(t.getActiveFaultCount() > 0 ? "power-badge-fault" : "power-badge");
    }

    // ------------------------------------------------------------------ shutdown

    @Override
    public void stop() {
        shutdown();
    }

    private void shutdown() {
        if (simulationEngine != null) {
            simulationEngine.stop();
        }
        if (simulationThread != null) {
            try {
                simulationThread.join(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (logger != null) {
            logger.close();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
