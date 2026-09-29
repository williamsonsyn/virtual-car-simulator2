package hyperdrive.enums;

/**
 * Every fault the simulator can inject.
 * An enum can hold data and methods: each constant carries a description and a default severity.
 */
public enum FaultType {
    LOW_FUEL_PRESSURE("Low fuel pressure", Severity.CRITICAL),
    LOW_BRAKE_PRESSURE("Low brake pressure", Severity.CRITICAL),
    LOW_BATTERY("Low battery", Severity.WARNING),
    OVERHEATING("Overheating", Severity.CRITICAL),
    LOW_TYRE_PRESSURE("Low tyre pressure", Severity.WARNING),
    TRANSMISSION_FAULT("Transmission fault", Severity.CRITICAL),
    SENSOR_FAULT("Fuel pressure sensor failure", Severity.WARNING);

    private final String description;
    private final Severity defaultSeverity;

    FaultType(String description, Severity defaultSeverity) {
        this.description = description;
        this.defaultSeverity = defaultSeverity;
    }

    public String getDescription() { return description; }
    public Severity getDefaultSeverity() { return defaultSeverity; }
}
