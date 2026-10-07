package hyperdrive.systems;

import hyperdrive.enums.CoolingState;
import hyperdrive.enums.FaultType;
import hyperdrive.sensors.TemperatureSensor;

/**
 * Coolant temperature. Heat generation grows with RPM and engine load, and with SUSTAINED high load ("heat soak");
 * cooling grows with airflow (vehicle speed) and the fan. When the engine stops it cools slowly towards ambient.
 * (Oil temperature now lives in OilSystem.)
 */
public class CoolingSystem extends VehicleSystem {
    private static final double AMBIENT = 30.0;
    public static final double WARM_FROM = 100.0;
    public static final double HIGH_FROM = 107.0;
    public static final double CRITICAL_FROM = 113.0;

    private final TemperatureSensor coolantTemp = new TemperatureSensor("Coolant temp", AMBIENT, CRITICAL_FROM);
    private boolean engineRunning = false;
    private double engineLoad = 0;
    private double rpm = 0;
    private double speedKmh = 0;
    private double heatSoak = 0;          // 0-1: builds during sustained high load, fades when load drops
    private double heatGeneration = 0;    // 0-1, normalised
    private double coolingRate = 0;       // 0-1, fan + airflow

    public CoolingSystem() {
        super("Cooling System");
    }

    public void setEngineState(boolean running, double load) {
        this.engineRunning = running;
        this.engineLoad = load;
    }

    public void setConditions(double rpm, double speedKmh) {
        this.rpm = rpm;
        this.speedKmh = speedKmh;
    }

    @Override
    public boolean handles(FaultType type) { return type == FaultType.OVERHEATING; }

    @Override
    protected void onFaultChanged() {
        coolantTemp.setValue(hasFault(FaultType.OVERHEATING) ? 118.0 : 90.0);   // cleared: hot but back inside limits
    }

    @Override
    public void update(double dt) {
        if (hasFault(FaultType.OVERHEATING)) {
            return;   // the fault keeps the temperature high
        }
        double rpmFraction = rpm / Engine.REDLINE_RPM;
        heatGeneration = engineRunning ? 0.12 + engineLoad * (0.4 + 0.6 * rpmFraction) : 0.0;
        boolean heavy = engineRunning && engineLoad > 0.55;
        heatSoak += ((heavy ? 1.0 : 0.0) - heatSoak) * Math.min(1.0, dt / (heavy ? 40.0 : 70.0));
        double airflow = Math.min(1.0, speedKmh / 180.0);
        coolingRate = engineRunning ? 0.35 + airflow * 0.65 : 0.0;

        double t = coolantTemp.getValue();
        double target;
        double k;
        if (engineRunning) {
            target = 88.0 + engineLoad * 10.0 + heatSoak * 14.0 - airflow * 6.0;
            k = (target > t) ? 0.06 : 0.04;
        } else {
            target = AMBIENT;
            k = 0.012;
        }
        coolantTemp.setValue(t + (target - t) * Math.min(1.0, dt * k));
    }

    /** Simulator utility: jump to normal operating temperature. */
    public void preWarm() { coolantTemp.setValue(90.0); }

    public CoolingState getState() {
        double t = coolantTemp.getValue();
        if (t >= CRITICAL_FROM) {
            return CoolingState.CRITICAL;
        }
        if (t >= HIGH_FROM) {
            return CoolingState.HIGH;
        }
        return t >= WARM_FROM ? CoolingState.WARM : CoolingState.NORMAL;
    }

    @Override
    public boolean selfTest() { return coolantTemp.isHealthy(); }

    public boolean isWithinLimits() { return coolantTemp.isHealthy(); }
    public boolean isCoolantOk() { return coolantTemp.isHealthy(); }
    public double getCoolantTemp() { return coolantTemp.getValue(); }
    public double getCoolantLimit() { return coolantTemp.getMaxSafe(); }
    public double getHeatGeneration() { return heatGeneration; }
    public double getCoolingRate() { return coolingRate; }
    public double getHeatSoak() { return heatSoak; }
}
