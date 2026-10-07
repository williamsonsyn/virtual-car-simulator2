package hyperdrive.modes;

/**
 * Abstract base for the three driving modes.
 * A mode is just a bundle of behaviour - it does not touch the Car directly.
 * The Car, Engine, Transmission and tyre/ESC logic ask the current mode "how should you affect things?"
 * and apply the answer, so Comfort/Sport/Track genuinely change how the car behaves (polymorphism).
 */
public abstract class DriveMode {
    private final String name;

    protected DriveMode(String name) {
        this.name = name;
    }

    /** Multiplies how fast RPM chases its target. 1.0 = normal (Comfort). */
    public abstract double getThrottleResponse();

    /** True when ESC intervenes at full strength. Track loosens this for a livelier feel. */
    public abstract boolean isEscFullyActive();

    /** Only Track allows Launch Control to be requested. */
    public abstract boolean allowsLaunchControl();

    /** Pedal shaping: throttle^exponent. >1 = soft at the bottom of the pedal, <1 = sharp. */
    public abstract double getThrottleCurveExponent();

    /** How fast (per second) the real throttle plate follows the pedal. Higher = sharper. */
    public abstract double getThrottleRate();

    /** RPM at which AUTO shifts up, for a given throttle (0-1). Higher in Sport/Track. */
    public abstract double getShiftUpRpm(double throttle);

    /** RPM below which AUTO shifts down. */
    public abstract double getShiftDownRpm();

    /** Seconds a gear change takes (torque is cut while it happens). */
    public abstract double getShiftTime();

    /** Scales how much tyre slip ESC tolerates before intervening (1.0 = normal). */
    public abstract double getSlipPermissiveness();

    /** 0 = Comfort, 1 = Sport, 2 = Track. Used for "at least Sport" style rules. */
    public abstract int getRank();

    /** Track shows shift-light blocks on the tachometer. */
    public boolean hasShiftLights() { return false; }

    public String getName() { return name; }

    /** Upper-case label for the instrument cluster: COMFORT / SPORT / TRACK. */
    public String getLabel() { return name.toUpperCase(); }

    @Override
    public String toString() {
        return String.format("%s [throttle x%.1f, ESC %s, launch %s]",
                name, getThrottleResponse(),
                isEscFullyActive() ? "full" : "relaxed",
                allowsLaunchControl() ? "allowed" : "locked");
    }
}
