package hyperdrive.telemetry;

import hyperdrive.enums.ActiveState;
import hyperdrive.enums.AirbrakeState;
import hyperdrive.enums.BatteryState;
import hyperdrive.enums.BrakeState;
import hyperdrive.enums.CoolingState;
import hyperdrive.enums.DiagnosticLevel;
import hyperdrive.enums.EngineState;
import hyperdrive.enums.EpbState;
import hyperdrive.enums.EscMode;
import hyperdrive.enums.GearPosition;
import hyperdrive.enums.LateralState;
import hyperdrive.enums.LaunchState;
import hyperdrive.enums.LiftState;
import hyperdrive.enums.OilState;
import hyperdrive.enums.PowerState;
import hyperdrive.enums.Severity;
import hyperdrive.enums.StartupPhase;
import hyperdrive.enums.TransmissionMode;
import hyperdrive.enums.WarningLight;
import hyperdrive.model.Notification;
import hyperdrive.model.TripComputer;
import hyperdrive.systems.Tyre;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * A single, immutable snapshot of everything a dashboard would want to show, captured atomically inside one
 * Car.getTelemetry() call. Every field is final and set once - once you hold a TelemetrySnapshot, nothing can change
 * underneath you, even if the SimulationEngine thread ticks the real Car a hundred times right after you read it.
 *
 * The UI takes ONE snapshot per frame and reads everything from it; it never touches the live systems.
 * Built with the nested Builder (there are too many values for a sensible constructor argument list).
 * The array / map / list values are copied on the way in and out, so the snapshot really is immutable.
 */
public final class TelemetrySnapshot {
    private final PowerState powerState;
    private final StartupPhase startupPhase;
    private final double startupProgress;
    private final double gaugeSweep;
    private final DriveModeInfo mode;
    private final DriveModeInfo handlingMode;
    private final DriveModeInfo powertrainMode;
    private final ActiveState activeState;
    private final double speedKmh;
    private final double rpm;
    private final double throttle;
    private final double brakePedal;
    private final double brakePressureBar;
    private final double brakeForce;
    private final double brakeDiscTemp;
    private final BrakeState brakeState;
    private final boolean absActive;
    private final boolean brakeAssistActive;
    private final boolean prefillActive;
    private final boolean hillHoldActive;
    private final boolean brakeSteerActive;
    private final boolean discWipingActive;
    private final double[] wheelBrake;
    private final double steeringValue;
    private final double steeringAngleDeg;
    private final String steeringDirection;
    private final LateralState lateralState;
    private final double lateralG;
    private final double longitudinalG;
    private final GearPosition gear;
    private final TransmissionMode transmissionMode;
    private final boolean shifting;
    private final double fuelLevelPercent;
    private final double fuelLitres;
    private final double fuelRangeKm;
    private final double fuelFlowLph;
    private final double fuelInstantL100;
    private final double fuelAvgL100;
    private final boolean lowFuel;
    private final double fuelPressure;
    private final double coolantTemp;
    private final CoolingState coolingState;
    private final double oilTemp;
    private final double oilPressure;
    private final OilState oilState;
    private final EngineState engineState;
    private final double engineLoad;
    private final double engineTorqueNm;
    private final double rpmLimit;
    private final boolean limiterActive;
    private final double batteryVoltage;
    private final BatteryState batteryState;
    private final double batteryCharge;
    private final EscMode escMode;
    private final boolean escIntervening;
    private final double driftLevel;
    private final double torqueCut;
    private final AirbrakeState airbrakeState;
    private final double airbrakeProgress;
    private final LiftState liftState;
    private final double liftProgress;
    private final LaunchState launchState;
    private final double launchBoostPercent;
    private final String launchReason;
    private final EpbState epbState;
    private final boolean seatBeltFastened;
    private final boolean doorOpen;
    private final boolean srsOk;
    private final Tyre[] tyres;
    private final boolean rearCameraActive;
    private final double rearDistanceM;
    private final int rearWarningLevel;
    private final double roadSlopePercent;
    private final TripComputer.TripData tripSinceStart;
    private final TripComputer.TripData tripLongTerm;
    private final double sessionDistanceKm;
    private final Map<WarningLight, Severity> warningLights;
    private final List<Notification> activeMessages;
    private final List<Notification> recentNotifications;
    private final int warningCount;
    private final int activeFaultCount;
    private final double criticalFaultCountdown;
    private final DiagnosticLevel diagnosticLevel;
    private final boolean warningSelfTest;

