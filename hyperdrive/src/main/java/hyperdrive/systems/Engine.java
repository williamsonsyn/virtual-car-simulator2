package hyperdrive.systems;

import hyperdrive.enums.CoolingState;
import hyperdrive.enums.EngineState;
import hyperdrive.enums.FaultType;

/**
 * Engine: continuous RPM / throttle / load / torque model. IS-A VehicleSystem (and through it Faultable) AND Loggable.
 *
 * The Car decides WHETHER the engine may start (diagnostics) and feeds the Engine what it needs to know about the
 * rest of the car once per tick (gear coupling, oil, coolant, ESC torque cut). The Engine then behaves on its own:
 * smooth RPM that follows the wheels when a gear is engaged, free-revs in neutral, slips the clutch off the line,
 * respects a dynamic RPM limit (cold oil, overheating, low oil pressure) and hits a hard limiter at the top.
 */
public class Engine extends VehicleSystem implements Loggable {
    public static final double IDLE_RPM = 850;
    public static final double REDLINE_RPM = 8000;   // start of the red zone / the RPM at which each gear's top speed is defined
    public static final double MAX_RPM = 8500;       // hard limiter when everything is warm and healthy
    public static final double DIAL_MAX_RPM = 9000;  // the tachometer scale goes to 9 x1000
    public static final double PEAK_TORQUE_NM = 770;

    private static final double CRANK_RPM = 250;
    private static final double CRANK_FIRE_TIME = 1.2;     // seconds of cranking before the engine fires
    private static final double CRANK_TIMEOUT = 6.0;       // gives up if it has not started by now
    private static final double OIL_MIN_PRESSURE = 1.0;    // bar needed before the engine counts as "running"
    private static final double COLD_OIL_LIMIT_RPM = 4500;
    private static final double COLD_OIL_BELOW = 40.0;     // C: at/below this the cold limit applies fully
    private static final double WARM_OIL_ABOVE = 70.0;     // C: at/above this there is no cold limit

    private EngineState state = EngineState.OFF;
    private double rpm = 0;
    private double throttleCommand = 0;   // what the driver's foot asks for (0-1)
    private double throttle = 0;          // actual throttle opening after pedal map + smoothing
    private double load = 0;              // 0-1
    private double torqueNm = 0;
    private double effectiveRpmLimit = MAX_RPM;
    private boolean limiterActive = false;
    private double crankTime = 0;
    private boolean startFailed = false;

    // Inputs pushed in by the Car every tick
    private double responseFactor = 1.0;
    private double curveExponent = 1.0;
    private double throttleRate = 8.0;
    private double wheelRpm = 0;
    private boolean coupled = false;
    private double launchHoldRpm = 0;
    private double oilTemp = 30;
    private double oilPressure = 0;
    private boolean oilLow = false;
    private CoolingState coolantState = CoolingState.NORMAL;
    private double torqueCut = 0;         // 0-1: ESC / gear change / launch

    public Engine() {
        super("Engine");
    }

    // ------------------------------------------------------------------ start / stop

    /** Starter motor engages. The Car has already run its diagnostics before calling this. */
    public void beginCranking() {
        state = EngineState.CRANKING;
        crankTime = 0;
        startFailed = false;
        throttleCommand = 0;
        throttle = 0;
    }

    public void stop() {
        state = EngineState.OFF;
        throttleCommand = 0;
        throttle = 0;
        launchHoldRpm = 0;
    }

    /** Pedal position 0-1. Ignored (treated as 0) unless the engine is actually running. */
    public void setThrottle(double amount) {
        if (amount < 0 || amount > 1) {
            throw new IllegalArgumentException("Throttle must be between 0 and 1");
        }
        this.throttleCommand = (state == EngineState.RUNNING) ? amount : 0;
    }

    // ------------------------------------------------------------------ inputs from the Car

    /** Drive mode: multiplies how fast RPM chases its target. */
    public void setResponseFactor(double factor) { this.responseFactor = factor; }

