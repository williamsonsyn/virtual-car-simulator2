package hyperdrive.safety;

import hyperdrive.enums.AirbrakeState;
import hyperdrive.enums.BatteryState;
import hyperdrive.enums.CoolingState;
import hyperdrive.enums.DiagnosticLevel;
import hyperdrive.enums.EpbState;
import hyperdrive.enums.EscMode;
import hyperdrive.enums.LaunchState;
import hyperdrive.enums.LiftState;
import hyperdrive.enums.OilState;
import hyperdrive.enums.PowerState;
import hyperdrive.enums.Severity;
import hyperdrive.enums.WarningLight;
import hyperdrive.model.Car;
import hyperdrive.model.Notification;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Runs a list of checks. It does not know what each check does - it just calls check() on every SafetyCheck
 * (polymorphism through an interface). Results are PASS / WARNING / FAULT / CRITICAL; only FAULT and CRITICAL stop the
 * engine starting. The same class also turns the live state of the car into the warning telltales and their messages,
 * so the JavaFX code never contains diagnostic logic - it only displays what this class decided.
 */
public class DiagnosticSystem {
    private final SafetyCheck[] checks;
    private final SafetyCheck startInterlock = new StartInterlockCheck();

    public DiagnosticSystem() {
        checks = new SafetyCheck[] {
            new BatteryCheck(),
            new FuelPressureCheck(),
            new OilCheck(),
            new BrakeCheck(),
            new TemperatureCheck(),
            new TransmissionCheck(),
            new TyreCheck(),
            new SystemsCheck()
        };
    }

    /** Returns one line per BLOCKING failed check (empty list = the engine may start). Warnings do not block. */
    public List<String> runStartupChecks(Car car) {
        List<String> failures = new ArrayList<>();
        if (!startInterlock.check(car)) {
            failures.add(startInterlock.getName() + ": " + startInterlock.getFailureReason());
        }
        for (SafetyCheck check : checks) {
            if (!check.check(car) && check.getLevel().isBlocking()) {
                failures.add(check.getName() + ": " + check.getFailureReason());
            }
        }
        return failures;
    }

    /** One line per check - PASS, WARN, FAIL or CRIT - this feeds the Diagnostics screen. */
    public List<String> getReport(Car car) {
        List<String> report = new ArrayList<>();
        for (DiagnosticResult r : getResults(car)) {
            report.add(r.toString());
        }
        return report;
    }

    /** Structured results of every check. */
    public List<DiagnosticResult> getResults(Car car) {
        List<DiagnosticResult> results = new ArrayList<>();
        for (SafetyCheck check : checks) {
            check.check(car);
            results.add(new DiagnosticResult(check.getName(), check.getLevel(), check.getFailureReason()));
        }
        return results;
    }

    /** The worst level across all checks. */
    public DiagnosticLevel getOverallLevel(Car car) {
        DiagnosticLevel worst = DiagnosticLevel.PASS;
        for (SafetyCheck check : checks) {
            check.check(car);
            worst = worst.worse(check.getLevel());
        }
        return worst;
    }

