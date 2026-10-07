package hyperdrive.modes;

/** Smooth throttle, smooth shifts, full ESC, no launch control. The default mode. */
public class ComfortMode extends DriveMode {
    public ComfortMode() { super("Comfort"); }

    @Override
    public double getThrottleResponse() { return 1.0; }

    @Override
    public boolean isEscFullyActive() { return true; }

    @Override
    public boolean allowsLaunchControl() { return false; }

    @Override
    public double getThrottleCurveExponent() { return 1.4; }

    @Override
    public double getThrottleRate() { return 5.0; }

    @Override
    public double getShiftUpRpm(double throttle) { return 2400 + throttle * 3000; }   // 5400 at full throttle

    @Override
    public double getShiftDownRpm() { return 1300; }

    @Override
    public double getShiftTime() { return 0.50; }

    @Override
    public double getSlipPermissiveness() { return 1.0; }

    @Override
    public int getRank() { return 0; }
}
