package hyperdrive.model;

import hyperdrive.enums.FaultType;
import hyperdrive.enums.GearPosition;
import hyperdrive.enums.PowerState;
import hyperdrive.exceptions.InvalidGearException;
import hyperdrive.exceptions.OperationDeniedException;
import hyperdrive.modes.ComfortMode;
import hyperdrive.modes.DriveMode;
import hyperdrive.safety.DiagnosticSystem;
import hyperdrive.sensors.SpeedSensor;
import hyperdrive.systems.BrakeSystem;
import hyperdrive.systems.CoolingSystem;
import hyperdrive.systems.ElectricalSystem;
import hyperdrive.systems.Engine;
import hyperdrive.systems.FuelSystem;
import hyperdrive.systems.Transmission;
import hyperdrive.systems.Tyre;
import hyperdrive.systems.TyreSystem;
import hyperdrive.systems.VehicleSystem;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * The Car OWNS all its systems (composition) and is the ONLY place where operations are authorised.
 * The UI never decides anything - it calls these methods and shows the result.
 * Public methods are synchronized because the simulation thread (step 9) and the UI thread will share this object.
 */
public class Car {
    private static final double MODE_CHANGE_MAX_SPEED = 20.0;   // km/h - must slow down to switch modes

    private PowerState powerState = PowerState.SLEEP;
    private DriveMode currentMode = new ComfortMode();   // Comfort is always the default on start

    private final Engine engine = new Engine();
    private final FuelSystem fuel = new FuelSystem();
    private final BrakeSystem brakes = new BrakeSystem();
    private final ElectricalSystem electrical = new ElectricalSystem();
    private final CoolingSystem cooling = new CoolingSystem();
    private final Transmission transmission = new Transmission();
    private final TyreSystem tyres = new TyreSystem();
    private final SpeedSensor speedSensor = new SpeedSensor();

    private final VehicleSystem[] systems;
    private final DiagnosticSystem diagnostics = new DiagnosticSystem();
    private final List<Fault> activeFaults = new ArrayList<>();
    private final List<String> events = new ArrayList<>();

    public Car() {
        // Different classes, one array type: this is what makes update() polymorphic.
        systems = new VehicleSystem[] {engine, fuel, brakes, electrical, cooling, transmission, tyres};
        addEvent("Vehicle created (SLEEP)");
    }

    // ------------------------------------------------------------------ power / engine

    public synchronized void powerOn() throws OperationDeniedException {
        if (powerState != PowerState.SLEEP) {
            deny("POWER ON", "Vehicle is already awake");
        }
        if (electrical.getVoltage() < ElectricalSystem.MIN_WAKE_VOLTAGE) {
            deny("POWER ON", "Battery too low to wake the vehicle");
        }
        powerState = PowerState.POWER_ON;
        addEvent("POWER ON - vehicle awake");
    }

    public synchronized void powerOff() throws OperationDeniedException {
        if (powerState == PowerState.SLEEP) {
            deny("POWER OFF", "Vehicle is already asleep");
        }
        if (powerState == PowerState.ENGINE_RUNNING) {
            deny("POWER OFF", "Stop the engine first");
        }
        powerState = PowerState.SLEEP;
        addEvent("POWER OFF - vehicle asleep");
    }

    public synchronized void startEngine() throws OperationDeniedException {
        if (powerState == PowerState.SLEEP) {
            deny("START ENGINE", "Vehicle is asleep - power on first");
        }
        if (powerState == PowerState.ENGINE_RUNNING) {
            deny("START ENGINE", "Engine is already running");
        }
        List<String> failures = diagnostics.runStartupChecks(this);
        if (!failures.isEmpty()) {
            deny("START ENGINE", failures);
        }
        engine.start();
        powerState = PowerState.ENGINE_RUNNING;
        addEvent("ENGINE STARTED");
    }

