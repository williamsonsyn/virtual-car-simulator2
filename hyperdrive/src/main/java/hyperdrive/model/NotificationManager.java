package hyperdrive.model;

import hyperdrive.enums.Severity;
import hyperdrive.io.Logger;
import java.util.ArrayList;
import java.util.List;

/**
 * Keeps the session's notifications (info/warning/critical).
 * This is the single source of truth for anything the Car wants to tell the driver -
 * the plain event log, the file logger, and any on-screen warning banner all read from here.
 *
 * A Logger can be attached so every notification is ALSO written to disk the moment it happens.
 * This class has no idea what a Car or an Engine is - it only knows about Notification and Logger.
 */
public class NotificationManager {
    private static final int MAX_STORED = 500;   // keep memory bounded for a long session

    private final List<Notification> notifications = new ArrayList<>();
    private Logger logger;   // null = no file logging attached

    public void attachLogger(Logger logger) { this.logger = logger; }

    // Method overloading: a plain message defaults to INFO.
    public void add(String message) { add(message, Severity.INFO); }

    public void add(String message, Severity severity) {
        Notification n = new Notification(message, severity);
        notifications.add(n);
        if (notifications.size() > MAX_STORED) {
            notifications.remove(0);   // drop the oldest once the in-memory log gets long
        }
        if (logger != null) {
            logger.log(n);   // the file keeps everything; only memory is bounded
        }
    }

    /** Copies (via the copy constructor) - callers cannot alter the real history. */
    public List<Notification> getAll() {
        List<Notification> copy = new ArrayList<>();
        for (Notification n : notifications) {
            copy.add(new Notification(n));
        }
        return copy;
    }

    /** Only notifications at or above the given severity - e.g. WARNING to see active issues. */
    public List<Notification> getBySeverity(Severity minimum) {
        List<Notification> filtered = new ArrayList<>();
        for (Notification n : notifications) {
            if (n.getSeverity().ordinal() >= minimum.ordinal()) {
                filtered.add(new Notification(n));
            }
        }
        return filtered;
    }

    public List<Notification> getRecent(int count) {
        List<Notification> all = getAll();
        int from = Math.max(0, all.size() - count);
        return new ArrayList<>(all.subList(from, all.size()));
    }
}
