package hyperdrive.systems;

import hyperdrive.enums.AirbrakeState;
import hyperdrive.enums.FaultType;

/**
 * Active aerodynamic airbrake (the rear wing flap). Deploys for extra drag and rear downforce under hard braking, high
 * speed or strong deceleration; stows at low speed or when the driver is flat out in a straight line.
 * Moves through DEPLOYING/RETRACTING with a continuous 0-1 progress value (the UI animates from it).
 * After engine start it runs a short self-test; if the oil/transmission is too cold it is UNAVAILABLE until warm.
 */
public class Airbrake extends VehicleSystem implements Loggable {
    public static final double MIN_MANUAL_DEPLOY_SPEED = 80.0;   // km/h - needs airflow to work
    public static final double MIN_OIL_TEMP = 45.0;              // C: proxy for "transmission/oil warm enough"
    private static final double AUTO_DEPLOY_SPEED = 100.0;
    private static final double HIGH_SPEED_DEPLOY = 220.0;
    private static final double AUTO_RETRACT_SPEED = 45.0;
    private static final double MANUAL_RETRACT_SPEED = 60.0;
    private static final double DEPLOY_TIME = 0.7;
    private static final double RETRACT_TIME = 0.6;
    private static final double TEST_HOLD = 0.4;
    private static final double DRAG_DECELERATION = 8.0;         // km/h per second, while fully deployed
    private static final double DOWNFORCE_GAIN = 0.06;           // +6% tyre grip when fully deployed

    private AirbrakeState state = AirbrakeState.STOWED;
    private double progress = 0;             // 0 = stowed, 1 = fully deployed
    private boolean manual = false;          // deployed by the driver (B key), so auto-logic leaves it alone
    private boolean selfTestPending = false;
    private int testStage = 0;               // 0 = none, 1 = deploying, 2 = holding, 3 = retracting
    private double testTimer = 0;
    private double holdTimer = 0;

    private double speedKmh = 0;
    private double brakePedal = 0;
    private boolean engineRunning = false;
    private double oilTemp = 30;
    private double decelG = 0;
    private double throttle = 0;
    private double steer = 0;

    public Airbrake() {
        super("Airbrake");
    }

    /** Car feeds in what the airbrake needs to know, once per tick. */
    public void setInputs(double speedKmh, double brakePedal, boolean engineRunning) {
        this.speedKmh = speedKmh;
        this.brakePedal = brakePedal;
        this.engineRunning = engineRunning;
    }

    // Overload: the extra conditions used by the automatic logic.
    public void setInputs(double speedKmh, double brakePedal, boolean engineRunning, double oilTemp,
            double decelG, double throttle, double steerNorm) {
        setInputs(speedKmh, brakePedal, engineRunning);
        this.oilTemp = oilTemp;
        this.decelG = decelG;
        this.throttle = throttle;
        this.steer = steerNorm;
    }

    /** Called when the engine starts: the airbrake will run its self-test as soon as it is allowed to. */
    public void requestSelfTest() { selfTestPending = true; }

    /** Car has already validated preconditions before calling this. */
    public void deployManual() {
        if (state == AirbrakeState.STOWED) {
            manual = true;
            state = AirbrakeState.DEPLOYING;
        }
    }

    public void retractManual() {
        if (state == AirbrakeState.DEPLOYED || state == AirbrakeState.DEPLOYING) {
            manual = false;
            state = AirbrakeState.RETRACTING;
        }
    }

    public boolean isAvailable() {
        return engineRunning && oilTemp >= MIN_OIL_TEMP && !hasFault(FaultType.AIRBRAKE_FAULT);
    }

