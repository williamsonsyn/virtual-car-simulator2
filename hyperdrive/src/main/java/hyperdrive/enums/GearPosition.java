package hyperdrive.enums;

/** Gear selector positions: P, R, N and forward gears 1-7. */
public enum GearPosition {
    P("P"), R("R"), N("N"),
    G1("1"), G2("2"), G3("3"), G4("4"), G5("5"), G6("6"), G7("7");

    private final String label;

    GearPosition(String label) { this.label = label; }

    public String getLabel() { return label; }

    /** True for gears 1-7. */
    public boolean isForward() { return ordinal() >= G1.ordinal(); }

    /** 1-7 for forward gears, 0 for P/R/N. */
    public int getGearNumber() { return isForward() ? ordinal() - G1.ordinal() + 1 : 0; }
}
