package hyperdrive.systems;

import hyperdrive.enums.AirbrakeState;
import hyperdrive.enums.FaultType;

/**
 * Active aerodynamic airbrake. Deploys for extra drag and stability at speed.
 * Transition states (DEPLOYING/RETRACTING) mean it takes a moment to move, like Transmission's shiftTimer.
 */
public class Airbrake extends VehicleSystem implements Loggable {
    public static final double MIN_MANUAL_DEPLOY_SPEED = 80.0;   // km/h - needs airflow to work
    private static final double AUTO_DEPLOY_SPEED = 120.0;
    private static final double AUTO_RETRACT_SPEED = 60.0;
    private static final double TRANSITION_TIME = 0.4;
    private static final double DRAG_DECELERATION = 8.0;         // km/h per second, while fully deployed

    private AirbrakeState state = AirbrakeState.RETRACTED;
    private double transitionTimer = 0;
    private double speedKmh = 0;
    private double brakePedal = 0;
    private boolean engineRunning = false;

    public Airbrake() {
        super("Airbrake");
    }

    /** Car feeds in what the airbrake needs to know, once per tick. */
    public void setInputs(double speedKmh, double brakePedal, boolean engineRunning) {
        this.speedKmh = speedKmh;
        this.brakePedal = brakePedal;
        this.engineRunning = engineRunning;
    }

    /** Car has already validated preconditions before calling this. */
    public void deployManual() {
        if (state == AirbrakeState.RETRACTED) {
            state = AirbrakeState.DEPLOYING;
            transitionTimer = TRANSITION_TIME;
        }
    }

    public void retractManual() {
        if (state == AirbrakeState.DEPLOYED) {
            state = AirbrakeState.RETRACTING;
            transitionTimer = TRANSITION_TIME;
        }
    }

    @Override
    public void update(double dt) {
        if (state == AirbrakeState.DEPLOYING || state == AirbrakeState.RETRACTING) {
            transitionTimer -= dt;
            if (transitionTimer <= 0) {
                state = (state == AirbrakeState.DEPLOYING) ? AirbrakeState.DEPLOYED : AirbrakeState.RETRACTED;
            }
            return;   // don't also evaluate auto-logic mid-transition
        }
        // Automatic deployment based on conditions: hard braking at high speed.
        if (state == AirbrakeState.RETRACTED && engineRunning
                && speedKmh >= AUTO_DEPLOY_SPEED && brakePedal > 0.6) {
            state = AirbrakeState.DEPLOYING;
            transitionTimer = TRANSITION_TIME;
        } else if (state == AirbrakeState.DEPLOYED && (speedKmh < AUTO_RETRACT_SPEED || brakePedal < 0.2)) {
            state = AirbrakeState.RETRACTING;
            transitionTimer = TRANSITION_TIME;
        }
    }

    public boolean isDeployed() { return state == AirbrakeState.DEPLOYED; }

    /** Extra deceleration (km/h per second) the airbrake contributes while deployed. */
    public double getDragDeceleration() { return isDeployed() ? DRAG_DECELERATION : 0.0; }

    public AirbrakeState getState() { return state; }

    @Override
    public boolean handles(FaultType type) { return false; }

    @Override
    public boolean selfTest() { return true; }   // no fault path wired to the airbrake yet

    @Override
    public String getLogSummary() { return "Airbrake state=" + state; }
}
