package hyperdrive.modes;

/**
 * Abstract base for the three driving modes.
 * A mode is just a bundle of behaviour - it does not touch the Car directly.
 * The Car asks the current mode "how should you affect things?" and applies the answer.
 */
public abstract class DriveMode {
    private final String name;

    protected DriveMode(String name) {
        this.name = name;
    }

    /** Multiplies how fast RPM chases the throttle target. 1.0 = normal (Comfort). */
    public abstract double getThrottleResponse();

    /** True when ESC intervenes at full strength. Track loosens this for a livelier feel. */
    public abstract boolean isEscFullyActive();

    /** Only Track allows Launch Control to be requested. */
    public abstract boolean allowsLaunchControl();

    public String getName() { return name; }

    @Override
    public String toString() {
        return String.format("%s [throttle x%.1f, ESC %s, launch %s]",
                name, getThrottleResponse(),
                isEscFullyActive() ? "full" : "relaxed",
                allowsLaunchControl() ? "allowed" : "locked");
    }
}