    private TelemetrySnapshot(Builder b) {
        this.powerState = b.powerState;
        this.startupPhase = b.startupPhase;
        this.startupProgress = b.startupProgress;
        this.gaugeSweep = b.gaugeSweep;
        this.mode = b.mode;
        this.handlingMode = b.handlingMode;
        this.powertrainMode = b.powertrainMode;
        this.activeState = b.activeState;
        this.speedKmh = b.speedKmh;
        this.rpm = b.rpm;
        this.throttle = b.throttle;
        this.brakePedal = b.brakePedal;
        this.brakePressureBar = b.brakePressureBar;
        this.brakeForce = b.brakeForce;
        this.brakeDiscTemp = b.brakeDiscTemp;
        this.brakeState = b.brakeState;
        this.absActive = b.absActive;
        this.brakeAssistActive = b.brakeAssistActive;
        this.prefillActive = b.prefillActive;
        this.hillHoldActive = b.hillHoldActive;
        this.brakeSteerActive = b.brakeSteerActive;
        this.discWipingActive = b.discWipingActive;
        this.wheelBrake = b.wheelBrake == null ? new double[4] : b.wheelBrake.clone();
        this.steeringValue = b.steeringValue;
        this.steeringAngleDeg = b.steeringAngleDeg;
        this.steeringDirection = b.steeringDirection;
        this.lateralState = b.lateralState;
        this.lateralG = b.lateralG;
        this.longitudinalG = b.longitudinalG;
        this.gear = b.gear;
        this.transmissionMode = b.transmissionMode;
        this.shifting = b.shifting;
        this.fuelLevelPercent = b.fuelLevelPercent;
        this.fuelLitres = b.fuelLitres;
        this.fuelRangeKm = b.fuelRangeKm;
        this.fuelFlowLph = b.fuelFlowLph;
        this.fuelInstantL100 = b.fuelInstantL100;
        this.fuelAvgL100 = b.fuelAvgL100;
        this.lowFuel = b.lowFuel;
        this.fuelPressure = b.fuelPressure;
        this.coolantTemp = b.coolantTemp;
        this.coolingState = b.coolingState;
        this.oilTemp = b.oilTemp;
        this.oilPressure = b.oilPressure;
        this.oilState = b.oilState;
        this.engineState = b.engineState;
        this.engineLoad = b.engineLoad;
        this.engineTorqueNm = b.engineTorqueNm;
        this.rpmLimit = b.rpmLimit;
        this.limiterActive = b.limiterActive;
        this.batteryVoltage = b.batteryVoltage;
        this.batteryState = b.batteryState;
        this.batteryCharge = b.batteryCharge;
        this.escMode = b.escMode;
        this.escIntervening = b.escIntervening;
        this.driftLevel = b.driftLevel;
        this.torqueCut = b.torqueCut;
        this.airbrakeState = b.airbrakeState;
        this.airbrakeProgress = b.airbrakeProgress;
        this.liftState = b.liftState;
        this.liftProgress = b.liftProgress;
        this.launchState = b.launchState;
        this.launchBoostPercent = b.launchBoostPercent;
        this.launchReason = b.launchReason;
        this.epbState = b.epbState;
        this.seatBeltFastened = b.seatBeltFastened;
        this.doorOpen = b.doorOpen;
        this.srsOk = b.srsOk;
        this.tyres = b.tyres == null ? new Tyre[0] : b.tyres;   // Car already hands us copies (Tyre's copy constructor)
        this.rearCameraActive = b.rearCameraActive;
        this.rearDistanceM = b.rearDistanceM;
        this.rearWarningLevel = b.rearWarningLevel;
        this.roadSlopePercent = b.roadSlopePercent;
        this.tripSinceStart = b.tripSinceStart;
        this.tripLongTerm = b.tripLongTerm;
        this.sessionDistanceKm = b.sessionDistanceKm;
        EnumMap<WarningLight, Severity> lights = new EnumMap<>(WarningLight.class);
        lights.putAll(b.warningLights);
        this.warningLights = Collections.unmodifiableMap(lights);
        this.activeMessages = Collections.unmodifiableList(new ArrayList<>(b.activeMessages));
        this.recentNotifications = Collections.unmodifiableList(new ArrayList<>(b.recentNotifications));
        this.warningCount = b.warningCount;
        this.activeFaultCount = b.activeFaultCount;
        this.criticalFaultCountdown = b.criticalFaultCountdown;
        this.diagnosticLevel = b.diagnosticLevel;
        this.warningSelfTest = b.warningSelfTest;
    }

