# HyperDrive: Object-Oriented Hypercar Control & Simulation System
Educational simulator INSPIRED by the McLaren 720S. Not McLaren software; all logic and thresholds are our own simplifications.
UI branding: HYPERDRIVE X-01.

## Status: Step 9c - critical-fault auto-shutdown + Fault Simulator screen
Step 9's Cockpit screen was tested end-to-end by the user and confirmed working: engine start/brake
interlock, live telemetry, mode-switch denials, clean shutdown all verified on a real run.

This step adds the Fault Simulator screen (2 of 3 planned) and refactors the UI to support multiple
screens cleanly:
- Screen (interface): both screens implement refresh(TelemetrySnapshot) - the AnimationTimer refreshes
  BOTH every frame via a Screen[] array regardless of which is visible, the same polymorphism pattern
  as VehicleSystem[] in Car and SafetyCheck[] in DiagnosticSystem.
- CockpitScreen: the Step 9 dashboard, extracted out of HyperDriveApp into its own class.
- FaultSimulatorScreen: one row per FaultType with an INJECT/CLEAR button and live OK/ACTIVE status.
  Needed ZERO new Car methods - injectFault()/clearFault()/getActiveFaults() already existed from the
  console steps and are already synchronized, so button clicks from the JavaFX thread are safe to run
  alongside the SimulationEngine's background thread for the same reason explained in "Thread safety" below.
- HyperDriveApp is now a coordinator: shared header (title, power badge, screen-name badge) and footer
  (status + hint text that changes per screen), swapping only the center content on F/C.

**New since then - critical-fault auto-shutdown (in Car, not the UI):** if the engine is running and ANY
active fault has CRITICAL severity, a 10-second countdown starts. Clearing every critical fault before it
reaches zero cancels the countdown. If it reaches zero, the engine shuts itself down - and startEngine()'s
existing diagnostics already refuse to restart while a critical fault is active, so no separate "locked out"
flag was needed. This is CONFIRMED WORKING via a real console run (Section 16): countdown ticks 10->8->6->4,
cancels correctly when cleared mid-countdown, genuinely shuts the engine off at 11s uncleared, restart is
denied with the real reason while the fault remains, and restart succeeds once cleared. TelemetrySnapshot
now carries this countdown too, and CockpitScreen shows a red "CRITICAL FAULT - ENGINE SHUTDOWN IN Xs"
banner when it's active (display-only addition to the already-confirmed-working Cockpit screen).

**Verification status:** the Car logic (countdown, cancellation, shutdown, restart-lockout) is
CONFIRMED WORKING - tested in the console, not just read. The JavaFX changes (Fault Simulator screen,
the new warning banner) are UNVERIFIED by me beyond static checks, same limitation as every UI step (no
display, no Maven Central here): every Car/FaultType/Fault/TelemetrySnapshot method call cross-checked
against our actual source, all UI files compile with zero genuine syntax errors (only expected "javafx
package does not exist" cascades), and the console demo still runs all 20 sections unaffected. Run it and
paste back what happens.

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
- Every key in the on-screen hint should work except A/D (no steering system exists to bind to) and T
  (the Diagnostics screen doesn't exist yet). F now works - it switches to the Fault Simulator screen,
  and C switches back to Cockpit.

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
| hyperdrive.ui | HyperDriveApp (coordinator, extends Application), Screen (interface), CockpitScreen, FaultSimulatorScreen |

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
Diagnostics screen (T key) | A/D steering (no system to bind to yet) |
10 polish (input debounce, throttle/brake ramping instead of instant on/off) | 11 viva prep

## Critical-fault shutdown (Car.java)
CRITICAL_FAULT_SHUTDOWN_SECONDS = 10.0. Car.hasCriticalFault() checks activeFaults for ANY fault whose
Severity is CRITICAL (not just a specific FaultType) - so OVERHEATING, LOW_FUEL_PRESSURE, LOW_BRAKE_PRESSURE,
and TRANSMISSION_FAULT (the four CRITICAL-severity faults) all trigger it. getCriticalFaultCountdown()
returns 0 when no countdown is active, or the remaining seconds otherwise - used by both the console demo
(Section 16) and TelemetrySnapshot/CockpitScreen.

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
