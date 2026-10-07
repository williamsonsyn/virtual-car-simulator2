package hyperdrive.enums;

/**
 * Every fault the simulator can inject.
 * An enum can hold data and methods: each constant carries a description and a default severity.
 */
public enum FaultType {
    LOW_FUEL_PRESSURE("Low fuel pressure", Severity.CRITICAL),
    LOW_OIL_PRESSURE("Low oil pressure", Severity.CRITICAL),
    OVERHEATING("Engine overheat", Severity.CRITICAL),
    LOW_BRAKE_PRESSURE("Brake fault (low pressure)", Severity.CRITICAL),
    ABS_FAULT("ABS fault", Severity.WARNING),
    ESC_FAULT("ESC fault", Severity.WARNING),
    TPMS_FAULT("TPMS fault", Severity.WARNING),
    LOW_TYRE_PRESSURE("Tyre low pressure (FL)", Severity.WARNING),
    TYRE_OVERHEAT("Tyre overheat (RR)", Severity.WARNING),
    LOW_BATTERY("Battery fault", Severity.WARNING),
    TRANSMISSION_FAULT("Transmission fault", Severity.CRITICAL),
    AIRBRAKE_FAULT("Airbrake fault", Severity.WARNING),
    LIFT_FAULT("Vehicle lift fault", Severity.WARNING),
    SRS_FAULT("SRS (airbag) fault", Severity.WARNING),
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
