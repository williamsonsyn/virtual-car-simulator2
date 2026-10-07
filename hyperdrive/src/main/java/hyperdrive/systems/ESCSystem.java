package hyperdrive.systems;

import hyperdrive.enums.EscMode;
import hyperdrive.enums.FaultType;

/**
 * Simplified Electronic Stability / Traction Control with four states: ESC ON, ESC DYNAMIC, ESC TRACK DYNAMIC, ESC OFF.
 * It reads the tyre slip the dynamics model reports and intervenes in two ways: it CUTS ENGINE TORQUE and it asks the
 * BrakeSystem to brake individual wheels (outer front against oversteer, inner rear against understeer).
 * Each mode tolerates a different amount of slip before it acts; the drive mode's permissiveness widens that further.
 * ESC OFF stays OFF until the next ignition cycle (the Car resets it to ESC ON in powerOn()).
 */
public class ESCSystem extends VehicleSystem implements Loggable {
    private static final double BASE_SLIP_THRESHOLD = 0.10;

    private EscMode mode = EscMode.ON;

    // Inputs
    private double throttle = 0;
    private double speedKmh = 0;
    private double steer = 0;
    private double frontSlip = 0;
    private double rearSlip = 0;
    private double permissiveness = 1.0;

    // Outputs
    private boolean intervening = false;
    private double torqueCut = 0;
    private double yawRelief = 0;
    private final double[] wheelRequest = new double[4];   // FL FR RL RR, 0-1
    private double driftLevel = 0;

    public ESCSystem() {
        super("ESC");
    }

    /** Car feeds the driving situation, once per tick. */
    public void setInputs(double throttle, double speedKmh, double steerNorm, double frontSlip, double rearSlip,
            double permissiveness) {
        this.throttle = throttle;
        this.speedKmh = speedKmh;
        this.steer = steerNorm;
        this.frontSlip = frontSlip;
        this.rearSlip = rearSlip;
        this.permissiveness = permissiveness;
    }

    public EscMode getMode() { return mode; }

    /** The Car has already validated that this ESC mode is allowed right now. */
    public void setMode(EscMode mode) { this.mode = mode; }

    /** Kept from earlier steps: true = ESC ON, false = ESC OFF. */
    public void setEnabled(boolean enabled) { this.mode = enabled ? EscMode.ON : EscMode.OFF; }

    public boolean isEnabled() { return mode != EscMode.OFF; }

    @Override
    public void update(double dt) {
        intervening = false;
        torqueCut = 0;
        yawRelief = 0;
        for (int i = 0; i < 4; i++) {
            wheelRequest[i] = 0;
        }
        driftLevel = Math.min(100.0, Math.max(frontSlip, rearSlip) * 100.0);

        if (hasFault(FaultType.ESC_FAULT) || mode == EscMode.OFF || speedKmh < 5.0 && throttle < 0.5) {
            return;
        }
        double threshold = BASE_SLIP_THRESHOLD / Math.max(0.2, mode.getStrength()) * permissiveness;
        double cut = 0;

        if (rearSlip > threshold) {                       // wheelspin / oversteer
            cut = Math.min(0.8, (rearSlip - threshold) * 4.0);
            if (Math.abs(steer) > 0.2) {
                wheelRequest[steer > 0 ? 0 : 1] = Math.min(1.0, (rearSlip - threshold) * 4.0);   // outer front wheel
            } else {
                wheelRequest[2] = wheelRequest[3] = Math.min(0.5, (rearSlip - threshold) * 2.0);  // straight-line wheelspin
            }
        }
        if (frontSlip > threshold && Math.abs(steer) > 0.2) {   // understeer
            cut = Math.max(cut, Math.min(0.5, (frontSlip - threshold) * 3.0));
            wheelRequest[steer > 0 ? 3 : 2] = Math.max(wheelRequest[steer > 0 ? 3 : 2],
                    Math.min(1.0, (frontSlip - threshold) * 3.0));                                // inner rear wheel
        }
        torqueCut = cut;
        intervening = cut > 0.02;
        if (intervening) {
            yawRelief = Math.min(0.7, 0.3 + cut * 0.5) * Math.min(1.0, mode.getStrength() + 0.3);
        }
    }

    /** How much the acceleration target is reduced by right now (0.0 = no cut). */
    public double getInterventionStrength() { return torqueCut; }

    public boolean isTractionCutActive() { return intervening; }
    public boolean isIntervening() { return intervening; }
    public double getTorqueCut() { return torqueCut; }
    public double getYawRelief() { return yawRelief; }
    public double getDriftLevel() { return driftLevel; }

    public double[] getWheelRequests() {
        double[] copy = new double[4];
        System.arraycopy(wheelRequest, 0, copy, 0, 4);
        return copy;
    }

    @Override
    public boolean handles(FaultType type) { return type == FaultType.ESC_FAULT; }

    @Override
    public boolean selfTest() { return !hasFault(FaultType.ESC_FAULT); }

    @Override
    public String getLogSummary() {
        return String.format("ESC mode=%s intervening=%b torqueCut=%.0f%% drift=%.0f%%",
                mode, intervening, torqueCut * 100, driftLevel);
    }
}
