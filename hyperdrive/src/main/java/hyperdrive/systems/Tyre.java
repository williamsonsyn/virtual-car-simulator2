package hyperdrive.systems;

import hyperdrive.enums.TyreCondition;
import hyperdrive.sensors.PressureSensor;
import hyperdrive.sensors.TemperatureSensor;

/**
 * One tyre. Not a VehicleSystem - it is a plain part owned by TyreSystem (composition).
 * Tracks pressure, temperature, grip and slip, and reports a TyreCondition for the vehicle diagram.
 * Shows: default, parameterized and copy constructors, constructor chaining with this(...).
 */
public class Tyre {
    public static final double NOMINAL_PRESSURE = 2.4;   // bar (cold)
    public static final double MIN_SAFE_PRESSURE = 1.8;  // bar
    public static final double MAX_SAFE_TEMP = 105.0;    // C: above this the tyre is HOT
    public static final double COLD_BELOW_TEMP = 45.0;   // C: below this the tyre is COLD (less grip)

    private final String position;                 // FL, FR, RL, RR
    private final PressureSensor pressureSensor;
    private final TemperatureSensor temperatureSensor;
    private double coldPressure;                   // inflation pressure at 25 C; the live reading rises with heat
    private double grip = 1.0;                     // available grip in g (computed by TyreSystem)
    private double slip = 0.0;                     // 0-1

    // Default constructor
    public Tyre() {
        this("FL");
    }

    // Constructor with position only
    public Tyre(String position) {
        this(position, NOMINAL_PRESSURE, 30.0);
    }

    // Full parameterized constructor - all others end up here
    public Tyre(String position, double pressureBar, double temperatureC) {
        this.position = position;
        this.coldPressure = pressureBar;
        this.pressureSensor = new PressureSensor(position + " tyre pressure", pressureBar, MIN_SAFE_PRESSURE);
        this.temperatureSensor = new TemperatureSensor(position + " tyre temp", temperatureC, MAX_SAFE_TEMP);
    }

    // Copy constructor: builds a NEW, independent Tyre (with its own sensors = deep copy)
    public Tyre(Tyre other) {
        this(other.position, other.getPressure(), other.getTemperature());
        this.coldPressure = other.coldPressure;
        this.grip = other.grip;
        this.slip = other.slip;
        this.pressureSensor.setWorking(other.pressureSensor.isWorking());
        this.temperatureSensor.setWorking(other.temperatureSensor.isWorking());
    }

    public String getPosition() { return position; }
    public double getPressure() { return pressureSensor.getValue(); }
    public double getTemperature() { return temperatureSensor.getValue(); }
    public double getColdPressure() { return coldPressure; }
    public double getGrip() { return grip; }
    public double getSlip() { return slip; }

    /** Sets the pressure reading directly (and the cold inflation pressure to match). */
    public void setPressure(double bar) {
        if (bar < 0) {
            throw new IllegalArgumentException("Pressure cannot be negative");
        }
        pressureSensor.setValue(bar);
        coldPressure = bar;
    }

    /** Sets the cold inflation pressure; the live reading is recomputed from it and the temperature each tick. */
    public void setColdPressure(double bar) {
        if (bar < 0) {
            throw new IllegalArgumentException("Pressure cannot be negative");
        }
        this.coldPressure = bar;
    }

    public void setTemperature(double celsius) {
        temperatureSensor.setValue(celsius);
        // Gas law, simplified: pressure rises as the tyre heats up (reference: 25 C).
        pressureSensor.setValue(coldPressure * (celsius + 273.0) / (25.0 + 273.0));
    }

    public void setGrip(double grip) { this.grip = grip; }
    public void setSlip(double slip) { this.slip = Math.max(0.0, Math.min(1.0, slip)); }

    /** TPMS fault: the sensors stop reporting. */
    public void setSensorWorking(boolean working) {
        pressureSensor.setWorking(working);
        temperatureSensor.setWorking(working);
    }

    public boolean isPressureLow() { return !pressureSensor.isReadingSafe(); }
    public boolean isTemperatureHigh() { return !temperatureSensor.isReadingSafe(); }
    public boolean isSensorWorking() {
        return pressureSensor.isWorking() && temperatureSensor.isWorking();
    }

    public TyreCondition getCondition() {
        if (!isSensorWorking()) {
            return TyreCondition.FAULT;
        }
        if (isPressureLow()) {
            return TyreCondition.LOW_PRESSURE;
        }
        if (isTemperatureHigh()) {
            return TyreCondition.HOT;
        }
        return getTemperature() < COLD_BELOW_TEMP ? TyreCondition.COLD : TyreCondition.NORMAL;
    }

    @Override
    public String toString() {
        return String.format("%s: %.1f bar, %.0f C", position, getPressure(), getTemperature());
    }
}
