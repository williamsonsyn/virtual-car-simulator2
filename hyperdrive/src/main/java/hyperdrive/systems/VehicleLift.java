package hyperdrive.systems;

import hyperdrive.enums.FaultType;
import hyperdrive.enums.LiftState;

/**
 * Nose lift for clearing speed bumps. Only usable at low speed; auto-lowers once the car speeds up.
 * Moves through RAISING / LOWERING with a 0-1 progress value so the UI can animate it.
 */
public class VehicleLift extends VehicleSystem implements Loggable {
    public static final double MAX_RAISE_SPEED = 20.0;   // km/h - cannot be raised faster than this
    private static final double TRANSITION_TIME = 1.2;
    private static final double AUTO_LOWER_SPEED = 40.0;   // km/h

    private LiftState state = LiftState.NORMAL;
    private double progress = 0;     // 0 = normal height, 1 = fully raised
    private double speedKmh = 0;

    public VehicleLift() {
        super("Vehicle Lift");
    }

    public void setInputs(double speedKmh) { this.speedKmh = speedKmh; }

    /** Car has already validated preconditions (speed, mode, fault) before calling this. */
    public void raise() {
        if (state == LiftState.NORMAL) {
            state = LiftState.RAISING;
        }
    }

    public void lowerManual() {
        if (state == LiftState.RAISED || state == LiftState.RAISING) {
            state = LiftState.LOWERING;
        }
    }

    @Override
    public void update(double dt) {
        if (hasFault(FaultType.LIFT_FAULT) && (state == LiftState.RAISED || state == LiftState.RAISING)) {
            state = LiftState.LOWERING;   // a fault drops the nose back to normal height
        }
        switch (state) {
            case RAISING -> {
                progress = Math.min(1.0, progress + dt / TRANSITION_TIME);
                if (progress >= 1.0) {
                    state = LiftState.RAISED;
                }
            }
            case LOWERING -> {
                progress = Math.max(0.0, progress - dt / TRANSITION_TIME);
                if (progress <= 0.0) {
                    state = LiftState.NORMAL;
                }
            }
            case RAISED -> {
                // Never drive around with the nose lifted.
                if (speedKmh >= AUTO_LOWER_SPEED) {
                    state = LiftState.LOWERING;
                }
            }
            default -> { }
        }
    }

    /** True when the car is at normal ride height (not raised, raising or lowering). */
    public boolean isDown() { return state == LiftState.NORMAL; }

    public LiftState getState() { return state; }
    public double getProgress() { return progress; }

    @Override
    public boolean handles(FaultType type) { return type == FaultType.LIFT_FAULT; }

    @Override
    public boolean selfTest() { return !hasFault(FaultType.LIFT_FAULT); }

    @Override
    public String getLogSummary() { return "Vehicle lift state=" + state; }
}
