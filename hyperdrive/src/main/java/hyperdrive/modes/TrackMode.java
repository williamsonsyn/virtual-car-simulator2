package hyperdrive.modes;

/**
 * Multilevel inheritance: TrackMode extends SportMode extends DriveMode.
 * Track IS a sharper Sport - it overrides every behaviour Sport defined.
 * Track does NOT switch ESC off by itself - it only makes the dynamics more permissive.
 */
public class TrackMode extends SportMode {
    public TrackMode() { super("Track"); }

    @Override
    public double getThrottleResponse() { return 2.0; }

    @Override
    public boolean isEscFullyActive() { return false; }   // relaxed intervention for a livelier feel

    @Override
    public boolean allowsLaunchControl() { return true; }

    @Override
    public double getThrottleCurveExponent() { return 0.8; }

    @Override
    public double getThrottleRate() { return 14.0; }

    @Override
    public double getShiftUpRpm(double throttle) { return 5200 + throttle * 2700; }   // 7900 at full throttle

    @Override
    public double getShiftDownRpm() { return 3300; }

    @Override
    public double getShiftTime() { return 0.15; }

    @Override
    public double getSlipPermissiveness() { return 1.5; }

    @Override
    public int getRank() { return 2; }

    @Override
    public boolean hasShiftLights() { return true; }
}
