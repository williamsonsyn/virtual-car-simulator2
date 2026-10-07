package hyperdrive.safety;

import hyperdrive.enums.DiagnosticLevel;

/** Immutable result of one SafetyCheck, for the Diagnostics screen. */
public final class DiagnosticResult {
    private final String name;
    private final DiagnosticLevel level;
    private final String reason;

    public DiagnosticResult(String name, DiagnosticLevel level, String reason) {
        this.name = name;
        this.level = level;
        this.reason = reason;
    }

    public String getName() { return name; }
    public DiagnosticLevel getLevel() { return level; }
    public String getReason() { return reason; }

    @Override
    public String toString() {
        return level == DiagnosticLevel.PASS ? "PASS  " + name : label(level) + "  " + name + " - " + reason;
    }

    private static String label(DiagnosticLevel level) {
        return switch (level) {
            case PASS -> "PASS";
            case WARNING -> "WARN";
            case FAULT -> "FAIL";
            case CRITICAL -> "CRIT";
        };
    }
}
