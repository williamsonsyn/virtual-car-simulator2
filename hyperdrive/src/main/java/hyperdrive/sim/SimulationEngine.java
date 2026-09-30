package hyperdrive.sim;

import hyperdrive.model.Car;

/**
 * Runs the simulation continuously in real time: repeatedly calls Car.update(dt) at a fixed rate,
 * on its own background thread. Car's public methods are all synchronized, so another thread (the
 * console demo's main thread today, a JavaFX UI thread later) can safely call them at the same time
 * this thread is ticking - as long as BOTH sides only touch Car through its synchronized methods
 * (see the caveat on Car.getEngine() and friends).
 *
 * This class only knows about Car.update(dt). It has no idea what a console, a thread, or a UI is -
 * whoever starts it (Main today, the UI in a later step) decides what runs it and how often.
 * That's why it implements Runnable rather than extending Thread: it's a recurring JOB, not a thread
 * with its own identity - something else supplies the thread (see Main, and compare to
 * StartupSequenceThread, which genuinely IS its own one-shot thread).
 */
public class SimulationEngine implements Runnable {
    private static final long TICK_MILLIS = 20;         // ~50 updates per second
    private static final double MAX_DT_SECONDS = 0.1;    // clamp a stalled tick so the car doesn't "jump"

    private final Car car;
    private volatile boolean running = true;   // volatile: the stop() call comes from a different thread

    public SimulationEngine(Car car) {
        this.car = car;
    }

    @Override
    public void run() {
        long lastTick = System.nanoTime();
        while (running) {
            long now = System.nanoTime();
            double dt = (now - lastTick) / 1_000_000_000.0;
            lastTick = now;
            dt = Math.min(dt, MAX_DT_SECONDS);   // a slow tick (GC pause, OS scheduling) must not cause a jump

            car.update(dt);

            try {
                Thread.sleep(TICK_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();   // restore the interrupt flag for anyone checking it
                running = false;
            }
        }
    }

    /** Signals the loop to stop after its current tick. Does not block - call join() on the Thread to wait. */
    public void stop() { running = false; }

    public boolean isRunning() { return running; }
}
