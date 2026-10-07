# HyperDrive X-01 - Object-Oriented Hypercar Control & Simulation System

An **educational, 720S-inspired** hypercar simulator written in Java 17 + JavaFX. It is not McLaren software and does
not reproduce any proprietary ECU software or calibration: every model, number and threshold here is our own
simplified engineering model, loosely inspired by publicly documented behaviour of the 720S.

**Status (v0.3): connected vehicle systems + new instrument cluster.** Every number on the cockpit comes from the
simulation (`TelemetrySnapshot`), never from the UI.

## Run

Needs JDK 17+.

    mvn clean javafx:run                  # the JavaFX cockpit (first run downloads the JavaFX jars)
    mvn compile exec:java                 # the console demo (no JavaFX needed)

Without Maven (console only):

    javac -d out -sourcepath src/main/java src/main/java/hyperdrive/Main.java
    java -cp out hyperdrive.Main

Headless verification of the vehicle model (173 checks, no JavaFX needed):

    javac -d out -sourcepath src/main/java:src/test/java src/test/java/hyperdrive/SimulationVerification.java
    java -cp out hyperdrive.SimulationVerification

## Controls

| Key | Action |
|---|---|
| **I** | Ignition on: dashboard wakes and runs its self-test (warning lights, gauge sweep, system check) |
| **ENTER** | Start engine (brake pedal pressed, gear P/N): cranking -> RPM rise -> oil pressure builds -> running |
| **O** | Stop engine (auto Park + parking brake), or ignition off |
| **W / S** | Throttle / brake (held). **SHIFT+S** stamps on the brake (Brake Assist) |
| **A / D** | Steer left / right (self-centres on release) |
| **Q / E** | Downshift / upshift (in AUTO a paddle pull gives ~4 s of manual control) |
| **M** | AUTO / MANUAL gearbox |
| **N / R / Z** | Neutral / Reverse / **Park** (P is now the Powertrain selector, so Park moved to Z) |
| **H / P** | Handling mode / Powertrain mode: Comfort -> Sport -> Track |
| **1 / 2 / 3** | Comfort / Sport / Track for both H and P (also switches Active Dynamics on) |
| **X** | Active Dynamics on/off (off = `NON-ACTIVE`, selections remembered) |
| **Y** | ESC mode: ON -> DYNAMIC -> TRACK DYNAMIC -> OFF (higher states need Active + Sport/Track handling) |
| **SPACE** | Electronic parking brake |
| **B / V** | Airbrake / vehicle lift |
| **L** | Launch Control (press again to cancel) |
| **G** | Simulated 8 % road slope on/off (for Hill Hold) |
| **K / U** | Door open/closed, seatbelt fastened/unfastened |
| **J** | Next page of the left panel: Vehicle, Messages, Trip, Tyres, Oil status, Battery, Vehicle info |
| **F / T / C** | Fault Simulator / Diagnostics / Cockpit |

Quick start: press `I`, wait about 4 s for the self-test, hold `S` and press `ENTER`. Still holding `S`, press `N`
and then `E` (Park can only be left with the brake on, and `E` from Neutral selects 1st). Release `S` and hold `W`;
the AUTO gearbox shifts up by itself. Open the Fault Simulator (`F`) and click **PRE-WARM (SIM)** to skip the
warm-up: cold oil limits the RPM, and the airbrake and Launch Control need warm fluids.

Launch Control recipe: PRE-WARM, press `3` (Track + Active), hold `S`, select gear 1, press `L`, hold `W`, wait for
`LAUNCH READY`, release `S`. If something is wrong the cluster says `LAUNCH CONTROL UNAVAILABLE` and lists why.

## Architecture (everything is connected through the Car)

    USER INPUT -> Car -> vehicle systems -> SimulationEngine thread -> sensors/telemetry
               -> diagnostics/safety -> notifications -> JavaFX UI

* `Car` owns all systems (composition) and is the mediator: each tick it reads outputs from some systems and feeds
  them into others; the systems never depend on each other. Public methods are `synchronized`.
* `SimulationEngine` (Runnable on its own thread) calls `Car.update(dt)` every 20 ms. The JavaFX thread only reads
  one atomic `TelemetrySnapshot` per frame (immutable, built with a Builder) and sends input to `Car`.
* `DiagnosticSystem` runs the `SafetyCheck` implementations (PASS / WARNING / FAULT / CRITICAL) and also turns the
  live car state into warning telltales and messages. `EventMonitor` converts changes into `NotificationManager`
  messages (INFO / WARNING / CRITICAL), which `Logger` writes to the log file. No diagnostic logic is in JavaFX.

### Vehicle systems (package `hyperdrive.systems`)

