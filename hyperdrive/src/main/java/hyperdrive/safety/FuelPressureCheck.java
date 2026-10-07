package hyperdrive.safety;

import hyperdrive.enums.DiagnosticLevel;
import hyperdrive.model.Car;
import hyperdrive.systems.FuelSystem;

public class FuelPressureCheck extends AbstractSafetyCheck {
    public FuelPressureCheck() { super("Fuel system"); }

    @Override
    public boolean check(Car car) {
        begin();
        FuelSystem fuel = car.getFuel();
        if (!fuel.isSensorWorking()) {
            fail(DiagnosticLevel.FAULT, "Fuel pressure sensor not responding");
        } else if (!fuel.isPressureSafe()) {
            fail(DiagnosticLevel.CRITICAL, String.format("Fuel pressure %.1f bar is below %.1f bar",
                    fuel.getPressure(), fuel.getMinPressure()));
        }
        if (fuel.getLevelPercent() <= FuelSystem.MIN_LEVEL_PERCENT) {
            fail(DiagnosticLevel.FAULT, String.format("Fuel level too low (%.0f%%)", fuel.getLevelPercent()));
        } else if (fuel.isLowFuel()) {
            fail(DiagnosticLevel.WARNING, String.format("Low fuel (%.0f%%)", fuel.getLevelPercent()));
        }
        return finish();
    }
}