    public static Builder builder() { return new Builder(); }

    public PowerState getPowerState() { return powerState; }
    public StartupPhase getStartupPhase() { return startupPhase; }
    public double getStartupProgress() { return startupProgress; }
    public double getGaugeSweep() { return gaugeSweep; }
    public DriveModeInfo getMode() { return mode; }
    public DriveModeInfo getHandlingMode() { return handlingMode; }
    public DriveModeInfo getPowertrainMode() { return powertrainMode; }
    public ActiveState getActiveState() { return activeState; }
    public double getSpeedKmh() { return speedKmh; }
    public double getRpm() { return rpm; }
    public double getThrottle() { return throttle; }
    public double getBrakePedal() { return brakePedal; }
    public double getBrakePressureBar() { return brakePressureBar; }
    public double getBrakeForce() { return brakeForce; }
    public double getBrakeDiscTemp() { return brakeDiscTemp; }
    public BrakeState getBrakeState() { return brakeState; }
    public boolean isAbsActive() { return absActive; }
    public boolean isBrakeAssistActive() { return brakeAssistActive; }
    public boolean isPrefillActive() { return prefillActive; }
    public boolean isHillHoldActive() { return hillHoldActive; }
    public boolean isBrakeSteerActive() { return brakeSteerActive; }
    public boolean isDiscWipingActive() { return discWipingActive; }
    public double[] getWheelBrake() { return wheelBrake.clone(); }
    public double getSteeringValue() { return steeringValue; }
    public double getSteeringAngleDeg() { return steeringAngleDeg; }
    public String getSteeringDirection() { return steeringDirection; }
    public LateralState getLateralState() { return lateralState; }
    public double getLateralG() { return lateralG; }
    public double getLongitudinalG() { return longitudinalG; }
    public GearPosition getGear() { return gear; }
    public TransmissionMode getTransmissionMode() { return transmissionMode; }
    public boolean isShifting() { return shifting; }
    public double getFuelLevelPercent() { return fuelLevelPercent; }
    public double getFuelLitres() { return fuelLitres; }
    public double getFuelRangeKm() { return fuelRangeKm; }
    public double getFuelFlowLph() { return fuelFlowLph; }
    public double getFuelInstantL100() { return fuelInstantL100; }
    public double getFuelAvgL100() { return fuelAvgL100; }
    public boolean isLowFuel() { return lowFuel; }
    public double getFuelPressure() { return fuelPressure; }
    public double getCoolantTemp() { return coolantTemp; }
    public CoolingState getCoolingState() { return coolingState; }
    public double getOilTemp() { return oilTemp; }
    public double getOilPressure() { return oilPressure; }
    public OilState getOilState() { return oilState; }
    public EngineState getEngineState() { return engineState; }
    public double getEngineLoad() { return engineLoad; }
    public double getEngineTorqueNm() { return engineTorqueNm; }
    public double getRpmLimit() { return rpmLimit; }
    public boolean isLimiterActive() { return limiterActive; }
    public double getBatteryVoltage() { return batteryVoltage; }
    public BatteryState getBatteryState() { return batteryState; }
    public double getBatteryCharge() { return batteryCharge; }
    public EscMode getEscMode() { return escMode; }
    public boolean isEscIntervening() { return escIntervening; }
    public double getDriftLevel() { return driftLevel; }
    public double getTorqueCut() { return torqueCut; }
    public AirbrakeState getAirbrakeState() { return airbrakeState; }
    public double getAirbrakeProgress() { return airbrakeProgress; }
    public LiftState getLiftState() { return liftState; }
    public double getLiftProgress() { return liftProgress; }
    public LaunchState getLaunchState() { return launchState; }
    public double getLaunchBoostPercent() { return launchBoostPercent; }
    public String getLaunchReason() { return launchReason; }
    public EpbState getEpbState() { return epbState; }
    public boolean isSeatBeltFastened() { return seatBeltFastened; }
    public boolean isDoorOpen() { return doorOpen; }
    public boolean isSrsOk() { return srsOk; }
    public Tyre[] getTyres() { return tyres; }
    public boolean isRearCameraActive() { return rearCameraActive; }
    public double getRearDistanceM() { return rearDistanceM; }
    public int getRearWarningLevel() { return rearWarningLevel; }
    public double getRoadSlopePercent() { return roadSlopePercent; }
    public TripComputer.TripData getTripSinceStart() { return tripSinceStart; }
    public TripComputer.TripData getTripLongTerm() { return tripLongTerm; }
    public double getSessionDistanceKm() { return sessionDistanceKm; }
    public Map<WarningLight, Severity> getWarningLights() { return warningLights; }
    public List<Notification> getActiveMessages() { return activeMessages; }
    public List<Notification> getRecentNotifications() { return recentNotifications; }
    public int getWarningCount() { return warningCount; }
    public int getActiveFaultCount() { return activeFaultCount; }
    public double getCriticalFaultCountdown() { return criticalFaultCountdown; }
    public DiagnosticLevel getDiagnosticLevel() { return diagnosticLevel; }
    public boolean isWarningSelfTest() { return warningSelfTest; }

