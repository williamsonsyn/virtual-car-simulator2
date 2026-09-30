package hyperdrive.systems;

import hyperdrive.enums.FaultType;
import hyperdrive.enums.LiftState;

/** Nose lift for clearing speed bumps. Only usable stationary; auto-lowers once moving. */
public class VehicleLift extends VehicleSystem implements Loggable {
    private static final double TRANSITION_TIME = 1.0;
    private static final double AUTO_LOWER_SPEED = 40.0;   // km/h

    private LiftState state = LiftState.DOWN;
    private double transitionTimer = 0;
    private double speedKmh = 0;

    public VehicleLift() {
        super("Vehicle Lift");
    }

    public void setInputs(double speedKmh) { this.speedKmh = speedKmh; }

    /** Car has already validated preconditions (stationary, not Track mode) before calling this. */
    public void raise() {
        if (state == LiftState.DOWN) {
            state = LiftState.RAISING;
            transitionTimer = TRANSITION_TIME;
        }
    }

    public void lowerManual() {
        if (state == LiftState.RAISED) {
            state = LiftState.LOWERING;
            transitionTimer = TRANSITION_TIME;
        }
    }

    @Override
    public void update(double dt) {
        if (state == LiftState.RAISING || state == LiftState.LOWERING) {
            transitionTimer -= dt;
            if (transitionTimer <= 0) {
                state = (state == LiftState.RAISING) ? LiftState.RAISED : LiftState.DOWN;
            }
            return;
        }
        // Automatic lowering logic: never drive around with the nose lifted.
        if (state == LiftState.RAISED && speedKmh >= AUTO_LOWER_SPEED) {
            state = LiftState.LOWERING;
            transitionTimer = TRANSITION_TIME;
        }
    }

    public boolean isDown() { return state == LiftState.DOWN; }

    public LiftState getState() { return state; }

    @Override
    public boolean handles(FaultType type) { return false; }

    @Override
    public boolean selfTest() { return true; }

    @Override
    public String getLogSummary() { return "Vehicle lift state=" + state; }
}
