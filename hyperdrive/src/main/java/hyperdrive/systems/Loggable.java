package hyperdrive.systems;

/** Anything that can describe its state in one line for the session log file (added in a later step). */
public interface Loggable {
    String getLogSummary();
}
