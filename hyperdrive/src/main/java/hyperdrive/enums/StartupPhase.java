package hyperdrive.enums;

/** The step of the ignition / start sequence the dashboard should be showing. */
public enum StartupPhase {
    NONE("", false),
    WARNING_SELF_TEST("WARNING SELF TEST", true),
    GAUGES_SELF_TEST("GAUGES SELF TEST", true),
    SYSTEM_CHECK("SYSTEM CHECK", true),
    READY("READY - PRESS ENTER TO START", true),
    CRANKING("CRANKING", true),
    ENGINE_STARTED("ENGINE STARTED", true);

    private final String label;
    private final boolean visible;

    StartupPhase(String label, boolean visible) {
        this.label = label;
        this.visible = visible;
    }

    public String getLabel() { return label; }
    public boolean isVisible() { return visible; }
}
