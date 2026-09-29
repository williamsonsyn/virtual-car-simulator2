package hyperdrive.systems;

import hyperdrive.enums.FaultType;
import hyperdrive.enums.GearPosition;
import hyperdrive.exceptions.InvalidGearException;

/**
 * Gearbox. It knows the gear rules and throws InvalidGearException when a change is not allowed.
 * The Car catches that and turns it into an OperationDeniedException.
 */
public class Transmission extends VehicleSystem implements Loggable {
    private static final double MOVING_THRESHOLD_KMH = 5.0;
    private static final double SHIFT_TIME_SECONDS = 0.25;
    private static final double REVERSE_TOP_SPEED = 15.0;

    // Top speed (km/h) usable in gears 1-7. Simplified numbers - our own, not real ECU data.
    private static final double[] GEAR_TOP_SPEED = {60, 100, 140, 190, 240, 290, 340};

    private GearPosition gear = GearPosition.P;
    private double shiftTimer = 0;

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
        gear = target;
        shiftTimer = SHIFT_TIME_SECONDS;
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
    }

    public void shiftDown(double speedKmh, boolean brakePressed) throws InvalidGearException {
        if (!gear.isForward()) {
            throw new InvalidGearException("Not in a forward gear");
        }
        if (gear == GearPosition.G1) {
            throw new InvalidGearException("Already in first gear");
        }
        selectGear(GearPosition.values()[gear.ordinal() - 1], speedKmh, brakePressed);
    }

    @Override
    public boolean handles(FaultType type) { return type == FaultType.TRANSMISSION_FAULT; }

    @Override
    public void update(double dt) {
        if (shiftTimer > 0) {
            shiftTimer = Math.max(0.0, shiftTimer - dt);   // a shift takes a moment
        }
    }

    @Override
    public boolean selfTest() { return !hasFault(FaultType.TRANSMISSION_FAULT); }

    @Override
    public String getLogSummary() { return "Transmission gear=" + gear.getLabel(); }

    public GearPosition getGear() { return gear; }
    public boolean isInSafeStartState() { return gear == GearPosition.P || gear == GearPosition.N; }
    public boolean hasTransmissionFault() { return hasFault(FaultType.TRANSMISSION_FAULT); }
}
