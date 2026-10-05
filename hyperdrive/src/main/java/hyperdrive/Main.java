package hyperdrive;

import hyperdrive.enums.FaultType;
import hyperdrive.enums.GearPosition;
import hyperdrive.exceptions.OperationDeniedException;
import hyperdrive.io.LogReader;
import hyperdrive.io.Logger;
import hyperdrive.model.Car;
import hyperdrive.model.Fault;
import hyperdrive.model.Notification;
import hyperdrive.modes.ComfortMode;
import hyperdrive.modes.DriveMode;
import hyperdrive.modes.SportMode;
import hyperdrive.modes.TrackMode;
import hyperdrive.sim.SimulationEngine;
import hyperdrive.sim.StartupSequenceThread;
import hyperdrive.systems.Tyre;
import hyperdrive.telemetry.TelemetrySnapshot;
import java.util.List;

/**
 * Console demo for Step 2. No UI yet - this proves the OOP core makes the decisions.
 * The JavaFX screens (step 10) will call the same Car methods.
 */
public class Main {

    public static void main(String[] args) {
        Logger logger = new Logger("logs/hyperdrive-session.log");
        try {
            logger.open();
            runDemo(logger);
        } finally {
            logger.close();   // always closes, even if something above throws
        }
    }

    private static void runDemo(Logger logger) {
        Car car = new Car(logger);   // constructor overload: this session's events are logged from line one

        StartupSequenceThread boot = new StartupSequenceThread(car);
        boot.start();
        try {
            boot.join();   // wait for the boot animation to finish before the demo continues
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        System.out.println();
        title("HYPERDRIVE X-01 - console demo (educational simulator)");

        title("1. Start while the car is asleep");
        attemptStart(car);

        title("2. Power on, then try to start WITHOUT pressing the brake");
        attemptPowerOn(car);
        for (String line : car.getDiagnosticReport()) {
            System.out.println("   " + line);
        }
        attemptStart(car);

        title("3. Press the brake and start");
        car.pressBrake(1.0);
        attemptStart(car);
        run(car, 20);
        status(car);

        title("4. Drive: shift rules and the speed model");
        attemptShift(car, GearPosition.G1);
        System.out.println("> UPSHIFT immediately after shifting");
        attemptUpshift(car);
        run(car, 1);
        car.releaseBrake();
        car.setThrottle(1.0);
        run(car, 6);
        status(car);
        System.out.println("> Try P while moving");
        attemptShift(car, GearPosition.P);
        System.out.println("> Upshift to 2nd, then 3rd");
        attemptUpshift(car);
        run(car, 1);
        attemptUpshift(car);
        run(car, 7);
        status(car);
        System.out.println("> Downshift at 100+ km/h (over-rev protection)");
        attemptDownshift(car);

        title("5. Stop, park, engine off");
        car.setThrottle(0.0);
        car.pressBrake(1.0);
        run(car, 12);
        status(car);
        attemptShift(car, GearPosition.P);
        attemptStop(car);
        car.releaseBrake();

        title("6. Faults block the start (Low Fuel Pressure)");
        car.injectFault(FaultType.LOW_FUEL_PRESSURE);
        car.pressBrake(1.0);
        attemptStart(car);

        title("7. Several faults at once -> ALL reasons are reported");
        car.injectFault(FaultType.LOW_BATTERY);
        car.injectFault(FaultType.OVERHEATING);
        car.injectFault(FaultType.LOW_BRAKE_PRESSURE);
        attemptStart(car);
        System.out.println("Active faults (copies):");
        for (Fault f : car.getActiveFaults()) {
            System.out.println("   " + f);
        }

        title("8. Clear the faults and start again");
        car.clearFault(FaultType.LOW_FUEL_PRESSURE);
        car.clearFault(FaultType.LOW_BATTERY);
        car.clearFault(FaultType.OVERHEATING);
        car.clearFault(FaultType.LOW_BRAKE_PRESSURE);
        attemptStart(car);

        title("9. Copy constructor: snapshot vs live tyres");
        Tyre[] snapshot = car.getTyreSnapshot();
        car.injectFault(FaultType.LOW_TYRE_PRESSURE);
        Tyre[] live = car.getTyreSnapshot();
        System.out.println("   snapshot FL: " + snapshot[0] + "   (taken before the fault, unchanged)");
        System.out.println("   now      FL: " + live[0] + "   low pressure? " + live[0].isPressureLow());

        title("10. Driving modes");
        System.out.println("   current mode: " + car.getCurrentMode());
        System.out.println("> Try SPORT while stationary but engine off (car is off after step 8's cycle - power on)");
        car.pressBrake(1.0);
        attemptPowerOn(car);
        attemptStart(car);
        attemptMode(car, new SportMode());
        System.out.println("> Drive above the mode-change speed limit, then try TRACK");
        run(car, 1);            // let the previous shift (parking, section 5) finish settling
        car.pressBrake(1.0);    // required to leave PARK
        attemptShift(car, GearPosition.G1);
        car.releaseBrake();
        car.setThrottle(0.6);
        run(car, 4);
        status(car);
        attemptMode(car, new TrackMode());
        System.out.println("> Slow down and try TRACK again, but inject a fault first");
        car.setThrottle(0.0);
        car.pressBrake(1.0);
        run(car, 8);
        status(car);
        car.injectFault(FaultType.LOW_TYRE_PRESSURE);
        attemptMode(car, new TrackMode());
        System.out.println("> Clear the fault and try TRACK again");
        car.clearFault(FaultType.LOW_TYRE_PRESSURE);
        attemptMode(car, new TrackMode());
        System.out.println("   current mode: " + car.getCurrentMode());
        System.out.println("> Compare throttle response: Comfort vs Track, same throttle, same time");
        compareThrottleResponse();

        title("11. Airbrake, Vehicle Lift, ESC, Launch Control");
        System.out.println("   current mode: " + car.getCurrentMode());
        System.out.println("> DEPLOY AIRBRAKE while stationary (too slow for airflow)");
        attemptAirbrake(car, true);

        System.out.println("> RAISE LIFT while in Track mode");
        attemptLift(car, true);

        System.out.println("> Switch to Comfort mode, then raise the lift");
        attemptMode(car, new ComfortMode());
        attemptLift(car, true);
        run(car, 2);
        System.out.println("   lift state: " + car.getLiftState());

        System.out.println("> Drive off - the lift should auto-lower above 40 km/h");
        System.out.println("   (ESC is still on here, so it's cutting wheelspin - this will take a bit)");
        car.releaseBrake();
        car.setThrottle(1.0);
        run(car, 25);
        status(car);
        System.out.println("   lift state: " + car.getLiftState());

        System.out.println("> Slow back down to a stop");
        car.setThrottle(0.0);
        car.pressBrake(1.0);
        run(car, 8);
        status(car);

        System.out.println("> Try ESC OFF in Comfort mode (denied)");
        attemptEsc(car, false);

        System.out.println("> Switch to Track mode, then ESC OFF");
        attemptMode(car, new TrackMode());
        attemptEsc(car, false);

        title("Launch Control");
        System.out.println("> Request launch before getting into position");
        attemptLaunch(car);

        System.out.println("> Get into position: gear 1, full brake, full throttle");
        car.pressBrake(1.0);
        car.setThrottle(1.0);
        System.out.printf("   gear %s | brake full | throttle full | speed %.0f km/h%n",
                car.getTransmission().getGear().getLabel(), car.getSpeedKmh());
        attemptLaunch(car);
        System.out.printf("   launch state: %s | boost %.0f%%%n", car.getLaunchState(), car.getLaunchBoostPercent());
        run(car, 1);
        System.out.printf("   after 1s -> state: %s | boost %.0f%%%n", car.getLaunchState(), car.getLaunchBoostPercent());
        run(car, 1);
        System.out.printf("   after 2s -> state: %s | boost %.0f%%%n", car.getLaunchState(), car.getLaunchBoostPercent());

        System.out.println("> Release the brake to execute the launch");
        double speedBefore = car.getSpeedKmh();
        car.releaseBrake();
        System.out.printf("   speed %.0f -> %.0f km/h | launch state: %s%n",
                speedBefore, car.getSpeedKmh(), car.getLaunchState());
        run(car, 2);
        System.out.println("   launch state settles back to: " + car.getLaunchState());

        System.out.println("> Abort example: come to a stop, request again, then ease off the throttle mid-arm");
        car.setThrottle(0.0);
        car.pressBrake(1.0);
        run(car, 10);
        status(car);
        car.setThrottle(1.0);
        attemptLaunch(car);
        car.setThrottle(0.3);
        run(car, 1);
        System.out.println("   launch state after easing off the throttle: " + car.getLaunchState());

        title("System status");
        for (String line : car.getSystemStatusLines()) {
            System.out.println("   " + line);
        }

        title("12. Notifications");
        System.out.println("   total notifications this session: " + car.getNotifications().size());
        System.out.println("   warnings and critical only:");
        for (Notification n : car.getWarnings()) {
            System.out.println("      " + n);
        }

        title("13. Reading the log file back (history screen)");
        System.out.println("   log file: " + logger.getFilePath());
        List<String> lastLines = LogReader.readLastLines(logger.getFilePath(), 12);
        System.out.println("   last " + lastLines.size() + " lines on disk right now:");
        for (String line : lastLines) {
            System.out.println("      " + line);
        }

        title("14. Live simulation thread (SimulationEngine)");
        System.out.println("   a fresh car, driven by a real background thread - no manual update() calls below");
        Car liveCar = new Car(logger);
        try {
            liveCar.powerOn();
            liveCar.pressBrake(1.0);
            liveCar.startEngine();
            liveCar.shiftGear(GearPosition.G1);
            liveCar.releaseBrake();
            liveCar.setThrottle(0.6);
        } catch (OperationDeniedException e) {
            printDenied(e);
        }

        SimulationEngine engine = new SimulationEngine(liveCar);
        Thread simThread = new Thread(engine, "Simulation-Thread");
        simThread.start();

        System.out.println("   main thread sleeping for 1s while the background thread drives the car...");
        sleepQuietly(1000);
        System.out.printf("   after 1s real time -> speed %.0f km/h | rpm %.0f | fuel %.1f%%%n",
                liveCar.getSpeedKmh(), liveCar.getRpm(), liveCar.getFuelLevelPercent());

        engine.stop();
        try {
            simThread.join();   // block until the background thread has actually exited
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        System.out.println("   simulation thread stopped (joined cleanly)");

        double speedAtStop = liveCar.getSpeedKmh();
        sleepQuietly(300);
        System.out.printf("   0.3s after stopping -> speed still %.0f km/h%n", liveCar.getSpeedKmh());
        System.out.println("   " + (liveCar.getSpeedKmh() == speedAtStop
                ? "confirmed: nothing is ticking the car anymore, the thread really did stop"
                : "unexpected: speed changed after stop() - investigate"));

        title("15. Telemetry snapshots");
        System.out.println("   another car, driving itself, sampled with one getTelemetry() call per row -");
        System.out.println("   this is exactly what a dashboard refresh will do every frame in the UI step");
        Car telemetryCar = new Car(logger);
        try {
            telemetryCar.powerOn();
            telemetryCar.pressBrake(1.0);
            telemetryCar.startEngine();
            telemetryCar.shiftGear(GearPosition.G1);
            telemetryCar.releaseBrake();
            telemetryCar.setThrottle(0.7);
        } catch (OperationDeniedException e) {
            printDenied(e);
        }

        SimulationEngine telemetryEngine = new SimulationEngine(telemetryCar);
        Thread telemetryThread = new Thread(telemetryEngine, "Telemetry-Sim-Thread");
        telemetryThread.start();

        for (int i = 0; i < 4; i++) {
            sleepQuietly(250);
            TelemetrySnapshot snap = telemetryCar.getTelemetry();
            System.out.println("      " + snap);
        }

        telemetryEngine.stop();
        try {
            telemetryThread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        System.out.println("   telemetry thread stopped");

        TelemetrySnapshot finalSnap = telemetryCar.getTelemetry();
        System.out.println("   tyres from that final snapshot (already copies - safe to hand straight to a UI):");
        for (Tyre t : finalSnap.getTyres()) {
            System.out.println("      " + t);
        }

        title("16. Critical-fault shutdown countdown");
        Car faultCar = new Car(logger);
        try {
            faultCar.powerOn();
            faultCar.pressBrake(1.0);
            faultCar.startEngine();
        } catch (OperationDeniedException e) {
            printDenied(e);
        }
        System.out.println("   engine running, no faults -> injecting OVERHEATING (critical)");
        faultCar.injectFault(FaultType.OVERHEATING);
        for (int i = 0; i < 3; i++) {
            run(faultCar, 2);
            System.out.printf("   after %ds -> engine running: %b | countdown: %.0fs%n",
                    (i + 1) * 2, faultCar.getEngine().isRunning(), faultCar.getCriticalFaultCountdown());
        }
        System.out.println("   clearing the fault before time runs out...");
        faultCar.clearFault(FaultType.OVERHEATING);
        run(faultCar, 1);
        System.out.printf("   after clearing -> engine running: %b | countdown: %.0fs (cancelled)%n",
                faultCar.getEngine().isRunning(), faultCar.getCriticalFaultCountdown());

        System.out.println("   now injecting it again and NOT clearing it this time...");
        faultCar.injectFault(FaultType.OVERHEATING);
        run(faultCar, 11);   // past the 10s shutdown threshold
        System.out.printf("   after 11s uncleared -> engine running: %b | countdown: %.0fs%n",
                faultCar.getEngine().isRunning(), faultCar.getCriticalFaultCountdown());

        System.out.println("   trying to restart while the fault is still active...");
        try {
            faultCar.pressBrake(1.0);
            faultCar.startEngine();
            System.out.println("   UNEXPECTED: engine started with a critical fault active");
        } catch (OperationDeniedException e) {
            printDenied(e);
        }

        System.out.println("   clearing the fault and restarting...");
        faultCar.clearFault(FaultType.OVERHEATING);
        try {
            faultCar.startEngine();
            System.out.println("   engine restarted - running: " + faultCar.getEngine().isRunning());
        } catch (OperationDeniedException e) {
            printDenied(e);
        }

        title("Session events (in-memory view - full history is in the log file above)");
        for (String e : car.getEvents()) {
            System.out.println("   " + e);
        }
    }

    // ---------------------------------------------------------------- helpers

    private static void attemptPowerOn(Car car) {
        System.out.println("> POWER ON");
        try {
            car.powerOn();
            System.out.println("   awake");
        } catch (OperationDeniedException e) {
            printDenied(e);
        }
    }

    private static void attemptStart(Car car) {
        System.out.println("> START ENGINE");
        try {
            car.startEngine();
            System.out.println("   ENGINE STARTED");
        } catch (OperationDeniedException e) {
            printDenied(e);
        } finally {
            System.out.println("   [state: " + car.getPowerState() + "]");   // runs whether it worked or not
        }
    }

    private static void attemptStop(Car car) {
        System.out.println("> STOP ENGINE");
        try {
            car.stopEngine();
            System.out.println("   ENGINE STOPPED");
        } catch (OperationDeniedException e) {
            printDenied(e);
        }
    }

    private static void attemptShift(Car car, GearPosition target) {
        System.out.println("> SHIFT TO " + target.getLabel());
        try {
            car.shiftGear(target);
            System.out.println("   gear is now " + car.getTransmission().getGear().getLabel());
        } catch (OperationDeniedException e) {
            printDenied(e);
        }
    }

    private static void attemptUpshift(Car car) {
        try {
            car.shiftUp();
            System.out.println("   gear is now " + car.getTransmission().getGear().getLabel());
        } catch (OperationDeniedException e) {
            printDenied(e);
        }
    }

    private static void attemptDownshift(Car car) {
        try {
            car.shiftDown();
            System.out.println("   gear is now " + car.getTransmission().getGear().getLabel());
        } catch (OperationDeniedException e) {
            printDenied(e);
        }
    }

    private static void attemptMode(Car car, DriveMode target) {
        System.out.println("> SELECT " + target.getName().toUpperCase() + " MODE");
        try {
            car.selectMode(target);
            System.out.println("   mode is now " + car.getCurrentMode());
        } catch (OperationDeniedException e) {
            printDenied(e);
        }
    }

    /** Two fresh cars, one left in Comfort and one switched to Track, same throttle input. */
    private static void compareThrottleResponse() {
        Car comfortCar = new Car();
        Car trackCar = new Car();
        try {
            comfortCar.pressBrake(1.0);
            comfortCar.powerOn();
            comfortCar.startEngine();

            trackCar.pressBrake(1.0);
            trackCar.powerOn();
            trackCar.startEngine();
            trackCar.selectMode(new TrackMode());
        } catch (OperationDeniedException e) {
            printDenied(e);
            return;
        }
        comfortCar.setThrottle(1.0);
        trackCar.setThrottle(1.0);
        for (int i = 0; i < 5; i++) {
            comfortCar.update(0.1);
            trackCar.update(0.1);
        }
        System.out.printf("   after 0.5 s full throttle - Comfort rpm %.0f  |  Track rpm %.0f%n",
                comfortCar.getEngine().getRpm(), trackCar.getEngine().getRpm());
    }

    private static void attemptAirbrake(Car car, boolean deploy) {
        System.out.println("> " + (deploy ? "DEPLOY" : "RETRACT") + " AIRBRAKE");
        try {
            if (deploy) {
                car.deployAirbrake();
            } else {
                car.retractAirbrake();
            }
            System.out.println("   airbrake state: " + car.getAirbrakeState());
        } catch (OperationDeniedException e) {
            printDenied(e);
        }
    }

    private static void attemptLift(Car car, boolean raise) {
        System.out.println("> " + (raise ? "RAISE" : "LOWER") + " LIFT");
        try {
            if (raise) {
                car.raiseLift();
            } else {
                car.lowerLift();
            }
            System.out.println("   lift state: " + car.getLiftState());
        } catch (OperationDeniedException e) {
            printDenied(e);
        }
    }

    private static void attemptEsc(Car car, boolean enabled) {
        System.out.println("> ESC " + (enabled ? "ON" : "OFF"));
        try {
            car.setEscEnabled(enabled);
            System.out.println("   ESC enabled: " + car.isEscEnabled());
        } catch (OperationDeniedException e) {
            printDenied(e);
        }
    }

    private static void attemptLaunch(Car car) {
        System.out.println("> REQUEST LAUNCH");
        try {
            car.requestLaunch();
            System.out.println("   launch armed");
        } catch (OperationDeniedException e) {
            printDenied(e);
        }
    }

    private static void printDenied(OperationDeniedException e) {
        System.out.println("   " + e.getOperation() + " DENIED");
        for (String reason : e.getReasons()) {
            System.out.println("    - " + reason);
        }
    }

    /** Advance the simulation by 'seconds' in 0.1 s steps (the SimulationEngine thread will do this later). */
    private static void run(Car car, int seconds) {
        for (int i = 0; i < seconds * 10; i++) {
            car.update(0.1);
        }
    }

    private static void status(Car car) {
        System.out.printf("   speed %.0f km/h | gear %s | rpm %.0f | fuel %.1f%% | coolant %.0f C | oil %.0f C | brake disc %.0f C%n",
                car.getSpeedKmh(),
                car.getTransmission().getGear().getLabel(),
                car.getEngine().getRpm(),
                car.getFuel().getLevelPercent(),
                car.getCooling().getCoolantTemp(),
                car.getCooling().getOilTemp(),
                car.getBrakes().getDiscTemperature());
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void title(String text) {
        System.out.println();
        System.out.println("=== " + text + " ===");
    }
}