    /** Convenience: true unless the engine is OFF. */
    public boolean isEngineRunning() { return engineState == EngineState.RUNNING; }

    /** Legacy accessor from earlier steps: ESC counts as enabled in every mode except OFF. */
    public boolean isEscEnabled() { return escMode != EscMode.OFF; }

    @Override
    public String toString() {
        return String.format(
                "%s | H %s / P %s (%s) | %.0f km/h | %.0f rpm | gear %s%s | fuel %.1f%% | coolant %.0fC | oil %.0fC | faults %d",
                powerState, handlingMode.getName(), powertrainMode.getName(), activeState, speedKmh, rpm,
                gear.getLabel(), transmissionMode.getLetter(), fuelLevelPercent, coolantTemp, oilTemp, activeFaultCount);
    }

    /** Collects the values; every field has a harmless default so a Car only has to set what it knows. */
    public static final class Builder {
        private PowerState powerState;
        private StartupPhase startupPhase;
        private double startupProgress;
        private double gaugeSweep;
        private DriveModeInfo mode;
        private DriveModeInfo handlingMode;
        private DriveModeInfo powertrainMode;
        private ActiveState activeState;
        private double speedKmh;
        private double rpm;
        private double throttle;
        private double brakePedal;
        private double brakePressureBar;
        private double brakeForce;
        private double brakeDiscTemp;
        private BrakeState brakeState;
        private boolean absActive;
        private boolean brakeAssistActive;
        private boolean prefillActive;
        private boolean hillHoldActive;
        private boolean brakeSteerActive;
        private boolean discWipingActive;
        private double[] wheelBrake;
        private double steeringValue;
        private double steeringAngleDeg;
        private String steeringDirection = "";
        private LateralState lateralState;
        private double lateralG;
        private double longitudinalG;
        private GearPosition gear;
        private TransmissionMode transmissionMode;
        private boolean shifting;
        private double fuelLevelPercent;
        private double fuelLitres;
        private double fuelRangeKm;
        private double fuelFlowLph;
        private double fuelInstantL100;
        private double fuelAvgL100;
        private boolean lowFuel;
        private double fuelPressure;
        private double coolantTemp;
        private CoolingState coolingState;
        private double oilTemp;
        private double oilPressure;
        private OilState oilState;
        private EngineState engineState;
        private double engineLoad;
        private double engineTorqueNm;
        private double rpmLimit;
        private boolean limiterActive;
        private double batteryVoltage;
        private BatteryState batteryState;
        private double batteryCharge;
        private EscMode escMode;
        private boolean escIntervening;
        private double driftLevel;
        private double torqueCut;
        private AirbrakeState airbrakeState;
        private double airbrakeProgress;
        private LiftState liftState;
        private double liftProgress;
        private LaunchState launchState;
        private double launchBoostPercent;
        private String launchReason = "";
        private EpbState epbState;
        private boolean seatBeltFastened;
        private boolean doorOpen;
        private boolean srsOk;
        private Tyre[] tyres;
        private boolean rearCameraActive;
        private double rearDistanceM;
        private int rearWarningLevel;
        private double roadSlopePercent;
        private TripComputer.TripData tripSinceStart;
        private TripComputer.TripData tripLongTerm;
        private double sessionDistanceKm;
        private Map<WarningLight, Severity> warningLights = new EnumMap<>(WarningLight.class);
        private List<Notification> activeMessages = new ArrayList<>();
        private List<Notification> recentNotifications = new ArrayList<>();
        private int warningCount;
        private int activeFaultCount;
        private double criticalFaultCountdown;
        private DiagnosticLevel diagnosticLevel;
        private boolean warningSelfTest;

