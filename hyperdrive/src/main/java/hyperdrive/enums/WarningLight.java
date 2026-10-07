package hyperdrive.enums;

/** Every telltale on the warning indicator bar. */
public enum WarningLight {
    ENGINE("ENGINE"), OIL("OIL"), TEMP("TEMP"), BATTERY("BATT"), ABS("ABS"), ESC("ESC"), BRAKE("BRAKE"),
    EPB("EPB"), TPMS("TPMS"), SEATBELT("BELT"), SRS("SRS"), DOOR("DOOR"), TRANS("TRANS"),
    AIRBRAKE("AIRBRAKE"), LIFT("LIFT"), LAUNCH("LAUNCH"), FUEL("FUEL");

    private final String label;

    WarningLight(String label) { this.label = label; }

    public String getLabel() { return label; }
}
