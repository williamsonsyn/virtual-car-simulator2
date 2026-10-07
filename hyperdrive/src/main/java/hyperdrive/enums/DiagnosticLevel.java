package hyperdrive.enums;

/** Result of one safety check. PASS and WARNING never stop the engine starting; FAULT and CRITICAL do. */
public enum DiagnosticLevel {
    PASS, WARNING, FAULT, CRITICAL;

    public boolean isBlocking() { return this == FAULT || this == CRITICAL; }

    /** The more serious of two levels. */
    public DiagnosticLevel worse(DiagnosticLevel other) { return ordinal() >= other.ordinal() ? this : other; }
}
