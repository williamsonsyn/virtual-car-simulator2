package hyperdrive.model;

import hyperdrive.enums.Severity;

/** One entry in the car's notification history: a message with a severity and a time. */
public class Notification {
    private final String message;
    private final Severity severity;
    private final long timestamp;

    public Notification(String message) {
        this(message, Severity.INFO);
    }

    public Notification(String message, Severity severity) {
        this.message = message;
        this.severity = severity;
        this.timestamp = System.currentTimeMillis();
    }

    // Copy constructor - same pattern as Fault and Tyre.
    public Notification(Notification other) {
        this.message = other.message;
        this.severity = other.severity;
        this.timestamp = other.timestamp;
    }

    public String getMessage() { return message; }
    public Severity getSeverity() { return severity; }
    public long getTimestamp() { return timestamp; }

    /** How long ago this notification was created - the dashboard shows new ones as a temporary pop-up. */
    public long getAgeMillis() { return System.currentTimeMillis() - timestamp; }

    @Override
    public String toString() { return "[" + severity + "] " + message; }
}
