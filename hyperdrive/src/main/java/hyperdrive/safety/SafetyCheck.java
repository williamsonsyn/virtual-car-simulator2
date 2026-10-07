package hyperdrive.safety;

import hyperdrive.enums.DiagnosticLevel;
import hyperdrive.model.Car;

/** One condition the car must satisfy (or one health check the diagnostics run continuously). */
public interface SafetyCheck {
    boolean check(Car car);
    String getName();
    String getFailureReason();      // valid after check() returned false
    DiagnosticLevel getLevel();     // valid after check(): PASS when it passed, otherwise how serious the failure is
}
