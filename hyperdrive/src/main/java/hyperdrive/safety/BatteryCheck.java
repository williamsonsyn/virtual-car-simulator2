package hyperdrive.safety;

import hyperdrive.enums.DiagnosticLevel;
import hyperdrive.model.Car;
import hyperdrive.systems.ElectricalSystem;

public class BatteryCheck extends AbstractSafetyCheck {
    public BatteryCheck() { super("Battery"); }

    @Override
    public boolean check(Car car) {
        begin();
        double v = car.getElectrical().getRestingVoltage();
        if (v < ElectricalSystem.MIN_START_VOLTAGE) {
            fail(DiagnosticLevel.FAULT, String.format("Battery voltage %.1f V is below %.1f V",
                    v, ElectricalSystem.MIN_START_VOLTAGE));
        }
        return finish();
    }
}
