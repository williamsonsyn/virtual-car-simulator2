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
import hyperdrive.telemetry.TelemetrySnapshot;
import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import java.util.HashSet;
import java.util.Set;

/**
 * App entry point and coordinator. Holds the Car, the Logger, the SimulationEngine's thread, and
 * the shared header/footer chrome. Screen-specific UI lives in CockpitScreen and
 * FaultSimulatorScreen (both implement Screen) - this class never builds dashboard content
 * itself, it only swaps which screen sits in the center and refreshes both every frame.
 *
 * Keys: I power on, ENTER start engine, O stop/power off, W/S throttle/brake (held), Q/E gear
 * down/up, P/N/R park/neutral/reverse, 1/2/3 Comfort/Sport/Track, B airbrake, V lift, L launch,
 * F switch to Fault Simulator, T switch to Diagnostics, C switch back to Cockpit.
 * NOT implemented: A/D (steering - there is no steering system in the OOP model to bind to).
 *
 * Run with: mvn clean javafx:run   (see README "Run the JavaFX UI").
 */
public class HyperDriveApp extends Application {

    private Car car;
    private Logger logger;
    private SimulationEngine simulationEngine;
    private Thread simulationThread;

    private CockpitScreen cockpitScreen;
    private FaultSimulatorScreen faultScreen;
    private DiagnosticsScreen diagnosticsScreen;
    private Screen[] screens;   // refreshed every frame regardless of which is visible

    private BorderPane root;
    private Label powerStateLabel;
    private Label screenNameLabel;
    private Label statusLabel;
    private Label hintLabel;

    // Key-repeat guard: JavaFX fires KEY_PRESSED repeatedly while a key is held down (OS auto-repeat).
    // Without this, holding ENTER would call car.startEngine() dozens of times a second.
    private final Set<KeyCode> heldKeys = new HashSet<>();

    // Throttle/brake pedal smoothing: PRESS ramps up over PEDAL_RAMP_SECONDS (feels like a real pedal
    // push, not a light switch); RELEASE is instant, same as before - this also means releaseBrake()'s
    // launch-trigger check (see Car) still fires exactly when it always did.
    private static final double PEDAL_RAMP_SECONDS = 0.25;
    private boolean throttleHeld = false;
    private boolean brakeHeld = false;
    private double throttleValue = 0;
    private double brakeValue = 0;
    private long lastInputTickNanos = -1;

    private static final String COCKPIT_HINT =
            "I power on | ENTER start | O stop/power off | W/S throttle/brake | Q/E gear down/up | "
                    + "P/N/R park/neutral/reverse | 1/2/3 Comfort/Sport/Track | B airbrake | V lift | L launch | "
                    + "F faults | T diagnostics";
    private static final String FAULT_SIM_HINT =
            "Click a fault's INJECT/CLEAR button to toggle it. C = back to Cockpit.";
    private static final String DIAGNOSTICS_HINT =
            "Live safety checks, system status, and recent log history. C = back to Cockpit.";

