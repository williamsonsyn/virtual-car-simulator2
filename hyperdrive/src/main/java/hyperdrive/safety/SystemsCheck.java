package hyperdrive.safety;

import hyperdrive.enums.DiagnosticLevel;
import hyperdrive.model.Car;

/** Covers the driver-assist and body systems: ESC, SRS, airbrake and vehicle lift. Warnings only. */
public class SystemsCheck extends AbstractSafetyCheck {
    public SystemsCheck() { super("Driver assist / body"); }

    @Override
    public boolean check(Car car) {
        begin();
        if (!car.getEsc().selfTest()) {
            fail(DiagnosticLevel.WARNING, "ESC fault");
        }
        if (!car.getSrs().selfTest()) {
            fail(DiagnosticLevel.WARNING, "SRS fault");
        }
        if (!car.getAirbrake().selfTest()) {
            fail(DiagnosticLevel.WARNING, "Airbrake fault");
        }
        if (!car.getLift().selfTest()) {
            fail(DiagnosticLevel.WARNING, "Vehicle lift fault");
        }
        return finish();
    }
}
