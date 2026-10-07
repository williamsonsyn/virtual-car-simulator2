package hyperdrive.ui;

import hyperdrive.enums.AirbrakeState;
import hyperdrive.enums.GearPosition;
import hyperdrive.enums.LiftState;
import hyperdrive.enums.PowerState;
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
 * Keys: I ignition, ENTER start engine, O stop/power off, W throttle, S brake (SHIFT+S = emergency stamp),
 * A/D steer left/right, Q/E downshift/upshift, M auto/manual, N neutral, R reverse, Z park,
 * H handling mode, P powertrain mode, 1/2/3 Comfort/Sport/Track (both), X Active Dynamics on/off,
 * Y ESC mode, SPACE parking brake, B airbrake, V vehicle lift, L launch control, G road slope,
 * K door, U seatbelt, J next information page, F fault simulator, T diagnostics, C cockpit.
 * (P used to be Park: it is now the Powertrain mode selector, so Park moved to Z.)
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
    private static final double THROTTLE_RAMP_SECONDS = 0.25;
    private static final double BRAKE_RAMP_SECONDS = 0.60;   // a normal brake press is progressive; SHIFT+S slams it
    private static final String TRIP_FILE = "logs/trip-long-term.properties";
    private boolean throttleHeld = false;
    private boolean brakeHeld = false;
    private boolean leftHeld = false;
    private boolean rightHeld = false;
    private double throttleValue = 0;
    private double brakeValue = 0;
    private long lastInputTickNanos = -1;

    private static final String COCKPIT_HINT =
            "I ignition | ENTER start | O stop | W/S throttle/brake (SHIFT+S = slam) | A/D steer | Q/E shift | M auto/manual | "
                    + "N/R/Z neutral/reverse/park | H/P handling/powertrain | 1/2/3 mode | X active | Y ESC | SPACE EPB | "
                    + "B airbrake | V lift | L launch | G slope | K door | U belt | J page | F faults | T diagnostics";
    private static final String FAULT_SIM_HINT =
            "Click a fault's INJECT/CLEAR button to toggle it. C = back to Cockpit.";
    private static final String DIAGNOSTICS_HINT =
            "Live safety checks, system status, and recent log history. C = back to Cockpit.";

    @Override
    public void start(Stage stage) {
        logger = new Logger("logs/hyperdrive-session.log");
        logger.open();
        car = new Car(logger);
        car.loadLongTermTrip(TRIP_FILE);

        cockpitScreen = new CockpitScreen();
        faultScreen = new FaultSimulatorScreen(car);
        diagnosticsScreen = new DiagnosticsScreen(car, logger.getFilePath());
        screens = new Screen[] {cockpitScreen, faultScreen, diagnosticsScreen};

        root = buildShell();
        Scene scene = new Scene(root, 1180, 720);
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
        hintLabel.setWrapText(true);   // the key list is long: wrap instead of cutting it off
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
            case I -> {
                attempt("POWER ON", car::powerOn);
                if (car.getPowerState() == PowerState.SELF_TEST) {
                    new hyperdrive.sim.StartupSequenceThread(car).start();   // console narration of the sequence
                }
            }
            case ENTER -> attempt("START ENGINE", car::startEngine);
            case O -> attempt("STOP / POWER OFF", this::powerDown);
            case W -> throttleHeld = true;
            case S -> {
                if (event.isShiftDown()) {
                    brakeValue = 1.0;   // emergency stamp: full pedal at once, Brake Assist engages
                    car.pressBrake(1.0, true);
                    setStatus("EMERGENCY BRAKE");
                }
                brakeHeld = true;
            }
            case A -> {
                leftHeld = true;
                updateSteering();
            }
            case D -> {
                rightHeld = true;
                updateSteering();
            }
            case Q -> attempt("DOWNSHIFT", car::shiftDown);
            case E -> attempt("UPSHIFT", car::shiftUp);
            case M -> attempt("AUTO / MANUAL", car::toggleTransmissionMode);
            case Z -> attempt("PARK", () -> car.shiftGear(GearPosition.P));
            case N -> attempt("NEUTRAL", () -> car.shiftGear(GearPosition.N));
            case R -> attempt("REVERSE", () -> car.shiftGear(GearPosition.R));
            case H -> attempt("HANDLING MODE", car::cycleHandlingMode);
            case P -> attempt("POWERTRAIN MODE", car::cyclePowertrainMode);
            case X -> attempt("ACTIVE DYNAMICS", car::toggleActive);
            case Y -> attempt("ESC MODE", car::cycleEscMode);
            case SPACE -> attempt("PARKING BRAKE", car::toggleParkingBrake);
            case DIGIT1 -> attempt("COMFORT MODE", () -> car.selectMode(new ComfortMode()));
            case DIGIT2 -> attempt("SPORT MODE", () -> car.selectMode(new SportMode()));
            case DIGIT3 -> attempt("TRACK MODE", () -> car.selectMode(new TrackMode()));
            case B -> attempt(car.getAirbrakeState() == AirbrakeState.DEPLOYED ? "RETRACT AIRBRAKE" : "DEPLOY AIRBRAKE",
                    this::toggleAirbrake);
            case V -> attempt(car.getLiftState() == LiftState.NORMAL ? "RAISE LIFT" : "LOWER LIFT",
                    this::toggleLift);
            case L -> attempt("LAUNCH CONTROL", car::requestLaunch);
            case G -> {
                car.setRoadSlope(car.getRoadSlope() == 0 ? 8.0 : 0.0);
                setStatus(String.format("ROAD SLOPE %+.0f%%", car.getRoadSlope()));
            }
            case K -> car.setDoorOpen(!car.getTelemetry().isDoorOpen());
            case U -> car.setSeatBeltFastened(!car.getTelemetry().isSeatBeltFastened());
            case J -> cockpitScreen.nextPage();
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
                car.releaseBrake();   // instant release - Launch Control triggers when the brake is let go
            }
            case A -> {
                leftHeld = false;
                updateSteering();
            }
            case D -> {
                rightHeld = false;
                updateSteering();
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
            throttleValue = Math.min(1.0, throttleValue + dt / THROTTLE_RAMP_SECONDS);
            car.setThrottle(throttleValue);
        }
        if (brakeHeld && brakeValue < 1.0) {
            brakeValue = Math.min(1.0, brakeValue + dt / BRAKE_RAMP_SECONDS);
            car.pressBrake(brakeValue, false);
        }
    }

    /** A/D set the steering INPUT (the Car's SteeringSystem smooths it and self-centres when both are released). */
    private void updateSteering() {
        double input = (rightHeld ? 1.0 : 0.0) - (leftHeld ? 1.0 : 0.0);
        car.setSteering(input);
    }

    private void toggleAirbrake() throws OperationDeniedException {
        if (car.getAirbrakeState() == AirbrakeState.DEPLOYED) {
            car.retractAirbrake();
        } else {
            car.deployAirbrake();
        }
    }

    private void toggleLift() throws OperationDeniedException {
        if (car.getLiftState() == LiftState.NORMAL) {
            car.raiseLift();
        } else {
            car.lowerLift();
        }
    }

    private void powerDown() throws OperationDeniedException {
        if (car.getPowerState() == PowerState.ENGINE_RUNNING) {
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
        if (car != null) {
            car.saveLongTermTrip(TRIP_FILE);
        }
        if (logger != null) {
            logger.close();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
