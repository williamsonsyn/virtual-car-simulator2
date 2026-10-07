package hyperdrive.safety;

import hyperdrive.enums.DiagnosticLevel;

/**
 * Shared bookkeeping for the concrete checks (name, reason, level). A subclass calls fail(...) for every problem it
 * finds; the level becomes the worst one reported. This is an abstract class implementing an interface - the same
 * "interface + abstract helper" idea the rest of the project uses.
 */
public abstract class AbstractSafetyCheck implements SafetyCheck {
    private final String name;
    private String reason = "";
    private DiagnosticLevel level = DiagnosticLevel.PASS;

    protected AbstractSafetyCheck(String name) {
        this.name = name;
    }

    /** Subclasses call this first thing in check(). */
    protected void begin() {
        reason = "";
        level = DiagnosticLevel.PASS;
    }

    protected void fail(DiagnosticLevel failLevel, String why) {
        reason = reason.isEmpty() ? why : reason + ", " + why;
        level = level.worse(failLevel);
    }

    protected boolean finish() { return level == DiagnosticLevel.PASS; }

    @Override
    public String getName() { return name; }

    @Override
    public String getFailureReason() { return reason; }

    @Override
    public DiagnosticLevel getLevel() { return level; }
}
