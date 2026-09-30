package hyperdrive.sim;

import hyperdrive.model.Car;
import java.util.List;

/**
 * A short, one-shot animated boot sequence. This EXTENDS Thread directly, unlike SimulationEngine,
 * because it genuinely is a single task with its own identity and lifetime - not a recurring job that
 * something else schedules repeatedly. Whoever wants this boot animation just makes one and starts it.
 */
public class StartupSequenceThread extends Thread {
    private static final long LINE_DELAY_MILLIS = 150;

    private final Car car;

    public StartupSequenceThread(Car car) {
        super("Startup-Sequence");   // naming the thread helps if you ever inspect a thread dump
        this.car = car;
    }

    @Override
    public void run() {
        System.out.println("HYPERDRIVE X-01 - booting...");
        List<String> report = car.getDiagnosticReport();
        for (String line : report) {
            System.out.println("  " + line);
            sleepQuietly();
        }
        System.out.println("Boot sequence complete.");
    }

    private void sleepQuietly() {
        try {
            Thread.sleep(LINE_DELAY_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