        public Builder powerState(PowerState v) { this.powerState = v; return this; }
        public Builder startupPhase(StartupPhase v) { this.startupPhase = v; return this; }
        public Builder startupProgress(double v) { this.startupProgress = v; return this; }
        public Builder gaugeSweep(double v) { this.gaugeSweep = v; return this; }
        public Builder mode(DriveModeInfo v) { this.mode = v; return this; }
        public Builder handlingMode(DriveModeInfo v) { this.handlingMode = v; return this; }
        public Builder powertrainMode(DriveModeInfo v) { this.powertrainMode = v; return this; }
        public Builder activeState(ActiveState v) { this.activeState = v; return this; }
        public Builder speedKmh(double v) { this.speedKmh = v; return this; }
        public Builder rpm(double v) { this.rpm = v; return this; }
        public Builder throttle(double v) { this.throttle = v; return this; }
        public Builder brakePedal(double v) { this.brakePedal = v; return this; }
        public Builder brakePressureBar(double v) { this.brakePressureBar = v; return this; }
        public Builder brakeForce(double v) { this.brakeForce = v; return this; }
        public Builder brakeDiscTemp(double v) { this.brakeDiscTemp = v; return this; }
        public Builder brakeState(BrakeState v) { this.brakeState = v; return this; }
        public Builder absActive(boolean v) { this.absActive = v; return this; }
        public Builder brakeAssistActive(boolean v) { this.brakeAssistActive = v; return this; }
        public Builder prefillActive(boolean v) { this.prefillActive = v; return this; }
        public Builder hillHoldActive(boolean v) { this.hillHoldActive = v; return this; }
        public Builder brakeSteerActive(boolean v) { this.brakeSteerActive = v; return this; }
        public Builder discWipingActive(boolean v) { this.discWipingActive = v; return this; }
        public Builder wheelBrake(double[] v) { this.wheelBrake = v; return this; }
        public Builder steeringValue(double v) { this.steeringValue = v; return this; }
        public Builder steeringAngleDeg(double v) { this.steeringAngleDeg = v; return this; }
        public Builder steeringDirection(String v) { this.steeringDirection = v; return this; }
        public Builder lateralState(LateralState v) { this.lateralState = v; return this; }
        public Builder lateralG(double v) { this.lateralG = v; return this; }
        public Builder longitudinalG(double v) { this.longitudinalG = v; return this; }
        public Builder gear(GearPosition v) { this.gear = v; return this; }
        public Builder transmissionMode(TransmissionMode v) { this.transmissionMode = v; return this; }
        public Builder shifting(boolean v) { this.shifting = v; return this; }
        public Builder fuelLevelPercent(double v) { this.fuelLevelPercent = v; return this; }
        public Builder fuelLitres(double v) { this.fuelLitres = v; return this; }
        public Builder fuelRangeKm(double v) { this.fuelRangeKm = v; return this; }
        public Builder fuelFlowLph(double v) { this.fuelFlowLph = v; return this; }
        public Builder fuelInstantL100(double v) { this.fuelInstantL100 = v; return this; }
        public Builder fuelAvgL100(double v) { this.fuelAvgL100 = v; return this; }
        public Builder lowFuel(boolean v) { this.lowFuel = v; return this; }
        public Builder fuelPressure(double v) { this.fuelPressure = v; return this; }
        public Builder coolantTemp(double v) { this.coolantTemp = v; return this; }
        public Builder coolingState(CoolingState v) { this.coolingState = v; return this; }
        public Builder oilTemp(double v) { this.oilTemp = v; return this; }
        public Builder oilPressure(double v) { this.oilPressure = v; return this; }
        public Builder oilState(OilState v) { this.oilState = v; return this; }
        public Builder engineState(EngineState v) { this.engineState = v; return this; }
        public Builder engineLoad(double v) { this.engineLoad = v; return this; }
        public Builder engineTorqueNm(double v) { this.engineTorqueNm = v; return this; }
        public Builder rpmLimit(double v) { this.rpmLimit = v; return this; }
        public Builder limiterActive(boolean v) { this.limiterActive = v; return this; }
        public Builder batteryVoltage(double v) { this.batteryVoltage = v; return this; }
        public Builder batteryState(BatteryState v) { this.batteryState = v; return this; }
        public Builder batteryCharge(double v) { this.batteryCharge = v; return this; }
        public Builder escMode(EscMode v) { this.escMode = v; return this; }
        public Builder escIntervening(boolean v) { this.escIntervening = v; return this; }
        public Builder driftLevel(double v) { this.driftLevel = v; return this; }
        public Builder torqueCut(double v) { this.torqueCut = v; return this; }
        public Builder airbrakeState(AirbrakeState v) { this.airbrakeState = v; return this; }
        public Builder airbrakeProgress(double v) { this.airbrakeProgress = v; return this; }
        public Builder liftState(LiftState v) { this.liftState = v; return this; }
        public Builder liftProgress(double v) { this.liftProgress = v; return this; }
        public Builder launchState(LaunchState v) { this.launchState = v; return this; }
        public Builder launchBoostPercent(double v) { this.launchBoostPercent = v; return this; }
        public Builder launchReason(String v) { this.launchReason = v; return this; }
        public Builder epbState(EpbState v) { this.epbState = v; return this; }
        public Builder seatBeltFastened(boolean v) { this.seatBeltFastened = v; return this; }
        public Builder doorOpen(boolean v) { this.doorOpen = v; return this; }
        public Builder srsOk(boolean v) { this.srsOk = v; return this; }
        public Builder tyres(Tyre[] v) { this.tyres = v; return this; }
        public Builder rearCameraActive(boolean v) { this.rearCameraActive = v; return this; }
        public Builder rearDistanceM(double v) { this.rearDistanceM = v; return this; }
        public Builder rearWarningLevel(int v) { this.rearWarningLevel = v; return this; }
        public Builder roadSlopePercent(double v) { this.roadSlopePercent = v; return this; }
        public Builder tripSinceStart(TripComputer.TripData v) { this.tripSinceStart = v; return this; }
        public Builder tripLongTerm(TripComputer.TripData v) { this.tripLongTerm = v; return this; }
        public Builder sessionDistanceKm(double v) { this.sessionDistanceKm = v; return this; }
        public Builder warningLights(Map<WarningLight, Severity> v) { this.warningLights = v; return this; }
        public Builder activeMessages(List<Notification> v) { this.activeMessages = v; return this; }
        public Builder recentNotifications(List<Notification> v) { this.recentNotifications = v; return this; }
        public Builder warningCount(int v) { this.warningCount = v; return this; }
        public Builder activeFaultCount(int v) { this.activeFaultCount = v; return this; }
        public Builder criticalFaultCountdown(double v) { this.criticalFaultCountdown = v; return this; }
        public Builder diagnosticLevel(DiagnosticLevel v) { this.diagnosticLevel = v; return this; }
        public Builder warningSelfTest(boolean v) { this.warningSelfTest = v; return this; }

        public TelemetrySnapshot build() { return new TelemetrySnapshot(this); }
    }
}
