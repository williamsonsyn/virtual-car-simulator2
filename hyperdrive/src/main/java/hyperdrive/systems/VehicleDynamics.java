package hyperdrive.systems;

import hyperdrive.enums.FaultType;
import hyperdrive.enums.GearPosition;
import hyperdrive.enums.LateralState;
import hyperdrive.sensors.SpeedSensor;

/**
 * The simplified vehicle-motion model: integrates speed from tractive push, drag, engine braking, brakes, airbrake drag
 * and slope, and works out tyre slip and the understeer/oversteer balance while cornering. It owns the SpeedSensor.
 * This is NOT a physics engine - it is a handful of formulas, but every term comes from another real system
 * (engine torque, tyre grip, brake deceleration, steering, ESC, parking brake ...), pushed in by the Car each tick.
 */
public class VehicleDynamics extends VehicleSystem {
    private static final double KMH_PER_S_PER_G = 35.316;   // 1 g expressed in km/h per second
    /** Maximum acceleration (km/h per second) in gears 1-7 at full throttle on good grip. */
    private static final double[] GEAR_ACCEL = {34, 28, 22, 18, 14, 11, 8};
    private static final double REVERSE_ACCEL = 5.0;

    private final SpeedSensor speedSensor = new SpeedSensor();

    // Inputs
    private GearPosition gear = GearPosition.P;
    private boolean engineRunning = false;
    private double torqueFraction = 0;
    private double rpmLimit = Engine.MAX_RPM;
    private double throttle = 0;
    private double brakeG = 0;
    private double airbrakeDrag = 0;
    private double frontGrip = 1.2;
    private double rearGrip = 1.2;
    private double steer = 0;
    private double yawRelief = 0;        // ESC / brake-steer help (0-1)
    private boolean launching = false;
    private boolean holdStationary = false;
    private boolean parkingBrake = true;
    private boolean rearBlocked = false;
    private double slopePercent = 0;

    // Outputs
    private double longG = 0;
    private double latG = 0;
    private double frontSlip = 0;
    private double rearSlip = 0;
    private LateralState lateralState = LateralState.NEUTRAL;

    public VehicleDynamics() {
        super("Vehicle Dynamics");
    }

    public void setDrivetrain(GearPosition gear, boolean engineRunning, double torqueFraction, double rpmLimit,
            double throttle) {
        this.gear = gear;
        this.engineRunning = engineRunning;
        this.torqueFraction = torqueFraction;
        this.rpmLimit = rpmLimit;
        this.throttle = throttle;
    }

    public void setBraking(double brakeDecelG, double airbrakeDragKmhPerS) {
        this.brakeG = brakeDecelG;
        this.airbrakeDrag = airbrakeDragKmhPerS;
    }

    public void setGrip(double frontGripG, double rearGripG) {
        this.frontGrip = Math.max(0.2, frontGripG);
        this.rearGrip = Math.max(0.2, rearGripG);
    }

    public void setDriver(double steerNorm, double yawRelief) {
        this.steer = steerNorm;
        this.yawRelief = yawRelief;
    }

    public void setLaunch(boolean launching, boolean holdStationary) {
        this.launching = launching;
        this.holdStationary = holdStationary;
    }

    public void setConstraints(boolean parkingBrakeEngaged, boolean rearBlocked, double slopePercent) {
        this.parkingBrake = parkingBrakeEngaged;
        this.rearBlocked = rearBlocked;
        this.slopePercent = slopePercent;
    }

    /** Simulator/test utility: set the speed directly (also used by older demo code). */
    public void setSpeed(double kmh) { speedSensor.setValue(Math.max(0.0, Math.min(SpeedSensor.MAX_SPEED_KMH, kmh))); }

