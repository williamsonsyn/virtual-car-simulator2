# HyperDrive: Object-Oriented Hypercar Control & Simulation System
Educational simulator INSPIRED by the McLaren 720S. Not McLaren software; all logic and thresholds are our own simplifications.
UI branding: HYPERDRIVE X-01.

## Status: Step 11 - mechanical RPM/speed coupling fix, ABS, tachometer/speedometer gauges
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
- Every key in the on-screen hint should work except A/D (no steering system exists to bind to).
  F switches to the Fault Simulator screen, T switches to Diagnostics, C switches back to Cockpit.

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
| hyperdrive.ui | HyperDriveApp (coordinator), Screen (interface), CockpitScreen, FaultSimulatorScreen, DiagnosticsScreen |

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
A/D steering (declined - not needed) | Parking/camera system (declined - not needed) |
live graphs (declined - not needed) | viva prep

## Step 10 polish (HyperDriveApp.java only - zero Car/model changes)
- Key-repeat guard: a Set<KeyCode> tracks held keys; KEY_PRESSED is ignored if the OS is auto-repeating
  it (JavaFX fires KEY_PRESSED repeatedly while a key is held). Each physical press now triggers exactly
  one action, instead of holding ENTER spamming car.startEngine() denials dozens of times a second.
- Pedal ramping: holding W/S ramps throttle/brake UP smoothly over 0.25s (PEDAL_RAMP_SECONDS) instead of
  snapping instantly to 1.0 - computed per-frame in the AnimationTimer, same dt-clamping approach as
  SimulationEngine. RELEASE stays instant (car.setThrottle(0.0) / car.releaseBrake()), deliberately -
  this is why releaseBrake()'s launch-trigger check still fires exactly when it always did.
- This was deliberately NOT done at the Car/Engine/BrakeSystem level: Car.setThrottle()/pressBrake()
  already have well-defined, heavily-tested "instant" semantics that most of the console demo's ~20
  sections rely on (press brake, immediately check isPedalPressed() to start the engine; full throttle +
  full brake, immediately check readiness for Launch Control). Ramping the raw model value would have
  meant auditing and fixing every one of those call sites. Putting the ramp in the UI's input handling
  instead means the model's semantics - and everything already verified against them - are untouched.

## Diagnostics screen (T key)
Shows car.getDiagnosticReport() (the same startup safety checks used by StartupSequenceThread and
Section 2's console demo) and car.getSystemStatusLines() (every VehicleSystem's self-test), both
refreshed every frame - cheap in-memory calls, no issue running them 60 times a second. The session
log history panel is DELIBERATELY NOT refreshed every frame - re-reading a file from disk that often
for data that only changes on discrete events would be wasteful - so it only refreshes when you switch
TO this screen (T), plus its own Refresh button for picking up anything logged while you're already on it.

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


## Step 11: real physics bugs found and fixed (verified with isolated tests, not just read)

**Bug 1 - RPM was not mechanically tied to speed.** Engine.start() snapped RPM straight to 850
(idle) instead of cranking up gradually, and releasing the throttle made RPM rush back to idle
via its own independent throttle-chase, completely ignoring how fast the car was actually going.
In a real geared car the engine is mechanically locked to the wheels whenever a gear is engaged -
RPM can't just drop to idle while the car is still doing 80 km/h.

Fixed in Engine.java + Car.java (updateSpeed()):
- Engine.start() now sets rpm=0 and lets the EXISTING chase-up logic in update(dt) bring it to
  idle gradually (confirmed via isolated test: 0 -> 340 -> 544 -> ... -> 850 rpm over ~1.5s, not instant).
- Car.updateSpeed() now derives RPM from the car's ACTUAL speed and the current gear's ratio
  whenever a gear is engaged (Engine.setCoupledRpm()), floored at idle so it won't read below
  850 just from being slow or stopped in gear.
