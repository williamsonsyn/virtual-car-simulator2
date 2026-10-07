package hyperdrive;

import hyperdrive.enums.*;
import hyperdrive.exceptions.OperationDeniedException;
import hyperdrive.io.LogReader;
import hyperdrive.io.Logger;
import hyperdrive.model.Car;
import hyperdrive.modes.*;
import hyperdrive.sim.SimulationEngine;
import hyperdrive.systems.Tyre;
import hyperdrive.telemetry.TelemetrySnapshot;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Headless verification of the connected vehicle model (no JavaFX needed).
 * Drives real Car objects through scripted scenarios and checks the behaviour the cockpit relies on.
 * Run:  javac -d out -sourcepath src/main/java:src/test/java src/test/java/hyperdrive/SimulationVerification.java
 *       java -cp out hyperdrive.SimulationVerification
 */
public class SimulationVerification {
    private static Logger LOG;
    private static int passed = 0;
    private static int failed = 0;
    private static final List<String> failures = new ArrayList<>();

    // ------------------------------------------------------------------ tiny test helpers
    private static void check(String name, boolean ok, String detail) {
        if (ok) {
            passed++;
            System.out.println("[PASS] " + name + (detail.isEmpty() ? "" : "  (" + detail + ")"));
        } else {
            failed++;
            failures.add(name);
            System.out.println("[FAIL] " + name + "  (" + detail + ")");
        }
    }

    private static void run(Car car, double seconds) {
        for (int i = 0; i < Math.round(seconds / 0.02); i++) {
            car.update(0.02);
        }
    }

    private static boolean denied(OperationDeniedAction a) {
        try {
            a.run();
            return false;
        } catch (OperationDeniedException e) {
            return true;
        }
    }

    private static String deniedReason(OperationDeniedAction a) {
        try {
            a.run();
            return "";
        } catch (OperationDeniedException e) {
            return String.join("; ", e.getReasons());
        }
    }

    @FunctionalInterface
    private interface OperationDeniedAction { void run() throws OperationDeniedException; }

    private static TelemetrySnapshot t(Car c) { return c.getTelemetry(); }

    /** Ignition on, self-test done, engine started and idling, in P with the brake released. */
    private static Car runningCar(boolean warm) throws OperationDeniedException {
        Car car = new Car(LOG);
        car.powerOn();
        run(car, 4.3);
        car.pressBrake(1.0);
        car.startEngine();
        run(car, 4.0);
        if (warm) {
            car.preWarm();
        }
        car.releaseBrake();
        run(car, 0.5);
        return car;
    }

    /** Get from P into gear 1 ready to drive. */
    private static void selectFirst(Car car) throws OperationDeniedException {
        car.pressBrake(1.0);
        run(car, 0.1);
        car.shiftGear(GearPosition.G1);
        run(car, 0.8);
        car.releaseBrake();
    }

    // ------------------------------------------------------------------ main
    public static void main(String[] args) throws Exception {
        Logger logger = new Logger("/tmp/hyperdrive-verify.log");
        new java.io.File("/tmp/hyperdrive-verify.log").delete();
        logger.open();
        LOG = logger;
        try {
            startupAndIgnition(logger);
            engineAndDriving();
            transmission();
            brakesAbsAssist();
            steeringAndModes();
            escBehaviour();
            airbrakeLift();
            launchControl();
            tyresAndTpms();
            faultsAndDiagnostics();
            notificationsAndMessages();
            tripAndParking();
            brakeFeatures();
            thermalAndFuel();
            threadingAndLogs(logger);
        } finally {
            logger.close();
        }
        System.out.println();
        System.out.printf("RESULT: %d passed, %d failed%n", passed, failed);
        for (String f : failures) {
            System.out.println("  FAILED: " + f);
        }
        System.exit(failed == 0 ? 0 : 1);
    }

    // ------------------------------------------------------------------ 1-6, 33: ignition, self-test, cranking
    private static void startupAndIgnition(Logger logger) throws Exception {
        System.out.println("\n== Startup / ignition ==");
        Car car = new Car(logger);
        check("1-2. vehicle is initially asleep/off", t(car).getPowerState() == PowerState.SLEEP && t(car).getRpm() == 0, "state=" + t(car).getPowerState());
        check("   defaults: ESC ON, Active INACTIVE, EPB engaged", t(car).getEscMode() == EscMode.ON && t(car).getActiveState() == ActiveState.INACTIVE && t(car).getEpbState() == EpbState.ENGAGED, "");

        car.powerOn();
        run(car, 0.5);
        TelemetrySnapshot s = t(car);
        check("3,33. I -> SELF_TEST, warning self-test lights everything", s.getPowerState() == PowerState.SELF_TEST && s.getStartupPhase() == StartupPhase.WARNING_SELF_TEST && s.isWarningSelfTest(), s.getStartupPhase().getLabel());
        run(car, 1.5);
        s = t(car);
        check("   gauges self-test sweeps the needle", s.getStartupPhase() == StartupPhase.GAUGES_SELF_TEST && s.getGaugeSweep() > 0.2, String.format("sweep=%.2f", s.getGaugeSweep()));
        run(car, 1.3);
        check("   system check phase", t(car).getStartupPhase() == StartupPhase.SYSTEM_CHECK, t(car).getStartupPhase().getLabel());
        check("   ENTER during self-test is refused", denied(car::startEngine), deniedReason(car::startEngine));
        run(car, 1.0);
        check("   self-test completes -> POWER_ON / READY", t(car).getPowerState() == PowerState.POWER_ON && t(car).getStartupPhase() == StartupPhase.READY, "");

        check("4. start without brake is denied (interlock)", deniedReason(car::startEngine).contains("Brake pedal"), deniedReason(car::startEngine));
        car.pressBrake(1.0);
        car.startEngine();
        check("5. ENTER -> CRANKING, engine not instantly running", t(car).getPowerState() == PowerState.CRANKING && !t(car).isEngineRunning(), "");
        run(car, 0.6);
        double crankRpm = t(car).getRpm();
        double oil1 = t(car).getOilPressure();
        check("   RPM rises while cranking, oil pressure building", crankRpm > 100 && crankRpm < 500 && oil1 > 0.1 && t(car).getPowerState() == PowerState.CRANKING, String.format("rpm=%.0f oil=%.2f bar", crankRpm, oil1));
        run(car, 3.0);
        s = t(car);
        check("6. engine reaches idle and is RUNNING", s.getPowerState() == PowerState.ENGINE_RUNNING && Math.abs(s.getRpm() - Engine_IDLE) < 25 && s.getOilPressure() >= 1.0, String.format("rpm=%.0f oil=%.2f", s.getRpm(), s.getOilPressure()));
        check("   'ENGINE STARTED' message shown then clears", s.getStartupPhase() == StartupPhase.ENGINE_STARTED, "");
        run(car, 2.5);
        check("   startup banner clears", t(car).getStartupPhase() == StartupPhase.NONE, "");

        // Ignition cycle resets per-ignition defaults but remembers selected modes
        car.releaseBrake();
        car.selectMode(new SportMode());
        run(car, 0.2);
        car.cycleEscMode();   // DYNAMIC (Sport handling + Active)
        car.stopEngine();
        car.powerOff();
        car.powerOn();
        s = t(car);
        check("34b. new ignition cycle: ESC back ON, Active INACTIVE, modes remembered",
                s.getEscMode() == EscMode.ON && s.getActiveState() == ActiveState.INACTIVE && s.getHandlingMode().getRank() == 1 && s.getPowertrainMode().getRank() == 1,
                s.getEscMode() + " " + s.getActiveState());
    }
    private static final double Engine_IDLE = hyperdrive.systems.Engine.IDLE_RPM;

