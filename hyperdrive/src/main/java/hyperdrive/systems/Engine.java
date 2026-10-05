package hyperdrive.systems;

import hyperdrive.enums.FaultType;
import hyperdrive.sensors.PressureSensor;

/** Engine: RPM, throttle and oil pressure. IS-A VehicleSystem (and through it Faultable) AND Loggable. */
public class Engine extends VehicleSystem implements Loggable {
    public static final double IDLE_RPM = 850;
    public static final double REDLINE_RPM = 8000;

    private double rpm = 0;
    private double throttle = 0;      // 0.0 - 1.0
    private boolean running = false;
    private double responseFactor = 1.0;   // set by the Car from the current DriveMode each tick
    private final PressureSensor oilPressure = new PressureSensor("Oil pressure", 0.0, 1.0);

    public Engine() {
        super("Engine");
    }

    // NOTE: the Car decides WHETHER the engine may start (diagnostics). The Engine only obeys.
    public void start() {
        running = true;
        rpm = 0;   // NOT IDLE_RPM directly - update(dt)'s existing chase brings it up to idle gradually,
                   // like a real engine cranking, rather than snapping to idle the instant it starts.
    }

    public void stop() {
        running = false;
        throttle = 0;
    }

    public void setThrottle(double amount) {
        if (amount < 0 || amount > 1) {
            throw new IllegalArgumentException("Throttle must be between 0 and 1");
        }
        this.throttle = running ? amount : 0;
    }

    /** Called by the Car once per tick, from the current DriveMode's throttle response. */
    public void setResponseFactor(double factor) { this.responseFactor = factor; }

    /**
     * Called by the Car when the transmission is mechanically coupled to the wheels (in a forward
     * gear or reverse) - overrides whatever this tick's throttle-chase computed, so RPM reflects
     * the car's actual speed through the current gear ratio, the way a real geared car's engine
     * is tied to the wheels. See Car.updateSpeed() for when this is and isn't applied.
     */
    public void setCoupledRpm(double rpm) {
        this.rpm = rpm;
    }

    @Override
    public void update(double dt) {
        if (running) {
            double target = IDLE_RPM + throttle * (REDLINE_RPM - IDLE_RPM);
            rpm += (target - rpm) * Math.min(1.0, dt * 4.0 * responseFactor);   // mode changes how fast this chases
        } else {
            rpm -= rpm * Math.min(1.0, dt * 5.0);                // spin down
            if (rpm < 1.0) {
                rpm = 0.0;
            }
        }
        oilPressure.setValue(running ? 1.5 + rpm / 2000.0 : 0.0);
    }

    @Override
    public boolean handles(FaultType type) { return false; }   // engine has no fault of its own yet

    @Override
    public boolean selfTest() { return !running || oilPressure.isHealthy(); }

    @Override
    public String getLogSummary() {
        return String.format("Engine running=%b rpm=%.0f throttle=%.0f%%", running, rpm, throttle * 100);
    }

    public double getRpm() { return rpm; }
    public double getThrottle() { return throttle; }
    public boolean isRunning() { return running; }
    public double getOilPressure() { return oilPressure.getValue(); }
}