    public synchronized void stopEngine() throws OperationDeniedException {
        if (powerState != PowerState.ENGINE_RUNNING) {
            deny("STOP ENGINE", "Engine is not running");
        }
        if (speedSensor.getValue() > 5.0) {
            deny("STOP ENGINE", "Vehicle is still moving");
        }
        engine.stop();
        powerState = PowerState.POWER_ON;
        addEvent("ENGINE STOPPED");
    }

    // ------------------------------------------------------------------ driving inputs

    public synchronized void setThrottle(double amount) { engine.setThrottle(amount); }

    public synchronized void pressBrake(double amount) { brakes.apply(amount); }

    public synchronized void releaseBrake() { brakes.release(); }

    public synchronized void shiftGear(GearPosition target) throws OperationDeniedException {
        requireAwake("SHIFT TO " + target.getLabel());
        try {
            transmission.selectGear(target, speedSensor.getValue(), brakes.isPedalPressed());
            addEvent("GEAR -> " + transmission.getGear().getLabel());
        } catch (InvalidGearException e) {
            deny("SHIFT TO " + target.getLabel(), e.getMessage());
        }
    }

    public synchronized void shiftUp() throws OperationDeniedException {
        requireAwake("UPSHIFT");
        try {
            transmission.shiftUp(speedSensor.getValue(), brakes.isPedalPressed());
            addEvent("GEAR -> " + transmission.getGear().getLabel());
        } catch (InvalidGearException e) {
            deny("UPSHIFT", e.getMessage());
        }
    }

    public synchronized void shiftDown() throws OperationDeniedException {
        requireAwake("DOWNSHIFT");
        try {
            transmission.shiftDown(speedSensor.getValue(), brakes.isPedalPressed());
            addEvent("GEAR -> " + transmission.getGear().getLabel());
        } catch (InvalidGearException e) {
            deny("DOWNSHIFT", e.getMessage());
        }
    }

    // ------------------------------------------------------------------ driving mode

    public synchronized void selectMode(DriveMode target) throws OperationDeniedException {
        requireAwake("SELECT " + target.getName().toUpperCase() + " MODE");
        if (target.getClass() == currentMode.getClass()) {
            deny("SELECT " + target.getName().toUpperCase() + " MODE", "Already in " + target.getName() + " mode");
        }
        if (speedSensor.getValue() > MODE_CHANGE_MAX_SPEED) {
            deny("SELECT " + target.getName().toUpperCase() + " MODE", String.format(
                    "Slow below %.0f km/h to change driving mode (currently %.0f km/h)",
                    MODE_CHANGE_MAX_SPEED, speedSensor.getValue()));
        }
        if (target.allowsLaunchControl() && !activeFaults.isEmpty()) {
            // Track mode unlocks launch control, so it demands a clean bill of health.
            deny("SELECT " + target.getName().toUpperCase() + " MODE",
                    "Track mode requires no active faults (" + activeFaults.size() + " active)");
        }
        currentMode = target;
        addEvent("MODE -> " + target.getName());
    }

    public synchronized DriveMode getCurrentMode() { return currentMode; }

    // ------------------------------------------------------------------ faults

    public synchronized void injectFault(FaultType type) {
        for (Fault f : activeFaults) {
            if (f.getType() == type) {
                return;   // already active
            }
        }
        Fault fault = new Fault(type);
        activeFaults.add(fault);
        for (VehicleSystem s : systems) {
            if (s.handles(type)) {
                s.injectFault(type);
            }
        }
        addEvent("FAULT INJECTED " + fault);
    }

    public synchronized void clearFault(FaultType type) {
        Iterator<Fault> it = activeFaults.iterator();
        while (it.hasNext()) {
            if (it.next().getType() == type) {
                it.remove();
            }
        }
        for (VehicleSystem s : systems) {
            s.clearFault(type);
        }
        addEvent("FAULT CLEARED " + type.getDescription());
    }

