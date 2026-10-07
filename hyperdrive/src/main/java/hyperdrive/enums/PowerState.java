package hyperdrive.enums;

/** Top-level power / ignition state of the vehicle (simplified 720S-style electrical sequence). */
public enum PowerState {
    SLEEP,           // everything off
    SELF_TEST,       // woken by ignition: dashboard illuminates and runs its warning/gauge/system self-test
    POWER_ON,        // self-test finished, electronics awake, engine off, ready for ENTER
    CRANKING,        // starter turning the engine; RPM rising, oil pressure building
    ENGINE_RUNNING   // engine started and idling / driving
}
