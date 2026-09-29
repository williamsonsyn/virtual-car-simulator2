package hyperdrive.systems;

import hyperdrive.enums.FaultType;

/** Battery charge/voltage and the alternator. */
public class ElectricalSystem extends VehicleSystem {
    public static final double MIN_START_VOLTAGE = 11.5;
    public static final double MIN_WAKE_VOLTAGE = 10.0;

    private double chargePercent = 100.0;
    private boolean alternatorActive = false;

    public ElectricalSystem() {
        super("Electrical System");
    }

    public void setAlternatorActive(boolean active) { this.alternatorActive = active; }

    /** Voltage is derived from charge: 12% -> ~10.7 V, 100% -> 12.6 V. */
    public double getVoltage() { return 10.5 + chargePercent / 100.0 * 2.1; }

    public double getChargePercent() { return chargePercent; }

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
            chargePercent = Math.max(0.0, chargePercent - 0.01 * dt);   // tiny standby drain
        }
    }

    @Override
    public boolean selfTest() { return getVoltage() >= MIN_START_VOLTAGE; }
}
