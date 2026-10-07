package hyperdrive;

import hyperdrive.enums.FaultType;
import hyperdrive.enums.GearPosition;
import hyperdrive.enums.LaunchState;
import hyperdrive.exceptions.OperationDeniedException;
import hyperdrive.io.LogReader;
import hyperdrive.io.Logger;
import hyperdrive.model.Car;
import hyperdrive.model.Fault;
import hyperdrive.model.Notification;
import hyperdrive.modes.SportMode;
import hyperdrive.modes.TrackMode;
import hyperdrive.sim.SimulationEngine;
import hyperdrive.sim.StartupSequenceThread;
import hyperdrive.systems.Tyre;
import hyperdrive.telemetry.TelemetrySnapshot;
import java.util.List;

/**
 * Console demo of the OOP core (no JavaFX needed). It drives the same Car methods the cockpit calls, so every rule
 * you see denied here is a rule inside the vehicle model - not something the UI decides.
 * The JavaFX cockpit lives in hyperdrive.ui.HyperDriveApp.
 */
public class Main {
    private static final double DT = 0.02;

    public static void main(String[] args) {
        Logger logger = new Logger("logs/hyperdrive-console.log");
        try {
            logger.open();
            runDemo(logger);
        } finally {
            logger.close();   // always closes, even if something above throws
        }
    }

