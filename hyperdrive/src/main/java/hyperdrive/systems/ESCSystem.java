package hyperdrive.systems;

import hyperdrive.enums.FaultType;
import hyperdrive.enums.GearPosition;

/**
 * Simplified Electronic Stability / Traction Control.
 * Compares how fast the engine "thinks" the car should be going (from RPM and gear) against
 * how fast it actually is. A big gap means wheelspin. When ESC is on, it cuts power to correct it.
 * When off (Track mode only), the gap is reported as a simplified "drift level" instead of being corrected.
 */
public class ESCSystem extends VehicleSystem implements Loggable {
    private static final double SLIP_THRESHOLD = 0.4;
    private static final double FULL_INTERVENTION = 0.3;      // cuts acceleration by 30%
    private static final double RELAXED_INTERVENTION = 0.15;  // cuts acceleration by 15%

    private boolean enabled = true;
    private boolean escFullyActiveInMode = true;   // from the current DriveMode
    private double driftLevel = 0;                 // 0-100, telemetry only
    private boolean tractionCutActive = false;

    private double throttle = 0;
    private double speedKmh = 0;
    private double rpm = 0;
    private GearPosition gear = GearPosition.P;

    public ESCSystem() {
        super("ESC");
    }

    public void setInputs(double throttle, double speedKmh, double rpm, GearPosition gear, boolean modeFullyActive) {
        this.throttle = throttle;
        this.speedKmh = speedKmh;
        this.rpm = rpm;
        this.gear = gear;
        this.escFullyActiveInMode = modeFullyActive;
    }

    /** Car has already checked that ESC OFF is only allowed in Track mode before calling this. */
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public boolean isEnabled() { return enabled; }

    @Override
    public void update(double dt) {
        tractionCutActive = false;
        driftLevel = 0;
        if (!gear.isForward() || rpm <= 0) {
            return;   // nothing to correct when not driving forward
        }
        double rpmFraction = rpm / Engine.REDLINE_RPM;
        double topSpeed = Transmission.getTopSpeed(gear);
        double speedFraction = (topSpeed > 0) ? Math.min(1.0, speedKmh / topSpeed) : 0.0;
        double slip = Math.max(0.0, rpmFraction - speedFraction);   // engine "wants" to go faster than it is

        driftLevel = Math.min(100.0, slip * 100.0);
        if (enabled && throttle > 0.5 && slip > SLIP_THRESHOLD) {
            tractionCutActive = true;
        }
    }

    /** How much to reduce the acceleration target by, if intervening right now (0.0 = no cut). */
    public double getInterventionStrength() {
        if (!tractionCutActive) {
            return 0.0;
        }
        return escFullyActiveInMode ? FULL_INTERVENTION : RELAXED_INTERVENTION;
    }

    public boolean isTractionCutActive() { return tractionCutActive; }
    public double getDriftLevel() { return driftLevel; }

    @Override
    public boolean handles(FaultType type) { return false; }

    @Override
    public boolean selfTest() { return true; }

    @Override
    public String getLogSummary() {
        return String.format("ESC enabled=%b intervening=%b drift=%.0f%%", enabled, tractionCutActive, driftLevel);
    }
}
