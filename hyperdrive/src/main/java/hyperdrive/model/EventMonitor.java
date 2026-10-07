package hyperdrive.model;

import hyperdrive.enums.LaunchState;
import hyperdrive.enums.Severity;
import hyperdrive.enums.WarningLight;
import java.util.EnumMap;
import java.util.Map;

/**
 * Watches the car and turns CHANGES into notifications: a warning that just appeared, a warning that cleared, a short
 * safety intervention (ABS, ESC, Brake Assist, Hill Hold) or an aborted launch. Because everything goes through the
 * NotificationManager, it is also all written to the log file by the Logger, and the cockpit's message area shows it.
 * It only reads the Car and posts messages - it never changes the car.
 */
public class EventMonitor {
    private static final double INTERVENTION_COOLDOWN = 3.0;   // seconds between repeated "ESC INTERVENTION" lines

    private final NotificationManager notifications;
    private final Map<WarningLight, Notification> lastWarnings = new EnumMap<>(WarningLight.class);

    private boolean wasAbs, wasEsc, wasAssist, wasHillHold;
    private double absCooldown, escCooldown;
    private LaunchState lastLaunch = LaunchState.OFF;

    public EventMonitor(NotificationManager notifications) {
        this.notifications = notifications;
    }

    /** Compare the new set of lit warnings with the previous one and report what changed. */
    public void trackWarnings(Map<WarningLight, Notification> now) {
        for (Map.Entry<WarningLight, Notification> e : now.entrySet()) {
            WarningLight light = e.getKey();
            Notification n = e.getValue();
            Notification before = lastWarnings.get(light);
            boolean isNew = before == null || !before.getMessage().equals(n.getMessage());
            if (isNew && light != WarningLight.EPB) {
                notifications.add(n.getMessage(), n.getSeverity());
            }
        }
        for (Map.Entry<WarningLight, Notification> e : lastWarnings.entrySet()) {
            Notification gone = e.getValue();
            if (!now.containsKey(e.getKey()) && gone.getSeverity() != Severity.INFO) {
                notifications.add(gone.getMessage() + " - cleared", Severity.INFO);
            }
        }
        lastWarnings.clear();
        lastWarnings.putAll(now);
    }

    /** Report short-lived safety interventions (rising edge only, and rate-limited for ABS/ESC). */
    public void trackInterventions(double dt, boolean abs, boolean esc, boolean assist, boolean hillHold) {
        absCooldown = Math.max(0.0, absCooldown - dt);
        escCooldown = Math.max(0.0, escCooldown - dt);
        if (abs && !wasAbs && absCooldown == 0) {
            notifications.add("ABS INTERVENTION", Severity.INFO);
            absCooldown = INTERVENTION_COOLDOWN;
        }
        if (esc && !wasEsc && escCooldown == 0) {
            notifications.add("ESC INTERVENTION", Severity.INFO);
            escCooldown = INTERVENTION_COOLDOWN;
        }
        if (assist && !wasAssist) {
            notifications.add("BRAKE ASSIST", Severity.INFO);
        }
        if (hillHold && !wasHillHold) {
            notifications.add("HILL HOLD", Severity.INFO);
        }
        wasAbs = abs;
        wasEsc = esc;
        wasAssist = assist;
        wasHillHold = hillHold;
    }

    /** Report launch outcomes that the warning list does not already cover (aborts carry the reason). */
    public void trackLaunch(LaunchState state, String reason) {
        if (state != lastLaunch) {
            if (state == LaunchState.ABORTED) {
                notifications.add("LAUNCH CONTROL ABORTED - " + reason, Severity.WARNING);
            } else if (state == LaunchState.LAUNCHING) {
                notifications.add("LAUNCH EXECUTED", Severity.INFO);
            }
            lastLaunch = state;
        }
    }

    /** Forget everything (new ignition cycle). */
    public void reset() {
        lastWarnings.clear();
        wasAbs = wasEsc = wasAssist = wasHillHold = false;
        lastLaunch = LaunchState.OFF;
    }
}
