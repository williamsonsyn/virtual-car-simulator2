package hyperdrive.systems;

import hyperdrive.enums.FaultType;

/** Anything that can have simulated faults injected into it. */
public interface Faultable {
    void injectFault(FaultType type);
    void clearFault(FaultType type);
    boolean hasFault();
}