    @Override
    public void start(Stage stage) {
        logger = new Logger("logs/hyperdrive-session.log");
        logger.open();
        car = new Car(logger);

        cockpitScreen = new CockpitScreen();
        faultScreen = new FaultSimulatorScreen(car);
        diagnosticsScreen = new DiagnosticsScreen(car, logger.getFilePath());
        screens = new Screen[] {cockpitScreen, faultScreen, diagnosticsScreen};

        root = buildShell();
        Scene scene = new Scene(root, 900, 620);
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
                updateHeldInputs(now);   // ramps throttle/brake while W/S are held
                refreshAll();            // read-only: this timer never changes Car, only displays it
            }
        };
        refreshLoop.start();

        setStatus("HYPERDRIVE X-01 ready. Press I to power on, then ENTER to start the engine.");
    }

    // ------------------------------------------------------------------ shared chrome

    private BorderPane buildShell() {
        BorderPane shell = new BorderPane();
        shell.getStyleClass().add("root-pane");

        Label title = new Label("HYPERDRIVE X-01");
        title.getStyleClass().add("app-title");
        powerStateLabel = new Label("SLEEP");
        powerStateLabel.getStyleClass().add("power-badge");
        screenNameLabel = new Label("COCKPIT");
        screenNameLabel.getStyleClass().add("screen-badge");
        HBox top = new HBox(16, title, powerStateLabel, screenNameLabel);
        top.setAlignment(Pos.CENTER_LEFT);
        top.setPadding(new Insets(12, 20, 12, 20));
        top.getStyleClass().add("top-bar");
        shell.setTop(top);

        shell.setCenter(cockpitScreen);

        statusLabel = new Label("");
        statusLabel.getStyleClass().add("status-label");
        hintLabel = new Label(COCKPIT_HINT);
        hintLabel.getStyleClass().add("hint-label");
        VBox bottom = new VBox(4, statusLabel, hintLabel);
        bottom.setPadding(new Insets(10, 20, 14, 20));
        bottom.getStyleClass().add("bottom-bar");
        shell.setBottom(bottom);

        return shell;
    }

    private void showCockpit() {
        root.setCenter(cockpitScreen);
        screenNameLabel.setText("COCKPIT");
        hintLabel.setText(COCKPIT_HINT);
    }

    private void showFaultSimulator() {
        root.setCenter(faultScreen);
        screenNameLabel.setText("FAULT SIMULATOR");
        hintLabel.setText(FAULT_SIM_HINT);
    }

    private void showDiagnostics() {
        root.setCenter(diagnosticsScreen);
        screenNameLabel.setText("DIAGNOSTICS");
        hintLabel.setText(DIAGNOSTICS_HINT);
        diagnosticsScreen.refreshLogHistory();   // pick up anything logged since we last looked
    }

    // ------------------------------------------------------------------ input

    private void handleKeyPressed(KeyEvent event) {
        KeyCode code = event.getCode();
        if (!heldKeys.add(code)) {
            return;   // OS key-repeat while already held - ignore; each physical press reaches here once
        }
        switch (code) {
            case I -> attempt("POWER ON", car::powerOn);
            case ENTER -> attempt("START ENGINE", car::startEngine);
            case O -> attempt("STOP / POWER OFF", this::powerDown);
            case W -> throttleHeld = true;
            case S -> brakeHeld = true;
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
            case F -> showFaultSimulator();
            case T -> showDiagnostics();
            case C -> showCockpit();
            default -> { }
        }
    }

    private void handleKeyReleased(KeyEvent event) {
        KeyCode code = event.getCode();
        heldKeys.remove(code);
        switch (code) {
            case W -> {
                throttleHeld = false;
                throttleValue = 0;
                car.setThrottle(0.0);   // instant release
            }
            case S -> {
                brakeHeld = false;
                brakeValue = 0;
                car.releaseBrake();   // instant release - this is what checks for a ready launch
            }
            default -> { }
        }
    }

    /** Ramps throttle/brake UP smoothly while W/S are held. Release is instant (see handleKeyReleased). */
    private void updateHeldInputs(long nowNanos) {
        if (lastInputTickNanos < 0) {
            lastInputTickNanos = nowNanos;
            return;
        }
        double dt = (nowNanos - lastInputTickNanos) / 1_000_000_000.0;
        lastInputTickNanos = nowNanos;
        dt = Math.min(dt, 0.1);   // clamp a stalled frame, same spirit as SimulationEngine's own clamp

        if (throttleHeld && throttleValue < 1.0) {
            throttleValue = Math.min(1.0, throttleValue + dt / PEDAL_RAMP_SECONDS);
            car.setThrottle(throttleValue);
        }
        if (brakeHeld && brakeValue < 1.0) {
            brakeValue = Math.min(1.0, brakeValue + dt / PEDAL_RAMP_SECONDS);
            car.pressBrake(brakeValue);
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

    // ------------------------------------------------------------------ refresh

    private void refreshAll() {
        TelemetrySnapshot t = car.getTelemetry();   // one atomic read - see hyperdrive.telemetry
        for (Screen screen : screens) {
            screen.refresh(t);   // polymorphism: each screen refreshes itself, we don't ask which kind it is
        }

        powerStateLabel.setText(t.getPowerState().name());
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
