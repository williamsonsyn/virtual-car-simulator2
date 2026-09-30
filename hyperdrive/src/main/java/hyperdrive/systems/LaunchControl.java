package hyperdrive.systems;

import hyperdrive.enums.FaultType;
import hyperdrive.enums.LaunchState;

/**
 * Launch Control's own state machine: ARM (boost builds) -> READY -> LAUNCHING (boost decays).
 * This class only manages the state and the boost number. It has NO idea about brakes, throttle,
 * gear or faults - the Car checks all of that (both to arm it, and to abort it every tick).
 */
public class LaunchControl extends VehicleSystem implements Loggable {
    private static final double ARM_TIME_SECONDS = 1.5;
    private static final double LAUNCH_DECAY_PER_SECOND = 250.0;   // %/s - quick burst, then done

    private LaunchState state = LaunchState.IDLE;
    private double boostPercent = 0;

    public LaunchControl() {
        super("Launch Control");
    }

    /** Car has already validated every readiness condition before calling this. */
    public void arm() {
        state = LaunchState.ARMING;
        boostPercent = 0;
    }

    public void execute() {
        state = LaunchState.LAUNCHING;
        boostPercent = 100;
    }

    public void abort() {
        state = LaunchState.IDLE;
        boostPercent = 0;
    }

    @Override
    public void update(double dt) {
        if (state == LaunchState.ARMING) {
            boostPercent = Math.min(100.0, boostPercent + (100.0 / ARM_TIME_SECONDS) * dt);
            if (boostPercent >= 100.0) {
                state = LaunchState.READY;
            }
        } else if (state == LaunchState.LAUNCHING) {
            boostPercent = Math.max(0.0, boostPercent - LAUNCH_DECAY_PER_SECOND * dt);
            if (boostPercent <= 0.0) {
                state = LaunchState.IDLE;
            }
        }
    }

    public LaunchState getState() { return state; }
    public double getBoostPercent() { return boostPercent; }

    @Override
    public boolean handles(FaultType type) { return false; }

    @Override
    public boolean selfTest() { return true; }

    @Override
    public String getLogSummary() {
        return String.format("Launch Control state=%s boost=%.0f%%", state, boostPercent);
    }
}
