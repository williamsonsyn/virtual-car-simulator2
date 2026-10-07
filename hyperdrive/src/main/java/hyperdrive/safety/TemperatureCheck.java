package hyperdrive.safety;

import hyperdrive.enums.CoolingState;
import hyperdrive.enums.DiagnosticLevel;
import hyperdrive.model.Car;
import hyperdrive.systems.CoolingSystem;

public class TemperatureCheck extends AbstractSafetyCheck {
    public TemperatureCheck() { super("Temperatures"); }

    @Override
    public boolean check(Car car) {
        begin();
        CoolingSystem cooling = car.getCooling();
        if (!cooling.isCoolantOk()) {
            fail(DiagnosticLevel.CRITICAL, String.format("Coolant %.0f C is above %.0f C",
                    cooling.getCoolantTemp(), cooling.getCoolantLimit()));
        } else if (cooling.getState() == CoolingState.HIGH) {
            fail(DiagnosticLevel.WARNING, String.format("Coolant temperature high (%.0f C)", cooling.getCoolantTemp()));
        }
        return finish();
    }
}
