package hyperdrive.modes;

/** Sharper throttle, faster shifts at higher RPM, more permissive ESC, launch control still locked. */
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

    @Override
    public double getThrottleCurveExponent() { return 1.0; }

    @Override
    public double getThrottleRate() { return 9.0; }

    @Override
    public double getShiftUpRpm(double throttle) { return 3600 + throttle * 3600; }   // 7200 at full throttle

    @Override
    public double getShiftDownRpm() { return 2300; }

    @Override
    public double getShiftTime() { return 0.28; }

    @Override
    public double getSlipPermissiveness() { return 1.2; }

    @Override
    public int getRank() { return 1; }
}