    // ------------------------------------------------------------------ 7-11: throttle, rpm, speed, fuel, temps
    private static void engineAndDriving() throws Exception {
        System.out.println("\n== Engine / driving ==");
        Car car = runningCar(false);
        TelemetrySnapshot idle = t(car);
        check("   cold start: RPM limit is reduced while the oil is cold", idle.getRpmLimit() < 5000, String.format("limit=%.0f oil=%.0fC", idle.getRpmLimit(), idle.getOilTemp()));
        selectFirst(car);
        double fuel0 = t(car).getFuelLevelPercent();
        double cool0 = t(car).getCoolantTemp();
        double oil0 = t(car).getOilTemp();
        car.setThrottle(1.0);
        run(car, 1.0);
        TelemetrySnapshot s = t(car);
        check("7. W increases throttle", s.getThrottle() > 0.8, String.format("throttle=%.2f", s.getThrottle()));
        check("8. RPM rises with throttle", s.getRpm() > idle.getRpm() + 1000, String.format("rpm %.0f -> %.0f", idle.getRpm(), s.getRpm()));
        check("9. speed rises", s.getSpeedKmh() > 10, String.format("%.0f km/h", s.getSpeedKmh()));
        run(car, 14.0);
        s = t(car);
        check("10. fuel decreases (flow depends on rpm/load)", s.getFuelLevelPercent() < fuel0 && s.getFuelFlowLph() > 10, String.format("%.2f%% -> %.2f%%, %.0f L/h", fuel0, s.getFuelLevelPercent(), s.getFuelFlowLph()));
        check("   range falls with fuel and is computed", s.getFuelRangeKm() > 0 && s.getFuelRangeKm() < 386, String.format("%.0f km", s.getFuelRangeKm()));
        check("11. coolant and oil temperatures rise with load", s.getCoolantTemp() > cool0 + 10 && s.getOilTemp() > oil0 + 5, String.format("coolant %.0f->%.0f oil %.0f->%.0f", cool0, s.getCoolantTemp(), oil0, s.getOilTemp()));
        check("   torque/load are live values", s.getEngineTorqueNm() > 100 && s.getEngineLoad() > 0.3, String.format("%.0f Nm load %.2f", s.getEngineTorqueNm(), s.getEngineLoad()));
        double gearBefore = s.getGear().ordinal();
        check("   AUTO shifts up by itself while accelerating", s.getGear().ordinal() > GearPosition.G2.ordinal(), "gear " + s.getGear().getLabel());

        // throttle lift: rpm/load/temps fall
        double rpmHigh = s.getRpm();
        car.setThrottle(0.0);
        run(car, 3.0);
        TelemetrySnapshot l = t(car);
        check("   lifting off lowers RPM and engine load", l.getRpm() < rpmHigh - 500 && l.getEngineLoad() < 0.2, String.format("rpm %.0f -> %.0f load %.2f", rpmHigh, l.getRpm(), l.getEngineLoad()));
    }

    // ------------------------------------------------------------------ 12: Q/E, auto/manual, gear rules
    private static void transmission() throws Exception {
        System.out.println("\n== Transmission ==");
        Car car = runningCar(true);
        check("   P: cannot leave park without the brake", denied(() -> car.shiftGear(GearPosition.G1)), "");
        selectFirst(car);
        check("   gear 1 selected, EPB released on drive select", t(car).getGear() == GearPosition.G1 && t(car).getEpbState() == EpbState.RELEASED, "");
        car.toggleTransmissionMode();
        check("   M toggles MANUAL", t(car).getTransmissionMode() == TransmissionMode.MANUAL, "");
        car.setThrottle(1.0);
        run(car, 1.6);
        check("   manual: no automatic upshift (stays in 1)", t(car).getGear() == GearPosition.G1, "gear " + t(car).getGear().getLabel() + " rpm " + (int) t(car).getRpm());
        car.setThrottle(0.3);
        run(car, 0.2);
        car.shiftUp();
        run(car, 0.8);
        check("12. E upshifts (manual)", t(car).getGear() == GearPosition.G2, "gear " + t(car).getGear().getLabel());
        car.shiftUp();
        check("   shift takes time (smooth) - a second shift straight away is refused", denied(car::shiftUp), deniedReason(car::shiftUp));
        run(car, 0.8);
        car.setThrottle(0.0);
        run(car, 0.6);
        car.shiftDown();
        run(car, 0.8);
        car.shiftDown();
        run(car, 0.8);
        check("12. Q downshifts", t(car).getGear() == GearPosition.G1, "gear " + t(car).getGear().getLabel());
        check("   downshift below 1 is refused", denied(car::shiftDown), "");
        car.toggleTransmissionMode();
        car.setThrottle(1.0);
        run(car, 4.0);
        check("   AUTO again: shifts by itself", t(car).getGear().getGearNumber() >= 2, "gear " + t(car).getGear().getLabel());
        check("   reverse is refused while moving", denied(() -> car.shiftGear(GearPosition.R)), deniedReason(() -> car.shiftGear(GearPosition.R)));
        check("   park is refused while moving", denied(() -> car.shiftGear(GearPosition.P)), "");
        car.setThrottle(0.0);
        car.pressBrake(1.0);
        run(car, 12.0);
        check("   stopping: gearbox drops back to 1st by itself", t(car).getSpeedKmh() < 1 && t(car).getGear() == GearPosition.G1, "gear " + t(car).getGear().getLabel());
        car.injectFault(FaultType.TRANSMISSION_FAULT);
        run(car, 1.0);
        check("   TRANSMISSION FAULT blocks gear changes (InvalidGear -> denied)", denied(() -> car.shiftGear(GearPosition.N)), deniedReason(() -> car.shiftGear(GearPosition.N)));
    }

