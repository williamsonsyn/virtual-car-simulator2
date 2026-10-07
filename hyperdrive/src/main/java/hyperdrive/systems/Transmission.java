package hyperdrive.systems;

import hyperdrive.enums.FaultType;
import hyperdrive.enums.GearPosition;
import hyperdrive.enums.TransmissionMode;
import hyperdrive.exceptions.InvalidGearException;
import hyperdrive.modes.ComfortMode;
import hyperdrive.modes.DriveMode;

/**
 * Seven-speed gearbox (P R N 1-7) with AUTO and MANUAL modes.
 * It knows the gear rules and throws InvalidGearException when a driver's change is not allowed; the Car catches that and
 * turns it into an OperationDeniedException. In AUTO it also shifts by itself using the current DriveMode's shift
 * points and shift time (Comfort: early + slow, Sport: later + quick, Track: near redline + fastest).
 * Launch Control forces automatic shifting regardless of the AUTO/MANUAL setting.
 */
public class Transmission extends VehicleSystem implements Loggable {
    private static final double MOVING_THRESHOLD_KMH = 5.0;
    private static final double REVERSE_TOP_SPEED = 15.0;
    private static final double MANUAL_OVERRIDE_SECONDS = 4.0;   // a paddle pull in AUTO pauses auto-shifting for a while
    private static final double LUG_RPM = 700;                   // below this in gear 2+ the box drops a gear by itself

    // Top speed (km/h) usable in gears 1-7. Simplified numbers - our own, not real ECU data.
    private static final double[] GEAR_TOP_SPEED = {60, 100, 140, 190, 240, 290, 340};

    private GearPosition gear = GearPosition.P;
    private TransmissionMode mode = TransmissionMode.AUTO;
    private double shiftTimer = 0;
    private double shiftDuration = 0.3;
    private double manualOverrideTimer = 0;
    private String pendingAutoShiftNote = null;

    // Inputs
    private double speedKmh = 0;
    private double throttle = 0;
    private boolean brakePressed = false;
    private boolean engineRunning = false;
    private boolean launchActive = false;
    private DriveMode profile = new ComfortMode();
    private double rpmLimit = Engine.MAX_RPM;

    public Transmission() {
        super("Transmission");
    }

    /** Top speed allowed in a gear. P and N give 0 (no drive). */
    public static double getTopSpeed(GearPosition g) {
        if (g.isForward()) {
            return GEAR_TOP_SPEED[g.getGearNumber() - 1];
        }
        return (g == GearPosition.R) ? REVERSE_TOP_SPEED : 0.0;
    }

    /** RPM the wheels would force on the engine in a gear at a given speed (0 in P/N). */
    public static double wheelRpm(GearPosition g, double speedKmh) {
        double top = getTopSpeed(g);
        return top > 0 ? speedKmh / top * Engine.REDLINE_RPM : 0.0;
    }

    // ------------------------------------------------------------------ inputs from the Car

    public void setDriveInputs(double speedKmh, double throttleCommand, boolean brakePressed, boolean engineRunning) {
        this.speedKmh = speedKmh;
        this.throttle = throttleCommand;
        this.brakePressed = brakePressed;
        this.engineRunning = engineRunning;
    }

    /** The engine's current RPM ceiling (lower while the oil is cold): AUTO never plans to shift beyond it. */
    public void setRpmLimit(double rpmLimit) { this.rpmLimit = rpmLimit; }

    /** The drive mode whose shift points and shift time apply. */
    public void setProfile(DriveMode profile) { this.profile = profile; }

    public void setLaunchActive(boolean launchActive) { this.launchActive = launchActive; }

    public void setMode(TransmissionMode mode) { this.mode = mode; }

    public TransmissionMode getMode() { return mode; }

    // ------------------------------------------------------------------ driver gear changes

    public void selectGear(GearPosition target, double speedKmh, boolean brakePressed)
            throws InvalidGearException {
        if (hasFault(FaultType.TRANSMISSION_FAULT)) {
            throw new InvalidGearException("Transmission fault - gear changes disabled");
        }
        if (target == gear) {
            throw new InvalidGearException("Already in " + gear.getLabel());
        }
        if (shiftTimer > 0) {
            throw new InvalidGearException("Previous shift still in progress");
        }
        boolean moving = speedKmh > MOVING_THRESHOLD_KMH;
        if (gear == GearPosition.P && !brakePressed) {
            throw new InvalidGearException("Press the brake pedal to leave PARK");
        }
        if ((target == GearPosition.P || target == GearPosition.R) && moving) {
            throw new InvalidGearException(String.format(
                    "Cannot select %s while moving (%.0f km/h)", target.getLabel(), speedKmh));
        }
        if (gear == GearPosition.R && target.isForward() && moving) {
            throw new InvalidGearException("Stop before changing from REVERSE to a forward gear");
        }
        if (target.isForward() && speedKmh > getTopSpeed(target)) {
            throw new InvalidGearException(String.format(
                    "Speed %.0f km/h is too high for gear %s (max %.0f km/h)",
                    speedKmh, target.getLabel(), getTopSpeed(target)));
        }
        beginShift(target);
    }

