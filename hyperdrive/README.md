# HyperDrive: Object-Oriented Hypercar Control & Simulation System
Educational simulator INSPIRED by the McLaren 720S. Not McLaren software; all logic and thresholds are our own simplifications.
UI branding: HYPERDRIVE X-01.

## Status: Step 9 - JavaFX Cockpit/Dashboard screen (first UI screen)
Everything from Step 8, plus a real JavaFX window: HyperDriveApp, the Cockpit/Dashboard screen (1 of the
original 3 planned screens - Diagnostics and Fault Simulator come in a later pass). Keyboard-driven,
dark themed, backed by the exact same Car + SimulationEngine + TelemetrySnapshot from the console steps -
no new vehicle logic was written for the UI, it only calls Car's public methods and displays the result.

**IMPORTANT: this step is UNVERIFIED by me.** I have no display and no access to Maven Central in my
environment, so I could not compile or run this with the real JavaFX libraries - only cross-check every
method call against our own Car/TelemetrySnapshot source (all present and correctly named) and confirm the
file has no syntax errors (compiled it without the JavaFX jars on the classpath and got only "package does
not exist" / "cannot find symbol" errors - no parse errors). You are the first real compile of this file.
Run it and paste back whatever happens, exactly like every Windows/Maven run so far - that's how we'll
find and fix anything wrong with it.

## Run the console demo
Needs JDK 17+.

Without Maven (works everywhere):
    javac -d out -sourcepath src/main/java src/main/java/hyperdrive/Main.java
    java -cp out hyperdrive.Main

With Maven:
    mvn compile exec:java

## Run the JavaFX UI
    mvn clean javafx:run

Do NOT try to launch HyperDriveApp with plain `java` or with an IDE "Run" button that bypasses Maven -
JavaFX needs its native platform modules on the module path, which only the javafx-maven-plugin sets up
here. Launching it directly typically fails with:
    Error: JavaFX runtime components are missing, and are required to run this application
If you see that error, you're not using `mvn clean javafx:run` - switch to that command.

Other things that could go wrong (all untested by me - report back what you actually see):
- First run downloads the org.openjfx jars from Maven Central - needs internet access once.
- If `javafx-maven-plugin` version 0.0.8 isn't found, try 0.0.6 or check search.maven.org for the latest.
- If the window opens but is blank/styling looks off, the CSS path
  (src/main/resources/hyperdrive/ui/dashboard.css) might not be on the classpath - confirm the file exists
  at exactly that path relative to the project root.
- Every key in the on-screen hint should work except A/D (no steering system exists to bind to) and F/T
  (their screens don't exist yet).

## Packages
| Package | Contents |
|---|---|
| hyperdrive.enums | PowerState, GearPosition, FaultType, Severity |
| hyperdrive.exceptions | OperationDeniedException, InvalidGearException |
| hyperdrive.sensors | Sensor (abstract), PressureSensor, TemperatureSensor, SpeedSensor |
| hyperdrive.systems | Faultable, Loggable, VehicleSystem (abstract), Engine, FuelSystem, BrakeSystem, ElectricalSystem, CoolingSystem, Transmission, Tyre, TyreSystem |
| hyperdrive.safety | SafetyCheck (interface), Battery/FuelPressure/Brake/Temperature/Transmission checks, DiagnosticSystem |
| hyperdrive.modes | DriveMode (abstract), ComfortMode, SportMode, TrackMode (extends SportMode) |
| hyperdrive.systems (new) | Airbrake, VehicleLift, ESCSystem, LaunchControl - all extend VehicleSystem |
| hyperdrive.model | Car, Fault, Notification, NotificationManager |
| hyperdrive.io | Logger (FileWriter/BufferedWriter, append mode), LogReader (FileReader/BufferedReader) |
| hyperdrive.sim | SimulationEngine (implements Runnable - the recurring tick loop), StartupSequenceThread (extends Thread - a one-shot task) |
| hyperdrive.telemetry | TelemetrySnapshot (immutable dashboard DTO), DriveModeInfo (small immutable copy of the current mode) |
| hyperdrive.ui | HyperDriveApp - the JavaFX Cockpit/Dashboard screen (extends Application) |

## Design rule
The UI calls Car. Car decides (via SafetyChecks and the systems' own rules) and throws OperationDeniedException with every reason.
No decision logic in UI event handlers.

## Thread safety
Every public method on Car is `synchronized`, including the six "live system" getters (getEngine() etc.) -
but synchronizing a GETTER only protects the moment it hands the reference out. Whatever the caller does with
the returned Engine/FuelSystem/etc. object afterward is NOT synchronized against a background SimulationEngine
calling Car.update(). This is fine for the sequential console demo (nothing else touches Car while Main runs a
scripted section), but NOT safe to read from a second thread while a SimulationEngine is ticking - use the
scalar getters (getSpeedKmh(), getRpm(), getFuelLevelPercent(), ...) for that instead. This exact distinction
is demonstrated in Main's Section 14.

Step 8 goes one step further: Car.getTelemetry() returns a TelemetrySnapshot built from ALL of those
values under a SINGLE synchronized call, so they're guaranteed to be from the same instant - not just
individually safe, but consistent as a group. This is what the JavaFX UI (Step 9) will actually poll.

## Next steps
Diagnostics screen | Fault Simulator screen | A/D steering (no system to bind to yet) | F/T screen-switch keys |
10 polish (input debounce, throttle/brake ramping instead of instant on/off) | 11 viva prep

## Log file
Running Main creates/appends to logs/hyperdrive-session.log (git-ignored). Each run adds a
"=== Session started ... ===" header, then every notification, with a timestamp, in real time as it happens -
not batched at the end. Delete the file any time to start a fresh history.

## Known simplifications (be ready to explain these)
- Automatic transitions (airbrake auto-deploy, lift auto-lower, ESC cutting in) are NOT logged as notifications
  yet - only user-triggered actions and faults are. Since logging now happens automatically for every
  notification, this is really a NotificationManager question, not a Logger one, if it's ever added.
- Launch Control's "boost" is a state-machine percentage, not a physical quantity - the speed kick on execute
  is a fixed, simplified burst, not a physics simulation.
- ESC's slip detection compares RPM-implied speed to actual speed; it is deliberately NOT real tyre physics.