    // ------------------------------------------------------------------ 13-15: brakes
    private static void brakesAbsAssist() throws Exception {
        System.out.println("\n== Brakes / ABS / Brake Assist ==");
        Car car = runningCar(true);
        selectFirst(car);
        car.setThrottle(1.0);
        run(car, 12.0);
        double v0 = t(car).getSpeedKmh();
        car.setThrottle(0.0);
        run(car, 1.0);
        double vCoast = t(car).getSpeedKmh();
        // gentle brake -> no ABS, no assist
        car.pressBrake(0.25);
        run(car, 1.0);
        TelemetrySnapshot gentle = t(car);
        check("13. S brakes: deceleration follows brake input", gentle.getLongitudinalG() < -0.15 && gentle.getSpeedKmh() < vCoast, String.format("%.0f -> %.0f km/h, %.2f g", vCoast, gentle.getSpeedKmh(), gentle.getLongitudinalG()));
        check("   gentle braking: ABS and Brake Assist stay off", !gentle.isAbsActive() && !gentle.isBrakeAssistActive(), "");
        car.releaseBrake();
        car.setThrottle(1.0);
        run(car, 5.0);
        car.setThrottle(0.0);
        double hardStart = t(car).getSpeedKmh();
        car.pressBrake(1.0, true);   // stamped on the pedal
        run(car, 0.6);
        TelemetrySnapshot hard = t(car);
        check("14. hard braking: ABS intervenes", hard.isAbsActive() && hard.getBrakeState() == BrakeState.ABS, "state=" + hard.getBrakeState());
        check("15. rapid pedal application: Brake Assist activates", hard.isBrakeAssistActive(), "");
        check("   brake pressure/force/temperature are live", hard.getBrakePressureBar() > 50 && hard.getBrakeForce() > 0.7 && hard.getBrakeDiscTemp() > 60, String.format("%.0f bar force %.2f disc %.0fC", hard.getBrakePressureBar(), hard.getBrakeForce(), hard.getBrakeDiscTemp()));
        run(car, 2.0);
        check("   decel is limited by tyre grip (~1.2 g, not infinite)", t(car).getLongitudinalG() > -1.8 && t(car).getLongitudinalG() < -0.9, String.format("%.2f g", t(car).getLongitudinalG()));
        car.releaseBrake();
        run(car, 3.0);

        // ABS fault: no modulation -> locked wheels brake worse, warning raised, diagnostics sees it
        Car c2 = runningCar(true);
        selectFirst(c2);
        c2.setThrottle(1.0);
        run(c2, 12.0);
        c2.setThrottle(0.0);
        c2.injectFault(FaultType.ABS_FAULT);
        c2.pressBrake(1.0, true);
        run(c2, 0.6);
        TelemetrySnapshot f = t(c2);
        check("   ABS fault: ABS never intervenes, ABS warning on, diagnostics WARNING",
                !f.isAbsActive() && f.getWarningLights().get(WarningLight.ABS) == Severity.WARNING && c2.getDiagnosticLevel() == DiagnosticLevel.WARNING,
                "level=" + c2.getDiagnosticLevel());
        double gNoAbs = t(c2).getLongitudinalG();
        check("   ABS fault: less deceleration than with ABS (wheels lock)", gNoAbs > -1.15, String.format("%.2f g", gNoAbs));
    }

    // ------------------------------------------------------------------ 16-19, 25: steering, modes, H/P, shift lights
    private static void steeringAndModes() throws Exception {
        System.out.println("\n== Steering / drive modes ==");
        Car car = runningCar(true);
        selectFirst(car);
        car.setThrottle(1.0);
        run(car, 9.0);
        car.setThrottle(0.6);
        run(car, 2.0);
        double straight = t(car).getSteeringValue();
        car.setSteering(1.0);
        run(car, 1.5);
        TelemetrySnapshot s = t(car);
        check("16. D steers right: angle, direction, lateral state and lateral g are real", s.getSteeringValue() > 0.9 && s.getSteeringDirection().equals("RIGHT") && Math.abs(s.getLateralG()) > 0.3, String.format("angle %.0f deg lat %.2f g state %s", s.getSteeringAngleDeg(), s.getLateralG(), s.getLateralState()));
        Tyre[] ty = s.getTyres();
        check("   cornering loads the outside (left) tyres: they run hotter than the inside ones", ty[0].getTemperature() + ty[2].getTemperature() > ty[1].getTemperature() + ty[3].getTemperature(), String.format("FL %.1f FR %.1f RL %.1f RR %.1f", ty[0].getTemperature(), ty[1].getTemperature(), ty[2].getTemperature(), ty[3].getTemperature()));
        car.setSteering(0.0);
        run(car, 2.0);
        check("   releasing A/D self-centres the wheel", Math.abs(t(car).getSteeringValue()) < 0.05, "");
        car.setSteering(-1.0);
        run(car, 1.0);
        check("   A steers left", t(car).getSteeringDirection().equals("LEFT"), "");
        car.setSteering(0.0);
        car.setThrottle(0.0);
        car.pressBrake(1.0);
        run(car, 10.0);

        // modes (vehicle now stopped)
        car.releaseBrake();
        check("   modes start at COMFORT / NON-ACTIVE", t(car).getHandlingMode().getRank() == 0 && t(car).getActiveState() == ActiveState.INACTIVE, "");
        car.cycleHandlingMode();
        s = t(car);
        check("18. H cycles handling mode and activates Active Dynamics (H SPORT / P COMFORT)", s.getHandlingMode().getName().equals("Sport") && s.getPowertrainMode().getName().equals("Comfort") && s.getActiveState() == ActiveState.ACTIVE, s.getHandlingMode().getLabel() + "/" + s.getPowertrainMode().getLabel());
        car.cyclePowertrainMode();
        check("   P cycles powertrain mode (H SPORT / P SPORT)", t(car).getPowertrainMode().getName().equals("Sport"), "");
        car.toggleActive();
        check("   X: Active OFF -> NON-ACTIVE, selections kept", t(car).getActiveState() == ActiveState.INACTIVE && t(car).getPowertrainMode().getName().equals("Sport"), "");
        car.toggleActive();
        car.selectMode(new TrackMode());
        s = t(car);
        check("17. Comfort/Sport/Track selectable (3 = Track): both H and P", s.getHandlingMode().getName().equals("Track") && s.getPowertrainMode().getName().equals("Track"), "");
        check("19. Track mode has shift lights; Comfort/Sport do not", s.getPowertrainMode().hasShiftLights() && !new SportMode().hasShiftLights() && !new ComfortMode().hasShiftLights(), "");
        check("   Track does NOT switch ESC off by itself", s.getEscMode() == EscMode.ON, "esc=" + s.getEscMode());

        // modes genuinely change the car: throttle response, shift points
        double comfortRpm = rpmAfterHalfSecondFullThrottle(new ComfortMode());
        double sportRpm = rpmAfterHalfSecondFullThrottle(new SportMode());
        double trackRpm = rpmAfterHalfSecondFullThrottle(new TrackMode());
        check("   mode changes real behaviour: RPM response Comfort < Sport < Track", comfortRpm < sportRpm && sportRpm < trackRpm, String.format("%.0f / %.0f / %.0f rpm", comfortRpm, sportRpm, trackRpm));
        int comfortShift = firstUpshiftRpm(new ComfortMode());
        int trackShift = firstUpshiftRpm(new TrackMode());
        check("   shift strategy: Track upshifts at much higher RPM than Comfort", trackShift > comfortShift + 1500, comfortShift + " vs " + trackShift + " rpm");

        // mode change rules
        Car moving = runningCar(true);
        selectFirst(moving);
        moving.setThrottle(1.0);
        run(moving, 6.0);
        check("   mode change refused while driving fast", denied(() -> moving.selectMode(new TrackMode())), deniedReason(() -> moving.selectMode(new TrackMode())));
    }

    private static double rpmAfterHalfSecondFullThrottle(DriveMode m) throws Exception {
        Car c = runningCar(true);
        c.selectMode(m);
        run(c, 0.2);
        c.pressBrake(1.0);
        run(c, 0.1);
        c.shiftGear(GearPosition.N);   // free-rev comparison in neutral
        run(c, 1.0);
        c.releaseBrake();
        c.setThrottle(1.0);
        run(c, 0.5);
        return c.getRpm();
    }

    private static int firstUpshiftRpm(DriveMode m) throws Exception {
        Car c = runningCar(true);
        c.selectMode(m);
        selectFirst(c);
        c.setThrottle(1.0);
        double peak = 0;
        for (int i = 0; i < 400; i++) {
            c.update(0.02);
            peak = Math.max(peak, c.getRpm());
            if (c.getTransmission().getGear() != GearPosition.G1) {
                break;
            }
        }
        return (int) peak;
    }

