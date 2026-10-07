package hyperdrive.systems;

import hyperdrive.enums.BrakeState;
import hyperdrive.enums.FaultType;
import hyperdrive.enums.LateralState;
import hyperdrive.sensors.PressureSensor;
import hyperdrive.sensors.TemperatureSensor;

/**
 * Brake pedal -> line pressure -> deceleration, with the simplified driver-assist features that live in the brake system:
 * ABS (slip-based), Brake Assist (fast pedal), Electronic Brake Pre-Fill (sudden throttle lift-off), Hill Hold
 * (~2 s after release on a slope), Brake-Steer (inner rear wheel braking while understeering) and Brake Disc Wiping.
 * ESC's individual-wheel braking requests are merged in here too, so ALL braking goes through one class.
 * Wheel order is FL, FR, RL, RR.
 */
public class BrakeSystem extends VehicleSystem {
    private static final double NOMINAL_PRESSURE = 120.0;
    private static final double FAULT_PRESSURE = 40.0;
    private static final double AMBIENT_TEMP = 40.0;
    public static final double MAX_BRAKE_G = 1.30;

    private static final double ASSIST_PEDAL_RATE = 3.0;      // pedal units/second that count as "aggressive"
    private static final double ASSIST_GAIN = 1.30;
    private static final double PREFILL_THROTTLE_DROP = 3.0;  // throttle units/second lift-off that triggers pre-fill
    private static final double PREFILL_SECONDS = 1.5;
    private static final double HILL_MIN_SLOPE = 3.0;         // % gradient
    private static final double HILL_HOLD_SECONDS = 2.0;
    private static final double WIPE_IDLE_SECONDS = 20.0;
    private static final double WIPE_SECONDS = 0.6;

    private final PressureSensor hydraulicPressure =
            new PressureSensor("Brake hydraulic pressure", NOMINAL_PRESSURE, 80.0);
    private final TemperatureSensor discTemp = new TemperatureSensor("Brake disc temp", AMBIENT_TEMP, 600.0);

    // Driver input
    private double pedal = 0;
    private double lastPedal = 0;
    private boolean forceAssist = false;

    // Inputs from the Car
    private double speedKmh = 0;
    private double throttle = 0;
    private double lastThrottle = 0;
    private boolean engineRunning = false;
    private boolean driveRequested = false;
    private double slopePercent = 0;
    private double gripG = 1.2;
    private double steer = 0;
    private LateralState lateralState = LateralState.NEUTRAL;
    private double understeerAmount = 0;
    private double brakeSteerStrength = 0.4;
    private final double[] escRequest = new double[4];

    // Internal state
    private double applied = 0;          // line pressure fraction 0-1 (what the calipers actually get)
    private boolean assistActive = false;
    private boolean absActive = false;
    private double absHold = 0;
    private double prefillTimer = 0;
    private boolean holdArmed = false;
    private boolean hillHoldActive = false;
    private double hillHoldTimer = 0;
    private double heldPedal = 0;
    private boolean brakeSteerActive = false;
    private double idleBrakeTimer = 0;
    private double wipeTimer = 0;
    private double decelG = 0;
    private double wheelSlip = 0;
    private final double[] wheelPressure = new double[4];

    public BrakeSystem() {
        super("Brake System");
    }

    // ------------------------------------------------------------------ driver input

    // Method overloading: same name, different parameter lists.
    public void apply(double amount) {
        apply(amount, false);
    }

    /** @param emergency true = the driver stamped on the pedal: Brake Assist engages immediately. */
    public void apply(double amount, boolean emergency) {
        if (amount < 0 || amount > 1) {
            throw new IllegalArgumentException("Brake input must be between 0 and 1");
        }
        this.pedal = amount;
        this.forceAssist = emergency && amount > 0.5;
    }

    public void release() {
        pedal = 0;
        forceAssist = false;
    }

    // ------------------------------------------------------------------ inputs from the Car

    public void setSpeed(double speedKmh) { this.speedKmh = speedKmh; }

    public void setConditions(double speedKmh, double throttle, boolean engineRunning, boolean driveRequested,
            double slopePercent, double brakeGripG) {
        this.speedKmh = speedKmh;
        this.throttle = throttle;
        this.engineRunning = engineRunning;
        this.driveRequested = driveRequested;
        this.slopePercent = slopePercent;
        this.gripG = Math.max(0.2, brakeGripG);
    }

    public void setDynamics(double steerNorm, LateralState lateralState, double understeerAmount, double strength) {
        this.steer = steerNorm;
        this.lateralState = lateralState;
        this.understeerAmount = understeerAmount;
        this.brakeSteerStrength = strength;
    }

    /** ESC asks for braking on individual wheels (0-1 each). */
    public void setEscRequests(double[] requests) {
        for (int i = 0; i < 4; i++) {
            escRequest[i] = requests[i];
        }
    }