    /**
     * Which telltales should be lit right now, with the message for each. INFO severity = a status light (green/blue/neutral),
     * WARNING = amber, CRITICAL = red. Does not include short-lived events (ABS pulse, ESC intervention) - those come
     * straight from the systems' flags.
     */
    public Map<WarningLight, Notification> evaluateWarnings(Car car) {
        Map<WarningLight, Notification> w = new EnumMap<>(WarningLight.class);
        PowerState ps = car.getPowerState();
        if (ps == PowerState.SLEEP) {
            return w;
        }
        boolean running = ps == PowerState.ENGINE_RUNNING;

        // Engine / oil / temperature
        OilState oil = car.getOil().getState();
        if (oil == OilState.LOW_PRESSURE) {
            put(w, WarningLight.OIL, "LOW OIL PRESSURE", Severity.CRITICAL);
            put(w, WarningLight.ENGINE, "ENGINE POWER REDUCED - LOW OIL PRESSURE", Severity.CRITICAL);
        } else if (oil == OilState.HOT) {
            put(w, WarningLight.OIL, "OIL TEMPERATURE HIGH", Severity.WARNING);
        }
        CoolingState cs = car.getCooling().getState();
        if (cs == CoolingState.CRITICAL) {
            put(w, WarningLight.TEMP, "ENGINE OVERHEATING - STOP SAFELY", Severity.CRITICAL);
            put(w, WarningLight.ENGINE, "ENGINE POWER REDUCED - OVERHEATING", Severity.CRITICAL);
        } else if (cs == CoolingState.HIGH) {
            put(w, WarningLight.TEMP, "ENGINE TEMPERATURE HIGH", Severity.WARNING);
        }
        if (!car.getFuel().isSensorWorking() || !car.getFuel().isPressureSafe()) {
            put(w, WarningLight.ENGINE, "ENGINE FUEL SYSTEM FAULT", car.getFuel().isPressureSafe()
                    ? Severity.WARNING : Severity.CRITICAL);
        }
        if (car.getFuel().isLowFuel()) {
            put(w, WarningLight.FUEL, "LOW FUEL", Severity.WARNING);
        }

        // Battery
        BatteryState bs = car.getElectrical().getBatteryState();
        if (bs == BatteryState.FAULT) {
            put(w, WarningLight.BATTERY, "BATTERY FAULT", Severity.WARNING);
        } else if (bs == BatteryState.LOW) {
            put(w, WarningLight.BATTERY, "BATTERY LOW", Severity.WARNING);
        }

        // Brakes, ABS, ESC, parking brake
        if (!car.getBrakes().isPressureSafe()) {
            put(w, WarningLight.BRAKE, "BRAKE FAULT - LOW PRESSURE", Severity.CRITICAL);
        } else if (car.getBrakes().getDiscTemperature() > 0.9 * car.getBrakes().getDiscTemperatureLimit()) {
            put(w, WarningLight.BRAKE, "BRAKE TEMPERATURE HIGH", Severity.WARNING);
        }
        if (car.getBrakes().hasAbsFault()) {
            put(w, WarningLight.ABS, "ABS FAULT", Severity.WARNING);
        }
        if (!car.getEsc().selfTest()) {
            put(w, WarningLight.ESC, "ESC FAULT", Severity.WARNING);
        } else if (car.getEsc().getMode() == EscMode.OFF) {
            put(w, WarningLight.ESC, "ESC OFF", Severity.WARNING);
        }
        if (car.getEpb().getState() == EpbState.ENGAGED) {
            put(w, WarningLight.EPB, "PARKING BRAKE ENGAGED", Severity.INFO);
        }

        // Tyres
        if (car.getTyres().hasSensorFault()) {
            put(w, WarningLight.TPMS, "TPMS FAULT", Severity.WARNING);
        } else if (car.getTyres().hasLowPressure()) {
            put(w, WarningLight.TPMS, "TPMS WARNING - LOW PRESSURE " + car.getTyres().firstLowPressurePosition(),
                    Severity.WARNING);
        } else if (car.getTyres().hasHotTyre()) {
            put(w, WarningLight.TPMS, "TYRE TEMPERATURE HIGH " + car.getTyres().firstHotPosition(), Severity.WARNING);
        }

        // Body
        if (!car.getSeatBelt().isFastened() && running) {
            put(w, WarningLight.SEATBELT, "FASTEN SEATBELT", Severity.WARNING);
        }
        if (!car.getSrs().selfTest()) {
            put(w, WarningLight.SRS, "SRS FAULT", Severity.WARNING);
        }
        if (car.getDoors().isOpen()) {
            put(w, WarningLight.DOOR, "DOOR OPEN", Severity.WARNING);
        }

        // Transmission, airbrake, lift, launch
        if (car.getTransmission().hasTransmissionFault()) {
            put(w, WarningLight.TRANS, "TRANSMISSION FAULT", Severity.CRITICAL);
        }
        AirbrakeState as = car.getAirbrake().getState();
        if (as == AirbrakeState.UNAVAILABLE) {
            put(w, WarningLight.AIRBRAKE, "AIRBRAKE TEMPORARILY UNAVAILABLE", Severity.WARNING);
        } else if (as == AirbrakeState.FAULT) {
            put(w, WarningLight.AIRBRAKE, "AIRBRAKE FAULT", Severity.WARNING);
        } else if (car.getAirbrake().isSelfTesting()) {
            put(w, WarningLight.AIRBRAKE, "AIRBRAKE SELF-TEST", Severity.INFO);
        } else if (as == AirbrakeState.DEPLOYED || as == AirbrakeState.DEPLOYING) {
            put(w, WarningLight.AIRBRAKE, "AIRBRAKE DEPLOYED", Severity.INFO);
        }
        LiftState ls = car.getLift().getState();
        if (!car.getLift().selfTest()) {
            put(w, WarningLight.LIFT, "VEHICLE LIFT FAULT", Severity.WARNING);
        } else if (ls != LiftState.NORMAL) {
            put(w, WarningLight.LIFT, "VEHICLE LIFT ACTIVE", Severity.INFO);
        }
        LaunchState lcs = car.getLaunch().getState();
        if (lcs == LaunchState.UNAVAILABLE) {
            put(w, WarningLight.LAUNCH, "LAUNCH CONTROL UNAVAILABLE", Severity.WARNING);
        } else if (lcs == LaunchState.READY) {
            put(w, WarningLight.LAUNCH, "LAUNCH CONTROL READY", Severity.INFO);
        } else if (lcs != LaunchState.OFF && lcs != LaunchState.ABORTED) {
            put(w, WarningLight.LAUNCH, "LAUNCH CONTROL " + lcs.getLabel(), Severity.INFO);
        }
        return w;
    }

    private void put(Map<WarningLight, Notification> map, WarningLight light, String message, Severity severity) {
        Notification existing = map.get(light);
        if (existing == null || severity.ordinal() > existing.getSeverity().ordinal()) {
            map.put(light, new Notification(message, severity));
        }
    }
}
