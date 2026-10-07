package hyperdrive.model;

import hyperdrive.enums.ActiveState;
import hyperdrive.enums.AirbrakeState;
import hyperdrive.enums.DiagnosticLevel;
import hyperdrive.enums.EscMode;
import hyperdrive.enums.FaultType;
import hyperdrive.enums.GearPosition;
import hyperdrive.enums.LaunchState;
import hyperdrive.enums.LiftState;
import hyperdrive.enums.PowerState;
import hyperdrive.enums.Severity;
import hyperdrive.enums.StartupPhase;
import hyperdrive.enums.TransmissionMode;
import hyperdrive.enums.WarningLight;
import hyperdrive.exceptions.InvalidGearException;
import hyperdrive.exceptions.OperationDeniedException;
import hyperdrive.modes.ComfortMode;
import hyperdrive.modes.DriveMode;
import hyperdrive.modes.SportMode;
import hyperdrive.modes.TrackMode;
import hyperdrive.safety.DiagnosticResult;
import hyperdrive.safety.DiagnosticSystem;
import hyperdrive.systems.Airbrake;
import hyperdrive.systems.BrakeSystem;
import hyperdrive.systems.CoolingSystem;
import hyperdrive.systems.DoorSystem;
import hyperdrive.systems.ESCSystem;
import hyperdrive.systems.ElectricalSystem;
import hyperdrive.systems.ElectronicParkingBrake;
import hyperdrive.systems.Engine;
import hyperdrive.systems.FuelSystem;
import hyperdrive.systems.LaunchControl;
import hyperdrive.systems.OilSystem;
import hyperdrive.systems.RearCameraSystem;
import hyperdrive.systems.SRSSystem;
import hyperdrive.systems.SeatBeltSystem;
import hyperdrive.systems.SteeringSystem;
import hyperdrive.systems.Transmission;
import hyperdrive.systems.Tyre;
import hyperdrive.systems.TyreSystem;
import hyperdrive.systems.VehicleDynamics;
import hyperdrive.systems.VehicleLift;
import hyperdrive.systems.VehicleSystem;
import hyperdrive.telemetry.DriveModeInfo;
import hyperdrive.telemetry.TelemetrySnapshot;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * The Car OWNS all its systems (composition) and is the ONLY place where operations are authorised and where systems
 * are connected: each tick it reads outputs from some systems and feeds them to others, so the systems themselves never
 * depend on each other. The UI never decides anything - it calls these methods and shows the result.
 * Public methods are synchronized because the simulation thread and the UI thread share this object.
 *
 * Dataflow:  USER INPUT -> CAR -> VEHICLE SYSTEMS -> SIMULATION ENGINE -> SENSORS / TELEMETRY -> DIAGNOSTICS / SAFETY
 *            -> NOTIFICATIONS -> JAVAFX UI
 */
public class Car {
    private static final double MODE_CHANGE_MAX_SPEED = 20.0;   // km/h - must slow down to switch modes
    private static final double CRITICAL_FAULT_SHUTDOWN_SECONDS = 10.0;
    private static final double WARNING_SELF_TEST_END = 1.2;
    private static final double GAUGES_SELF_TEST_END = 2.8;
    private static final double SYSTEM_CHECK_END = 4.0;
    private static final double ENGINE_STARTED_DISPLAY = 2.0;
    private static final double DIAGNOSTICS_INTERVAL = 0.2;
    private static final double MIN_LAUNCH_COOLANT = 60.0;
    private static final double MIN_LAUNCH_OIL = 55.0;

    // ---- state owned by the Car itself
    private PowerState powerState = PowerState.SLEEP;
    private StartupPhase startupPhase = StartupPhase.NONE;
    private double phaseTimer = 0;
    private double gaugeSweep = 0;
    private DriveMode handlingMode = new ComfortMode();     // selected modes are remembered across ignition cycles
    private DriveMode powertrainMode = new ComfortMode();
    private ActiveState activeState = ActiveState.INACTIVE; // but Active Dynamics always starts INACTIVE
    private double slopePercent = 0;
    private double criticalFaultCountdown = -1;             // -1 = not counting down; otherwise seconds remaining
    private double diagnosticsTimer = 0;
    private Map<WarningLight, Notification> warnings = new EnumMap<>(WarningLight.class);
    private DiagnosticLevel diagnosticLevel = DiagnosticLevel.PASS;

    // ---- the vehicle systems
    private final Engine engine = new Engine();
    private final OilSystem oil = new OilSystem();
    private final CoolingSystem cooling = new CoolingSystem();
    private final FuelSystem fuel = new FuelSystem();
    private final ElectricalSystem electrical = new ElectricalSystem();
    private final Transmission transmission = new Transmission();
    private final SteeringSystem steering = new SteeringSystem();
    private final ESCSystem esc = new ESCSystem();
    private final BrakeSystem brakes = new BrakeSystem();
    private final Airbrake airbrake = new Airbrake();
    private final VehicleLift lift = new VehicleLift();
    private final LaunchControl launch = new LaunchControl();
    private final ElectronicParkingBrake epb = new ElectronicParkingBrake();
    private final SeatBeltSystem seatBelt = new SeatBeltSystem();
    private final SRSSystem srs = new SRSSystem();
    private final DoorSystem doors = new DoorSystem();
    private final RearCameraSystem rearCamera = new RearCameraSystem();
    private final VehicleDynamics dynamics = new VehicleDynamics();
    private final TyreSystem tyres = new TyreSystem();

    private final VehicleSystem[] systems;
    private final DiagnosticSystem diagnostics = new DiagnosticSystem();
    private final List<Fault> activeFaults = new ArrayList<>();
    private final NotificationManager notifications = new NotificationManager();
    private final EventMonitor eventMonitor = new EventMonitor(notifications);
    private final TripComputer trip = new TripComputer();

    public Car() {
        this(null);
    }

    /** Constructor overloading: pass a Logger to capture the session from the very first event. */
    public Car(hyperdrive.io.Logger logger) {
        // Different classes, one array type: this is what makes update() polymorphic.
        systems = new VehicleSystem[] {
            engine, oil, cooling, fuel, electrical, transmission, steering, esc, brakes, airbrake, lift, launch,
            epb, seatBelt, srs, doors, rearCamera, dynamics, tyres
        };
        if (logger != null) {
            notifications.attachLogger(logger);
        }
        addEvent("Vehicle created (SLEEP)");
    }

