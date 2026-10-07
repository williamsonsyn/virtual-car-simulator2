package hyperdrive.systems;

import hyperdrive.enums.FaultType;

/**
 * Steering state: the driver's A/D input becomes a smoothed steering angle that the rest of the car actually uses -
 * tyres (load transfer), ESC, brake-steer, launch control ("steering straight") and the lateral dynamics.
 * Positive = steering to the right.
 */
public class SteeringSystem extends VehicleSystem {
    public static final double MAX_WHEEL_ANGLE_DEG = 270.0;   // steering wheel angle at full lock
    private static final double TURN_RATE = 1.6;    // normalised units per second while the key is held
    private static final double CENTRE_RATE = 2.6;  // self-centring speed after release

    /** Which way the wheel is turned. */
    public enum Direction { LEFT, CENTER, RIGHT }

    private double input = 0;   // -1 .. +1 from the driver
    private double value = 0;   // -1 .. +1 actual (smoothed)

    public SteeringSystem() {
        super("Steering");
    }

    /** Driver input: -1 = full left, 0 = hands off / centre, +1 = full right. */
    public void setInput(double input) {
        if (input < -1 || input > 1) {
            throw new IllegalArgumentException("Steering input must be between -1 and 1");
        }
        this.input = input;
    }

    @Override
    public void update(double dt) {
        double rate = (Math.abs(input) < 0.001) ? CENTRE_RATE : TURN_RATE;
        double step = rate * dt;
        if (value < input) {
            value = Math.min(input, value + step);
        } else if (value > input) {
            value = Math.max(input, value - step);
        }
    }

    public double getValue() { return value; }
    public double getAngleDegrees() { return value * MAX_WHEEL_ANGLE_DEG; }

    public Direction getDirection() {
        if (Math.abs(value) < 0.03) {
            return Direction.CENTER;
        }
        return value < 0 ? Direction.LEFT : Direction.RIGHT;
    }

    public boolean isApproximatelyStraight() { return Math.abs(value) < 0.10; }

    @Override
    public boolean handles(FaultType type) { return false; }

    @Override
    public boolean selfTest() { return true; }
}
