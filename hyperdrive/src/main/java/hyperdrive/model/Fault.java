package hyperdrive.model;

import hyperdrive.enums.FaultType;
import hyperdrive.enums.Severity;

/** A fault that is currently active in the car. Has a copy constructor so the UI gets snapshots. */
public class Fault {
    private final FaultType type;
    private final Severity severity;
    private final long timestamp;

    public Fault(FaultType type) {
        this(type, type.getDefaultSeverity());
    }

    public Fault(FaultType type, Severity severity) {
        this.type = type;
        this.severity = severity;
        this.timestamp = System.currentTimeMillis();
    }

    // Copy constructor
    public Fault(Fault other) {
        this.type = other.type;
        this.severity = other.severity;
        this.timestamp = other.timestamp;
    }

    public FaultType getType() { return type; }
    public Severity getSeverity() { return severity; }
    public long getTimestamp() { return timestamp; }

    @Override
    public String toString() { return "[" + severity + "] " + type.getDescription(); }
}