- This is a HYBRID, not a blanket lock: while COASTING or BRAKING (throttle <= 0.05), RPM is
  locked straight to the mechanical value (confirmed via isolated test: speed 54->48->43->38->33->30
  km/h tracked by rpm 8000->6574->5918->5336->4822->4366, proportionally, instead of snapping to
  idle). While ACCELERATING (throttle applied), the engine's own throttle-chase RPM is allowed to
  run AHEAD of the mechanical value - that gap is exactly what ESC's wheelspin detection (Step 4)
  looks for, and blanket-locking RPM to speed at all times would have silently neutered ESC's
  traction-cut feature. Confirmed ESC/launch control/lift auto-lower still work correctly afterward.

**Bug 2 - ABS was dead code.** BrakeSystem.apply(double, boolean) existed as a method-overloading
example, but nothing ever called it with absActive=true - the absFactor braking-force math was
unreachable. Now Car doesn't decide ABS at all; BrakeSystem.update(dt) continuously re-evaluates
absActive every tick from its own pedal position and tracked speed (ABS_MIN_PEDAL=0.7,
ABS_MIN_SPEED=40 km/h) - a real car's ABS monitors continuously, the driver doesn't request it.
Caught and fixed a real bug here too during testing: my first version only checked the condition
at the moment pressBrake() was called, so ABS got "stuck" active even after the car had slowed
to a stop under one sustained brake hold. Confirmed fixed: ABS now correctly turns off the moment
speed drops below 40 km/h, verified down to the exact tick it disengages.

**Demo retuning (not a bug, a consequence of the more honest model):** the console demo's "over-rev
protection" moment (Section 4) stopped triggering, because the old formula for partial throttle
(via an RPM-fraction detour that included the idle-RPM baseline) actually over-delivered speed
relative to the throttle percentage - 70% throttle reached ~104 km/h under the old model, but
genuinely caps at 98 km/h under the new, more direct formula (speed target = throttle x gear's
top speed). Bumped that section's throttle to 1.0 (full) to keep demonstrating the denial; the
Transmission validation logic itself was never wrong, only the speed the demo script reached by
that point changed.

## Tachometer/speedometer gauges (GaugeView) - visually UNVERIFIED by me

CockpitScreen's plain RPM/speed numbers are now semicircular gauges (GaugeView.java), each with a
track arc, a red zone near the high end, and a needle. Reused as one component for both (same
idea as Screen - one class, different parameters).

**This is the piece I'm least certain about**, for two reasons I want to be upfront about:
1. JavaFX's Arc uses mathematical angles (0=right, 90=up, counterclockwise-positive) but
   Node rotation uses screen angles (clockwise-positive) - mixing these without converting is
   the single most common bug in a hand-built JavaFX gauge. I converted carefully and checked
   the boundary cases algebraically (min value -> needle points left, max value -> needle points
   right, mid value -> needle points up), but I cannot render it to confirm visually.
2. Compiling without the JavaFX jars means the compiler fails at "package does not exist" BEFORE
   it would ever check whether a constructor overload actually exists - so unlike every other
   static check in this project, a wrong-arity JavaFX constructor call could have slipped through
   undetected. I found and fixed exactly one such risk (a 4-argument Circle(x,y,radius,Paint)
   constructor I wasn't fully certain exists) by rewriting it with a constructor I AM certain about
   plus a separate setFill() call. Everything else in GaugeView uses constructors I'm confident in
   (Arc's 6-arg, Line's 4-arg, Rotate's 3-arg), but please tell me immediately if the gauge doesn't
   render, looks mirrored/upside-down, or the needle doesn't track the value correctly.

## Other Step 11 additions
- Fault count shown directly on the Cockpit screen now (CockpitScreen's mode row), not just via
  the shared header's power badge turning red - turns red itself once count > 0.
- Brake disc temperature is now shown in the UI at all (it existed in TelemetrySnapshot since
  Step 8 but was never wired to a label) - combined with an "ABS" indicator that highlights blue
  when ABS is actively engaged.
