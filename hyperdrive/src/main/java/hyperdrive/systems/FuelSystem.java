package hyperdrive.systems;

import hyperdrive.enums.FaultType;
import hyperdrive.sensors.PressureSensor;

/** Fuel level and fuel pressure. */
public class FuelSystem extends VehicleSystem {
    private static final double NOMINAL_PRESSURE = 5.0;
    private static final double FAULT_PRESSURE = 1.2;
    public static final double MIN_LEVEL_PERCENT = 5.0;

    private final PressureSensor pressureSensor = new PressureSensor("Fuel pressure", NOMINAL_PRESSURE, 3.0);
    private double levelPercent;
    private double engineLoad = 0;

    public FuelSystem() {
        this(75.0);
    }

    public FuelSystem(double startLevelPercent) {
        super("Fuel System");
        this.levelPercent = Math.max(0.0, Math.min(100.0, startLevelPercent));
    }

    public void setEngineLoad(double load) { this.engineLoad = load; }

    @Override
    public boolean handles(FaultType type) {
        return type == FaultType.LOW_FUEL_PRESSURE || type == FaultType.SENSOR_FAULT;
    }

    @Override
    protected void onFaultChanged() {
        pressureSensor.setValue(hasFault(FaultType.LOW_FUEL_PRESSURE) ? FAULT_PRESSURE : NOMINAL_PRESSURE);
        pressureSensor.setWorking(!hasFault(FaultType.SENSOR_FAULT));
    }

    @Override
    public void update(double dt) {
        levelPercent = Math.max(0.0, levelPercent - engineLoad * 0.2 * dt);   // burn fuel with load
    }

    @Override
    public boolean selfTest() { return pressureSensor.isHealthy(); }

    public double getLevelPercent() { return levelPercent; }
    public double getPressure() { return pressureSensor.getValue(); }
    public double getMinPressure() { return pressureSensor.getMinSafe(); }
    public boolean isPressureSafe() { return pressureSensor.isReadingSafe(); }
    public boolean isSensorWorking() { return pressureSensor.isWorking(); }
}
