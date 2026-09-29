package hyperdrive.safety;

import hyperdrive.model.Car;
import hyperdrive.systems.ElectricalSystem;

public class BatteryCheck implements SafetyCheck {
    private String failureReason = "";

    @Override
    public boolean check(Car car) {
        double v = car.getElectrical().getVoltage();
        if (v < ElectricalSystem.MIN_START_VOLTAGE) {
            failureReason = String.format("Battery voltage %.1f V is below %.1f V",
                    v, ElectricalSystem.MIN_START_VOLTAGE);
            return false;
        }
        failureReason = "";
        return true;
    }

    @Override
    public String getName() { return "Battery"; }

    @Override
    public String getFailureReason() { return failureReason; }
}