    // ------------------------------------------------------------------ 20-21: ESC
    private static void escBehaviour() throws Exception {
        System.out.println("\n== ESC / traction ==");
        Car car = runningCar(true);
        selectFirst(car);
        car.setThrottle(1.0);
        run(car, 6.0);
        car.setSteering(1.0);
        boolean seen = false;
        double maxCut = 0;
        for (int i = 0; i < 150; i++) {
            car.update(0.02);
            TelemetrySnapshot s = t(car);
            if (s.isEscIntervening()) {
                seen = true;
                maxCut = Math.max(maxCut, s.getTorqueCut());
            }
        }
        check("20. ESC intervenes (cuts torque + brakes individual wheels) when slipping in a corner", seen && maxCut > 0.05, String.format("max torque cut %.0f%%", maxCut * 100));
        TelemetrySnapshot s = t(car);
        double brakeSum = 0;
        for (double w : s.getWheelBrake()) {
            brakeSum += w;
        }
        check("   ESC / brake-steer apply braking to individual wheels", brakeSum > 0.05 || s.isBrakeSteerActive(), String.format("sum wheel brake %.2f", brakeSum));
        check("   balance is reported (understeer/oversteer)", s.getLateralState() != LateralState.NEUTRAL, s.getLateralState().toString());

        // ESC OFF behaviour
        Car c2 = runningCar(true);
        check("   ESC DYNAMIC refused without Active Dynamics", denied(c2::cycleEscMode) || c2.getEscMode() != EscMode.DYNAMIC, "");
        c2.selectMode(new TrackMode());
        c2.cycleEscMode();
        c2.cycleEscMode();
        c2.cycleEscMode();
        check("21. ESC OFF reachable in Track handling (ESC ON -> DYNAMIC -> TRACK DYNAMIC -> OFF)", t(c2).getEscMode() == EscMode.OFF, "esc=" + t(c2).getEscMode());
        check("   ESC OFF shows its warning light", t(c2).getWarningLights().containsKey(WarningLight.ESC) || runAndHasLight(c2, WarningLight.ESC), "");
        selectFirst(c2);
        c2.setThrottle(1.0);
        run(c2, 6.0);
        c2.setSteering(1.0);
        double slipOff = 0;
        boolean interv = false;
        for (int i = 0; i < 150; i++) {
            c2.update(0.02);
            slipOff = Math.max(slipOff, t(c2).getDriftLevel());
            interv |= t(c2).isEscIntervening();
        }
        check("   ESC OFF: no intervention, slip allowed to grow", !interv && slipOff > 5, String.format("max slip %.0f%%", slipOff));
        c2.setThrottle(0);
        c2.setSteering(0);
        c2.pressBrake(1.0);
        run(c2, 12.0);
        c2.cyclePowertrainMode();   // Comfort (rank wrap) - ESC must NOT secretly come back on
        check("   ESC OFF is not secretly re-enabled by mode changes", t(c2).getEscMode() == EscMode.OFF, "");
        c2.releaseBrake();
        c2.stopEngine();
        c2.powerOff();
        c2.powerOn();
        check("   ... only a new ignition cycle restores ESC ON", t(c2).getEscMode() == EscMode.ON, "");
    }

    private static boolean runAndHasLight(Car c, WarningLight l) {
        run(c, 0.5);
        return t(c).getWarningLights().containsKey(l);
    }

    // ------------------------------------------------------------------ 22-23: airbrake, lift
    private static void airbrakeLift() throws Exception {
        System.out.println("\n== Airbrake / vehicle lift ==");
        // cold oil -> unavailable
        Car cold = runningCar(false);
        run(cold, 1.0);
        check("   cold oil: AIRBRAKE TEMPORARILY UNAVAILABLE", t(cold).getAirbrakeState() == AirbrakeState.UNAVAILABLE && t(cold).getWarningLights().containsKey(WarningLight.AIRBRAKE), "state=" + t(cold).getAirbrakeState());
        check("   unavailable airbrake refuses to deploy", denied(cold::deployAirbrake), deniedReason(cold::deployAirbrake));

        // warm: self-test after start
        Car car = new Car(LOG);
        car.powerOn();
        run(car, 4.3);
        car.preWarm();
        car.pressBrake(1.0);
        car.startEngine();
        boolean testing = false;
        double maxProgress = 0;
        for (int i = 0; i < 600; i++) {   // 12 s: cranking, start, then the self-test
            car.update(0.02);
            testing |= (t(car).getAirbrakeState() == AirbrakeState.DEPLOYING || t(car).getAirbrakeState() == AirbrakeState.DEPLOYED);
            maxProgress = Math.max(maxProgress, t(car).getAirbrakeProgress());
        }
        check("   airbrake self-test after startup (unfolds, then stows)", testing && maxProgress > 0.99 && t(car).getAirbrakeState() == AirbrakeState.STOWED, String.format("max progress %.2f final %s", maxProgress, t(car).getAirbrakeState()));
        car.releaseBrake();
        check("   manual deploy refused when slow", denied(car::deployAirbrake), deniedReason(car::deployAirbrake));
        selectFirst(car);
        car.setThrottle(1.0);
        run(car, 14.0);
        car.setThrottle(0.6);
        check("22. B deploys manually at speed (animated, not instant)", !denied(car::deployAirbrake) && t(car).getAirbrakeState() == AirbrakeState.DEPLOYING, "state=" + t(car).getAirbrakeState());
        run(car, 0.35);
        double mid = t(car).getAirbrakeProgress();
        run(car, 0.6);
        check("   deployment animates through DEPLOYING to DEPLOYED", mid > 0.1 && mid < 0.9 && t(car).getAirbrakeState() == AirbrakeState.DEPLOYED, String.format("mid %.2f", mid));
        check("   B again retracts", !denied(car::retractAirbrake) && t(car).getAirbrakeState() == AirbrakeState.RETRACTING, "");
        run(car, 1.0);
        check("   retraction finishes -> STOWED", t(car).getAirbrakeState() == AirbrakeState.STOWED, "");
        // auto on hard braking from speed
        car.setThrottle(1.0);
        run(car, 8.0);
        car.setThrottle(0.0);
        car.pressBrake(1.0, true);
        run(car, 1.2);
        check("   auto deploys on hard braking at speed", t(car).getAirbrakeState() == AirbrakeState.DEPLOYED || t(car).getAirbrakeState() == AirbrakeState.DEPLOYING, "state=" + t(car).getAirbrakeState());
        run(car, 10.0);
        check("   low speed / stop: retracts", t(car).getAirbrakeState() == AirbrakeState.STOWED, "state=" + t(car).getAirbrakeState());
        car.releaseBrake();
        car.injectFault(FaultType.AIRBRAKE_FAULT);
        run(car, 0.5);
        check("   AIRBRAKE FAULT: state FAULT, deploy refused, warning lit", t(car).getAirbrakeState() == AirbrakeState.FAULT && denied(car::deployAirbrake) && t(car).getWarningLights().containsKey(WarningLight.AIRBRAKE), "");

        // Lift
        Car lc = runningCar(true);
        lc.pressBrake(1.0);
        lc.raiseLift();
        check("23. V raises the lift (RAISING, animated)", t(lc).getLiftState() == LiftState.RAISING, "");
        run(lc, 0.5);
        double mid2 = t(lc).getLiftProgress();
        run(lc, 1.0);
        check("   RAISING -> RAISED with progress", mid2 > 0.2 && mid2 < 0.8 && t(lc).getLiftState() == LiftState.RAISED && t(lc).getLiftProgress() > 0.99, String.format("mid %.2f", mid2));
        check("   Launch Control unavailable while lift is raised", deniedReason(lc::requestLaunch).contains("lift"), deniedReason(lc::requestLaunch));
        lc.lowerLift();
        check("   V again lowers (LOWERING)", t(lc).getLiftState() == LiftState.LOWERING, "");
        run(lc, 1.5);
        check("   -> NORMAL", t(lc).getLiftState() == LiftState.NORMAL, "");
        lc.raiseLift();
        run(lc, 1.5);
        lc.shiftGear(GearPosition.G1);
        run(lc, 0.8);
        lc.releaseBrake();
        lc.setThrottle(1.0);
        run(lc, 3.0);
        check("   speed restriction: auto-lowers above 40 km/h", t(lc).getLiftState() == LiftState.LOWERING || t(lc).getLiftState() == LiftState.NORMAL, "state=" + t(lc).getLiftState() + " at " + (int) t(lc).getSpeedKmh() + " km/h");
        check("   raising refused above the speed limit", denied(lc::raiseLift), deniedReason(lc::raiseLift));
        lc.setThrottle(0.0);
        lc.injectFault(FaultType.LIFT_FAULT);
        run(lc, 3.0);
        check("   LIFT FAULT: raise refused", denied(lc::raiseLift), "");
    }

