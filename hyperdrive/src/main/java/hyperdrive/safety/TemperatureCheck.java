package hyperdrive.safety;

import hyperdrive.model.Car;
import hyperdrive.systems.CoolingSystem;

public class TemperatureCheck implements SafetyCheck {
    private String failureReason = "";

    @Override
    public boolean check(Car car) {
        CoolingSystem cooling = car.getCooling();
        failureReason = "";
        if (!cooling.isCoolantOk()) {
            failureReason = String.format("Coolant %.0f C is above %.0f C",
                    cooling.getCoolantTemp(), cooling.getCoolantLimit());
        }
        if (!cooling.isOilOk()) {
            String oil = String.format("Oil %.0f C is above %.0f C", cooling.getOilTemp(), cooling.getOilLimit());
            failureReason = failureReason.isEmpty() ? oil : failureReason + ", " + oil;
        }
        return failureReason.isEmpty();
    }

    @Override
    public String getName() { return "Temperatures"; }

    @Override
    public String getFailureReason() { return failureReason; }
}
