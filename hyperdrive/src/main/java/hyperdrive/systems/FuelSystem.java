package hyperdrive.systems;

import hyperdrive.enums.FaultType;
import hyperdrive.sensors.PressureSensor;

/**
 * Fuel level, fuel flow, consumption, estimated range and the low-fuel warning.
 * Consumption depends on RPM, load and throttle (flow in litres/hour), not on a random number.
 *
 * SIM_BURN_SCALE: fuel is burnt faster than in reality so the gauge visibly moves during a short demo session.
 * Everything that is shown as a consumption figure (L/100 km, trip average) uses the UNSCALED flow.
 */
public class FuelSystem extends VehicleSystem {
    private static final double NOMINAL_PRESSURE = 5.0;
    private static final double FAULT_PRESSURE = 1.2;
    public static final double MIN_LEVEL_PERCENT = 5.0;
    public static final double LOW_FUEL_PERCENT = 10.0;
    public static final double TANK_LITRES = 72.0;
    public static final double SIM_BURN_SCALE = 6.0;

    private final PressureSensor pressureSensor = new PressureSensor("Fuel pressure", NOMINAL_PRESSURE, 3.0);
    private double litres;

    // Inputs (set by the Car every tick)
    private boolean engineRunning = false;
    private double rpm = 0;
    private double load = 0;
    private double throttle = 0;
    private boolean coupled = false;
    private double speedKmh = 0;

    // Outputs
    private double flowLitresPerHour = 0;       // real (unscaled) flow
    private double avgConsumptionL100 = 14.0;   // smoothed, starts at a typical figure so range is meaningful at once

    public FuelSystem() {
        this(75.0);
    }

    public FuelSystem(double startLevelPercent) {
        super("Fuel System");
        double pct = Math.max(0.0, Math.min(100.0, startLevelPercent));
        this.litres = pct / 100.0 * TANK_LITRES;
    }

    /** Car feeds the operating point of the engine and the car, once per tick. */
    public void setOperatingPoint(boolean engineRunning, double rpm, double throttle, double load,
            boolean coupled, double speedKmh) {
        this.engineRunning = engineRunning;
        this.rpm = rpm;
        this.throttle = throttle;
        this.load = load;
        this.coupled = coupled;
        this.speedKmh = speedKmh;
    }

    /** Older single-argument form from earlier steps: engine load only. */
    public void setEngineLoad(double load) { this.load = load; }

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
        if (!engineRunning) {
            flowLitresPerHour = 0;
            return;
        }
        boolean decelCut = coupled && throttle < 0.03 && rpm > 1400;   // fuel is cut while engine-braking
        flowLitresPerHour = decelCut ? 0.2 : 1.6 + (rpm / 1000.0) * load * 15.0;
        litres = Math.max(0.0, litres - flowLitresPerHour / 3600.0 * dt * SIM_BURN_SCALE);

        if (speedKmh > 10.0) {
            double instant = flowLitresPerHour / speedKmh * 100.0;
            avgConsumptionL100 += (Math.min(instant, 80.0) - avgConsumptionL100) * Math.min(1.0, dt * 0.05);
        }
    }

    @Override
    public boolean selfTest() { return pressureSensor.isHealthy(); }

    public double getLevelPercent() { return litres / TANK_LITRES * 100.0; }
    public double getLitres() { return litres; }
    public double getFlowLitresPerHour() { return flowLitresPerHour; }
    public double getAverageConsumptionL100() { return avgConsumptionL100; }

    /** Instantaneous consumption in L/100 km (0 when almost stationary). */
    public double getInstantConsumptionL100() {
        return speedKmh > 5.0 ? Math.min(99.9, flowLitresPerHour / speedKmh * 100.0) : 0.0;
    }

    /** Estimated range (km) from the fuel left and the smoothed average consumption. */
    public double getEstimatedRangeKm() {
        return avgConsumptionL100 > 0.1 ? litres / avgConsumptionL100 * 100.0 : 0.0;
    }

    public boolean isLowFuel() { return getLevelPercent() <= LOW_FUEL_PERCENT; }

    public double getPressure() { return pressureSensor.getValue(); }
    public double getMinPressure() { return pressureSensor.getMinSafe(); }
    public boolean isPressureSafe() { return pressureSensor.isReadingSafe(); }
    public boolean isSensorWorking() { return pressureSensor.isWorking(); }
}