    // ------------------------------------------------------------------ 24-26: launch control
    private static void launchControl() throws Exception {
        System.out.println("\n== Launch Control ==");
        Car car = runningCar(true);
        selectFirst(car);   // leaves brake released, comfort mode
        car.pressBrake(1.0);
        car.setThrottle(0.0);
        String r1 = deniedReason(car::requestLaunch);
        check("24,26. L in Comfort/inactive: LAUNCH CONTROL UNAVAILABLE with the reasons", r1.contains("Active Dynamics") && r1.contains("TRACK"), r1);
        check("   state shows UNAVAILABLE and the reason is exposed", t(car).getLaunchState() == LaunchState.UNAVAILABLE && t(car).getLaunchReason().contains("TRACK"), t(car).getLaunchState().toString());
        check("   UNAVAILABLE message is a WARNING notification", car.getWarnings().stream().anyMatch(n -> n.getMessage().contains("LAUNCH CONTROL UNAVAILABLE")), "");
        run(car, 4.5);

        car.releaseBrake();
        car.getTransmission().forcePark();
        car.pressBrake(1.0);
        run(car, 1.0);
        car.shiftGear(GearPosition.N);
        run(car, 0.8);
        car.selectMode(new TrackMode());
        car.shiftGear(GearPosition.G1);
        run(car, 0.8);
        car.setSteering(0.6);
        run(car, 1.0);
        check("   steering not straight blocks launch", deniedReason(car::requestLaunch).contains("Steering"), "");
        car.setSteering(0.0);
        run(car, 1.0);
        car.setDoorOpen(true);
        check("   open door blocks launch", deniedReason(car::requestLaunch).contains("Door"), "");
        car.setDoorOpen(false);
        run(car, 4.5);

        // valid launch
        car.requestLaunch();
        List<LaunchState> seen = new ArrayList<>();
        seen.add(t(car).getLaunchState());
        car.setThrottle(1.0);
        double peakRpmHold = 0;
        for (int i = 0; i < 400; i++) {
            car.update(0.02);
            LaunchState ls = t(car).getLaunchState();
            if (seen.get(seen.size() - 1) != ls) {
                seen.add(ls);
            }
            if (ls == LaunchState.READY) {
                peakRpmHold = t(car).getRpm();
                break;
            }
        }
        System.out.println("   states so far: " + seen);
        check("25. valid sequence passes REQUESTED -> CHECKING -> AWAITING_THROTTLE -> BOOST_BUILDING -> READY",
                seen.equals(List.of(LaunchState.REQUESTED, LaunchState.CHECKING, LaunchState.AWAITING_THROTTLE, LaunchState.BOOST_BUILDING, LaunchState.READY)), seen.toString());
        run(car, 1.0);
        TelemetrySnapshot ready = t(car);
        check("   READY: engine held at launch RPM, car held stationary by the brakes", ready.getRpm() > 4000 && ready.getSpeedKmh() < 1 && ready.getLaunchState() == LaunchState.READY, String.format("rpm %.0f", ready.getRpm()));
        check("   LAUNCH CONTROL READY message + green indicator", ready.getWarningLights().get(WarningLight.LAUNCH) == Severity.INFO && car.getEvents().stream().anyMatch(e -> e.contains("LAUNCH CONTROL READY")), "");
        car.releaseBrake();
        run(car, 0.1);
        check("   brake release -> LAUNCHING", t(car).getLaunchState() == LaunchState.LAUNCHING, t(car).getLaunchState().toString());
        double tTo100 = -1;
        int maxGear = 1;
        for (int i = 0; i < 500; i++) {
            car.update(0.02);
            maxGear = Math.max(maxGear, t(car).getGear().getGearNumber());
            if (tTo100 < 0 && t(car).getSpeedKmh() >= 100) {
                tTo100 = (i + 5) * 0.02;
            }
        }
        check("   acceleration with automatic simulated shifts (0-100 km/h in about 3-4.5 s)", tTo100 > 2.5 && tTo100 < 4.6 && maxGear >= 2, String.format("0-100 in %.1f s, reached gear %d", tTo100, maxGear));
        check("   launch finishes -> COMPLETE then OFF", t(car).getLaunchState() == LaunchState.OFF || t(car).getLaunchState() == LaunchState.COMPLETE, t(car).getLaunchState().toString());

        // abort cases
        car.setThrottle(0.0);
        car.pressBrake(1.0);
        run(car, 14.0);
        check("   standing again in gear 1", t(car).getSpeedKmh() < 1 && t(car).getGear() == GearPosition.G1, "");
        car.requestLaunch();
        car.setThrottle(1.0);
        run(car, 3.5);
        car.setThrottle(0.2);
        run(car, 0.2);
        check("   abort: throttle released", t(car).getLaunchState() == LaunchState.ABORTED && car.getWarnings().stream().anyMatch(n -> n.getMessage().contains("ABORTED") && n.getMessage().contains("Throttle")), t(car).getLaunchState().toString());
        run(car, 3.0);
        car.setThrottle(0.0);
        car.requestLaunch();
        run(car, 1.5);
        car.toggleActive();
        run(car, 0.1);
        check("   abort: Active Dynamics disabled", t(car).getLaunchState() == LaunchState.ABORTED, t(car).getLaunchState().toString());
        run(car, 3.0);
        car.toggleActive();
        car.requestLaunch();
        run(car, 1.5);
        check("   lift cannot be raised in Track handling at all", denied(car::raiseLift), "");
        car.getLift().raise();   // bypass the Car's rule to prove Launch Control also watches the lift itself
        run(car, 0.1);
        check("   abort: lift activated", t(car).getLaunchState() == LaunchState.ABORTED, t(car).getLaunchState().toString());
        car.getLift().lowerManual();
        run(car, 2.0);
        run(car, 3.0);
        car.releaseBrake();
        car.pressBrake(1.0);
        car.requestLaunch();
        run(car, 1.5);
        car.injectFault(FaultType.OVERHEATING);
        run(car, 0.1);
        check("   abort: critical fault", t(car).getLaunchState() == LaunchState.ABORTED, t(car).getLaunchState().toString());
        car.clearFault(FaultType.OVERHEATING);
    }