    /** Drive mode: pedal map exponent and throttle-plate speed. */
    public void setThrottleShaping(double exponent, double ratePerSecond) {
        this.curveExponent = exponent;
        this.throttleRate = ratePerSecond;
    }

    /** What RPM the wheels would force on the engine in the current gear, and whether a gear is engaged at all. */
    public void setDrivetrain(double wheelRpm, boolean coupled) {
        this.wheelRpm = wheelRpm;
        this.coupled = coupled;
    }

    /** Launch Control holds the engine at a fixed RPM while the brakes hold the car (0 = no hold). */
    public void setLaunchHoldRpm(double rpm) { this.launchHoldRpm = rpm; }

    public void setOilCondition(double oilTempC, double oilPressureBar, boolean oilPressureLow) {
        this.oilTemp = oilTempC;
        this.oilPressure = oilPressureBar;
        this.oilLow = oilPressureLow;
    }

    public void setCoolantState(CoolingState coolantState) { this.coolantState = coolantState; }

    /** 0 = full torque, 1 = none. Set by ESC intervention, gear changes, etc. */
    public void setTorqueCut(double cut) { this.torqueCut = Math.max(0.0, Math.min(1.0, cut)); }

    /**
     * Kept from earlier steps: forces the RPM straight to a value. The Car no longer needs it (the RPM model
     * follows the wheels by itself) but it is still handy for tests.
     */
    public void setCoupledRpm(double rpm) { this.rpm = rpm; }

    // ------------------------------------------------------------------ simulation

    @Override
    public void update(double dt) {
        switch (state) {
            case OFF -> {
                rpm -= rpm * Math.min(1.0, dt * 5.0);   // spin down
                if (rpm < 1.0) {
                    rpm = 0.0;
                }
                throttle = 0;
                load = 0;
                torqueNm = 0;
                limiterActive = false;
                effectiveRpmLimit = computeRpmLimit();
            }
            case CRANKING -> updateCranking(dt);
            case RUNNING -> updateRunning(dt);
        }
    }

    private void updateCranking(double dt) {
        crankTime += dt;
        boolean fired = crankTime >= CRANK_FIRE_TIME;
        double target = fired ? IDLE_RPM : CRANK_RPM;
        rpm += (target - rpm) * Math.min(1.0, dt * (fired ? 3.0 : 4.0));
        load = 0;
        torqueNm = 0;
        throttle = 0;
        effectiveRpmLimit = computeRpmLimit();
        if (fired && rpm >= IDLE_RPM * 0.9 && oilPressure >= OIL_MIN_PRESSURE) {
            state = EngineState.RUNNING;   // RPM has risen AND oil pressure has built: the engine is running
        } else if (crankTime > CRANK_TIMEOUT) {
            state = EngineState.OFF;
            startFailed = true;
        }
    }

    private void updateRunning(double dt) {
        effectiveRpmLimit = computeRpmLimit();

        // Pedal map + throttle plate smoothing: this is where Comfort feels soft and Track feels sharp.
        double shaped = Math.pow(throttleCommand, curveExponent);
        throttle += (shaped - throttle) * Math.min(1.0, dt * throttleRate);
        if (throttle < 0.001) {
            throttle = 0;
        }

        double target;
        if (coupled) {
            // Wheels drive the engine. Off the line the clutch slips, so RPM rises with the pedal even at 0 km/h.
            double slipTarget = IDLE_RPM + throttle * 3200.0 * Math.max(0.0, 1.0 - wheelRpm / 2600.0);
            target = Math.max(Math.max(IDLE_RPM, wheelRpm), slipTarget);
            if (launchHoldRpm > 0 && wheelRpm < 2000) {
                target = Math.max(target, launchHoldRpm);
            }
        } else {
            target = IDLE_RPM + throttle * (effectiveRpmLimit - IDLE_RPM);   // neutral / park: free revving
        }
        target = Math.min(target, effectiveRpmLimit);

        double k = (coupled ? 9.0 : 6.0) * responseFactor;
        rpm += (target - rpm) * Math.min(1.0, dt * k);
        rpm = Math.min(rpm, effectiveRpmLimit + 40);
        limiterActive = rpm >= effectiveRpmLimit - 25 && throttle > 0.3;

        double usable = limiterActive ? 0.0 : 1.0;
        double throttleEff = throttle * (1.0 - torqueCut);
        torqueNm = PEAK_TORQUE_NM * throttleEff * torqueCurve(rpm) * usable * getDerate();
        load = Math.max(0.0, Math.min(1.0, 0.06 + throttleEff * torqueCurve(rpm) * 0.94));
    }