    @Override
    public void update(double dt) {
        if (hasFault(FaultType.AIRBRAKE_FAULT)) {
            state = AirbrakeState.FAULT;
            progress = Math.max(0.0, progress - dt / RETRACT_TIME);   // fails safe: flap folds away
            return;
        }
        if (state == AirbrakeState.FAULT) {
            state = AirbrakeState.STOWED;
        }
        if (!engineRunning) {
            // Engine off: fold away and be ready for a fresh self-test next time.
            progress = Math.max(0.0, progress - dt / RETRACT_TIME);
            state = progress > 0 ? AirbrakeState.RETRACTING : AirbrakeState.STOWED;
            manual = false;
            testStage = 0;
            return;
        }
        if (oilTemp < MIN_OIL_TEMP && testStage == 0 && state == AirbrakeState.STOWED) {
            state = AirbrakeState.UNAVAILABLE;
        }
        if (state == AirbrakeState.UNAVAILABLE) {
            if (oilTemp >= MIN_OIL_TEMP) {
                state = AirbrakeState.STOWED;     // warm enough now: the self-test (if pending) runs next
            } else {
                return;
            }
        }

        if (selfTestPending && state == AirbrakeState.STOWED && testStage == 0) {
            testStage = 1;
            state = AirbrakeState.DEPLOYING;
        }
        if (testStage != 0) {
            runSelfTest(dt);
            return;
        }

        // Normal movement
        switch (state) {
            case DEPLOYING -> {
                progress = Math.min(1.0, progress + dt / DEPLOY_TIME);
                if (progress >= 1.0) {
                    state = AirbrakeState.DEPLOYED;
                    holdTimer = 1.2;
                }
            }
            case RETRACTING -> {
                progress = Math.max(0.0, progress - dt / RETRACT_TIME);
                if (progress <= 0.0) {
                    state = AirbrakeState.STOWED;
                }
            }
            case STOWED -> {
                boolean hardBraking = speedKmh >= AUTO_DEPLOY_SPEED && (brakePedal > 0.55 || decelG > 0.7);
                boolean highSpeedBraking = speedKmh >= HIGH_SPEED_DEPLOY && brakePedal > 0.2;
                if (hardBraking || highSpeedBraking) {
                    state = AirbrakeState.DEPLOYING;
                    manual = false;
                }
            }
            case DEPLOYED -> updateDeployed(dt);
            default -> { }
        }
    }

    private void updateDeployed(double dt) {
        holdTimer = Math.max(0.0, holdTimer - dt);
        boolean retract;
        if (manual) {
            retract = speedKmh < MANUAL_RETRACT_SPEED;
        } else {
            boolean fullThrottleStraight = throttle > 0.9 && Math.abs(steer) < 0.1 && brakePedal < 0.1;
            retract = speedKmh < AUTO_RETRACT_SPEED || fullThrottleStraight
                    || (holdTimer <= 0 && brakePedal < 0.15 && decelG < 0.2);
        }
        if (retract) {
            state = AirbrakeState.RETRACTING;
        }
    }

    /** Self-test: unfold fully, hold briefly, fold away again. */
    private void runSelfTest(double dt) {
        switch (testStage) {
            case 1 -> {
                progress = Math.min(1.0, progress + dt / DEPLOY_TIME);
                if (progress >= 1.0) {
                    testStage = 2;
                    testTimer = TEST_HOLD;
                    state = AirbrakeState.DEPLOYED;
                }
            }
            case 2 -> {
                testTimer -= dt;
                if (testTimer <= 0) {
                    testStage = 3;
                    state = AirbrakeState.RETRACTING;
                }
            }
            case 3 -> {
                progress = Math.max(0.0, progress - dt / RETRACT_TIME);
                if (progress <= 0.0) {
                    testStage = 0;
                    selfTestPending = false;
                    state = AirbrakeState.STOWED;
                }
            }
            default -> testStage = 0;
        }
    }

    public boolean isDeployed() { return state == AirbrakeState.DEPLOYED; }
    public boolean isSelfTesting() { return testStage != 0; }
    public boolean isManual() { return manual; }

    /** Extra deceleration (km/h per second) the airbrake contributes - none during the self-test (it is stationary). */
    public double getDragDeceleration() {
        return testStage != 0 ? 0.0 : DRAG_DECELERATION * progress * Math.min(1.0, speedKmh / 120.0);
    }

    /** Multiplier for tyre grip: a deployed flap pushes the rear of the car down. */
    public double getDownforceFactor() { return 1.0 + DOWNFORCE_GAIN * progress; }

    public double getProgress() { return progress; }
    public AirbrakeState getState() { return state; }

    @Override
    public boolean handles(FaultType type) { return type == FaultType.AIRBRAKE_FAULT; }

    @Override
    public boolean selfTest() { return !hasFault(FaultType.AIRBRAKE_FAULT); }

    @Override
    public String getLogSummary() { return String.format("Airbrake state=%s progress=%.0f%%", state, progress * 100); }
}
