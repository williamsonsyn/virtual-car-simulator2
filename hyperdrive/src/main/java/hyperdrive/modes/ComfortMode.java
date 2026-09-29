package hyperdrive.modes;

/** Softest throttle, full ESC, no launch control. The default mode. */
public class ComfortMode extends DriveMode {
    public ComfortMode() { super("Comfort"); }

    @Override
    public double getThrottleResponse() { return 1.0; }

    @Override
    public boolean isEscFullyActive() { return true; }

    @Override
    public boolean allowsLaunchControl() { return false; }
}
