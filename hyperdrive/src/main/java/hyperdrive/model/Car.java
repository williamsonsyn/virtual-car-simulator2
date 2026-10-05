package hyperdrive.model;

import hyperdrive.enums.FaultType;
import hyperdrive.enums.GearPosition;
import hyperdrive.enums.LaunchState;
import hyperdrive.enums.PowerState;
import hyperdrive.enums.Severity;
import hyperdrive.exceptions.InvalidGearException;
import hyperdrive.exceptions.OperationDeniedException;
import hyperdrive.modes.ComfortMode;
import hyperdrive.modes.DriveMode;
import hyperdrive.modes.TrackMode;
import hyperdrive.safety.DiagnosticSystem;
import hyperdrive.sensors.SpeedSensor;
import hyperdrive.systems.Airbrake;
import hyperdrive.systems.BrakeSystem;
import hyperdrive.systems.CoolingSystem;
import hyperdrive.systems.ESCSystem;
import hyperdrive.systems.ElectricalSystem;
import hyperdrive.systems.Engine;
import hyperdrive.systems.FuelSystem;
import hyperdrive.systems.LaunchControl;
import hyperdrive.systems.Transmission;
import hyperdrive.systems.Tyre;
import hyperdrive.systems.TyreSystem;
import hyperdrive.systems.VehicleLift;
import hyperdrive.systems.VehicleSystem;
import hyperdrive.telemetry.DriveModeInfo;
import hyperdrive.telemetry.TelemetrySnapshot;
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
    private static final double CRITICAL_FAULT_SHUTDOWN_SECONDS = 10.0;

    private PowerState powerState = PowerState.SLEEP;
    private DriveMode currentMode = new ComfortMode();   // Comfort is always the default on start
    private double criticalFaultCountdown = -1;   // -1 = not counting down; otherwise seconds remaining

    private final Engine engine = new Engine();
    private final FuelSystem fuel = new FuelSystem();
    private final BrakeSystem brakes = new BrakeSystem();
    private final ElectricalSystem electrical = new ElectricalSystem();
    private final CoolingSystem cooling = new CoolingSystem();
    private final Transmission transmission = new Transmission();
    private final TyreSystem tyres = new TyreSystem();
    private final Airbrake airbrake = new Airbrake();
    private final VehicleLift lift = new VehicleLift();
    private final ESCSystem esc = new ESCSystem();
    private final LaunchControl launch = new LaunchControl();
    private final SpeedSensor speedSensor = new SpeedSensor();

    private final VehicleSystem[] systems;
    private final DiagnosticSystem diagnostics = new DiagnosticSystem();
    private final List<Fault> activeFaults = new ArrayList<>();
    private final NotificationManager notifications = new NotificationManager();

    public Car() {
        this(null);
    }

    /** Constructor overloading: pass a Logger to capture the session from the very first event. */
    public Car(hyperdrive.io.Logger logger) {
        // Different classes, one array type: this is what makes update() polymorphic.
        systems = new VehicleSystem[] {
            engine, fuel, brakes, electrical, cooling, transmission, tyres, airbrake, lift, esc, launch
        };
        if (logger != null) {
            notifications.attachLogger(logger);
        }
        addEvent("Vehicle created (SLEEP)");
    }

    /** Attach (or replace) the Logger after construction, if you didn't pass one to the constructor. */
    public synchronized void attachLogger(hyperdrive.io.Logger logger) { notifications.attachLogger(logger); }

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

    public synchronized void releaseBrake() {
        if (launch.getState() == LaunchState.READY) {
            launch.execute();
            double kick = Math.min(25.0, Transmission.getTopSpeed(transmission.getGear()));
            speedSensor.setValue(Math.min(SpeedSensor.MAX_SPEED_KMH, speedSensor.getValue() + kick));
            addEvent("LAUNCH EXECUTED");
        }
        brakes.release();
    }

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

    // ------------------------------------------------------------------ airbrake

    public synchronized void deployAirbrake() throws OperationDeniedException {
        requireAwake("DEPLOY AIRBRAKE");
        if (airbrake.isDeployed()) {
            deny("DEPLOY AIRBRAKE", "Airbrake is already deployed");
        }
        if (!engine.isRunning()) {
            deny("DEPLOY AIRBRAKE", "Engine must be running");
        }
        if (speedSensor.getValue() < Airbrake.MIN_MANUAL_DEPLOY_SPEED) {
            deny("DEPLOY AIRBRAKE", String.format("Speed too low - need at least %.0f km/h",
                    Airbrake.MIN_MANUAL_DEPLOY_SPEED));
        }
        airbrake.deployManual();
        addEvent("AIRBRAKE DEPLOYED (manual)");
    }

    public synchronized void retractAirbrake() throws OperationDeniedException {
        if (!airbrake.isDeployed()) {
            deny("RETRACT AIRBRAKE", "Airbrake is already retracted");
        }
        airbrake.retractManual();
        addEvent("AIRBRAKE RETRACTED (manual)");
    }

    // ------------------------------------------------------------------ vehicle lift

    public synchronized void raiseLift() throws OperationDeniedException {
        requireAwake("RAISE LIFT");
        if (!lift.isDown()) {
            deny("RAISE LIFT", "Lift is not currently down");
        }
        if (speedSensor.getValue() > 5.0) {
            deny("RAISE LIFT", "Vehicle must be stationary");
        }
        if (currentMode instanceof TrackMode) {
            deny("RAISE LIFT", "Lift is unavailable in Track mode");
        }
        lift.raise();
        addEvent("LIFT RAISED");
    }

    public synchronized void lowerLift() throws OperationDeniedException {
        if (lift.isDown()) {
            deny("LOWER LIFT", "Lift is already down");
        }
        lift.lowerManual();
        addEvent("LIFT LOWERED");
    }

    // ------------------------------------------------------------------ ESC

    public synchronized void setEscEnabled(boolean enabled) throws OperationDeniedException {
        if (!enabled && !(currentMode instanceof TrackMode)) {
            deny("ESC OFF", "ESC can only be disabled in Track mode");
        }
        esc.setEnabled(enabled);
        addEvent("ESC " + (enabled ? "ENABLED" : "DISABLED"));
    }

    // ------------------------------------------------------------------ launch control

    public synchronized void requestLaunch() throws OperationDeniedException {
        requireAwake("REQUEST LAUNCH");
        List<String> failures = new ArrayList<>();
        if (!currentMode.allowsLaunchControl()) {
            failures.add("Launch Control requires Track mode");
        }
        if (!engine.isRunning()) {
            failures.add("Engine must be running");
        }
        if (transmission.getGear() != GearPosition.G1) {
            failures.add("Gear must be 1st (currently " + transmission.getGear().getLabel() + ")");
        }
        if (brakes.getPedalPosition() < 0.9) {
            failures.add("Brake pedal must be fully pressed");
        }
        if (engine.getThrottle() < 0.9) {
            failures.add("Throttle must be fully pressed while braking");
        }
        if (speedSensor.getValue() > 5.0) {
            failures.add("Vehicle must be stationary");
        }
        if (!activeFaults.isEmpty()) {
            failures.add("No active faults allowed (" + activeFaults.size() + " active)");
        }
        if (!lift.isDown()) {
            failures.add("Vehicle lift must be down");
        }
        if (launch.getState() != LaunchState.IDLE) {
            failures.add("Launch sequence is already in progress");
        }
        if (!failures.isEmpty()) {
            deny("REQUEST LAUNCH", failures);
        }
        launch.arm();
        addEvent("LAUNCH ARMED - building boost");
    }

    public synchronized void abortLaunch() throws OperationDeniedException {
        if (launch.getState() == LaunchState.IDLE) {
            deny("ABORT LAUNCH", "No launch sequence is active");
        }
        launchAbort("Manually aborted");
    }

    private void launchAbort(String reason) {
        launch.abort();
        addEvent("LAUNCH ABORTED - " + reason, Severity.WARNING);
    }

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
        addEvent("FAULT INJECTED " + fault.getType().getDescription(), fault.getSeverity());
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
        airbrake.setInputs(speedSensor.getValue(), brakes.getPedalPosition(), running);
        lift.setInputs(speedSensor.getValue());
        esc.setInputs(engine.getThrottle(), speedSensor.getValue(), engine.getRpm(),
                transmission.getGear(), currentMode.isEscFullyActive());

        // Launch Control aborts itself if any of its conditions break while armed or ready.
        if (launch.getState() == LaunchState.ARMING || launch.getState() == LaunchState.READY) {
            if (!activeFaults.isEmpty()) {
                launchAbort("A fault became active");
            } else if (brakes.getPedalPosition() < 0.9) {
                launchAbort("Brake released early");
            } else if (engine.getThrottle() < 0.9) {
                launchAbort("Throttle released");
            } else if (transmission.getGear() != GearPosition.G1) {
                launchAbort("Gear changed");
            }
        }

        for (VehicleSystem s : systems) {
            s.update(dt);   // polymorphism: each system runs ITS OWN update()
        }
        updateSpeed(dt);

        if (running && fuel.getLevelPercent() <= 0.0) {
            engine.stop();
            powerState = PowerState.POWER_ON;
            addEvent("ENGINE STALLED - out of fuel", Severity.CRITICAL);
        }

        // Critical-fault shutdown: a critical fault while the engine is running starts a countdown.
        // Clearing every critical fault before it reaches zero cancels the countdown. If it reaches
        // zero, the engine shuts itself down - and startEngine()'s diagnostics already refuse to
        // restart while a critical fault is active, so no separate "locked out" flag is needed.
        if (engine.isRunning() && hasCriticalFault()) {
            if (criticalFaultCountdown < 0) {
                criticalFaultCountdown = CRITICAL_FAULT_SHUTDOWN_SECONDS;
                addEvent(String.format("CRITICAL FAULT ACTIVE - engine will shut down in %.0fs if not cleared",
                        CRITICAL_FAULT_SHUTDOWN_SECONDS), Severity.CRITICAL);
            } else {
                criticalFaultCountdown -= dt;
                if (criticalFaultCountdown <= 0) {
                    engine.stop();
                    powerState = PowerState.POWER_ON;
                    criticalFaultCountdown = -1;
                    addEvent("ENGINE SHUTDOWN - critical fault was not cleared in time", Severity.CRITICAL);
                }
            }
        } else if (criticalFaultCountdown >= 0) {
            criticalFaultCountdown = -1;
            addEvent("Critical fault cleared - shutdown countdown cancelled", Severity.INFO);
        }
    }

    private boolean hasCriticalFault() {
        for (Fault f : activeFaults) {
            if (f.getSeverity() == Severity.CRITICAL) {
                return true;
            }
        }
        return false;
    }

    /** Seconds until auto-shutdown if a critical fault isn't cleared in time; 0 if no countdown is active. */
    public synchronized double getCriticalFaultCountdown() { return Math.max(0.0, criticalFaultCountdown); }

    /**
     * Speed model, driven by throttle directly (not via RPM - see below for why).
     * No real physics - but the engine/wheel RELATIONSHIP is now mechanically honest: see the
     * RPM coupling step after speed is updated.
     */
    private void updateSpeed(double dt) {
        double speed = speedSensor.getValue();
        GearPosition gear = transmission.getGear();
        boolean coupled = gear.isForward() || gear == GearPosition.R;   // clutch/torque path to the wheels

        double target = 0.0;
        if (engine.isRunning() && coupled) {
            target = engine.getThrottle() * Transmission.getTopSpeed(gear);
            target *= (1.0 - esc.getInterventionStrength());   // ESC cuts acceleration during wheelspin
        }
        if (target > speed) {
            speed += (target - speed) * Math.min(1.0, dt * 0.8);    // accelerate
        } else {
            speed -= (speed - target) * Math.min(1.0, dt * 0.3);    // coast / engine braking
        }
        speed -= brakes.getBrakingForce() * 45.0 * dt;               // brakes: up to 45 km/h per second
        speed -= airbrake.getDragDeceleration() * dt;                // extra drag while the airbrake is out
        if (speed < 0.1) {
            speed = 0.0;
        }
        speedSensor.setValue(speed);

        // Mechanical RPM coupling: in a real geared car, the engine is tied to the wheels whenever
        // a gear is engaged - lifting off the throttle does NOT let RPM drop independently of speed,
        // it falls WITH speed (engine braking). We only let RPM run ahead of this mechanical value
        // while actively accelerating (throttle applied) - that gap is exactly what ESC's wheelspin
        // detection (Step 4) is looking for. Coasting or braking locks RPM straight to speed.
        if (coupled && engine.isRunning()) {
            double topSpeed = Transmission.getTopSpeed(gear);
            double speedFraction = (topSpeed > 0) ? Math.min(1.0, speed / topSpeed) : 0.0;
            double mechanicalRpm = Math.max(Engine.IDLE_RPM,
                    Engine.IDLE_RPM + speedFraction * (Engine.REDLINE_RPM - Engine.IDLE_RPM));

            if (engine.getThrottle() > 0.05) {
                // Accelerating: let the engine's own throttle-chase RPM stand (it may run ahead of
                // the mechanical value - that's wheelspin/ESC territory) but never let it read
                // BELOW what the wheels are mechanically forcing it to.
                if (engine.getRpm() < mechanicalRpm) {
                    engine.setCoupledRpm(mechanicalRpm);
                }
            } else {
                // Coasting or braking: no engine power being delivered, so there is no reason for
                // RPM to differ from the mechanical value - lock it, exactly like engine braking.
                engine.setCoupledRpm(mechanicalRpm);
            }
        }
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
        addEvent(operation + " DENIED: " + String.join("; ", reasons), Severity.WARNING);
        throw new OperationDeniedException(operation, reasons);
    }

    private void addEvent(String message) { notifications.add(message); }

    // Overload: explicit severity for anything more than routine information.
    private void addEvent(String message, Severity severity) { notifications.add(message, severity); }

    // ------------------------------------------------------------------ read access

    public synchronized PowerState getPowerState() { return powerState; }
    public synchronized double getSpeedKmh() { return speedSensor.getValue(); }
    public synchronized List<String> getEvents() {
        List<String> lines = new ArrayList<>();
        for (Notification n : notifications.getAll()) {
            lines.add(n.toString());
        }
        return lines;
    }

    public synchronized List<Notification> getNotifications() { return notifications.getAll(); }

    /** Active-issue view: warnings and critical notifications only. */
    public synchronized List<Notification> getWarnings() { return notifications.getBySeverity(Severity.WARNING); }
    public synchronized List<String> getDiagnosticReport() { return diagnostics.getReport(this); }
    public synchronized Tyre[] getTyreSnapshot() { return tyres.getSnapshot(); }
    public synchronized hyperdrive.enums.AirbrakeState getAirbrakeState() { return airbrake.getState(); }
    public synchronized hyperdrive.enums.LiftState getLiftState() { return lift.getState(); }
    public synchronized boolean isEscEnabled() { return esc.isEnabled(); }
    public synchronized double getDriftLevel() { return esc.getDriftLevel(); }
    public synchronized LaunchState getLaunchState() { return launch.getState(); }
    public synchronized double getLaunchBoostPercent() { return launch.getBoostPercent(); }

    public synchronized List<String> getSystemStatusLines() {
        List<String> lines = new ArrayList<>();
        for (VehicleSystem s : systems) {
            lines.add(s.toString() + (s.selfTest() ? "  self-test OK" : "  self-test FAILED"));
        }
        return lines;
    }

    public synchronized double getRpm() { return engine.getRpm(); }
    public synchronized double getFuelLevelPercent() { return fuel.getLevelPercent(); }

    /**
     * One atomic read of everything a dashboard needs, captured under a single synchronized call.
     * This is the safe alternative to calling several separate getters: nothing can change
     * partway through building this snapshot, even with a SimulationEngine ticking concurrently.
     */
    public synchronized TelemetrySnapshot getTelemetry() {
        DriveModeInfo modeInfo = new DriveModeInfo(currentMode.getName(), currentMode.getThrottleResponse(),
                currentMode.isEscFullyActive(), currentMode.allowsLaunchControl());
        return new TelemetrySnapshot(
                powerState, modeInfo, speedSensor.getValue(), engine.getRpm(), engine.getThrottle(),
                transmission.getGear(), fuel.getLevelPercent(), fuel.getPressure(),
                cooling.getCoolantTemp(), cooling.getOilTemp(), brakes.getDiscTemperature(),
                brakes.isAbsActive(), electrical.getVoltage(), airbrake.getState(), lift.getState(), esc.isEnabled(),
                esc.getDriftLevel(), launch.getState(), launch.getBoostPercent(),
                tyres.getSnapshot(), activeFaults.size(), getCriticalFaultCountdown());
    }

    // Live system access, used by the SafetyChecks - those run from inside Car's own synchronized
    // methods, so they're safe. These are also `synchronized` now for defense in depth, but that only
    // protects the moment of handing the reference out: whatever the caller does with the Engine/
    // FuelSystem/etc. object AFTER that is NOT synchronized against a background thread calling
    // Car.update(). Fine for the sequential console demo below; NOT safe to read from a second thread
    // while a SimulationEngine is ticking - use the scalar getters above (or Step 8's telemetry) for that.
    public synchronized Engine getEngine() { return engine; }
    public synchronized FuelSystem getFuel() { return fuel; }
    public synchronized BrakeSystem getBrakes() { return brakes; }
    public synchronized ElectricalSystem getElectrical() { return electrical; }
    public synchronized CoolingSystem getCooling() { return cooling; }
    public synchronized Transmission getTransmission() { return transmission; }
}