    /** Attach (or replace) the Logger after construction, if you didn't pass one to the constructor. */
    public synchronized void attachLogger(hyperdrive.io.Logger logger) { notifications.attachLogger(logger); }

    // ------------------------------------------------------------------ power / ignition / engine

    /** Ignition ON: the vehicle wakes, the dashboard self-test runs, and the per-ignition defaults are restored. */
    public synchronized void powerOn() throws OperationDeniedException {
        if (powerState != PowerState.SLEEP) {
            deny("POWER ON", "Vehicle is already awake");
        }
        if (electrical.getVoltage() < ElectricalSystem.MIN_WAKE_VOLTAGE) {
            deny("POWER ON", "Battery too low to wake the vehicle");
        }
        powerState = PowerState.SELF_TEST;
        startupPhase = StartupPhase.WARNING_SELF_TEST;
        phaseTimer = 0;
        gaugeSweep = 0;
        // Defaults that every ignition cycle restores:
        esc.setMode(EscMode.ON);
        activeState = ActiveState.INACTIVE;
        epb.engage();
        launch.reset();
        eventMonitor.reset();
        warnings = new EnumMap<>(WarningLight.class);
        trip.resetSinceStart();
        addEvent("IGNITION ON - dashboard self-test");
    }

    public synchronized void powerOff() throws OperationDeniedException {
        if (powerState == PowerState.SLEEP) {
            deny("POWER OFF", "Vehicle is already asleep");
        }
        if (powerState == PowerState.ENGINE_RUNNING || powerState == PowerState.CRANKING) {
            deny("POWER OFF", "Stop the engine first");
        }
        powerState = PowerState.SLEEP;
        startupPhase = StartupPhase.NONE;
        launch.reset();
        addEvent("IGNITION OFF - vehicle asleep");
    }

    /** ENTER: run the start checks, then begin cranking. The engine becomes "running" only after RPM and oil pressure build. */
    public synchronized void startEngine() throws OperationDeniedException {
        if (powerState == PowerState.SLEEP) {
            deny("START ENGINE", "Vehicle is asleep - power on first");
        }
        if (powerState == PowerState.SELF_TEST) {
            deny("START ENGINE", "Dashboard self-test still running");
        }
        if (powerState == PowerState.CRANKING) {
            deny("START ENGINE", "Engine is already cranking");
        }
        if (powerState == PowerState.ENGINE_RUNNING) {
            deny("START ENGINE", "Engine is already running");
        }
        List<String> failures = diagnostics.runStartupChecks(this);
        if (!failures.isEmpty()) {
            deny("START ENGINE", failures);
        }
        engine.beginCranking();
        powerState = PowerState.CRANKING;
        startupPhase = StartupPhase.CRANKING;
        phaseTimer = 0;
        addEvent("CRANKING");
    }

    public synchronized void stopEngine() throws OperationDeniedException {
        if (powerState != PowerState.ENGINE_RUNNING) {
            deny("STOP ENGINE", "Engine is not running");
        }
        if (dynamics.getSpeedKmh() > 5.0) {
            deny("STOP ENGINE", "Vehicle is still moving");
        }
        shutEngineDown();
        addEvent("ENGINE STOPPED - gear P, parking brake engaged");
    }

    /** Common shutdown work: engine off, back to POWER_ON, gearbox to PARK and the parking brake applied. */
    private void shutEngineDown() {
        engine.stop();
        powerState = PowerState.POWER_ON;
        startupPhase = StartupPhase.READY;
        launch.reset();
        if (dynamics.getSpeedKmh() <= 5.0) {
            transmission.forcePark();
            epb.engage();
        }
    }

    // ------------------------------------------------------------------ driving inputs

    public synchronized void setThrottle(double amount) { engine.setThrottle(amount); }

    public synchronized void pressBrake(double amount) { brakes.apply(amount); }

    /** Overload: emergency = the pedal was stamped on, so Brake Assist engages immediately. */
    public synchronized void pressBrake(double amount, boolean emergency) { brakes.apply(amount, emergency); }

    public synchronized void releaseBrake() { brakes.release(); }

    /** A/D: -1 = full left, 0 = centre, +1 = full right. */
    public synchronized void setSteering(double input) { steering.setInput(input); }

    public synchronized void shiftGear(GearPosition target) throws OperationDeniedException {
        requireAwake("SHIFT TO " + target.getLabel());
        requireNoLaunchOwnership("SHIFT TO " + target.getLabel());
        try {
            transmission.selectGear(target, dynamics.getSpeedKmh(), brakes.isPedalPressed());
            addEvent("GEAR -> " + transmission.getGear().getLabel());
            releaseParkingBrakeOnDriveSelect(target);
            if (target == GearPosition.R) {
                rearCamera.resetObstacle();
            }
        } catch (InvalidGearException e) {
            deny("SHIFT TO " + target.getLabel(), e.getMessage());
        }
    }

    public synchronized void shiftUp() throws OperationDeniedException {
        requireAwake("UPSHIFT");
        requireNoLaunchOwnership("UPSHIFT");
        try {
            transmission.shiftUp(dynamics.getSpeedKmh(), brakes.isPedalPressed());
            addEvent("GEAR -> " + transmission.getGear().getLabel());
            releaseParkingBrakeOnDriveSelect(transmission.getGear());
        } catch (InvalidGearException e) {
            deny("UPSHIFT", e.getMessage());
        }
    }

    public synchronized void shiftDown() throws OperationDeniedException {
        requireAwake("DOWNSHIFT");
        requireNoLaunchOwnership("DOWNSHIFT");
        try {
            transmission.shiftDown(dynamics.getSpeedKmh(), brakes.isPedalPressed());
            addEvent("GEAR -> " + transmission.getGear().getLabel());
        } catch (InvalidGearException e) {
            deny("DOWNSHIFT", e.getMessage());
        }
    }

    public synchronized void toggleTransmissionMode() throws OperationDeniedException {
        requireAwake("TRANSMISSION MODE");
        requireNoLaunchOwnership("TRANSMISSION MODE");
        TransmissionMode next = transmission.getMode() == TransmissionMode.AUTO
                ? TransmissionMode.MANUAL : TransmissionMode.AUTO;
        transmission.setMode(next);
        addEvent("TRANSMISSION MODE -> " + next);
    }

