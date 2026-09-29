package hyperdrive.systems;

import hyperdrive.sensors.PressureSensor;
import hyperdrive.sensors.TemperatureSensor;

/**
 * One tyre. Not a VehicleSystem - it is a plain part owned by TyreSystem (composition).
 * Shows: default, parameterized and copy constructors, constructor chaining with this(...).
 */
public class Tyre {
    public static final double NOMINAL_PRESSURE = 2.4;   // bar

    private final String position;                 // FL, FR, RL, RR
    private final PressureSensor pressureSensor;
    private final TemperatureSensor temperatureSensor;

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
        this.pressureSensor = new PressureSensor(position + " tyre pressure", pressureBar, 1.8);
        this.temperatureSensor = new TemperatureSensor(position + " tyre temp", temperatureC, 110.0);
    }

    // Copy constructor: builds a NEW, independent Tyre (with its own sensors = deep copy)
    public Tyre(Tyre other) {
        this(other.position, other.getPressure(), other.getTemperature());
        this.pressureSensor.setWorking(other.pressureSensor.isWorking());
        this.temperatureSensor.setWorking(other.temperatureSensor.isWorking());
    }

    public String getPosition() { return position; }
    public double getPressure() { return pressureSensor.getValue(); }
    public double getTemperature() { return temperatureSensor.getValue(); }

    public void setPressure(double bar) {
        if (bar < 0) {
            throw new IllegalArgumentException("Pressure cannot be negative");
        }
        pressureSensor.setValue(bar);
    }

    public void setTemperature(double celsius) { temperatureSensor.setValue(celsius); }

    public boolean isPressureLow() { return !pressureSensor.isReadingSafe(); }
    public boolean isTemperatureHigh() { return !temperatureSensor.isReadingSafe(); }
    public boolean isSensorWorking() {
        return pressureSensor.isWorking() && temperatureSensor.isWorking();
    }

    @Override
    public String toString() {
        return String.format("%s: %.1f bar, %.0f C", position, getPressure(), getTemperature());
    }
}
