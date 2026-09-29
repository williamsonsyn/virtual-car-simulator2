package hyperdrive.modes;

/** Sharper throttle, ESC still fully active, launch control still locked. */
public class SportMode extends DriveMode {
    public SportMode() { this("Sport"); }

    // Protected constructor so TrackMode can reuse this class and relabel itself.
    protected SportMode(String name) { super(name); }

    @Override
    public double getThrottleResponse() { return 1.5; }

    @Override
    public boolean isEscFullyActive() { return true; }

    @Override
    public boolean allowsLaunchControl() { return false; }
}
