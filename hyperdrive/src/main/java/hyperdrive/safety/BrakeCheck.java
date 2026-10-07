package hyperdrive.safety;

import hyperdrive.enums.DiagnosticLevel;
import hyperdrive.model.Car;
import hyperdrive.systems.BrakeSystem;

public class BrakeCheck extends AbstractSafetyCheck {
    public BrakeCheck() { super("Brakes"); }

    @Override
    public boolean check(Car car) {
        begin();
        BrakeSystem brakes = car.getBrakes();
        if (!brakes.isPressureSafe()) {
            fail(DiagnosticLevel.CRITICAL, String.format("Brake pressure %.0f bar is below %.0f bar",
                    brakes.getHydraulicPressure(), brakes.getMinPressure()));
        }
        if (brakes.hasAbsFault()) {
            fail(DiagnosticLevel.WARNING, "ABS fault - wheels may lock under hard braking");
        }
        if (brakes.getDiscTemperature() > 0.9 * brakes.getDiscTemperatureLimit()) {
            fail(DiagnosticLevel.WARNING, String.format("Brake discs very hot (%.0f C)", brakes.getDiscTemperature()));
        }
        return finish();
    }
}
