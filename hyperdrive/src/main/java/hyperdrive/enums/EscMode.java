package hyperdrive.enums;

/** Simplified ESC / traction control states (inspired by ESC ON / DYNAMIC / TRACK DYNAMIC / OFF). */
public enum EscMode {
    ON("ESC ON", 1.0),
    DYNAMIC("ESC DYNAMIC", 0.6),
    TRACK_DYNAMIC("ESC TRACK DYNAMIC", 0.35),
    OFF("ESC OFF", 0.0);

    private final String label;
    private final double strength;   // 1.0 = intervenes early and hard, 0.0 = never

    EscMode(String label, double strength) {
        this.label = label;
        this.strength = strength;
    }

    public String getLabel() { return label; }
    public double getStrength() { return strength; }
}