    /** Selecting a gear with the brake pressed releases the parking brake (as long as it is safe to move). */
    private void releaseParkingBrakeOnDriveSelect(GearPosition target) {
        if ((target.isForward() || target == GearPosition.R) && epb.isEngaged() && brakes.isPedalPressed()
                && !doors.isOpen() && seatBelt.isFastened()) {
            epb.release();
            addEvent("PARKING BRAKE RELEASED (drive selected)");
        }
    }

    // ------------------------------------------------------------------ driving modes (Handling / Powertrain / Active)

    /** Legacy 1/2/3 keys: sets BOTH the Handling and Powertrain selectors and switches Active Dynamics on. */
    public synchronized void selectMode(DriveMode target) throws OperationDeniedException {
        String op = "SELECT " + target.getName().toUpperCase() + " MODE";
        requireAwake(op);
        if (target.getClass() == handlingMode.getClass() && target.getClass() == powertrainMode.getClass()
                && activeState == ActiveState.ACTIVE) {
            deny(op, "Already in " + target.getName() + " mode");
        }
        validateModeChange(op, target);
        handlingMode = copyOf(target);
        powertrainMode = copyOf(target);
        activate();
        addEvent("MODE -> " + target.getName() + " (H " + handlingMode.getLabel() + " / P " + powertrainMode.getLabel() + ")");
    }

    /** H key: Comfort -> Sport -> Track -> Comfort. */
    public synchronized void cycleHandlingMode() throws OperationDeniedException {
        DriveMode next = nextMode(handlingMode);
        String op = "HANDLING " + next.getLabel();
        requireAwake(op);
        validateModeChange(op, next);
        handlingMode = next;
        activate();
        addEvent("HANDLING MODE -> " + next.getLabel());
    }

    /** P key: Comfort -> Sport -> Track -> Comfort. */
    public synchronized void cyclePowertrainMode() throws OperationDeniedException {
        DriveMode next = nextMode(powertrainMode);
        String op = "POWERTRAIN " + next.getLabel();
        requireAwake(op);
        validateModeChange(op, next);
        powertrainMode = next;
        activate();
        addEvent("POWERTRAIN MODE -> " + next.getLabel());
    }

    /** X key: Active Dynamics on/off. Off shows NON-ACTIVE and the car behaves normally; the selections are kept. */
    public synchronized void toggleActive() throws OperationDeniedException {
        requireAwake("ACTIVE DYNAMICS");
        if (activeState == ActiveState.INACTIVE) {
            activate();
            addEvent("ACTIVE DYNAMICS ON (H " + handlingMode.getLabel() + " / P " + powertrainMode.getLabel() + ")");
        } else {
            activeState = ActiveState.INACTIVE;
            addEvent("ACTIVE DYNAMICS OFF - NON-ACTIVE");
        }
    }

    private void activate() { activeState = ActiveState.ACTIVE; }

    private void validateModeChange(String op, DriveMode target) throws OperationDeniedException {
        if (dynamics.getSpeedKmh() > MODE_CHANGE_MAX_SPEED) {
            deny(op, String.format("Slow below %.0f km/h to change driving mode (currently %.0f km/h)",
                    MODE_CHANGE_MAX_SPEED, dynamics.getSpeedKmh()));
        }
        if (target.allowsLaunchControl() && !activeFaults.isEmpty()) {
            // Track mode unlocks launch control, so it demands a clean bill of health.
            deny(op, "Track mode requires no active faults (" + activeFaults.size() + " active)");
        }
    }

    private DriveMode nextMode(DriveMode current) {
        return switch (current.getRank()) {
            case 0 -> new SportMode();
            case 1 -> new TrackMode();
            default -> new ComfortMode();
        };
    }

    private DriveMode copyOf(DriveMode mode) {
        return switch (mode.getRank()) {
            case 0 -> new ComfortMode();
            case 1 -> new SportMode();
            default -> new TrackMode();
        };
    }

    /** The mode that actually shapes behaviour: the selected one when Active is on, otherwise plain Comfort. */
    private DriveMode effective(DriveMode selected) {
        return activeState == ActiveState.ACTIVE ? selected : new ComfortMode();
    }

    /** Powertrain mode that is currently selected (used by older code and the console demo). */
    public synchronized DriveMode getCurrentMode() { return powertrainMode; }
    public synchronized DriveMode getHandlingMode() { return handlingMode; }
    public synchronized DriveMode getPowertrainMode() { return powertrainMode; }
    public synchronized ActiveState getActiveState() { return activeState; }

    // ------------------------------------------------------------------ airbrake

    public synchronized void deployAirbrake() throws OperationDeniedException {
        requireAwake("DEPLOY AIRBRAKE");
        if (airbrake.getState() == AirbrakeState.FAULT) {
            deny("DEPLOY AIRBRAKE", "Airbrake fault");
        }
        if (airbrake.getState() == AirbrakeState.UNAVAILABLE) {
            deny("DEPLOY AIRBRAKE", "Airbrake temporarily unavailable (oil/transmission too cold)");
        }
        if (airbrake.isDeployed() || airbrake.getState() == AirbrakeState.DEPLOYING) {
            deny("DEPLOY AIRBRAKE", "Airbrake is already deployed");
        }
        if (airbrake.getState() == AirbrakeState.RETRACTING || airbrake.isSelfTesting()) {
            deny("DEPLOY AIRBRAKE", "Airbrake is moving");
        }
        if (!engine.isRunning()) {
            deny("DEPLOY AIRBRAKE", "Engine must be running");
        }
        if (dynamics.getSpeedKmh() < Airbrake.MIN_MANUAL_DEPLOY_SPEED) {
            deny("DEPLOY AIRBRAKE", String.format("Speed too low - need at least %.0f km/h",
                    Airbrake.MIN_MANUAL_DEPLOY_SPEED));
        }
        airbrake.deployManual();
        addEvent("AIRBRAKE DEPLOYING (manual)");
    }

    public synchronized void retractAirbrake() throws OperationDeniedException {
        if (!(airbrake.isDeployed() || airbrake.getState() == AirbrakeState.DEPLOYING)) {
            deny("RETRACT AIRBRAKE", "Airbrake is already retracted");
        }
        airbrake.retractManual();
        addEvent("AIRBRAKE RETRACTING (manual)");
    }

    // ------------------------------------------------------------------ vehicle lift

