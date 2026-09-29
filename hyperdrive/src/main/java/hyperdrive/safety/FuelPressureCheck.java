package hyperdrive.safety;

import hyperdrive.model.Car;
import hyperdrive.systems.FuelSystem;

public class FuelPressureCheck implements SafetyCheck {
    private String failureReason = "";

    @Override
    public boolean check(Car car) {
        FuelSystem fuel = car.getFuel();
        failureReason = "";
        if (!fuel.isSensorWorking()) {
            failureReason = "Fuel pressure sensor not responding";
        } else if (fuel.getLevelPercent() <= FuelSystem.MIN_LEVEL_PERCENT) {
            failureReason = String.format("Fuel level too low (%.0f%%)", fuel.getLevelPercent());
        } else if (!fuel.isPressureSafe()) {
            failureReason = String.format("Fuel pressure %.1f bar is below %.1f bar",
                    fuel.getPressure(), fuel.getMinPressure());
        }
        return failureReason.isEmpty();
    }

    @Override
    public String getName() { return "Fuel system"; }

    @Override
    public String getFailureReason() { return failureReason; }
}
