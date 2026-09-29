package hyperdrive.modes;

/**
 * Multilevel inheritance: TrackMode extends SportMode extends DriveMode.
 * Track IS a sharper Sport - it overrides every behaviour Sport defined.
 */
public class TrackMode extends SportMode {
    public TrackMode() { super("Track"); }

    @Override
    public double getThrottleResponse() { return 2.0; }

    @Override
    public boolean isEscFullyActive() { return false; }   // loosened for a livelier feel

    @Override
    public boolean allowsLaunchControl() { return true; }
}