    public synchronized void raiseLift() throws OperationDeniedException {
        requireAwake("RAISE LIFT");
        if (!lift.selfTest()) {
            deny("RAISE LIFT", "Vehicle lift fault");
        }
        if (!lift.isDown()) {
            deny("RAISE LIFT", "Lift is not at normal height");
        }
        if (dynamics.getSpeedKmh() > VehicleLift.MAX_RAISE_SPEED) {
            deny("RAISE LIFT", String.format("Vehicle lift only works below %.0f km/h", VehicleLift.MAX_RAISE_SPEED));
        }
        if (activeState == ActiveState.ACTIVE && handlingMode instanceof TrackMode) {
            deny("RAISE LIFT", "Lift is unavailable in Track mode");
        }
        lift.raise();
        addEvent("VEHICLE LIFT RAISING");
    }

    public synchronized void lowerLift() throws OperationDeniedException {
        if (lift.isDown()) {
            deny("LOWER LIFT", "Lift is already at normal height");
        }
        if (lift.getState() == LiftState.LOWERING) {
            deny("LOWER LIFT", "Lift is already lowering");
        }
        lift.lowerManual();
        addEvent("VEHICLE LIFT LOWERING");
    }

    // ------------------------------------------------------------------ ESC

    /** Y key: next allowed ESC state (ESC ON -> DYNAMIC -> TRACK DYNAMIC -> OFF -> ON), skipping ones not allowed now. */
    public synchronized void cycleEscMode() throws OperationDeniedException {
        requireAwake("ESC MODE");
        EscMode[] all = EscMode.values();
        EscMode current = esc.getMode();
        String firstReason = null;
        for (int i = 1; i < all.length; i++) {
            EscMode candidate = all[(current.ordinal() + i) % all.length];
            String reason = escModeBlocker(candidate);
            if (reason == null) {
                applyEscMode(candidate);
                return;
            }
            if (firstReason == null) {
                firstReason = candidate.getLabel() + " - " + reason;
            }
        }
        deny("ESC MODE", firstReason == null ? "No other ESC mode available" : firstReason);
    }

    public synchronized void setEscMode(EscMode target) throws OperationDeniedException {
        requireAwake(target.getLabel());
        if (target == esc.getMode()) {
            deny(target.getLabel(), "Already " + target.getLabel());
        }
        String reason = escModeBlocker(target);
        if (reason != null) {
            deny(target.getLabel(), reason);
        }
        applyEscMode(target);
    }

    /** Legacy form: true = ESC ON, false = ESC OFF (only with Active Dynamics in Track handling). */
    public synchronized void setEscEnabled(boolean enabled) throws OperationDeniedException {
        if (enabled) {
            if (esc.getMode() == EscMode.ON) {
                return;
            }
            setEscMode(EscMode.ON);
        } else {
            if (esc.getMode() == EscMode.OFF) {
                return;
            }
            setEscMode(EscMode.OFF);
        }
    }

    private String escModeBlocker(EscMode mode) {
        boolean active = activeState == ActiveState.ACTIVE;
        switch (mode) {
            case DYNAMIC:
                if (!active || handlingMode.getRank() < 1) {
                    return "needs Active Dynamics with Sport or Track handling";
                }
                return null;
            case TRACK_DYNAMIC:
            case OFF:
                if (!active || handlingMode.getRank() < 2) {
                    return mode == EscMode.OFF ? "ESC can only be disabled with Active Dynamics in Track handling"
                            : "needs Active Dynamics with Track handling";
                }
                return null;
            default:
                return null;
        }
    }

    private void applyEscMode(EscMode mode) {
        esc.setMode(mode);
        addEvent(mode.getLabel(), mode == EscMode.OFF ? Severity.WARNING : Severity.INFO);
    }

    // ------------------------------------------------------------------ parking brake, body, environment

    /** SPACE: apply/release the electronic parking brake. */
    public synchronized void toggleParkingBrake() throws OperationDeniedException {
        requireAwake("PARKING BRAKE");
        if (epb.isEngaged()) {
            epb.release();
            addEvent("PARKING BRAKE RELEASED");
        } else {
            if (dynamics.getSpeedKmh() > 5.0) {
                deny("PARKING BRAKE", "Can only be applied when the vehicle is stationary");
            }
            epb.engage();
            addEvent("PARKING BRAKE ENGAGED");
        }
    }

    public synchronized void setDoorOpen(boolean open) {
        doors.setOpen(open);
        addEvent(open ? "DOOR OPENED" : "DOOR CLOSED");
    }

    public synchronized void setSeatBeltFastened(boolean fastened) {
        seatBelt.setFastened(fastened);
        addEvent(fastened ? "SEATBELT FASTENED" : "SEATBELT UNFASTENED");
    }

    /** G key: simulated road gradient in percent (for Hill Hold). */
    public synchronized void setRoadSlope(double percent) {
        slopePercent = Math.max(-12.0, Math.min(12.0, percent));
        addEvent(String.format("ROAD SLOPE %+.0f%%", slopePercent));
    }

    public synchronized double getRoadSlope() { return slopePercent; }

    /** Simulator utility: bring coolant, oil and tyres to working temperature (a real warm-up takes minutes). */
    public synchronized void preWarm() {
        cooling.preWarm();
        oil.preWarm();
        tyres.preWarm();
        addEvent("SIMULATOR: coolant, oil and tyres pre-warmed");
    }

    // ------------------------------------------------------------------ launch control

    /** L key: start a launch sequence (all preconditions are checked), or cancel one that is already running. */
    public synchronized void requestLaunch() throws OperationDeniedException {
        requireAwake("REQUEST LAUNCH");
        if (launch.getState().isSequenceActive()) {
            abortLaunchInternal("Cancelled by driver");
            return;
        }
        List<String> failures = launchBlockers();
        if (!failures.isEmpty()) {
            launch.markUnavailable(String.join("; ", failures));
            addEvent("LAUNCH CONTROL UNAVAILABLE: " + String.join("; ", failures), Severity.WARNING);
            throw new OperationDeniedException("REQUEST LAUNCH", failures);
        }
        launch.request();
        addEvent("LAUNCH CONTROL REQUESTED - checking");
    }

    public synchronized void abortLaunch() throws OperationDeniedException {
        if (!launch.getState().isSequenceActive()) {
            deny("ABORT LAUNCH", "No launch sequence is active");
        }
        abortLaunchInternal("Manually aborted");
    }