    /** Returns COPIES of the faults (copy constructor) - the UI cannot modify the real list. */
    public synchronized List<Fault> getActiveFaults() {
        List<Fault> copy = new ArrayList<>();
        for (Fault f : activeFaults) {
            copy.add(new Fault(f));
        }
        return copy;
    }

    // ------------------------------------------------------------------ simulation step

    /** Advance the whole car by dt seconds. The SimulationEngine thread will call this repeatedly. */
    public synchronized void update(double dt) {
        boolean running = engine.isRunning();
        double load = engine.getRpm() / Engine.REDLINE_RPM;

        // The Car passes information between systems, so the systems never depend on each other.
        engine.setResponseFactor(currentMode.getThrottleResponse());
        fuel.setEngineLoad(load);
        cooling.setEngineState(running, load);
        electrical.setAlternatorActive(running);
        tyres.setSpeed(speedSensor.getValue());
        brakes.setSpeed(speedSensor.getValue());

        for (VehicleSystem s : systems) {
            s.update(dt);   // polymorphism: each system runs ITS OWN update()
        }
        updateSpeed(dt);

        if (running && fuel.getLevelPercent() <= 0.0) {
            engine.stop();
            powerState = PowerState.POWER_ON;
            addEvent("ENGINE STALLED - out of fuel");
        }
    }

    /** Deliberately simple speed model: speed chases (rpm fraction x gear top speed). No real physics. */
    private void updateSpeed(double dt) {
        double speed = speedSensor.getValue();
        GearPosition gear = transmission.getGear();

        double target = 0.0;
        if (engine.isRunning() && (gear.isForward() || gear == GearPosition.R)) {
            target = (engine.getRpm() / Engine.REDLINE_RPM) * Transmission.getTopSpeed(gear);
        }
        if (target > speed) {
            speed += (target - speed) * Math.min(1.0, dt * 0.8);    // accelerate
        } else {
            speed -= (speed - target) * Math.min(1.0, dt * 0.3);    // coast / engine braking
        }
        speed -= brakes.getBrakingForce() * 45.0 * dt;               // brakes: up to 45 km/h per second
        if (speed < 0.1) {
            speed = 0.0;
        }
        speedSensor.setValue(speed);
    }

    // ------------------------------------------------------------------ helpers

    private void requireAwake(String operation) throws OperationDeniedException {
        if (powerState == PowerState.SLEEP) {
            deny(operation, "Vehicle is asleep - power on first");
        }
    }

    // Overloaded: one reason ...
    private void deny(String operation, String reason) throws OperationDeniedException {
        deny(operation, List.of(reason));
    }

    // ... or several. Always logs the denial and throws.
    private void deny(String operation, List<String> reasons) throws OperationDeniedException {
        addEvent(operation + " DENIED: " + String.join("; ", reasons));
        throw new OperationDeniedException(operation, reasons);
    }

    private void addEvent(String message) { events.add(message); }

    // ------------------------------------------------------------------ read access

    public synchronized PowerState getPowerState() { return powerState; }
    public synchronized double getSpeedKmh() { return speedSensor.getValue(); }
    public synchronized List<String> getEvents() { return new ArrayList<>(events); }
    public synchronized List<String> getDiagnosticReport() { return diagnostics.getReport(this); }
    public synchronized Tyre[] getTyreSnapshot() { return tyres.getSnapshot(); }

    public synchronized List<String> getSystemStatusLines() {
        List<String> lines = new ArrayList<>();
        for (VehicleSystem s : systems) {
            lines.add(s.toString() + (s.selfTest() ? "  self-test OK" : "  self-test FAILED"));
        }
        return lines;
    }

    // Live system access, used by the SafetyChecks (read-only by convention).
    // In the UI step we will hand the screens snapshots instead.
    public Engine getEngine() { return engine; }
    public FuelSystem getFuel() { return fuel; }
    public BrakeSystem getBrakes() { return brakes; }
    public ElectricalSystem getElectrical() { return electrical; }
    public CoolingSystem getCooling() { return cooling; }
    public Transmission getTransmission() { return transmission; }
}
