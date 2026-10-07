package hyperdrive.telemetry;

/**
 * A tiny immutable copy of what a DriveMode says about itself, for the snapshot.
 * We don't put the live DriveMode object into TelemetrySnapshot - this keeps the telemetry package
 * independent of hyperdrive.modes, and means a UI never needs to import hyperdrive.modes at all.
 */
public final class DriveModeInfo {
    private final String name;
    private final double throttleResponse;
    private final boolean escFullyActive;
    private final boolean launchControlAllowed;
    private final int rank;
    private final boolean shiftLights;

    // Constructor overloading: the original four-argument form (rank/shift lights default to Comfort values) ...
    public DriveModeInfo(String name, double throttleResponse, boolean escFullyActive,
            boolean launchControlAllowed) {
        this(name, throttleResponse, escFullyActive, launchControlAllowed, 0, false);
    }

    // ... and the full form used by Car.getTelemetry().
    public DriveModeInfo(String name, double throttleResponse, boolean escFullyActive,
            boolean launchControlAllowed, int rank, boolean shiftLights) {
        this.name = name;
        this.throttleResponse = throttleResponse;
        this.escFullyActive = escFullyActive;
        this.launchControlAllowed = launchControlAllowed;
        this.rank = rank;
        this.shiftLights = shiftLights;
    }

    public String getName() { return name; }
    public String getLabel() { return name.toUpperCase(); }
    public double getThrottleResponse() { return throttleResponse; }
    public boolean isEscFullyActive() { return escFullyActive; }
    public boolean isLaunchControlAllowed() { return launchControlAllowed; }
    public int getRank() { return rank; }
    public boolean hasShiftLights() { return shiftLights; }
}