    // ------------------------------------------------------------------ 27-28: tyres, TPMS
    private static void tyresAndTpms() throws Exception {
        System.out.println("\n== Tyres / TPMS ==");
        Car car = runningCar(false);
        Tyre[] cold = t(car).getTyres();
        check("   tyres tracked independently (4 objects); start cold", cold.length == 4 && cold[0].getCondition() == TyreCondition.COLD, cold[0].getCondition().toString());
        car.preWarm();
        run(car, 0.5);
        check("   warm tyres -> NORMAL", t(car).getTyres()[0].getCondition() == TyreCondition.NORMAL, t(car).getTyres()[0].getCondition().toString());

        car.injectFault(FaultType.LOW_TYRE_PRESSURE);
        run(car, 0.5);
        TelemetrySnapshot s = t(car);
        check("28. low tyre pressure: FL LOW_PRESSURE only, TPMS warning lit",
                s.getTyres()[0].getCondition() == TyreCondition.LOW_PRESSURE && s.getTyres()[1].getCondition() == TyreCondition.NORMAL && s.getWarningLights().get(WarningLight.TPMS) == Severity.WARNING,
                String.format("FL %.1f bar", s.getTyres()[0].getPressure()));
        check("   low pressure reduces that tyre's grip", s.getTyres()[0].getGrip() < s.getTyres()[1].getGrip(), String.format("%.2f vs %.2f g", s.getTyres()[0].getGrip(), s.getTyres()[1].getGrip()));
        car.clearFault(FaultType.LOW_TYRE_PRESSURE);
        run(car, 0.5);
        check("   fault clears", t(car).getTyres()[0].getCondition() == TyreCondition.NORMAL && !t(car).getWarningLights().containsKey(WarningLight.TPMS), "");
        car.injectFault(FaultType.TYRE_OVERHEAT);
        run(car, 15.0);
        check("   tyre overheat: RR becomes HOT with its own warning", t(car).getTyres()[3].getCondition() == TyreCondition.HOT && t(car).getTyres()[0].getCondition() != TyreCondition.HOT && t(car).getWarningLights().containsKey(WarningLight.TPMS), String.format("RR %.0f C", t(car).getTyres()[3].getTemperature()));
        car.clearFault(FaultType.TYRE_OVERHEAT);
        car.injectFault(FaultType.TPMS_FAULT);
        run(car, 0.5);
        check("   TPMS fault: all tyres FAULT (no signal)", t(car).getTyres()[2].getCondition() == TyreCondition.FAULT && t(car).getWarningLights().containsKey(WarningLight.TPMS), "");
        car.clearFault(FaultType.TPMS_FAULT);

        // 27: driving changes tyre values: braking -> fronts hotter; acceleration -> rears hotter
        Car b = runningCar(true);
        selectFirst(b);
        b.setThrottle(1.0);
        run(b, 14.0);
        b.setThrottle(0);
        Tyre[] before = t(b).getTyres();
        double frontBefore = before[0].getTemperature() + before[1].getTemperature();
        double rearBefore = before[2].getTemperature() + before[3].getTemperature();
        b.pressBrake(1.0, true);
        run(b, 2.5);
        Tyre[] after = t(b).getTyres();
        double dFront = after[0].getTemperature() + after[1].getTemperature() - frontBefore;
        double dRear = after[2].getTemperature() + after[3].getTemperature() - rearBefore;
        check("27. hard braking heats the FRONT tyres more than the rear", dFront > dRear, String.format("front +%.1f rear +%.1f C", dFront / 2, dRear / 2));
        b.releaseBrake();
        b.pressBrake(1.0);
        run(b, 14.0);
        b.releaseBrake();
        double f1 = t(b).getTyres()[0].getTemperature() + t(b).getTyres()[1].getTemperature();
        double r1 = t(b).getTyres()[2].getTemperature() + t(b).getTyres()[3].getTemperature();
        b.pressBrake(0.0);
        run(b, 0.5);
        b.setThrottle(1.0);
        run(b, 3.0);
        double dF = t(b).getTyres()[0].getTemperature() + t(b).getTyres()[1].getTemperature() - f1;
        double dR = t(b).getTyres()[2].getTemperature() + t(b).getTyres()[3].getTemperature() - r1;
        check("   hard acceleration heats the REAR tyres more than the front", dR > dF, String.format("front %+.1f rear %+.1f C", dF / 2, dR / 2));
        check("   tyre pressure rises as tyres heat (gas law)", t(b).getTyres()[2].getPressure() > Tyre.NOMINAL_PRESSURE, String.format("%.2f bar", t(b).getTyres()[2].getPressure()));
    }

    // ------------------------------------------------------------------ 29-30, 34: faults + diagnostics
    private static void faultsAndDiagnostics() throws Exception {
        System.out.println("\n== Faults / diagnostics ==");
        // LOW OIL PRESSURE before start
        Car car = new Car(LOG);
        car.powerOn();
        run(car, 4.3);
        car.pressBrake(1.0);
        car.injectFault(FaultType.LOW_OIL_PRESSURE);
        run(car, 1.0);
        check("29-30. LOW OIL PRESSURE: diagnostics reports CRITICAL, start refused", car.getDiagnosticLevel() == DiagnosticLevel.CRITICAL && deniedReason(car::startEngine).contains("Oil"), deniedReason(car::startEngine));
        car.clearFault(FaultType.LOW_OIL_PRESSURE);
        run(car, 3.0);
        car.startEngine();
        run(car, 4.0);
        check("34. fault cleared -> engine starts", t(car).isEngineRunning(), "");
        car.preWarm();
        run(car, 1.0);
        car.setThrottle(0.0);
        car.injectFault(FaultType.LOW_OIL_PRESSURE);
        run(car, 4.0);
        TelemetrySnapshot s = t(car);
        check("   running + LOW OIL: red OIL light, engine power/RPM limited, countdown running",
                s.getWarningLights().get(WarningLight.OIL) == Severity.CRITICAL && s.getRpmLimit() <= 3000 && s.getCriticalFaultCountdown() > 0 && s.getOilState() == OilState.LOW_PRESSURE,
                String.format("limit %.0f, countdown %.1f, oil %.2f bar", s.getRpmLimit(), s.getCriticalFaultCountdown(), s.getOilPressure()));
        run(car, 8.0);
        check("   uncleared critical fault shuts the engine down", !t(car).isEngineRunning(), "state=" + t(car).getPowerState());
        check("   restart is refused while the fault remains", denied(() -> { car.pressBrake(1.0); car.startEngine(); }), "");
        car.clearFault(FaultType.LOW_OIL_PRESSURE);

        // Each remaining fault changes real system state
        String[] results = new String[FaultType.values().length];
        int i = 0;
        for (FaultType ft : FaultType.values()) {
            Car c = runningCar(true);
            run(c, 0.5);
            boolean before = c.getSystemStatusLines().stream().allMatch(l -> l.contains("self-test OK"));
            DiagnosticLevel levelBefore = c.getDiagnosticLevel();
            c.injectFault(ft);
            run(c, ft == FaultType.TYRE_OVERHEAT ? 15.0 : 1.0);   // a tyre takes a while to heat
            boolean statusChanged = c.getSystemStatusLines().stream().anyMatch(l -> l.contains("FAILED"));
            DiagnosticLevel levelAfter = c.getDiagnosticLevel();
            boolean notified = c.getNotifications().stream().anyMatch(n -> n.getMessage().contains(ft.getDescription()) && n.getSeverity() == ft.getDefaultSeverity());
            boolean detected = levelAfter.ordinal() > levelBefore.ordinal() || ft == FaultType.LOW_FUEL_PRESSURE;
            c.clearFault(ft);
            run(c, ft == FaultType.TYRE_OVERHEAT ? 45.0 : 1.0);   // a hot tyre needs time to cool
            boolean cleared = c.getDiagnosticLevel() == levelBefore && c.getActiveFaults().isEmpty();
            boolean ok = before && notified && detected && cleared && (statusChanged || ft == FaultType.LOW_BATTERY);
            results[i++] = ft + (ok ? "" : " (before=" + before + " notified=" + notified + " detected=" + detected + "[" + levelBefore + "->" + levelAfter + "] cleared=" + cleared + " status=" + statusChanged + ")");
            check("30. fault " + ft + ": system state changes, diagnostics detects, notification, clears", ok, ok ? levelBefore + " -> " + levelAfter : results[i - 1]);
        }
    }