    public void shiftUp(double speedKmh, boolean brakePressed) throws InvalidGearException {
        if (gear == GearPosition.G7) {
            throw new InvalidGearException("Already in top gear");
        }
        if (gear == GearPosition.P || gear == GearPosition.R) {
            throw new InvalidGearException("Select N first, then shift up");
        }
        GearPosition next = (gear == GearPosition.N) ? GearPosition.G1
                : GearPosition.values()[gear.ordinal() + 1];
        selectGear(next, speedKmh, brakePressed);
        markManualOverride();
    }

    public void shiftDown(double speedKmh, boolean brakePressed) throws InvalidGearException {
        if (!gear.isForward()) {
            throw new InvalidGearException("Not in a forward gear");
        }
        if (gear == GearPosition.G1) {
            throw new InvalidGearException("Already in first gear");
        }
        selectGear(GearPosition.values()[gear.ordinal() - 1], speedKmh, brakePressed);
        markManualOverride();
    }

    private void markManualOverride() {
        if (mode == TransmissionMode.AUTO) {
            manualOverrideTimer = MANUAL_OVERRIDE_SECONDS;   // paddle pull in AUTO = temporary manual control
        }
    }

    private void beginShift(GearPosition target) {
        gear = target;
        shiftDuration = Math.max(0.1, profile.getShiftTime());
        shiftTimer = shiftDuration;
    }

    /** Engine shut down: the box goes to PARK by itself (no restrictions apply - the car is stationary). */
    public void forcePark() {
        gear = GearPosition.P;
        shiftTimer = 0;
    }

    // ------------------------------------------------------------------ simulation

    @Override
    public void update(double dt) {
        if (shiftTimer > 0) {
            shiftTimer = Math.max(0.0, shiftTimer - dt);   // a shift takes a moment
            return;
        }
        manualOverrideTimer = Math.max(0.0, manualOverrideTimer - dt);
        if (hasFault(FaultType.TRANSMISSION_FAULT) || !engineRunning || !gear.isForward()) {
            return;
        }
        double rpmNow = wheelRpm(gear, speedKmh);

        // Protection that applies in every mode: never let the engine lug in a tall gear (e.g. braking to a stop).
        if (gear != GearPosition.G1 && rpmNow < LUG_RPM) {
            autoShift(GearPosition.values()[gear.ordinal() - 1], "protection downshift");
            return;
        }
        boolean automatic = launchActive || (mode == TransmissionMode.AUTO && manualOverrideTimer <= 0);
        if (!automatic) {
            return;
        }

        double upRpm = launchActive ? 7800 : profile.getShiftUpRpm(throttle);
        upRpm = Math.min(upRpm, rpmLimit - 350);   // shift before the limiter, even with cold oil
        if (rpmNow >= upRpm && gear != GearPosition.G7 && (throttle > 0.25 || launchActive || !brakePressed)) {
            GearPosition next = GearPosition.values()[gear.ordinal() + 1];
            autoShift(next, "upshift");
        } else if (!launchActive && gear != GearPosition.G1) {
            GearPosition lower = GearPosition.values()[gear.ordinal() - 1];
            double rpmInLower = wheelRpm(lower, speedKmh);
            boolean slow = rpmNow < profile.getShiftDownRpm();
            boolean kickdown = throttle > 0.92 && rpmInLower < Engine.REDLINE_RPM * 0.85
                    && rpmNow < upRpm * 0.55;
            if ((slow || kickdown) && speedKmh <= getTopSpeed(lower)) {
                autoShift(lower, slow ? "downshift" : "kickdown");
            }
        }
    }

    private void autoShift(GearPosition target, String why) {
        beginShift(target);
        pendingAutoShiftNote = "GEAR -> " + target.getLabel() + " (auto " + why + ")";
    }

    /** The Car calls this once per tick; returns a log message if the box shifted by itself, else null. */
    public String consumeAutoShiftNote() {
        String note = pendingAutoShiftNote;
        pendingAutoShiftNote = null;
        return note;
    }

    @Override
    public boolean handles(FaultType type) { return type == FaultType.TRANSMISSION_FAULT; }

    @Override
    public boolean selfTest() { return !hasFault(FaultType.TRANSMISSION_FAULT); }

    @Override
    public String getLogSummary() { return "Transmission gear=" + gear.getLabel() + " mode=" + mode; }

    public GearPosition getGear() { return gear; }
    public boolean isShifting() { return shiftTimer > 0; }
    public double getShiftProgress() { return shiftTimer > 0 ? 1.0 - shiftTimer / shiftDuration : 1.0; }
    public boolean isInSafeStartState() { return gear == GearPosition.P || gear == GearPosition.N; }
    public boolean hasTransmissionFault() { return hasFault(FaultType.TRANSMISSION_FAULT); }
}
