package hyperdrive.systems;

import hyperdrive.enums.EngineState;
import hyperdrive.enums.FaultType;
import hyperdrive.enums.OilState;
import hyperdrive.sensors.PressureSensor;
import hyperdrive.sensors.TemperatureSensor;

/**
 * Engine lubrication: oil temperature (slow) and oil pressure (depends on whether the engine is cranking or running,
 * on RPM, and on the LOW_OIL_PRESSURE fault). Cold oil lowers the engine's RPM limit; low pressure is critical.
 * Extends VehicleSystem like every other system, so Car.update() ticks it through the same polymorphic loop.
 */
public class OilSystem extends VehicleSystem {
    public static final double MIN_PRESSURE_BAR = 1.0;
    public static final double COLD_BELOW_C = 50.0;
    public static final double HOT_ABOVE_C = 125.0;
    private static final double AMBIENT = 30.0;

    private final TemperatureSensor oilTemp = new TemperatureSensor("Oil temp", AMBIENT, 140.0);
    private final PressureSensor oilPressure = new PressureSensor("Oil pressure", 0.0, MIN_PRESSURE_BAR);

    private EngineState engineState = EngineState.OFF;
    private double rpm = 0;
    private double load = 0;
    private double coolantTemp = AMBIENT;

    public OilSystem() {
        super("Oil System");
    }

    /** Car feeds in what the oil system needs to know, once per tick. */
    public void setEngineConditions(EngineState engineState, double rpm, double load, double coolantTemp) {
        this.engineState = engineState;
        this.rpm = rpm;
        this.load = load;
        this.coolantTemp = coolantTemp;
    }

    @Override
    public boolean handles(FaultType type) { return type == FaultType.LOW_OIL_PRESSURE; }

    @Override
    public void update(double dt) {
        // Pressure: builds while cranking, follows RPM while running, collapses with the fault.
        double target;
        switch (engineState) {
            case CRANKING -> target = Math.min(1.4, rpm / 250.0 * 1.2);
            case RUNNING -> {
                double viscosity = oilTemp.getValue() < COLD_BELOW_C ? 1.15 : (oilTemp.getValue() > 115 ? 0.85 : 1.0);
                target = (1.0 + rpm * 0.00055) * viscosity;
            }
            default -> target = 0.0;
        }
        if (hasFault(FaultType.LOW_OIL_PRESSURE)) {
            target = Math.min(target * 0.2, 0.6);
        }
        double p = oilPressure.getValue();
        p += (target - p) * Math.min(1.0, dt * (target > p ? 1.5 : 4.0));
        oilPressure.setValue(Math.max(0.0, p));

        // Temperature: follows the coolant but much more slowly; load heats it. Cools very slowly when stopped.
        double tempTarget = (engineState == EngineState.RUNNING)
                ? coolantTemp * 0.9 + 12.0 + load * 14.0
                : (engineState == EngineState.CRANKING ? oilTemp.getValue() : AMBIENT);
        double t = oilTemp.getValue();
        double k = (tempTarget > t) ? 0.05 : 0.006;
        oilTemp.setValue(t + (tempTarget - t) * Math.min(1.0, dt * k));
    }

    /** Simulator utility: jump to normal operating temperature. */
    public void preWarm() { oilTemp.setValue(95.0); }

    public boolean isPressureLow() {
        return hasFault(FaultType.LOW_OIL_PRESSURE)
                || (engineState == EngineState.RUNNING && oilPressure.getValue() < MIN_PRESSURE_BAR);
    }

    public boolean isTemperatureOk() { return oilTemp.isHealthy(); }

    public OilState getState() {
        if (isPressureLow()) {
            return OilState.LOW_PRESSURE;
        }
        double t = oilTemp.getValue();
        if (t > HOT_ABOVE_C) {
            return OilState.HOT;
        }
        return t < COLD_BELOW_C ? OilState.COLD : OilState.NORMAL;
    }

    @Override
    public boolean selfTest() { return !isPressureLow() && oilTemp.isHealthy(); }

    public double getTemperature() { return oilTemp.getValue(); }
    public double getTemperatureLimit() { return oilTemp.getMaxSafe(); }
    public double getPressure() { return oilPressure.getValue(); }
}