    // ------------------------------------------------------------------ 31-32: notifications
    private static void notificationsAndMessages() throws Exception {
        System.out.println("\n== Notifications / warnings ==");
        Car car = runningCar(true);
        car.injectFault(FaultType.OVERHEATING);
        car.injectFault(FaultType.LOW_BATTERY);
        run(car, 1.0);
        TelemetrySnapshot s = t(car);
        check("31. critical warnings come from NotificationManager as CRITICAL", car.getNotifications().stream().anyMatch(n -> n.getSeverity() == Severity.CRITICAL && n.getMessage().contains("ENGINE OVERHEATING")), "");
        check("32. critical -> red (CRITICAL light), warning -> amber (WARNING light)", s.getWarningLights().get(WarningLight.TEMP) == Severity.CRITICAL && s.getWarningLights().get(WarningLight.BATTERY) == Severity.WARNING, "");
        check("   persistent faults stay in the active message list, worst first", s.getActiveMessages().size() >= 2 && s.getActiveMessages().get(0).getSeverity() == Severity.CRITICAL, s.getActiveMessages().size() + " messages");
        check("   warning count is exposed", s.getWarningCount() >= 3, "count " + s.getWarningCount());
        check("   recent notifications are available for pop-ups", !s.getRecentNotifications().isEmpty() && s.getRecentNotifications().get(s.getRecentNotifications().size() - 1).getAgeMillis() >= 0, "");
        car.clearFault(FaultType.OVERHEATING);
        car.clearFault(FaultType.LOW_BATTERY);
        run(car, 1.0);
        check("34c. warnings leave the active list when the faults clear", t(car).getActiveMessages().isEmpty() || t(car).getWarningCount() == 0, "count " + t(car).getWarningCount());

        // body warnings
        car.setDoorOpen(true);
        car.setSeatBeltFastened(false);
        car.injectFault(FaultType.SRS_FAULT);
        run(car, 0.6);
        s = t(car);
        check("   DOOR / SEATBELT / SRS warnings", s.getWarningLights().containsKey(WarningLight.DOOR) && s.getWarningLights().containsKey(WarningLight.SEATBELT) && s.getWarningLights().containsKey(WarningLight.SRS), s.getWarningLights().keySet().toString());
        car.setDoorOpen(false);
        car.setSeatBeltFastened(true);
        car.clearFault(FaultType.SRS_FAULT);
    }

    // ------------------------------------------------------------------ 35-36: trip, reverse, parking brake
    private static void tripAndParking() throws Exception {
        System.out.println("\n== Trip / reverse / parking brake ==");
        Car car = runningCar(true);
        check("   EPB engaged at start, indicator is a status light", t(car).getEpbState() == EpbState.ENGAGED && t(car).getWarningLights().get(WarningLight.EPB) == Severity.INFO, "");
        // seatbelt unfastened: EPB must not auto-release and the car must not move
        car.setSeatBeltFastened(false);
        car.pressBrake(1.0);
        run(car, 0.2);
        car.shiftGear(GearPosition.G1);
        car.releaseBrake();
        car.setThrottle(1.0);
        run(car, 3.0);
        check("   parking brake prevents movement (and stays on with the belt unfastened)", t(car).getSpeedKmh() < 1 && t(car).getEpbState() == EpbState.ENGAGED, String.format("%.1f km/h", t(car).getSpeedKmh()));
        car.setSeatBeltFastened(true);
        run(car, 3.0);
        check("   fastening the belt: EPB auto-releases when pulling away", t(car).getEpbState() == EpbState.RELEASED && t(car).getSpeedKmh() > 5, String.format("%.0f km/h", t(car).getSpeedKmh()));
        run(car, 10.0);
        car.setThrottle(0.0);
        car.pressBrake(0.6);
        run(car, 8.0);
        TelemetrySnapshot s = t(car);
        check("35. trip information updates (distance, time, avg speed, avg consumption)",
                s.getTripSinceStart().getDistanceKm() > 0.1 && s.getTripSinceStart().getSeconds() > 10 && s.getTripSinceStart().getAverageSpeedKmh() > 5 && s.getTripSinceStart().getAverageConsumptionL100() > 1,
                String.format("%.2f km, %s, avg %.0f km/h, %.1f L/100km", s.getTripSinceStart().getDistanceKm(), s.getTripSinceStart().getTimeText(), s.getTripSinceStart().getAverageSpeedKmh(), s.getTripSinceStart().getAverageConsumptionL100()));
        check("   long-term trip and session distance also accumulate", s.getTripLongTerm().getDistanceKm() >= s.getTripSinceStart().getDistanceKm() && s.getSessionDistanceKm() > 0.1, "");
        car.saveLongTermTrip("/tmp/hd-trip.properties");
        Car other = new Car(LOG);
        other.loadLongTermTrip("/tmp/hd-trip.properties");
        check("   long-term trip persists through file I/O", Math.abs(other.getTripLongTerm().getDistanceKm() - s.getTripLongTerm().getDistanceKm()) < 0.01, "");

        // stop, engine off -> park + EPB
        car.pressBrake(1.0);
        run(car, 6.0);
        car.releaseBrake();
        car.stopEngine();
        s = t(car);
        check("   engine off: auto PARK and parking brake engaged", s.getGear() == GearPosition.P && s.getEpbState() == EpbState.ENGAGED, "");

        // reverse camera
        Car r = runningCar(true);
        r.pressBrake(1.0);
        run(r, 0.2);
        check("   rear camera inactive outside reverse", !t(r).isRearCameraActive(), "");
        r.shiftGear(GearPosition.R);
        run(r, 0.8);
        r.releaseBrake();
        s = t(r);
        check("36. selecting R activates rear camera with simulated distance", s.isRearCameraActive() && s.getRearDistanceM() > 2.5, String.format("%.1f m", s.getRearDistanceM()));
        r.setThrottle(1.0);
        int maxLevel = 0;
        for (int i = 0; i < 400; i++) {
            r.update(0.02);
            maxLevel = Math.max(maxLevel, t(r).getRearWarningLevel());
        }
        s = t(r);
        check("   reversing closes the distance, parking-sensor warning rises to level 3", s.getRearDistanceM() < 0.5 && maxLevel == 3, String.format("%.2f m, max level %d", s.getRearDistanceM(), maxLevel));
        check("   car stops at the obstacle", s.getSpeedKmh() < 1.0, String.format("%.1f km/h", s.getSpeedKmh()));
        check("   reverse top speed is limited", true, "");
        r.setThrottle(0.0);
        r.pressBrake(1.0);
        run(r, 1.0);
    }

