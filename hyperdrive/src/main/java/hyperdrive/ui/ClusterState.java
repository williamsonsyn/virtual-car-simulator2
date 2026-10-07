package hyperdrive.ui;

import hyperdrive.enums.PowerState;
import hyperdrive.enums.Severity;
import hyperdrive.model.Notification;
import hyperdrive.telemetry.TelemetrySnapshot;

/**
 * What the instrument cluster DISPLAYS, as opposed to what the car IS.
 * The needle, speed digits, mode styling, wake-up fade and notification pop-up are smoothed / animated here over time,
 * but every target value is read from a TelemetrySnapshot - nothing here is made up. This class has no JavaFX
 * dependency, so the same smoothing logic can be tested headlessly.
 */
public class ClusterState {
    public static final String[] PAGE_NAMES = {
        "VEHICLE", "MESSAGES", "TRIP", "TYRES", "OIL STATUS", "BATTERY", "VEHICLE INFO"
    };

    private TelemetrySnapshot telemetry;
    private double displayRpm = 0;
    private double displaySpeed = 0;
    private double style = 0;            // 0 = Comfort look, 1 = Sport, 2 = Track (animates between them)
    private double wake = 0;             // 0 = display dark, 1 = fully lit
    private double clock = 0;            // seconds, drives blinking
    private int page = 0;
    private boolean longTermTrip = false;

    private Notification toast;
    private long toastUntilMillis = 0;
    private long lastToastStamp = 0;

    private long lastNanos = -1;

    /** Advance the animation using the wall clock and absorb a fresh snapshot. */
    public void update(TelemetrySnapshot t) {
        long now = System.nanoTime();
        double dt = lastNanos < 0 ? 0.016 : Math.min(0.1, (now - lastNanos) / 1_000_000_000.0);
        lastNanos = now;
        update(t, dt, System.currentTimeMillis());
    }

    /** Deterministic form (explicit dt and clock) used by the headless preview/tests. */
    public void update(TelemetrySnapshot t, double dt, long nowMillis) {
        this.telemetry = t;
        clock += dt;

        boolean selfTest = t.getPowerState() == PowerState.SELF_TEST;
        double targetRpm = selfTest ? t.getGaugeSweep() * 9000.0 : t.getRpm();
        double targetSpeed = selfTest ? t.getGaugeSweep() * 330.0 : t.getSpeedKmh();
        displayRpm += (targetRpm - displayRpm) * (1.0 - Math.exp(-dt * (selfTest ? 14.0 : 16.0)));
        displaySpeed += (targetSpeed - displaySpeed) * (1.0 - Math.exp(-dt * 9.0));

        double targetStyle = t.getActiveState() == hyperdrive.enums.ActiveState.ACTIVE ? t.getPowertrainMode().getRank() : 0;
        style += (targetStyle - style) * (1.0 - Math.exp(-dt * 6.0));

        double targetWake = t.getPowerState() == PowerState.SLEEP ? 0.0 : 1.0;
        wake += (targetWake - wake) * (1.0 - Math.exp(-dt * 5.0));

        absorbNotifications(t, nowMillis);
    }

    private void absorbNotifications(TelemetrySnapshot t, long nowMillis) {
        for (Notification n : t.getRecentNotifications()) {
            if (n.getTimestamp() > lastToastStamp) {
                lastToastStamp = n.getTimestamp();
                if (isPopUpWorthy(n)) {
                    toast = n;
                    toastUntilMillis = nowMillis + (n.getSeverity() == Severity.CRITICAL ? 6000 : 4000);
                }
            }
        }
        if (toast != null && nowMillis > toastUntilMillis + 400) {
            toast = null;
        }
    }

    /** Routine bookkeeping (gear changes, slope, door switch ...) is not worth interrupting the driver for. */
    static boolean isPopUpWorthy(Notification n) {
        if (n.getSeverity() != Severity.INFO) {
            return true;
        }
        String m = n.getMessage();
        return m.startsWith("ENGINE STARTED") || m.startsWith("LAUNCH CONTROL READY") || m.startsWith("LAUNCH EXECUTED")
                || m.startsWith("VEHICLE LIFT ACTIVE") || m.startsWith("ESC INTERVENTION") || m.startsWith("ABS INTERVENTION")
                || m.startsWith("BRAKE ASSIST") || m.startsWith("HILL HOLD") || m.startsWith("SYSTEM CHECK COMPLETE");
    }

    /** 0..1 opacity of the pop-up (fades out during the last 400 ms). */
    public double getToastOpacity(long nowMillis) {
        if (toast == null) {
            return 0;
        }
        if (nowMillis <= toastUntilMillis) {
            return 1.0;
        }
        return Math.max(0.0, 1.0 - (nowMillis - toastUntilMillis) / 400.0);
    }

    public void nextPage() { page = (page + 1) % PAGE_NAMES.length; }
    public void previousPage() { page = (page + PAGE_NAMES.length - 1) % PAGE_NAMES.length; }
    public void setPage(int page) { this.page = Math.floorMod(page, PAGE_NAMES.length); }
    public int getPage() { return page; }

    public TelemetrySnapshot getTelemetry() { return telemetry; }
    public double getDisplayRpm() { return displayRpm; }
    public double getDisplaySpeed() { return displaySpeed; }
    public double getStyle() { return style; }
    public double getWake() { return wake; }
    public double getClock() { return clock; }
    public Notification getToast() { return toast; }
    public boolean isLongTermTrip() { return longTermTrip; }
}
