package hyperdrive.enums;

/**
 * Launch Control's state machine:
 * OFF -> REQUESTED -> CHECKING -> AWAITING_THROTTLE -> BOOST_BUILDING -> READY -> LAUNCHING -> COMPLETE -> OFF
 * with ABORTED and UNAVAILABLE as the two ways out that are not a successful launch.
 */
public enum LaunchState {
    OFF("OFF"),
    REQUESTED("REQUESTED"),
    CHECKING("CHECKING"),
    AWAITING_THROTTLE("AWAITING THROTTLE"),
    BOOST_BUILDING("BOOST BUILDING"),
    READY("READY"),
    LAUNCHING("LAUNCHING"),
    COMPLETE("COMPLETE"),
    ABORTED("ABORTED"),
    UNAVAILABLE("UNAVAILABLE");

    private final String label;

    LaunchState(String label) { this.label = label; }

    public String getLabel() { return label; }

    /** True while a launch sequence owns the car (inputs/transmission/engine behave differently). */
    public boolean isSequenceActive() {
        return this == REQUESTED || this == CHECKING || this == AWAITING_THROTTLE
                || this == BOOST_BUILDING || this == READY || this == LAUNCHING;
    }
}
