package hyperdrive.safety;

import hyperdrive.model.Car;
import hyperdrive.systems.BrakeSystem;

public class BrakeCheck implements SafetyCheck {
    private String failureReason = "";

    @Override
    public boolean check(Car car) {
        BrakeSystem brakes = car.getBrakes();
        failureReason = "";
        if (!brakes.isPressureSafe()) {
            failureReason = String.format("Brake pressure %.0f bar is below %.0f bar",
                    brakes.getHydraulicPressure(), brakes.getMinPressure());
        } else if (!brakes.isPedalPressed()) {
            failureReason = "Brake pedal must be pressed to start";
        }
        return failureReason.isEmpty();
    }

    @Override
    public String getName() { return "Brakes"; }

    @Override
    public String getFailureReason() { return failureReason; }
}
