package hyperdrive.safety;

import hyperdrive.model.Car;

/** One condition the car must satisfy before an operation is allowed. */
public interface SafetyCheck {
    boolean check(Car car);
    String getName();
    String getFailureReason();   // valid after check() returned false
}
