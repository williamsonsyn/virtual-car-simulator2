package hyperdrive.safety;

import hyperdrive.model.Car;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs a list of checks. It does not know what each check does - it just calls
 * check() on every SafetyCheck (polymorphism through an interface).
 */
public class DiagnosticSystem {
    private final SafetyCheck[] startupChecks;

    public DiagnosticSystem() {
        startupChecks = new SafetyCheck[] {
            new BatteryCheck(),
            new FuelPressureCheck(),
            new BrakeCheck(),
            new TemperatureCheck(),
            new TransmissionCheck()
        };
    }

    /** Returns one line per FAILED check (empty list = all passed). */
    public List<String> runStartupChecks(Car car) {
        List<String> failures = new ArrayList<>();
        for (SafetyCheck check : startupChecks) {
            if (!check.check(car)) {
                failures.add(check.getName() + ": " + check.getFailureReason());
            }
        }
        return failures;
    }

    /** One line per check, PASS or FAIL - this will feed the Diagnostics screen. */
    public List<String> getReport(Car car) {
        List<String> report = new ArrayList<>();
        for (SafetyCheck check : startupChecks) {
            if (check.check(car)) {
                report.add("PASS  " + check.getName());
            } else {
                report.add("FAIL  " + check.getName() + " - " + check.getFailureReason());
            }
        }
        return report;
    }
}
