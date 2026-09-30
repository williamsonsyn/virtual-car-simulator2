package hyperdrive.enums;

/** Launch Control's state machine. */
public enum LaunchState {
    IDLE,       // not requested
    ARMING,     // conditions met, boost building
    READY,      // boost at 100%, waiting for the driver to release the brake
    LAUNCHING   // launch executing, boost decaying back to 0
}