    private void abortLaunchInternal(String reason) { launch.abort(reason); }

    /** Every reason a launch cannot start right now (empty list = it can). */
    private List<String> launchBlockers() {
        List<String> f = new ArrayList<>();
        if (activeState != ActiveState.ACTIVE) {
            f.add("Active Dynamics must be enabled");
        }
        if (!powertrainMode.allowsLaunchControl()) {
            f.add("Powertrain must be in TRACK mode");
        }
        if (!engine.isRunning()) {
            f.add("Engine must be running");
        }
        if (dynamics.getSpeedKmh() > 2.0) {
            f.add("Vehicle must be stationary");
        }
        if (transmission.getGear() != GearPosition.G1) {
            f.add("Gear must be 1st (currently " + transmission.getGear().getLabel() + ")");
        }
        if (brakes.getPedalPosition() < 0.5) {
            f.add("Brake pedal must be applied");
        }
        if (!steering.isApproximatelyStraight()) {
            f.add("Steering must be straight");
        }
        if (!lift.isDown()) {
            f.add("Vehicle lift must be at normal height");
        }
        if (cooling.getCoolantTemp() < MIN_LAUNCH_COOLANT) {
            f.add(String.format("Engine coolant too cold (%.0f C, need %.0f C)", cooling.getCoolantTemp(), MIN_LAUNCH_COOLANT));
        }
        if (oil.getTemperature() < MIN_LAUNCH_OIL) {
            f.add(String.format("Engine oil too cold (%.0f C, need %.0f C)", oil.getTemperature(), MIN_LAUNCH_OIL));
        }
        if (hasCriticalFault()) {
            f.add("A critical fault is active");
        }
        if (doors.isOpen()) {
            f.add("Door is open");
        }
        if (!seatBelt.isFastened()) {
            f.add("Seatbelt not fastened");
        }
        if (epb.isEngaged()) {
            f.add("Parking brake is engaged");
        }
        return f;
    }

    /** Launch Control aborts itself the moment one of its conditions breaks. */
    private void checkLaunchAbort() {
        LaunchState ls = launch.getState();
        if (!ls.isSequenceActive()) {
            return;
        }
        boolean preLaunch = ls != LaunchState.LAUNCHING;
        if (hasCriticalFault()) {
            abortLaunchInternal("A critical fault occurred");
        } else if (!lift.isDown()) {
            abortLaunchInternal("Vehicle lift activated");
        } else if (activeState != ActiveState.ACTIVE) {
            abortLaunchInternal("Active Dynamics disabled");
        } else if (!engine.isRunning()) {
            abortLaunchInternal("Engine stopped");
        } else if (preLaunch && transmission.getGear() != GearPosition.G1) {
            abortLaunchInternal("Gear changed manually");
        } else if (preLaunch && ls != LaunchState.READY && brakes.getPedalPosition() < 0.5) {
            abortLaunchInternal("Brake released early");
        } else if ((ls == LaunchState.BOOST_BUILDING || ls == LaunchState.READY) && engine.getThrottleCommand() < 0.9) {
            abortLaunchInternal("Throttle released");
        } else if (preLaunch && dynamics.getSpeedKmh() > 5.0) {
            abortLaunchInternal("Vehicle moved");
        } else if (preLaunch && doors.isOpen()) {
            abortLaunchInternal("Door opened");
        } else if (ls == LaunchState.LAUNCHING && brakes.getPedalPosition() > 0.3) {
            abortLaunchInternal("Brake applied during launch");
        } else if (ls == LaunchState.LAUNCHING && engine.getThrottleCommand() < 0.3) {
            abortLaunchInternal("Throttle released");
        }
    }

    // ------------------------------------------------------------------ faults

    public synchronized void injectFault(FaultType type) {
        for (Fault f : activeFaults) {
            if (f.getType() == type) {
                return;   // already active
            }
        }
        Fault fault = new Fault(type);
        activeFaults.add(fault);
        for (VehicleSystem s : systems) {
            if (s.handles(type)) {
                s.injectFault(type);
            }
        }
        addEvent("FAULT INJECTED: " + fault.getType().getDescription(), fault.getSeverity());
    }

    public synchronized void clearFault(FaultType type) {
        Iterator<Fault> it = activeFaults.iterator();
        while (it.hasNext()) {
            if (it.next().getType() == type) {
                it.remove();
            }
        }
        for (VehicleSystem s : systems) {
            s.clearFault(type);
        }
        addEvent("FAULT CLEARED: " + type.getDescription());
    }

    /** Returns COPIES of the faults (copy constructor) - the UI cannot modify the real list. */
    public synchronized List<Fault> getActiveFaults() {
        List<Fault> copy = new ArrayList<>();
        for (Fault f : activeFaults) {
            copy.add(new Fault(f));
        }
        return copy;
    }

    private boolean hasCriticalFault() {
        for (Fault f : activeFaults) {
            if (f.getSeverity() == Severity.CRITICAL) {
                return true;
            }
        }
        return false;
    }

    /** Seconds until auto-shutdown if a critical fault isn't cleared in time; 0 if no countdown is active. */
    public synchronized double getCriticalFaultCountdown() { return Math.max(0.0, criticalFaultCountdown); }

    // ------------------------------------------------------------------ simulation step

    /** Advance the whole car by dt seconds. The SimulationEngine thread calls this repeatedly. */
    public synchronized void update(double dt) {
        advanceIgnitionSequence(dt);
        autoReleaseParkingBrake();
        checkLaunchAbort();
        feedSystems();

        for (VehicleSystem s : systems) {
            s.update(dt);   // polymorphism: each system runs ITS OWN update()
        }

        String shiftNote = transmission.consumeAutoShiftNote();
        if (shiftNote != null) {
            addEvent(shiftNote);
        }
        afterSystemsUpdate(dt);
    }