    private static void runDemo(Logger logger) {
        Car car = new Car(logger);   // constructor overload: events are logged from the first line
        title("HYPERDRIVE X-01 - console demo (educational 720S-inspired simulator)");

        title("1. Ignition, self-test and engine start (real time: SimulationEngine thread + StartupSequenceThread)");
        SimulationEngine engine = new SimulationEngine(car);
        Thread simThread = new Thread(engine, "Simulation-Thread");
        simThread.start();
        attempt("START while asleep", car::startEngine);
        attempt("POWER ON", car::powerOn);
        StartupSequenceThread boot = new StartupSequenceThread(car);
        boot.start();
        sleep(4500);                                   // the self-test takes about four seconds
        attempt("START without the brake pedal", car::startEngine);
        car.pressBrake(1.0);
        attempt("START with the brake pressed", car::startEngine);
        sleep(3500);                                   // cranking -> RPM rise -> oil pressure -> running
        try {
            boot.join(8000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        engine.stop();
        try {
            simThread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        status(car);

        title("2. Gearbox: P -> 1, AUTO shifting, gear rules");
        car.preWarm();   // skip the minutes of warm-up
        attempt("SHIFT to 1", () -> car.shiftGear(GearPosition.G1));
        run(car, 1);
        attempt("UPSHIFT right after shifting", car::shiftUp);
        car.releaseBrake();
        car.setThrottle(1.0);
        run(car, 8);
        status(car);
        attempt("PARK while moving", () -> car.shiftGear(GearPosition.P));
        attempt("REVERSE while moving", () -> car.shiftGear(GearPosition.R));

        title("3. Braking: ABS and Brake Assist (pedal stamped on)");
        car.setThrottle(0.0);
        car.pressBrake(1.0, true);
        run(car, 0.6);
        TelemetrySnapshot t = car.getTelemetry();
        System.out.printf("   brake state %s | ABS %b | Brake Assist %b | %.2f g | disc %.0f C | airbrake %s%n",
                t.getBrakeState(), t.isAbsActive(), t.isBrakeAssistActive(), t.getLongitudinalG(),
                t.getBrakeDiscTemp(), t.getAirbrakeState());
        run(car, 10);
        status(car);
        car.releaseBrake();

        title("4. Driving modes: Handling / Powertrain / Active Dynamics, ESC rules");
        car.pressBrake(1.0);
        attempt("ESC TRACK DYNAMIC without Active", car::cycleEscMode);
        attempt("SELECT SPORT", () -> car.selectMode(new SportMode()));
        attempt("SELECT TRACK", () -> car.selectMode(new TrackMode()));
        attempt("ESC MODE (next)", car::cycleEscMode);
        attempt("ESC MODE (next)", car::cycleEscMode);
        attempt("ESC MODE (next -> OFF)", car::cycleEscMode);
        System.out.println("   ESC: " + car.getEscMode() + " | H " + car.getHandlingMode().getLabel()
                + " / P " + car.getPowertrainMode().getLabel() + " | " + car.getActiveState());
        attempt("ESC ON again", () -> car.setEscEnabled(true));

        title("5. Vehicle lift and Launch Control");
        car.releaseBrake();
        attempt("SHIFT to N", () -> { car.pressBrake(1.0); run(car, 0.2); car.shiftGear(GearPosition.N); });
        run(car, 1);
        attempt("SHIFT to 1", () -> car.shiftGear(GearPosition.G1));
        run(car, 1);
        attempt("RAISE LIFT in Track", car::raiseLift);
        run(car, 4.5);
        attempt("LAUNCH CONTROL", car::requestLaunch);
        car.setThrottle(1.0);
        run(car, 3.5);
        System.out.printf("   launch state %s | boost %.0f%% | %.0f rpm%n", car.getLaunchState(),
                car.getLaunchBoostPercent(), car.getRpm());
        car.releaseBrake();
        double timeTo100 = 0;
        for (int i = 0; i < 500 && car.getSpeedKmh() < 100; i++) {
            car.update(DT);
            timeTo100 += DT;
        }
        System.out.printf("   0-100 km/h after release: %.1f s (launch state %s)%n", timeTo100, car.getLaunchState());
        run(car, 4);

        title("6. Faults -> real system changes, diagnostics, notifications");
        car.setThrottle(0.0);
        car.pressBrake(1.0);
        run(car, 14);
        car.injectFault(FaultType.LOW_OIL_PRESSURE);
        car.injectFault(FaultType.LOW_TYRE_PRESSURE);
        car.injectFault(FaultType.ABS_FAULT);
        run(car, 1.5);
        t = car.getTelemetry();
        System.out.printf("   oil %.2f bar, RPM limit %.0f, warnings %d, diagnostics %s, shutdown in %.0fs%n",
                t.getOilPressure(), t.getRpmLimit(), t.getWarningCount(), car.getDiagnosticLevel(),
                car.getCriticalFaultCountdown());
        for (String line : car.getDiagnosticReport()) {
            System.out.println("   " + line);
        }
        System.out.println("   active faults (copies):");
        for (Fault f : car.getActiveFaults()) {
            System.out.println("     " + f);
        }
        car.clearFault(FaultType.LOW_OIL_PRESSURE);
        car.clearFault(FaultType.LOW_TYRE_PRESSURE);
        car.clearFault(FaultType.ABS_FAULT);
        run(car, 1);
        System.out.println("   after clearing: diagnostics " + car.getDiagnosticLevel());

        title("7. Copy constructor: tyre snapshot vs live tyres");
        Tyre[] snapshot = car.getTyreSnapshot();
        car.injectFault(FaultType.LOW_TYRE_PRESSURE);
        run(car, 0.5);
        Tyre[] live = car.getTyreSnapshot();
        System.out.printf("   snapshot FL %.1f bar (%s) | live FL %.1f bar (%s)%n", snapshot[0].getPressure(),
                snapshot[0].getCondition(), live[0].getPressure(), live[0].getCondition());
        car.clearFault(FaultType.LOW_TYRE_PRESSURE);

        title("8. Stop, engine off (auto PARK + parking brake) and the trip computer");
        run(car, 5);
        car.releaseBrake();
        attempt("STOP ENGINE", car::stopEngine);
        t = car.getTelemetry();
        System.out.printf("   gear %s | EPB %s | trip %.2f km, %s, avg %.0f km/h, %.1f L/100 km%n", t.getGear().getLabel(),
                t.getEpbState(), t.getTripSinceStart().getDistanceKm(), t.getTripSinceStart().getTimeText(),
                t.getTripSinceStart().getAverageSpeedKmh(), t.getTripSinceStart().getAverageConsumptionL100());
        attempt("POWER OFF", car::powerOff);

        title("9. Notifications and the log file");
        List<Notification> warnings = car.getWarnings();
        System.out.println("   " + warnings.size() + " warning/critical notifications this session, e.g.:");
        for (int i = 0; i < Math.min(5, warnings.size()); i++) {
            System.out.println("     " + warnings.get(i));
        }
        logger.close();
        List<String> last = LogReader.readLastLines(logger.getFilePath(), 5);
        System.out.println("   last lines of " + logger.getFilePath() + ":");
        for (String line : last) {
            System.out.println("     " + line);
        }
        title("End of demo. Launch states reachable: " + java.util.Arrays.toString(LaunchState.values()));
    }

    // ------------------------------------------------------------------ small helpers

    @FunctionalInterface
    private interface CarAction {
        void run() throws OperationDeniedException;
    }

    private static void attempt(String label, CarAction action) {
        try {
            action.run();
            System.out.println("> " + label + " - OK");
        } catch (OperationDeniedException e) {
            System.out.println("> " + label + " - DENIED: " + String.join("; ", e.getReasons()));
        }
    }

    /** Steps the simulation manually (fast, deterministic) - the real-time thread is shown in section 1. */
    private static void run(Car car, double seconds) {
        for (int i = 0; i < Math.round(seconds / DT); i++) {
            car.update(DT);
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void status(Car car) {
        System.out.println("   " + car.getTelemetry());
    }

    private static void title(String text) {
        System.out.println();
        System.out.println("=== " + text + " ===");
    }
}