| Class | What it does |
|---|---|
| `Engine` | RPM/throttle/load/torque; clutch slip off the line, free-rev in neutral, hard limiter, dynamic RPM limit (cold oil, overheating, low oil pressure), cranking state machine |
| `OilSystem` | Oil temperature (slow) and pressure (builds while cranking, follows RPM); LOW_OIL_PRESSURE derates the engine and is critical |
| `CoolingSystem` | Heat from RPM/load/sustained load, cooling from airflow; NORMAL / WARM / HIGH / CRITICAL |
| `FuelSystem` | Flow from RPM x load x throttle, fuel cut on overrun, range, low-fuel warning. Burn is accelerated (`SIM_BURN_SCALE`) so the gauge moves in a short session |
| `Transmission` | P R N 1-7, AUTO/MANUAL, mode-dependent shift points and shift time, lug protection, launch shifting, `InvalidGearException` rules |
| `BrakeSystem` | Pedal -> line pressure -> deceleration; ABS (slip based), Brake Assist, Pre-Fill, Hill Hold (~2 s), Brake-Steer, Disc Wiping |
| `ESCSystem` | ESC ON / DYNAMIC / TRACK DYNAMIC / OFF; cuts torque and requests individual wheel braking; OFF persists until the next ignition |
| `TyreSystem` / `Tyre` | `Tyre[4]`: pressure, temperature, grip, slip, condition (NORMAL/COLD/HOT/LOW PRESSURE/FAULT); weight transfer heats tyres |
| `SteeringSystem` | Smoothed steering angle used by tyres, ESC, brake-steer, launch (must be straight) |
| `VehicleDynamics` | Integrates speed (tractive push, drag, engine braking, brakes, airbrake, slope); understeer/oversteer from tyre grip |
| `Airbrake` | STOWED / DEPLOYING / DEPLOYED / RETRACTING / UNAVAILABLE / FAULT, animated, self-test, cold-fluid lockout, auto logic |
| `VehicleLift` | NORMAL / RAISING / RAISED / LOWERING, speed limit, auto-lowers |
| `LaunchControl` | State machine OFF -> REQUESTED -> CHECKING -> AWAITING_THROTTLE -> BOOST_BUILDING -> READY -> LAUNCHING -> COMPLETE (+ ABORTED, UNAVAILABLE); the Car checks the preconditions |
| `ElectricalSystem` | Battery charge/voltage, alternator, battery fault |
| `ElectronicParkingBrake`, `SeatBeltSystem`, `DoorSystem`, `SRSSystem`, `RearCameraSystem` | Lightweight systems for warnings, preconditions and the reverse camera |

Drive modes (`DriveMode` -> `ComfortMode`, `SportMode` -> `TrackMode`) are real behaviour bundles: pedal map and
throttle-plate speed, RPM response, shift points and shift time, slip permissiveness. Track does **not** switch ESC off.

### OOP concepts (all still demonstrated)

Classes/objects, encapsulation (private fields + getters/setters), constructors + overloading + copy constructors
(`Tyre`, `Fault`, `TripData`), arrays of objects (`Tyre[]`, `VehicleSystem[]`, `SafetyCheck[]`), inheritance (multilevel:
`TrackMode -> SportMode -> DriveMode`), abstract classes (`VehicleSystem`, `DriveMode`, `AbstractSafetyCheck`), interfaces
(`Faultable`, `Loggable`, `SafetyCheck`, `Screen`), polymorphism (the `update()` loop, `refresh()` loop, `check()` loop),
method overloading/overriding, packages, custom exceptions (`OperationDeniedException`, `InvalidGearException`),
multithreading (`SimulationEngine` Runnable, `StartupSequenceThread extends Thread`), Java I/O (`Logger`, `LogReader`,
long-term trip file `logs/trip-long-term.properties`).

## The instrument cluster

`ClusterRenderer` draws everything on one JavaFX `Canvas` in a 1000 x 540 design space scaled to the window, so it
stays proportional and the tachometer stays dominant at any size. `ClusterState` smooths the needle, speed, mode
styling, wake-up and pop-ups. Comfort / Sport / Track change the look (ring weight and colour, centre layout, shift-light
blocks in Track, digital RPM). Only active warnings light up; during ignition all of them light for the self-test.
Reverse replaces the left panel with the rear camera / parking-sensor view. Units are km/h, km and degrees C.

## Verification status - please read

What I **ran** here: the whole vehicle model, headlessly. `SimulationVerification` drives real `Car` objects through
scripted scenarios (ignition and self-test, cranking, driving, shifting, braking/ABS/Brake Assist, steering, modes,
ESC, airbrake, lift, Launch Control incl. aborts, tyres/TPMS, every fault type, notifications, trip, reverse, Hill
Hold, a live simulation thread and the log file): **173 checks pass**. The console demo also runs.

What I could **not** run: JavaFX itself (no JavaFX runtime or display in my environment). So the UI is not verified
on a real JavaFX stack. I compile-checked every UI class against hand-written stubs of the JavaFX API (this catches
every mistake in calls to our own classes, but not a wrongly remembered JavaFX signature), and I rendered the cluster
from the same drawing code into PNGs with a small replay tool to check layout (`docs/preview/`). Those PNGs are
approximations, **not screenshots**: fonts, anti-aliasing and glow will look different in real JavaFX. Expect to run
`mvn clean javafx:run` and tell me what you see - layout nudges are likely.

## Known simplifications

* Handling is a handful of formulas, not a physics engine; slip, grip and balance are qualitative.
* Warm-up is far faster than reality (minutes -> ~30 s); the Fault Simulator has a PRE-WARM button for testing.
* Fuel burns faster than reality (`SIM_BURN_SCALE`); consumption figures use the real flow.
* Hill Hold holds the car but a slope does not roll it backwards afterwards.
* Navigation / media / phone areas are visual placeholders only.
* Keyboard: P now selects the Powertrain mode, so Park is on Z.
