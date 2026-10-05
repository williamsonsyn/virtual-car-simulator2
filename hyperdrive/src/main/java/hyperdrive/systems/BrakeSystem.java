package hyperdrive.systems;

import hyperdrive.enums.FaultType;
import hyperdrive.sensors.PressureSensor;
import hyperdrive.sensors.TemperatureSensor;

/** Brake pedal, hydraulic pressure and disc temperature. */
public class BrakeSystem extends VehicleSystem {
    private static final double NOMINAL_PRESSURE = 120.0;
    private static final double FAULT_PRESSURE = 40.0;
    private static final double AMBIENT_TEMP = 40.0;
    private static final double ABS_MIN_PEDAL = 0.7;    // hard braking
    private static final double ABS_MIN_SPEED = 40.0;   // km/h - ABS only matters above walking pace

    private final PressureSensor hydraulicPressure =
            new PressureSensor("Brake hydraulic pressure", NOMINAL_PRESSURE, 80.0);
    private final TemperatureSensor discTemp = new TemperatureSensor("Brake disc temp", AMBIENT_TEMP, 600.0);
    private double pedal = 0;            // 0.0 - 1.0
    private boolean absActive = false;
    private double speedKmh = 0;

    public BrakeSystem() {
        super("Brake System");
    }

    // Method overloading: same name, different parameter lists.
    public void apply(double amount) {
        apply(amount, false);
    }

    // NOTE: update(dt) recomputes absActive from pedal+speed every tick (real ABS decides for
    // itself) - so this parameter only holds until the next tick. Kept for callers that want an
    // immediate value for this instant, and to keep the two-overload example meaningful.
    public void apply(double amount, boolean absActive) {
        if (amount < 0 || amount > 1) {
            throw new IllegalArgumentException("Brake input must be between 0 and 1");
        }
        this.pedal = amount;
        this.absActive = absActive;
    }

    public void setSpeed(double speedKmh) { this.speedKmh = speedKmh; }

    public void release() {
        pedal = 0;
        absActive = false;
    }

    public boolean isPedalPressed() { return pedal > 0.1; }

    public boolean isAbsActive() { return absActive; }

    /** Raw pedal position, 0.0-1.0. Used by ESC and Launch Control to check the driver's inputs. */
    public double getPedalPosition() { return pedal; }

    /** 0.0 - 1.0. A low hydraulic pressure fault weakens braking; ABS trades a little force for stability. */
    public double getBrakingForce() {
        double pressureFactor = Math.min(1.0, hydraulicPressure.getValue() / NOMINAL_PRESSURE);
        double absFactor = absActive ? 0.9 : 1.0;
        return pedal * pressureFactor * absFactor;
    }

    @Override
    public boolean handles(FaultType type) { return type == FaultType.LOW_BRAKE_PRESSURE; }

    @Override
    protected void onFaultChanged() {
        hydraulicPressure.setValue(hasFault(FaultType.LOW_BRAKE_PRESSURE) ? FAULT_PRESSURE : NOMINAL_PRESSURE);
    }

    @Override
    public void update(double dt) {
        // ABS has its own continuous monitoring in a real car - it isn't something the driver
        // requests, it engages/disengages itself based on current conditions. Re-checking every
        // tick (rather than only when apply() is called) means it correctly disengages the
        // moment speed drops below the threshold, even if the pedal is still held hard.
        absActive = pedal >= ABS_MIN_PEDAL && speedKmh >= ABS_MIN_SPEED;

        double t = discTemp.getValue();
        double heating = pedal * (speedKmh / 100.0) * 90.0 * dt;   // braking from speed heats the discs
        double cooling = (t - AMBIENT_TEMP) * 0.05 * dt;      // air cools them
        discTemp.setValue(Math.max(AMBIENT_TEMP, t + heating - cooling));
    }

    @Override
    public boolean selfTest() { return hydraulicPressure.isHealthy() && discTemp.isHealthy(); }

    public double getHydraulicPressure() { return hydraulicPressure.getValue(); }
    public double getMinPressure() { return hydraulicPressure.getMinSafe(); }
    public boolean isPressureSafe() { return hydraulicPressure.isReadingSafe(); }
    public double getDiscTemperature() { return discTemp.getValue(); }
}
