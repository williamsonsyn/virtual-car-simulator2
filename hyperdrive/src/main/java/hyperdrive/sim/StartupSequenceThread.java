package hyperdrive.sim;

import hyperdrive.enums.PowerState;
import hyperdrive.enums.StartupPhase;
import hyperdrive.model.Car;
import java.util.List;

/**
 * Narrates the ignition / start sequence on the console while the car goes through it.
 * This EXTENDS Thread directly (unlike SimulationEngine) because it genuinely is one single task with its own
 * identity and lifetime - it starts when the driver switches the ignition on and ends once the engine is running
 * (or the car goes back to sleep). The sequence itself is NOT driven from here: the Car owns the timeline and the
 * SimulationEngine advances it. This thread only watches the Car's StartupPhase and reports every change, then prints
 * the diagnostic report - so it can never disturb the simulation.
 */
public class StartupSequenceThread extends Thread {
    private static final long POLL_MILLIS = 100;
    private static final long TIMEOUT_MILLIS = 30_000;

    private final Car car;

    public StartupSequenceThread(Car car) {
        super("Startup-Sequence");   // naming the thread helps if you ever inspect a thread dump
        this.car = car;
    }

    @Override
    public void run() {
        System.out.println("HYPERDRIVE X-01 - ignition sequence...");
        StartupPhase last = null;
        long deadline = System.currentTimeMillis() + TIMEOUT_MILLIS;
        while (System.currentTimeMillis() < deadline && !isInterrupted()) {
            PowerState power = car.getTelemetry().getPowerState();
            StartupPhase phase = car.getTelemetry().getStartupPhase();
            if (phase != last && phase.isVisible()) {
                System.out.println("  " + phase.getLabel());
                last = phase;
            }
            if (power == PowerState.SLEEP && last != null) {
                break;   // ignition switched off again
            }
            if (power == PowerState.ENGINE_RUNNING && phase == StartupPhase.NONE) {
                break;   // started and the banner has cleared
            }
            sleepQuietly();
        }
        List<String> report = car.getDiagnosticReport();
        for (String line : report) {
            System.out.println("  " + line);
        }
        System.out.println("Start-up sequence finished.");
    }

    private void sleepQuietly() {
        try {
            Thread.sleep(POLL_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
