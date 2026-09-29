package hyperdrive.enums;

/** The three top-level power states of the vehicle. */
public enum PowerState {
    SLEEP,           // everything off
    POWER_ON,        // electronics awake, engine off
    ENGINE_RUNNING   // engine started
}