    // ------------------------------------------------------------------ simulation

    @Override
    public void update(double dt) {
        double pedalRate = dt > 0 ? (pedal - lastPedal) / dt : 0;
        lastPedal = pedal;
        double throttleDrop = dt > 0 ? (lastThrottle - throttle) / dt : 0;

        // Brake Assist: an aggressive pedal application boosts the effective force until the pedal is eased.
        if (!assistActive && pedal >= 0.45 && (pedalRate >= ASSIST_PEDAL_RATE || forceAssist)) {
            assistActive = true;
        }
        if (assistActive && pedal < 0.25) {
            assistActive = false;
        }

        // Electronic Brake Pre-Fill: a sudden lift-off from a high throttle readies the pads (no braking happens).
        if (lastThrottle > 0.45 && throttleDrop >= PREFILL_THROTTLE_DROP) {
            prefillTimer = PREFILL_SECONDS;
        }
        lastThrottle = throttle;
        prefillTimer = Math.max(0.0, prefillTimer - dt);
        boolean prefill = prefillTimer > 0;

        updateHillHold(dt);

        // Driver demand (or the retained hill-hold pressure), with assist gain.
        double demand = hillHoldActive ? Math.max(pedal, heldPedal) : pedal;
        if (assistActive) {
            demand = Math.min(1.0, demand * ASSIST_GAIN);
        }
        double wipe = updateDiscWiping(dt);
        demand = Math.max(demand, wipe);

        // Line pressure follows demand with a small lag; pre-fill and assist shorten that lag.
        double tau = prefill ? 0.04 : 0.14;
        if (assistActive) {
            tau *= 0.6;
        }
        if (demand >= applied) {
            applied += (demand - applied) * Math.min(1.0, dt / tau);
        } else {
            applied += (demand - applied) * Math.min(1.0, dt / 0.08);
        }

        // Wheel slip and ABS: braking demand versus the grip the tyres can actually provide.
        double pressureFactor = Math.min(1.0, hydraulicPressure.getValue() / NOMINAL_PRESSURE);
        double requestedG = applied * pressureFactor * MAX_BRAKE_G;
        double effectiveG = requestedG;
        wheelSlip = 0;
        boolean wouldLock = speedKmh > 5.0 && requestedG > 0.05 && requestedG > gripG * 1.01;
        if (wouldLock) {
            double overload = Math.min(1.0, requestedG / gripG - 1.0);
            if (!hasFault(FaultType.ABS_FAULT)) {
                effectiveG = gripG * 0.97;      // ABS releases and re-applies: holds just below the limit
                wheelSlip = 0.10 + overload * 0.05;
                absHold = 0.25;                 // keep the indication visible for a moment
            } else {
                effectiveG = gripG * 0.78;      // locked wheels slide: less grip than rolling
                wheelSlip = Math.min(1.0, 0.45 + overload);
            }
        }
        absHold = Math.max(0.0, absHold - dt);
        absActive = absHold > 0 && speedKmh > 5.0;

        // Brake-Steer: while understeering with steering applied, brake the inner rear wheel to help the car turn.
        brakeSteerActive = speedKmh > 30.0 && Math.abs(steer) > 0.25
                && lateralState == LateralState.UNDERSTEER && understeerAmount > 0.03;
        double extraG = 0;
        double[] brakeSteer = new double[4];
        if (brakeSteerActive) {
            double p = Math.min(1.0, understeerAmount * 3.0) * brakeSteerStrength;
            brakeSteer[steer > 0 ? 3 : 2] = p;     // inner rear: RR when steering right, RL when steering left
            extraG += p * 0.12;
        }
        double escSum = 0;
        for (int i = 0; i < 4; i++) {
            escSum += escRequest[i];
        }
        extraG += escSum * 0.10;

        decelG = effectiveG + extraG;

        // Per-wheel pressure for the vehicle diagram: front-biased driver braking + ESC + brake-steer.
        for (int i = 0; i < 4; i++) {
            double driver = applied * (i < 2 ? 0.65 : 0.35) * 2.0 * pressureFactor;
            wheelPressure[i] = Math.min(1.0, Math.max(driver, Math.max(escRequest[i], brakeSteer[i])));
        }

        // Disc heating from braking energy, cooling from airflow.
        double t = discTemp.getValue();
        double heating = decelG * (speedKmh / 100.0) * 90.0 * dt;
        double cooling = (t - AMBIENT_TEMP) * 0.05 * (1.0 + speedKmh / 150.0) * dt;
        discTemp.setValue(Math.max(AMBIENT_TEMP, t + heating - cooling));
    }

