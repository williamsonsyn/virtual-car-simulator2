package hyperdrive.safety;

import hyperdrive.model.Car;
import hyperdrive.systems.Transmission;

public class TransmissionCheck implements SafetyCheck {
    private String failureReason = "";

    @Override
    public boolean check(Car car) {
        Transmission t = car.getTransmission();
        failureReason = "";
        if (t.hasTransmissionFault()) {
            failureReason = "Transmission fault is active";
        } else if (!t.isInSafeStartState()) {
            failureReason = "Gear must be P or N (currently " + t.getGear().getLabel() + ")";
        }
        return failureReason.isEmpty();
    }

    @Override
    public String getName() { return "Transmission"; }

    @Override
    public String getFailureReason() { return failureReason; }
}
