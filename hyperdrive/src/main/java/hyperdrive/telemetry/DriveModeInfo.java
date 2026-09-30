package hyperdrive.telemetry;

/**
 * A tiny immutable copy of what a DriveMode says about itself, for the snapshot.
 * We don't put the live DriveMode object into TelemetrySnapshot - the mode itself never changes
 * once selected, so this is more about keeping the telemetry package independent of hyperdrive.modes
 * than about mutability, but it also means a UI never needs to import hyperdrive.modes at all.
 */
public final class DriveModeInfo {
    private final String name;
    private final double throttleResponse;
    private final boolean escFullyActive;
    private final boolean launchControlAllowed;

    public DriveModeInfo(String name, double throttleResponse, boolean escFullyActive,
            boolean launchControlAllowed) {
        this.name = name;
        this.throttleResponse = throttleResponse;
        this.escFullyActive = escFullyActive;
        this.launchControlAllowed = launchControlAllowed;
    }

    public String getName() { return name; }
    public double getThrottleResponse() { return throttleResponse; }
    public boolean isEscFullyActive() { return escFullyActive; }
    public boolean isLaunchControlAllowed() { return launchControlAllowed; }
}