    /** Self-test timeline after ignition, and the cranking -> running hand-over. */
    private void advanceIgnitionSequence(double dt) {
        if (powerState == PowerState.SELF_TEST) {
            phaseTimer += dt;
            if (phaseTimer < WARNING_SELF_TEST_END) {
                startupPhase = StartupPhase.WARNING_SELF_TEST;
                gaugeSweep = 0;
            } else if (phaseTimer < GAUGES_SELF_TEST_END) {
                startupPhase = StartupPhase.GAUGES_SELF_TEST;
                double t = (phaseTimer - WARNING_SELF_TEST_END) / (GAUGES_SELF_TEST_END - WARNING_SELF_TEST_END);
                gaugeSweep = t < 0.5 ? t * 2.0 : (1.0 - t) * 2.0;
            } else if (phaseTimer < SYSTEM_CHECK_END) {
                startupPhase = StartupPhase.SYSTEM_CHECK;
                gaugeSweep = 0;
            } else {
                gaugeSweep = 0;
                powerState = PowerState.POWER_ON;
                startupPhase = StartupPhase.READY;
                diagnosticLevel = diagnostics.getOverallLevel(this);
                addEvent("SYSTEM CHECK COMPLETE - " + diagnosticLevel,
                        diagnosticLevel.isBlocking() ? Severity.WARNING : Severity.INFO);
            }
        } else if (powerState == PowerState.ENGINE_RUNNING && startupPhase == StartupPhase.ENGINE_STARTED) {
            phaseTimer += dt;
            if (phaseTimer >= ENGINE_STARTED_DISPLAY) {
                startupPhase = StartupPhase.NONE;
            }
        }
    }

    /** The parking brake lets go by itself when the driver pulls away (and it is safe to). */
    private void autoReleaseParkingBrake() {
        GearPosition g = transmission.getGear();
        if (epb.isEngaged() && powerState == PowerState.ENGINE_RUNNING && (g.isForward() || g == GearPosition.R)
                && engine.getThrottleCommand() > 0.1 && !doors.isOpen() && seatBelt.isFastened()) {
            epb.release();
            addEvent("PARKING BRAKE RELEASED (pulling away)");
        }
    }

    /** The Car is the mediator: it reads outputs from some systems and feeds them to others. */
    private void feedSystems() {
        double speed = dynamics.getSpeedKmh();
        GearPosition gear = transmission.getGear();
        boolean running = engine.isRunning();
        boolean coupled = gear.isForward() || gear == GearPosition.R;
        boolean launching = launch.isLaunching();
        DriveMode powertrain = effective(powertrainMode);
        DriveMode handling = effective(handlingMode);

        // Engine
        engine.setResponseFactor(powertrain.getThrottleResponse());
        engine.setThrottleShaping(powertrain.getThrottleCurveExponent(), powertrain.getThrottleRate());
        engine.setDrivetrain(Transmission.wheelRpm(gear, speed), coupled);
        engine.setLaunchHoldRpm(launch.getLaunchHoldRpm());
        engine.setOilCondition(oil.getTemperature(), oil.getPressure(), oil.isPressureLow());
        engine.setCoolantState(cooling.getState());
        double cut = launching ? 0.0 : esc.getTorqueCut();
        if (transmission.isShifting()) {
            cut = Math.max(cut, 0.85);   // torque is cut while the gearbox changes gear
        }
        engine.setTorqueCut(cut);

        // Thermal, fuel, electrical
        oil.setEngineConditions(engine.getState(), engine.getRpm(), engine.getLoad(), cooling.getCoolantTemp());
        cooling.setEngineState(running, engine.getLoad());
        cooling.setConditions(engine.getRpm(), speed);
        fuel.setOperatingPoint(running, engine.getRpm(), engine.getThrottle(), engine.getLoad(), coupled, speed);
        electrical.setAlternatorActive(running);
        electrical.setCranking(powerState == PowerState.CRANKING);
        electrical.setIgnitionOn(powerState != PowerState.SLEEP);

        // Transmission
        transmission.setDriveInputs(speed, engine.getThrottleCommand(), brakes.isPedalPressed(), running);
        transmission.setProfile(powertrain);
        transmission.setRpmLimit(engine.getEffectiveRpmLimit());
        transmission.setLaunchActive(launching);

        // Stability: ESC and brakes
        esc.setInputs(engine.getThrottle(), speed, steering.getValue(), dynamics.getFrontSlip(),
                dynamics.getRearSlip(), handling.getSlipPermissiveness());
        brakes.setConditions(speed, engine.getThrottle(), running, coupled, slopePercent, tyres.getBrakeGrip());
        brakes.setDynamics(steering.getValue(), dynamics.getLateralState(), dynamics.getFrontSlip(),
                0.4 + 0.3 * handling.getRank());
        brakes.setEscRequests(esc.getWheelRequests());

        // Aero, lift, launch, rear camera
        airbrake.setInputs(speed, brakes.getPedalPosition(), running, oil.getTemperature(), brakes.getDecelG(),
                engine.getThrottleCommand(), steering.getValue());
        lift.setInputs(speed);
        launch.setInputs(engine.getThrottleCommand(), brakes.getPedalPosition(), speed);
        rearCamera.setMotion(gear, speed);

        // Vehicle dynamics and tyres
        dynamics.setDrivetrain(gear, running, engine.getTorqueFraction(), engine.getEffectiveRpmLimit(), engine.getThrottle());
        dynamics.setBraking(brakes.getDecelG(), airbrake.getDragDeceleration());
        dynamics.setGrip(tyres.getFrontGrip(), tyres.getRearGrip());
        dynamics.setDriver(steering.getValue(), Math.max(esc.getYawRelief(), brakes.getYawAssist()));
        dynamics.setLaunch(launching, launch.isHoldingCar());
        dynamics.setConstraints(epb.isEngaged(), rearCamera.isObstacleReached(), slopePercent);
        tyres.setDynamics(speed, dynamics.getLongitudinalG(), dynamics.getLateralG(), dynamics.getFrontSlip(),
                dynamics.getRearSlip(), brakes.getWheelPressures(), airbrake.getDownforceFactor());
    }