    @Override
    public void update(double dt) {
        double speed = speedSensor.getValue();
        boolean forward = gear.isForward();
        boolean coupled = engineRunning && (forward || gear == GearPosition.R);
        double top = Transmission.getTopSpeed(gear);

        // ---- tractive push from the engine through the gearbox
        double tractive = 0;
        if (coupled) {
            double aMax = forward ? GEAR_ACCEL[gear.getGearNumber() - 1] : REVERSE_ACCEL;
            double standingStart = 1.0;
            if (launching) {
                standingStart = 1.15;                                   // launch control: ideal slip, more push
            } else if (throttle > 0.8 && speed < 30.0) {
                standingStart = 0.85 + 0.15 * (speed / 30.0);          // clutch slip / traction management off the line
            }
            tractive = aMax * torqueFraction * standingStart;
        }

        // ---- grip limit at the driven (rear) axle
        double availableKmhS = rearGrip * KMH_PER_S_PER_G * 0.97;
        double actualDrive = tractive;
        double wheelspin = 0;
        if (tractive > availableKmhS) {
            wheelspin = Math.min(1.0, tractive / availableKmhS - 1.0);
            actualDrive = launching ? availableKmhS * 1.08 : availableKmhS * (1.0 - 0.12 * Math.min(1.0, wheelspin * 3.0));
        }
        double driveG = actualDrive / KMH_PER_S_PER_G;

        // ---- resistances
        double drag = 0.25 + 0.55 * Math.pow(speed / 100.0, 2);
        double rpmNow = Transmission.wheelRpm(gear, speed);
        double engineBrake = (coupled && throttle < 0.03) ? 1.5 + (rpmNow / Engine.REDLINE_RPM) * 2.5 : 0.0;
        double overRev = (coupled && rpmNow > rpmLimit + 100) ? (rpmNow - rpmLimit) / rpmLimit * 60.0 : 0.0;

        // ---- cornering balance
        double demandG = Math.min(3.0, Math.abs(steer) * Math.pow(speed / 100.0, 2) * 2.2);
        double frontUtil = Math.hypot(demandG, brakeG) / frontGrip;
        double rearUtil = Math.hypot(demandG, driveG) / rearGrip;
        double fSlip = Math.max(0.0, Math.min(1.0, (frontUtil - 0.92) / 0.5));
        double rSlip = Math.max(0.0, Math.min(1.0, (rearUtil - 0.92) / 0.5));
        fSlip *= (1.0 - yawRelief);
        rSlip *= (1.0 - yawRelief);
        rSlip = Math.max(rSlip, wheelspin * 1.2 * (1.0 - yawRelief));
        frontSlip = fSlip;
        rearSlip = Math.min(1.0, rSlip);
        if (Math.max(frontSlip, rearSlip) < 0.04) {
            lateralState = LateralState.NEUTRAL;
        } else {
            lateralState = frontSlip >= rearSlip ? LateralState.UNDERSTEER : LateralState.OVERSTEER;
        }
        double cap = 0.95 * Math.min(frontGrip, rearGrip);
        latG = Math.signum(steer) * Math.min(demandG, cap);
        double scrub = (frontSlip + rearSlip) * 4.0;

        // ---- net acceleration
        double a = actualDrive - drag - engineBrake - overRev - brakeG * KMH_PER_S_PER_G - airbrakeDrag - scrub
                - slopePercent * 0.3;
        if (speed < 3.0 && (parkingBrake || gear == GearPosition.P || holdStationary || rearBlocked && gear == GearPosition.R)) {
            a = Math.min(a, 0.0);          // held by the parking brake / park pawl / launch hold / obstacle
            if (parkingBrake || gear == GearPosition.P || holdStationary || rearBlocked) {
                speed = 0;
            }
        } else if (parkingBrake && speed >= 3.0) {
            a -= 10.0;                      // dragging the parking brake while moving
        }
        if (rearBlocked && gear == GearPosition.R) {
            speed = 0;
            a = Math.min(a, 0.0);
        }
        if (speed <= 0.05 && a < 0) {
            a = 0;
        }

        speed = Math.max(0.0, Math.min(SpeedSensor.MAX_SPEED_KMH, speed + a * dt));
        if (speed < 0.1 && a <= 0) {
            speed = 0.0;
        }
        speedSensor.setValue(speed);
        longG = a / KMH_PER_S_PER_G;
    }

    @Override
    public boolean handles(FaultType type) { return false; }

    @Override
    public boolean selfTest() { return speedSensor.isHealthy(); }

    public double getSpeedKmh() { return speedSensor.getValue(); }
    public double getLongitudinalG() { return longG; }
    public double getLateralG() { return latG; }
    public double getFrontSlip() { return frontSlip; }
    public double getRearSlip() { return rearSlip; }
    public LateralState getLateralState() { return lateralState; }
}