    /** Hill Hold: stopped + brake held + on a slope -> after release, keep the pressure for about 2 seconds. */
    private void updateHillHold(double dt) {
        boolean stopped = speedKmh < 2.0;
        boolean slope = Math.abs(slopePercent) >= HILL_MIN_SLOPE;
        if (engineRunning && stopped && slope && pedal > 0.1) {
            holdArmed = true;
            heldPedal = Math.min(0.6, Math.max(heldPedal, pedal));
        }
        if (!hillHoldActive && holdArmed && pedal <= 0.1) {
            hillHoldActive = true;
            hillHoldTimer = HILL_HOLD_SECONDS;
        }
        if (hillHoldActive) {
            hillHoldTimer -= dt;
            if (hillHoldTimer <= 0 || (driveRequested && throttle > 0.15) || !stopped || pedal > 0.1) {
                hillHoldActive = false;
                holdArmed = false;
                heldPedal = 0;
            }
        }
        if (!stopped || !slope) {
            if (!hillHoldActive) {
                holdArmed = false;
                heldPedal = 0;
            }
        }
    }

    /** Brake Disc Wiping: after a stretch with no braking at speed, the pads lightly touch the discs for a moment. */
    private double updateDiscWiping(double dt) {
        if (wipeTimer > 0) {
            wipeTimer -= dt;
            return 0.08;
        }
        if (pedal > 0.05 || speedKmh < 60.0) {
            idleBrakeTimer = 0;
            return 0;
        }
        idleBrakeTimer += dt;
        if (idleBrakeTimer >= WIPE_IDLE_SECONDS) {
            idleBrakeTimer = 0;
            wipeTimer = WIPE_SECONDS;
        }
        return 0;
    }

    // ------------------------------------------------------------------ faults / state

    @Override
    public boolean handles(FaultType type) {
        return type == FaultType.LOW_BRAKE_PRESSURE || type == FaultType.ABS_FAULT;
    }

    @Override
    protected void onFaultChanged() {
        hydraulicPressure.setValue(hasFault(FaultType.LOW_BRAKE_PRESSURE) ? FAULT_PRESSURE : NOMINAL_PRESSURE);
    }

    @Override
    public boolean selfTest() {
        return hydraulicPressure.isHealthy() && discTemp.isHealthy() && !hasFault(FaultType.ABS_FAULT);
    }

    public BrakeState getState() {
        if (!hydraulicPressure.isHealthy()) {
            return BrakeState.FAULT;
        }
        if (absActive) {
            return BrakeState.ABS;
        }
        if (assistActive) {
            return BrakeState.ASSIST;
        }
        if (hillHoldActive) {
            return BrakeState.HILL_HOLD;
        }
        return applied > 0.05 ? BrakeState.APPLIED : BrakeState.RELEASED;
    }

    public boolean hasAbsFault() { return hasFault(FaultType.ABS_FAULT); }
    public boolean isPedalPressed() { return pedal > 0.1; }
    public boolean isAbsActive() { return absActive; }
    public boolean isBrakeAssistActive() { return assistActive; }
    public boolean isPrefillActive() { return prefillTimer > 0; }
    public boolean isHillHoldActive() { return hillHoldActive; }
    public boolean isBrakeSteerActive() { return brakeSteerActive; }
    public boolean isDiscWipingActive() { return wipeTimer > 0; }

    /** Raw pedal position, 0.0-1.0. Used by ESC and Launch Control to check the driver's inputs. */
    public double getPedalPosition() { return pedal; }

    /** Actual deceleration being produced, in g. */
    public double getDecelG() { return decelG; }

    /** 0.0 - 1.0 share of the maximum braking force currently being produced. */
    public double getBrakingForce() { return Math.min(1.0, decelG / MAX_BRAKE_G); }

    /** Line pressure in bar (what the dashboard shows as brake pressure). */
    public double getLinePressureBar() { return applied * hydraulicPressure.getValue(); }

    public double getWheelSlip() { return wheelSlip; }
    public double getWheelPressure(int wheel) { return wheelPressure[wheel]; }

    public double[] getWheelPressures() {
        double[] copy = new double[4];
        System.arraycopy(wheelPressure, 0, copy, 0, 4);
        return copy;
    }

    /** How much the brake-steer helps rotate the car (0-1), used to relieve understeer in the dynamics model. */
    public double getYawAssist() {
        return brakeSteerActive ? Math.min(0.6, understeerAmount * 3.0 * brakeSteerStrength * 0.6) : 0.0;
    }

    public double getHydraulicPressure() { return hydraulicPressure.getValue(); }
    public double getMinPressure() { return hydraulicPressure.getMinSafe(); }
    public boolean isPressureSafe() { return hydraulicPressure.isReadingSafe(); }
    public double getDiscTemperature() { return discTemp.getValue(); }
    public double getDiscTemperatureLimit() { return discTemp.getMaxSafe(); }
}
