package hyperdrive.telemetry;

import hyperdrive.enums.AirbrakeState;
import hyperdrive.enums.GearPosition;
import hyperdrive.enums.LaunchState;
import hyperdrive.enums.LiftState;
import hyperdrive.enums.PowerState;
import hyperdrive.systems.Tyre;

/**
 * A single, immutable snapshot of everything a dashboard would want to show, captured
 * atomically inside one Car.getTelemetry() call. Every field is final and set once in the
 * constructor - once you hold a TelemetrySnapshot, nothing can change underneath you, even
 * if the SimulationEngine thread ticks the real Car a hundred times right after you read it.
 *
 * This is what fixes the caveat from Step 7: instead of calling getSpeedKmh(), getRpm(),
 * getFuelLevelPercent() etc. as several separate synchronized calls (each individually safe,
 * but not consistent AS A GROUP - the car could change between two of those calls), a UI takes
 * ONE snapshot per frame and reads everything from that.
 */
public final class TelemetrySnapshot {
    private final PowerState powerState;
    private final DriveModeInfo mode;
    private final double speedKmh;
    private final double rpm;
    private final double throttle;
    private final GearPosition gear;
    private final double fuelLevelPercent;
    private final double fuelPressure;
    private final double coolantTemp;
    private final double oilTemp;
    private final double brakeDiscTemp;
    private final boolean absActive;
    private final double batteryVoltage;
    private final AirbrakeState airbrakeState;
    private final LiftState liftState;
    private final boolean escEnabled;
    private final double driftLevel;
    private final LaunchState launchState;
    private final double launchBoostPercent;
    private final Tyre[] tyres;
    private final int activeFaultCount;
    private final double criticalFaultCountdown;

    public TelemetrySnapshot(PowerState powerState, DriveModeInfo mode, double speedKmh, double rpm,
            double throttle, GearPosition gear, double fuelLevelPercent, double fuelPressure,
            double coolantTemp, double oilTemp, double brakeDiscTemp, boolean absActive, double batteryVoltage,
            AirbrakeState airbrakeState, LiftState liftState, boolean escEnabled, double driftLevel,
            LaunchState launchState, double launchBoostPercent, Tyre[] tyres, int activeFaultCount,
            double criticalFaultCountdown) {
        this.powerState = powerState;
        this.mode = mode;
        this.speedKmh = speedKmh;
        this.rpm = rpm;
        this.throttle = throttle;
        this.gear = gear;
        this.fuelLevelPercent = fuelLevelPercent;
        this.fuelPressure = fuelPressure;
        this.coolantTemp = coolantTemp;
        this.oilTemp = oilTemp;
        this.brakeDiscTemp = brakeDiscTemp;
        this.absActive = absActive;
        this.batteryVoltage = batteryVoltage;
        this.airbrakeState = airbrakeState;
        this.liftState = liftState;
        this.escEnabled = escEnabled;
        this.driftLevel = driftLevel;
        this.launchState = launchState;
        this.launchBoostPercent = launchBoostPercent;
        this.tyres = tyres;              // Car already hands us a defensive copy (Tyre's copy constructor)
        this.activeFaultCount = activeFaultCount;
        this.criticalFaultCountdown = criticalFaultCountdown;
    }

    public PowerState getPowerState() { return powerState; }
    public DriveModeInfo getMode() { return mode; }
    public double getSpeedKmh() { return speedKmh; }
    public double getRpm() { return rpm; }
    public double getThrottle() { return throttle; }
    public GearPosition getGear() { return gear; }
    public double getFuelLevelPercent() { return fuelLevelPercent; }
    public double getFuelPressure() { return fuelPressure; }
    public double getCoolantTemp() { return coolantTemp; }
    public double getOilTemp() { return oilTemp; }
    public double getBrakeDiscTemp() { return brakeDiscTemp; }
    public boolean isAbsActive() { return absActive; }
    public double getBatteryVoltage() { return batteryVoltage; }
    public AirbrakeState getAirbrakeState() { return airbrakeState; }
    public LiftState getLiftState() { return liftState; }
    public boolean isEscEnabled() { return escEnabled; }
    public double getDriftLevel() { return driftLevel; }
    public LaunchState getLaunchState() { return launchState; }
    public double getLaunchBoostPercent() { return launchBoostPercent; }
    public Tyre[] getTyres() { return tyres; }
    public int getActiveFaultCount() { return activeFaultCount; }
    public double getCriticalFaultCountdown() { return criticalFaultCountdown; }

    @Override
    public String toString() {
        return String.format(
                "%s | %s | %.0f km/h | %.0f rpm | gear %s | fuel %.1f%% | coolant %.0fC | oil %.0fC | faults %d",
                powerState, mode.getName(), speedKmh, rpm, gear.getLabel(), fuelLevelPercent,
                coolantTemp, oilTemp, activeFaultCount);
    }
}