    private void afterSystemsUpdate(double dt) {
        // Cranking -> running hand-over (RPM has risen and oil pressure has built), or a failed start.
        if (powerState == PowerState.CRANKING) {
            if (engine.isRunning()) {
                powerState = PowerState.ENGINE_RUNNING;
                startupPhase = StartupPhase.ENGINE_STARTED;
                phaseTimer = 0;
                airbrake.requestSelfTest();
                addEvent("ENGINE STARTED");
            } else if (!engine.isCranking()) {
                powerState = PowerState.POWER_ON;
                startupPhase = StartupPhase.READY;
                addEvent("ENGINE FAILED TO START - oil pressure did not build", Severity.CRITICAL);
            }
        }
        if (powerState == PowerState.ENGINE_RUNNING && fuel.getLevelPercent() <= 0.0) {
            shutEngineDown();
            addEvent("ENGINE STALLED - out of fuel", Severity.CRITICAL);
        }

        // Critical-fault shutdown: a critical fault while the engine is running starts a countdown. Clearing every
        // critical fault before it reaches zero cancels it. At zero the engine shuts itself down - and startEngine()'s
        // diagnostics already refuse to restart while a critical fault is active.
        if (engine.isRunning() && hasCriticalFault()) {
            if (criticalFaultCountdown < 0) {
                criticalFaultCountdown = CRITICAL_FAULT_SHUTDOWN_SECONDS;
                addEvent(String.format("CRITICAL FAULT ACTIVE - engine will shut down in %.0fs if not cleared",
                        CRITICAL_FAULT_SHUTDOWN_SECONDS), Severity.CRITICAL);
            } else {
                criticalFaultCountdown -= dt;
                if (criticalFaultCountdown <= 0) {
                    shutEngineDown();
                    criticalFaultCountdown = -1;
                    addEvent("ENGINE SHUTDOWN - critical fault was not cleared in time", Severity.CRITICAL);
                }
            }
        } else if (criticalFaultCountdown >= 0) {
            criticalFaultCountdown = -1;
            addEvent("Critical fault cleared - shutdown countdown cancelled", Severity.INFO);
        }

        trip.update(dt, dynamics.getSpeedKmh(), fuel.getFlowLitresPerHour(), engine.isRunning());

        // Live diagnostics -> warnings -> notifications
        diagnosticsTimer += dt;
        if (diagnosticsTimer >= DIAGNOSTICS_INTERVAL) {
            diagnosticsTimer = 0;
            warnings = new EnumMap<>(diagnostics.evaluateWarnings(this));
            diagnosticLevel = diagnostics.getOverallLevel(this);
            eventMonitor.trackWarnings(warnings);
        }
        eventMonitor.trackInterventions(dt, brakes.isAbsActive(), esc.isIntervening(),
                brakes.isBrakeAssistActive(), brakes.isHillHoldActive());
        eventMonitor.trackLaunch(launch.getState(), launch.getLastReason());
    }

    // ------------------------------------------------------------------ helpers

    private void requireAwake(String operation) throws OperationDeniedException {
        if (powerState == PowerState.SLEEP) {
            deny(operation, "Vehicle is asleep - power on first");
        }
    }

    private void requireNoLaunchOwnership(String operation) throws OperationDeniedException {
        if (launch.isLaunching()) {
            deny(operation, "Launch Control is controlling the gearbox");
        }
    }

    // Overloaded: one reason ...
    private void deny(String operation, String reason) throws OperationDeniedException {
        deny(operation, List.of(reason));
    }

    // ... or several. Always logs the denial and throws.
    private void deny(String operation, List<String> reasons) throws OperationDeniedException {
        addEvent(operation + " DENIED: " + String.join("; ", reasons), Severity.WARNING);
        throw new OperationDeniedException(operation, reasons);
    }

    private void addEvent(String message) { notifications.add(message); }

    // Overload: explicit severity for anything more than routine information.
    private void addEvent(String message, Severity severity) { notifications.add(message, severity); }

    // ------------------------------------------------------------------ read access

    public synchronized PowerState getPowerState() { return powerState; }
    public synchronized double getSpeedKmh() { return dynamics.getSpeedKmh(); }
    public synchronized List<String> getEvents() {
        List<String> lines = new ArrayList<>();
        for (Notification n : notifications.getAll()) {
            lines.add(n.toString());
        }
        return lines;
    }

    public synchronized List<Notification> getNotifications() { return notifications.getAll(); }

    /** Active-issue view: warnings and critical notifications only. */
    public synchronized List<Notification> getWarnings() { return notifications.getBySeverity(Severity.WARNING); }
    public synchronized List<String> getDiagnosticReport() { return diagnostics.getReport(this); }
    public synchronized List<DiagnosticResult> getDiagnosticResults() { return diagnostics.getResults(this); }
    public synchronized DiagnosticLevel getDiagnosticLevel() { return diagnostics.getOverallLevel(this); }
    public synchronized Tyre[] getTyreSnapshot() { return tyres.getSnapshot(); }
    public synchronized AirbrakeState getAirbrakeState() { return airbrake.getState(); }
    public synchronized LiftState getLiftState() { return lift.getState(); }
    public synchronized boolean isEscEnabled() { return esc.isEnabled(); }
    public synchronized EscMode getEscMode() { return esc.getMode(); }
    public synchronized double getDriftLevel() { return esc.getDriftLevel(); }
    public synchronized LaunchState getLaunchState() { return launch.getState(); }
    public synchronized double getLaunchBoostPercent() { return launch.getBoostPercent(); }
    public synchronized String getLaunchReason() { return launch.getLastReason(); }

    public synchronized List<String> getSystemStatusLines() {
        List<String> lines = new ArrayList<>();
        for (VehicleSystem s : systems) {
            lines.add(s.toString() + (s.selfTest() ? "  self-test OK" : "  self-test FAILED"));
        }
        return lines;
    }

    public synchronized double getRpm() { return engine.getRpm(); }
    public synchronized double getFuelLevelPercent() { return fuel.getLevelPercent(); }

    // Long-term trip persistence (plain Java I/O, called by the app on start-up / shutdown)
    public synchronized void loadLongTermTrip(String path) { trip.loadLongTerm(path); }
    public synchronized void saveLongTermTrip(String path) { trip.saveLongTerm(path); }
    public synchronized TripComputer.TripData getTripSinceStart() { return trip.getSinceStart(); }
    public synchronized TripComputer.TripData getTripLongTerm() { return trip.getLongTerm(); }

