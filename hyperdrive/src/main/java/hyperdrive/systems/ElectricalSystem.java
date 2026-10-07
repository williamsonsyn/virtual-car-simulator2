package hyperdrive.systems;

import hyperdrive.enums.BatteryState;
import hyperdrive.enums.FaultType;

/** Battery charge/voltage, the alternator (charging while the engine runs) and the electrical fault. */
public class ElectricalSystem extends VehicleSystem {
    public static final double MIN_START_VOLTAGE = 11.5;
    public static final double MIN_WAKE_VOLTAGE = 10.0;
    private static final double LOW_VOLTAGE = 11.8;

    private double chargePercent = 100.0;
    private boolean alternatorActive = false;
    private boolean cranking = false;
    private boolean ignitionOn = false;

    public ElectricalSystem() {
        super("Electrical System");
    }

    public void setAlternatorActive(boolean active) { this.alternatorActive = active; }
    public void setCranking(boolean cranking) { this.cranking = cranking; }
    public void setIgnitionOn(boolean ignitionOn) { this.ignitionOn = ignitionOn; }

    /**
     * Voltage is derived from charge (12% -> ~10.75 V, 100% -> 12.6 V), plus the alternator when charging,
     * minus a dip while the starter is turning.
     */
    public double getVoltage() {
        double v = 10.5 + chargePercent / 100.0 * 2.1;
        if (alternatorActive && !hasFault(FaultType.LOW_BATTERY)) {
            v += 1.4;
        }
        if (cranking) {
            v -= 1.0;
        }
        return v;
    }

    /** Battery voltage with no alternator and no starter load - what the start-up battery check looks at. */
    public double getRestingVoltage() { return 10.5 + chargePercent / 100.0 * 2.1; }

    public double getChargePercent() { return chargePercent; }

    public boolean isCharging() {
        return alternatorActive && !hasFault(FaultType.LOW_BATTERY) && chargePercent < 100.0;
    }

    public BatteryState getBatteryState() {
        if (hasFault(FaultType.LOW_BATTERY)) {
            return BatteryState.FAULT;
        }
        if (getRestingVoltage() < LOW_VOLTAGE && !alternatorActive) {
            return BatteryState.LOW;
        }
        if (isCharging()) {
            return BatteryState.CHARGING;
        }
        return (ignitionOn && !alternatorActive) ? BatteryState.DISCHARGING : BatteryState.OK;
    }

    @Override
    public boolean handles(FaultType type) { return type == FaultType.LOW_BATTERY; }

    @Override
    protected void onFaultChanged() {
        chargePercent = hasFault(FaultType.LOW_BATTERY) ? 12.0 : 100.0;
    }

    @Override
    public void update(double dt) {
        if (hasFault(FaultType.LOW_BATTERY)) {
            return;   // the fault keeps the battery low
        }
        if (alternatorActive) {
            chargePercent = Math.min(100.0, chargePercent + 2.0 * dt);
        } else {
            double drain = cranking ? 0.5 : (ignitionOn ? 0.03 : 0.01);   // starter > ignition-on > standby
            chargePercent = Math.max(0.0, chargePercent - drain * dt);
        }
    }

    @Override
    public boolean selfTest() { return getVoltage() >= MIN_START_VOLTAGE || alternatorActive; }
}