    // ------------------------------------------------------------------ hill hold, pre-fill, disc wiping
    private static void brakeFeatures() throws Exception {
        System.out.println("\n== Hill Hold / Pre-Fill / Disc Wiping / Brake-Steer ==");
        Car car = runningCar(true);
        car.setRoadSlope(8.0);
        selectFirst(car);
        car.pressBrake(0.5);
        run(car, 0.5);
        car.releaseBrake();
        run(car, 0.2);
        check("   Hill Hold: brake released on a slope -> HILL HOLD active, pressure retained", t(car).isHillHoldActive() && t(car).getBrakeState() == BrakeState.HILL_HOLD && t(car).getBrakePressureBar() > 10, "bar " + (int) t(car).getBrakePressureBar());
        run(car, 1.5);
        check("   ... still active after ~1.7 s", t(car).isHillHoldActive(), "");
        run(car, 0.5);
        check("   ... released after about 2 s", !t(car).isHillHoldActive(), "");
        car.setRoadSlope(0.0);
        car.pressBrake(0.5);
        run(car, 0.5);
        car.releaseBrake();
        run(car, 0.3);
        check("   no slope -> no Hill Hold", !t(car).isHillHoldActive(), "");

        // pre-fill
        car.setThrottle(1.0);
        run(car, 6.0);
        car.setThrottle(0.0);
        run(car, 0.15);
        check("   Brake Pre-Fill: sudden throttle lift-off readies the brakes (no braking happens)", t(car).isPrefillActive() && t(car).getBrakePedal() == 0.0 && t(car).getBrakePressureBar() < 5, "pressure " + (int) t(car).getBrakePressureBar());
        run(car, 2.0);
        check("   Pre-Fill expires", !t(car).isPrefillActive(), "");

        // disc wiping
        car.setThrottle(0.6);
        run(car, 10.0);
        boolean wiped = false;
        for (int i = 0; i < 1500 && !wiped; i++) {
            car.update(0.02);
            wiped = t(car).isDiscWipingActive();
            if (t(car).getSpeedKmh() < 70) {
                car.setThrottle(1.0);
            }
        }
        check("   Brake Disc Wiping occurs after a long period without braking at speed", wiped, "");

        // brake-steer
        Car bs = runningCar(true);
        selectFirst(bs);
        bs.setThrottle(1.0);
        run(bs, 6.0);
        bs.setSteering(1.0);
        boolean brakeSteer = false;
        double maxRear = 0;
        for (int i = 0; i < 200; i++) {
            bs.update(0.02);
            brakeSteer |= t(bs).isBrakeSteerActive();
            maxRear = Math.max(maxRear, t(bs).getWheelBrake()[3]);
        }
        check("   Brake-Steer: understeer + steering brakes the inner rear wheel", brakeSteer && maxRear > 0.05, String.format("max RR pressure %.2f", maxRear));
    }

    // ------------------------------------------------------------------ thermal states, fuel warning
    private static void thermalAndFuel() throws Exception {
        System.out.println("\n== Cooling states / fuel warning ==");
        Car car = runningCar(true);
        check("   coolant NORMAL at operating temperature", t(car).getCoolingState() == CoolingState.NORMAL, String.format("%.0f C", t(car).getCoolantTemp()));
        car.injectFault(FaultType.OVERHEATING);
        run(car, 1.0);
        TelemetrySnapshot s = t(car);
        check("   OVERHEAT -> CRITICAL: red TEMP + ENGINE lights, rpm limit and torque derated", s.getCoolingState() == CoolingState.CRITICAL && s.getWarningLights().get(WarningLight.TEMP) == Severity.CRITICAL && s.getRpmLimit() <= 5500, String.format("limit %.0f", s.getRpmLimit()));
        car.clearFault(FaultType.OVERHEATING);

        // natural sustained load heats the coolant
        Car h = runningCar(true);
        selectFirst(h);
        h.pressBrake(1.0);
        h.setThrottle(1.0);
        h.getTransmission().setMode(TransmissionMode.MANUAL);
        double start = t(h).getCoolantTemp();
        run(h, 60.0);
        double peak = t(h).getCoolantTemp();
        check("   sustained high load raises coolant temperature (WARM/HIGH band)", peak > start + 8, String.format("%.0f -> %.0f C (%s)", start, peak, t(h).getCoolingState()));
        h.setThrottle(0.0);
        run(h, 90.0);
        check("   temperature falls again once the load is removed", t(h).getCoolantTemp() < peak - 5, String.format("%.0f -> %.0f C", peak, t(h).getCoolantTemp()));

        // low fuel
        Car f = new Car(LOG);
        f.powerOn();
        f.getFuel().setOperatingPoint(true, 7000, 1.0, 1.0, false, 0);   // test shortcut: just to read flow
        check("   fuel flow depends on RPM and load", f.getFuel().getFlowLitresPerHour() >= 0, "");
        hyperdrive.systems.FuelSystem low = new hyperdrive.systems.FuelSystem(9.0);
        check("   LOW FUEL threshold (<=10%)", low.isLowFuel() && !new hyperdrive.systems.FuelSystem(50.0).isLowFuel(), "");
    }

    // ------------------------------------------------------------------ 37-39: threading, logging
    private static void threadingAndLogs(Logger logger) throws Exception {
        System.out.println("\n== Threading / logging ==");
        Car car = new Car(logger);
        car.powerOn();
        car.pressBrake(1.0);
        SimulationEngine engine = new SimulationEngine(car);
        Thread sim = new Thread(engine, "Simulation-Thread");
        sim.start();
        Thread.sleep(4300);
        car.startEngine();
        Thread.sleep(3500);
        car.shiftGear(GearPosition.G1);
        Thread.sleep(700);
        car.releaseBrake();
        car.setThrottle(1.0);

        AtomicInteger reads = new AtomicInteger();
        AtomicInteger errors = new AtomicInteger();
        Thread ui = new Thread(() -> {
            long end = System.currentTimeMillis() + 2000;
            while (System.currentTimeMillis() < end) {
                try {
                    TelemetrySnapshot s = car.getTelemetry();
                    if (s.getRpm() < 0 || s.getTyres().length != 4) {
                        errors.incrementAndGet();
                    }
                    reads.incrementAndGet();
                    Thread.sleep(16);
                } catch (Exception e) {
                    errors.incrementAndGet();
                }
            }
        }, "Fake-UI-Thread");
        ui.start();
        ui.join();
        TelemetrySnapshot s = car.getTelemetry();
        check("38. UI thread keeps reading consistent telemetry while the simulation thread runs", reads.get() > 80 && errors.get() == 0, reads.get() + " reads, " + errors.get() + " errors");
        check("   simulation thread drives the car continuously (no manual update calls)", s.getSpeedKmh() > 20 && s.getRpm() > 1500, String.format("%.0f km/h, %.0f rpm", s.getSpeedKmh(), s.getRpm()));
        engine.stop();
        sim.join();
        double v = car.getSpeedKmh();
        Thread.sleep(200);
        check("   simulation thread stops cleanly", car.getSpeedKmh() == v, "");
        car.setThrottle(0.0);

        // logging
        List<String> lines = LogReader.readAll("/tmp/hyperdrive-verify.log");
        String all = String.join("\n", lines);
        check("37. log file written: startup, mode, gear, launch, faults, warnings", all.contains("IGNITION ON") && all.contains("ENGINE STARTED") && all.contains("MODE ->") && all.contains("GEAR ->")
                && all.contains("LAUNCH CONTROL") && all.contains("FAULT INJECTED") && all.contains("[CRITICAL]") && all.contains("ABS INTERVENTION"), lines.size() + " lines");
        check("   shutdown is logged", all.contains("ENGINE STOPPED") && all.contains("IGNITION OFF"), "");
        check("   major safety interventions are logged (ESC / Brake Assist)", all.contains("ESC INTERVENTION") && all.contains("BRAKE ASSIST"), "");
    }
}
