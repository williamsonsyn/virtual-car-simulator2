# HyperDrive: Object-Oriented Hypercar Control & Simulation System
Educational simulator INSPIRED by the McLaren 720S. Not McLaren software; all logic and thresholds are our own simplifications.
UI branding: HYPERDRIVE X-01.

## Status: Step 3 - OOP core + driving modes (console)
Power/startup, engine, fuel, brakes, battery, cooling, transmission, tyres, safety checks, fault injection,
driving modes (Comfort/Sport/Track) with mode-dependent throttle response and mode-change authorization.
No UI, threads or file logging yet.

## Run
Needs JDK 17+.

Without Maven (works everywhere):
    javac -d out -sourcepath src/main/java src/main/java/hyperdrive/Main.java
    java -cp out hyperdrive.Main

With Maven:
    mvn compile exec:java

## Packages
| Package | Contents |
|---|---|
| hyperdrive.enums | PowerState, GearPosition, FaultType, Severity |
| hyperdrive.exceptions | OperationDeniedException, InvalidGearException |
| hyperdrive.sensors | Sensor (abstract), PressureSensor, TemperatureSensor, SpeedSensor |
| hyperdrive.systems | Faultable, Loggable, VehicleSystem (abstract), Engine, FuelSystem, BrakeSystem, ElectricalSystem, CoolingSystem, Transmission, Tyre, TyreSystem |
| hyperdrive.safety | SafetyCheck (interface), Battery/FuelPressure/Brake/Temperature/Transmission checks, DiagnosticSystem |
| hyperdrive.modes | DriveMode (abstract), ComfortMode, SportMode, TrackMode (extends SportMode) |
| hyperdrive.model | Car, Fault |

## Design rule
The UI calls Car. Car decides (via SafetyChecks and the systems' own rules) and throws OperationDeniedException with every reason.
No decision logic in UI event handlers.

## Next steps
4 launch, airbrake, lift, ESC | 5 notifications | 6 file logging (Logger, LogReader) |
7 SimulationEngine thread | 8 telemetry | 9 JavaFX UI + keyboard controls | 10 polish | 11 viva prep
