package hyperdrive.safety;

import hyperdrive.enums.DiagnosticLevel;
import hyperdrive.model.Car;

/**
 * The two driver-action conditions for starting: brake pedal pressed and the gearbox in P or N.
 * Unlike the other checks this is NOT a health check, so DiagnosticSystem only runs it when the driver tries to start
 * (otherwise the Diagnostics screen would show a "fault" every time the pedal is simply not being pressed).
 */
public class StartInterlockCheck extends AbstractSafetyCheck {
    public StartInterlockCheck() { super("Start interlock"); }

    @Override
    public boolean check(Car car) {
        begin();
        if (!car.getBrakes().isPedalPressed()) {
            fail(DiagnosticLevel.FAULT, "Brake pedal must be pressed to start");
        }
        if (!car.getTransmission().isInSafeStartState()) {
            fail(DiagnosticLevel.FAULT, "Gear must be P or N (currently " + car.getTransmission().getGear().getLabel() + ")");
        }
        return finish();
    }
}