    /** Cold oil, an overheating engine and low oil pressure all pull the usable RPM ceiling down. */
    private double computeRpmLimit() {
        double limit = MAX_RPM;
        double warmth = Math.max(0.0, Math.min(1.0, (oilTemp - COLD_OIL_BELOW) / (WARM_OIL_ABOVE - COLD_OIL_BELOW)));
        limit = Math.min(limit, COLD_OIL_LIMIT_RPM + warmth * (MAX_RPM - COLD_OIL_LIMIT_RPM));
        if (coolantState == CoolingState.CRITICAL) {
            limit = Math.min(limit, 5500);
        }
        if (oilLow && state != EngineState.OFF) {
            limit = Math.min(limit, 3000);
        }
        return Math.max(limit, IDLE_RPM + 400);
    }

    /** Fraction of full power the engine is allowed to deliver (derating). */
    private double getDerate() {
        double derate = 1.0;
        if (coolantState == CoolingState.CRITICAL) {
            derate = Math.min(derate, 0.6);
        }
        if (oilLow) {
            derate = Math.min(derate, 0.35);
        }
        return derate;
    }

    /** Simplified torque curve (0-1): weak at idle, peaks mid-range, tails off towards the redline. */
    public static double torqueCurve(double rpm) {
        if (rpm < 1000) {
            return 0.40;
        }
        if (rpm < 5500) {
            return 0.40 + 0.60 * (rpm - 1000) / 4500.0;
        }
        if (rpm < 7000) {
            return 1.0;
        }
        return Math.max(0.5, 1.0 - 0.15 * (rpm - 7000) / 1500.0);
    }

    /** What the drive wheels get as a 0-1 fraction of their maximum push (throttle x curve x derates, after any cut). */
    public double getTorqueFraction() {
        if (state != EngineState.RUNNING || limiterActive) {
            return 0.0;
        }
        return throttle * (1.0 - torqueCut) * torqueCurve(rpm) * getDerate();
    }

    // ------------------------------------------------------------------ faults / self test / logging

    @Override
    public boolean handles(FaultType type) { return false; }   // oil and cooling faults live in their own systems

    @Override
    public boolean selfTest() { return state != EngineState.RUNNING || !oilLow; }

    @Override
    public String getLogSummary() {
        return String.format("Engine state=%s rpm=%.0f throttle=%.0f%% load=%.0f%% limit=%.0f",
                state, rpm, throttle * 100, load * 100, effectiveRpmLimit);
    }

    public double getRpm() { return rpm; }
    public double getThrottle() { return throttle; }
    public double getThrottleCommand() { return throttleCommand; }
    public double getLoad() { return load; }
    public double getTorqueNm() { return torqueNm; }
    public double getEffectiveRpmLimit() { return effectiveRpmLimit; }
    public boolean isLimiterActive() { return limiterActive; }
    public EngineState getState() { return state; }
    public boolean isRunning() { return state == EngineState.RUNNING; }
    public boolean isCranking() { return state == EngineState.CRANKING; }
    public boolean hasStartFailed() { return startFailed; }
    public double getOilPressure() { return oilPressure; }
}
