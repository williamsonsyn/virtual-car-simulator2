package hyperdrive.safety;

import hyperdrive.enums.DiagnosticLevel;
import hyperdrive.model.Car;
import hyperdrive.systems.Transmission;

public class TransmissionCheck extends AbstractSafetyCheck {
    public TransmissionCheck() { super("Transmission"); }

    @Override
    public boolean check(Car car) {
        begin();
        Transmission t = car.getTransmission();
        if (t.hasTransmissionFault()) {
            fail(DiagnosticLevel.CRITICAL, "Transmission fault is active");
        }
        return finish();
    }
}
