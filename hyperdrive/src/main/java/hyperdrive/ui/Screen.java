package hyperdrive.ui;

import hyperdrive.telemetry.TelemetrySnapshot;

/**
 * A screen the app can show (Cockpit, Fault Simulator, and later Diagnostics).
 * HyperDriveApp's AnimationTimer calls refresh() on EVERY screen every frame, regardless of
 * which one is currently visible - this is the same pattern as VehicleSystem[] in Car and
 * SafetyCheck[] in DiagnosticSystem: a common type, refreshed polymorphically through one loop,
 * rather than the app needing to know which concrete screen class it's holding.
 */
public interface Screen {
    void refresh(TelemetrySnapshot telemetry);
}
