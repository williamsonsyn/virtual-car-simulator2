package hyperdrive;

import hyperdrive.enums.FaultType;
import hyperdrive.enums.GearPosition;
import hyperdrive.exceptions.OperationDeniedException;
import hyperdrive.model.Car;
import hyperdrive.model.Fault;
import hyperdrive.modes.DriveMode;
import hyperdrive.modes.SportMode;
import hyperdrive.modes.TrackMode;
import hyperdrive.systems.Tyre;

/**
 * Console demo for Step 2. No UI yet - this proves the OOP core makes the decisions.
 * The JavaFX screens (step 10) will call the same Car methods.
 */
public class Main {

    public static void main(String[] args) {
        Car car = new Car();
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
        car.setThrottle(0.7);
        run(car, 6);
        status(car);
        System.out.println("> Try P while moving");
        attemptShift(car, GearPosition.P);
        System.out.println("> Upshift to 2nd, then 3rd");
        attemptUpshift(car);
        run(car, 1);
        attemptUpshift(car);
        run(car, 5);
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

        title("System status");
        for (String line : car.getSystemStatusLines()) {
            System.out.println("   " + line);
        }

        title("Session events (these will go to a log file in a later step)");
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

    private static void title(String text) {
        System.out.println();
        System.out.println("=== " + text + " ===");
    }
}