    /**
     * One atomic read of everything a dashboard needs, captured under a single synchronized call.
     * Nothing can change partway through building this snapshot, even with a SimulationEngine ticking concurrently.
     */
    public synchronized TelemetrySnapshot getTelemetry() {
        Map<WarningLight, Severity> lights = new EnumMap<>(WarningLight.class);
        List<Notification> messages = new ArrayList<>();
        int warningCount = 0;
        for (Map.Entry<WarningLight, Notification> e : warnings.entrySet()) {
            lights.put(e.getKey(), e.getValue().getSeverity());
            if (e.getValue().getSeverity() != Severity.INFO) {
                warningCount++;
            }
            if (e.getKey() != WarningLight.EPB) {
                messages.add(e.getValue());
            }
        }
        messages.sort((a, b) -> b.getSeverity().ordinal() - a.getSeverity().ordinal());

        return TelemetrySnapshot.builder()
                .powerState(powerState)
                .startupPhase(startupPhase)
                .startupProgress(powerState == PowerState.SELF_TEST ? Math.min(1.0, phaseTimer / SYSTEM_CHECK_END) : 1.0)
                .gaugeSweep(gaugeSweep)
                .mode(info(powertrainMode))
                .handlingMode(info(handlingMode))
                .powertrainMode(info(powertrainMode))
                .activeState(activeState)
                .speedKmh(dynamics.getSpeedKmh())
                .rpm(engine.getRpm())
                .throttle(engine.getThrottle())
                .brakePedal(brakes.getPedalPosition())
                .brakePressureBar(brakes.getLinePressureBar())
                .brakeForce(brakes.getBrakingForce())
                .brakeDiscTemp(brakes.getDiscTemperature())
                .brakeState(brakes.getState())
                .absActive(brakes.isAbsActive())
                .brakeAssistActive(brakes.isBrakeAssistActive())
                .prefillActive(brakes.isPrefillActive())
                .hillHoldActive(brakes.isHillHoldActive())
                .brakeSteerActive(brakes.isBrakeSteerActive())
                .discWipingActive(brakes.isDiscWipingActive())
                .wheelBrake(brakes.getWheelPressures())
                .steeringValue(steering.getValue())
                .steeringAngleDeg(steering.getAngleDegrees())
                .steeringDirection(steering.getDirection().name())
                .lateralState(dynamics.getLateralState())
                .lateralG(dynamics.getLateralG())
                .longitudinalG(dynamics.getLongitudinalG())
                .gear(transmission.getGear())
                .transmissionMode(transmission.getMode())
                .shifting(transmission.isShifting())
                .fuelLevelPercent(fuel.getLevelPercent())
                .fuelLitres(fuel.getLitres())
                .fuelRangeKm(fuel.getEstimatedRangeKm())
                .fuelFlowLph(fuel.getFlowLitresPerHour())
                .fuelInstantL100(fuel.getInstantConsumptionL100())
                .fuelAvgL100(fuel.getAverageConsumptionL100())
                .lowFuel(fuel.isLowFuel())
                .fuelPressure(fuel.getPressure())
                .coolantTemp(cooling.getCoolantTemp())
                .coolingState(cooling.getState())
                .oilTemp(oil.getTemperature())
                .oilPressure(oil.getPressure())
                .oilState(oil.getState())
                .engineState(engine.getState())
                .engineLoad(engine.getLoad())
                .engineTorqueNm(engine.getTorqueNm())
                .rpmLimit(engine.getEffectiveRpmLimit())
                .limiterActive(engine.isLimiterActive())
                .batteryVoltage(electrical.getVoltage())
                .batteryState(electrical.getBatteryState())
                .batteryCharge(electrical.getChargePercent())
                .escMode(esc.getMode())
                .escIntervening(esc.isIntervening())
                .driftLevel(esc.getDriftLevel())
                .torqueCut(esc.getTorqueCut())
                .airbrakeState(airbrake.getState())
                .airbrakeProgress(airbrake.getProgress())
                .liftState(lift.getState())
                .liftProgress(lift.getProgress())
                .launchState(launch.getState())
                .launchBoostPercent(launch.getBoostPercent())
                .launchReason(launch.getLastReason())
                .epbState(epb.getState())
                .seatBeltFastened(seatBelt.isFastened())
                .doorOpen(doors.isOpen())
                .srsOk(srs.selfTest())
                .tyres(tyres.getSnapshot())
                .rearCameraActive(rearCamera.isActive())
                .rearDistanceM(rearCamera.getDistanceM())
                .rearWarningLevel(rearCamera.getWarningLevel())
                .roadSlopePercent(slopePercent)
                .tripSinceStart(trip.getSinceStart())
                .tripLongTerm(trip.getLongTerm())
                .sessionDistanceKm(trip.getSessionDistanceKm())
                .warningLights(lights)
                .activeMessages(messages)
                .recentNotifications(notifications.getRecent(8))
                .warningCount(warningCount)
                .activeFaultCount(activeFaults.size())
                .criticalFaultCountdown(getCriticalFaultCountdown())
                .diagnosticLevel(diagnosticLevel)
                .warningSelfTest(startupPhase == StartupPhase.WARNING_SELF_TEST)
                .build();
    }

    private DriveModeInfo info(DriveMode m) {
        return new DriveModeInfo(m.getName(), m.getThrottleResponse(), m.isEscFullyActive(),
                m.allowsLaunchControl(), m.getRank(), m.hasShiftLights());
    }

    // Live system access, used by the SafetyChecks and DiagnosticSystem - those run from inside Car's own
    // synchronized methods, so they're safe. These are also `synchronized`, but that only protects the moment of
    // handing the reference out: whatever a caller does with the object AFTER that is NOT synchronized against the
    // simulation thread. Fine for the sequential console demo and tests; a second thread should use getTelemetry().
    public synchronized Engine getEngine() { return engine; }
    public synchronized OilSystem getOil() { return oil; }
    public synchronized FuelSystem getFuel() { return fuel; }
    public synchronized BrakeSystem getBrakes() { return brakes; }
    public synchronized ElectricalSystem getElectrical() { return electrical; }
    public synchronized CoolingSystem getCooling() { return cooling; }
    public synchronized Transmission getTransmission() { return transmission; }
    public synchronized TyreSystem getTyres() { return tyres; }
    public synchronized ESCSystem getEsc() { return esc; }
    public synchronized Airbrake getAirbrake() { return airbrake; }
    public synchronized VehicleLift getLift() { return lift; }
    public synchronized LaunchControl getLaunch() { return launch; }
    public synchronized ElectronicParkingBrake getEpb() { return epb; }
    public synchronized SeatBeltSystem getSeatBelt() { return seatBelt; }
    public synchronized SRSSystem getSrs() { return srs; }
    public synchronized DoorSystem getDoors() { return doors; }
    public synchronized SteeringSystem getSteering() { return steering; }
    public synchronized VehicleDynamics getDynamics() { return dynamics; }
}
