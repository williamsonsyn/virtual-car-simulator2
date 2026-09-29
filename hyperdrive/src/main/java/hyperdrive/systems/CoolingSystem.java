package hyperdrive.systems;

import hyperdrive.enums.FaultType;
import hyperdrive.sensors.TemperatureSensor;

/** Coolant and oil temperature. They warm up while the engine runs and cool down when it stops. */
public class CoolingSystem extends VehicleSystem {
    private static final double AMBIENT = 30.0;

    private final TemperatureSensor coolantTemp = new TemperatureSensor("Coolant temp", AMBIENT, 105.0);
    private final TemperatureSensor oilTemp = new TemperatureSensor("Oil temp", AMBIENT, 130.0);
    private boolean engineRunning = false;
    private double engineLoad = 0;

    public CoolingSystem() {
        super("Cooling System");
    }

    public void setEngineState(boolean running, double load) {
        this.engineRunning = running;
        this.engineLoad = load;
    }

    @Override
    public boolean handles(FaultType type) { return type == FaultType.OVERHEATING; }

    @Override
    protected void onFaultChanged() {
        if (hasFault(FaultType.OVERHEATING)) {
            coolantTemp.setValue(118.0);
            oilTemp.setValue(140.0);
        } else {
            coolantTemp.setValue(90.0);   // hot but back inside the limits; it cools from here
            oilTemp.setValue(100.0);
        }
    }

    @Override
    public void update(double dt) {
        if (hasFault(FaultType.OVERHEATING)) {
            return;   // the fault keeps temperatures high
        }
        double coolantTarget = engineRunning ? 88.0 + engineLoad * 15.0 : AMBIENT;
        double oilTarget = engineRunning ? 95.0 + engineLoad * 25.0 : AMBIENT;
        double k = Math.min(1.0, dt * 0.05);
        coolantTemp.setValue(coolantTemp.getValue() + (coolantTarget - coolantTemp.getValue()) * k);
        oilTemp.setValue(oilTemp.getValue() + (oilTarget - oilTemp.getValue()) * k);
    }

    @Override
    public boolean selfTest() { return coolantTemp.isHealthy() && oilTemp.isHealthy(); }

    public boolean isWithinLimits() { return coolantTemp.isHealthy() && oilTemp.isHealthy(); }
    public boolean isCoolantOk() { return coolantTemp.isHealthy(); }
    public boolean isOilOk() { return oilTemp.isHealthy(); }
    public double getCoolantTemp() { return coolantTemp.getValue(); }
    public double getOilTemp() { return oilTemp.getValue(); }
    public double getCoolantLimit() { return coolantTemp.getMaxSafe(); }
    public double getOilLimit() { return oilTemp.getMaxSafe(); }
}
