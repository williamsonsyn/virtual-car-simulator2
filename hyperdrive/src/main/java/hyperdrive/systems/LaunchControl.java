package hyperdrive.systems;

import hyperdrive.enums.FaultType;
import hyperdrive.enums.LaunchState;

/**
 * Launch Control's own state machine:
 * OFF -> REQUESTED -> CHECKING -> AWAITING_THROTTLE -> BOOST_BUILDING -> READY -> LAUNCHING -> COMPLETE -> OFF
 * with ABORTED and UNAVAILABLE as the two ways out that are not a successful launch.
 *
 * This class owns the states, the timers and the boost number. It does NOT decide whether a launch is allowed - the Car
 * checks every precondition (engine, gear, brake, steering, lift, temperatures, faults ...) before request(), and
 * aborts the sequence if one of them breaks - because those facts belong to other systems.
 */
public class LaunchControl extends VehicleSystem implements Loggable {
    private static final double REQUEST_TIME = 0.3;
    private static final double CHECK_TIME = 0.8;
    private static final double THROTTLE_WAIT_TIMEOUT = 12.0;
    private static final double BOOST_TIME = 1.5;
    private static final double LAUNCH_MAX_TIME = 6.0;
    private static final double LAUNCH_COMPLETE_SPEED = 100.0;   // km/h
    private static final double RESULT_DISPLAY_TIME = 2.5;       // COMPLETE / ABORTED stay visible this long
    private static final double UNAVAILABLE_DISPLAY_TIME = 4.0;
    public static final double LAUNCH_HOLD_RPM = 4800;

    private LaunchState state = LaunchState.OFF;
    private double boostPercent = 0;
    private double timer = 0;
    private double launchTime = 0;
    private String lastReason = "";

    private double throttle = 0;
    private double brakePedal = 0;
    private double speedKmh = 0;

    public LaunchControl() {
        super("Launch Control");
    }

    public void setInputs(double throttle, double brakePedal, double speedKmh) {
        this.throttle = throttle;
        this.brakePedal = brakePedal;
        this.speedKmh = speedKmh;
    }

    /** Car has already validated every readiness condition before calling this. */
    public void request() {
        state = LaunchState.REQUESTED;
        timer = REQUEST_TIME;
        boostPercent = 0;
        lastReason = "";
    }

    /** The Car refused the request; the dashboard shows LAUNCH CONTROL UNAVAILABLE for a few seconds. */
    public void markUnavailable(String reason) {
        state = LaunchState.UNAVAILABLE;
        timer = UNAVAILABLE_DISPLAY_TIME;
        boostPercent = 0;
        lastReason = reason;
    }

    /** Stops a running sequence. Does nothing if no sequence is active. */
    public void abort(String reason) {
        if (state.isSequenceActive()) {
            state = LaunchState.ABORTED;
            timer = RESULT_DISPLAY_TIME;
            boostPercent = 0;
            lastReason = reason;
        }
    }

    /** Back to OFF immediately (used on ignition cycle / engine stop). */
    public void reset() {
        state = LaunchState.OFF;
        boostPercent = 0;
        timer = 0;
    }

    @Override
    public void update(double dt) {
        switch (state) {
            case REQUESTED -> {
                timer -= dt;
                if (timer <= 0) {
                    state = LaunchState.CHECKING;
                    timer = CHECK_TIME;
                }
            }
            case CHECKING -> {
                timer -= dt;
                if (timer <= 0) {
                    state = LaunchState.AWAITING_THROTTLE;
                    timer = THROTTLE_WAIT_TIMEOUT;
                }
            }
            case AWAITING_THROTTLE -> {
                timer -= dt;
                if (throttle >= 0.9) {
                    state = LaunchState.BOOST_BUILDING;
                    boostPercent = 0;
                } else if (timer <= 0) {
                    abort("Full throttle was not applied in time");
                }
            }
            case BOOST_BUILDING -> {
                boostPercent = Math.min(100.0, boostPercent + (100.0 / BOOST_TIME) * dt);
                if (boostPercent >= 100.0) {
                    state = LaunchState.READY;
                }
            }
            case READY -> {
                if (brakePedal < 0.15) {   // the driver released the brake: go!
                    state = LaunchState.LAUNCHING;
                    launchTime = 0;
                }
            }
            case LAUNCHING -> {
                launchTime += dt;
                boostPercent = Math.max(0.0, boostPercent - 60.0 * dt);
                if (speedKmh >= LAUNCH_COMPLETE_SPEED || launchTime >= LAUNCH_MAX_TIME) {
                    state = LaunchState.COMPLETE;
                    timer = RESULT_DISPLAY_TIME;
                    boostPercent = 0;
                }
            }
            case COMPLETE, ABORTED, UNAVAILABLE -> {
                timer -= dt;
                if (timer <= 0) {
                    state = LaunchState.OFF;
                }
            }
            default -> { }
        }
    }

    /** RPM the engine is held at while the brakes hold the car and boost builds (0 = no hold). */
    public double getLaunchHoldRpm() {
        return (state == LaunchState.BOOST_BUILDING || state == LaunchState.READY) ? LAUNCH_HOLD_RPM : 0.0;
    }

    /** True while the car must be held stationary by the brakes (boost building / ready). */
    public boolean isHoldingCar() {
        return state == LaunchState.BOOST_BUILDING || state == LaunchState.READY;
    }

    public boolean isLaunching() { return state == LaunchState.LAUNCHING; }

    public LaunchState getState() { return state; }
    public double getBoostPercent() { return boostPercent; }
    public String getLastReason() { return lastReason; }

    @Override
    public boolean handles(FaultType type) { return false; }

    @Override
    public boolean selfTest() { return true; }

    @Override
    public String getLogSummary() {
        return String.format("Launch Control state=%s boost=%.0f%%", state, boostPercent);
    }
}
