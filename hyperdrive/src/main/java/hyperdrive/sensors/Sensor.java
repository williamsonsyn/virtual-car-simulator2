package hyperdrive.sensors;

/**
 * Abstract base class for all sensors.
 * Common data (name, unit, value, working) lives here.
 * Each subclass decides for itself what a "safe" reading is (abstract method).
 */
public abstract class Sensor {
    private final String name;
    private final String unit;
    private double value;
    private boolean working;

    protected Sensor(String name, String unit, double initialValue) {
        this.name = name;
        this.unit = unit;
        this.value = initialValue;
        this.working = true;
    }

    /** Each sensor type defines its own safe range. */
    public abstract boolean isReadingSafe();

    public String getName() { return name; }
    public String getUnit() { return unit; }
    public double getValue() { return value; }
    public void setValue(double value) { this.value = value; }
    public boolean isWorking() { return working; }
    public void setWorking(boolean working) { this.working = working; }

    /** Healthy = the sensor is working AND its reading is in the safe range. */
    public boolean isHealthy() { return working && isReadingSafe(); }

    @Override
    public String toString() {
        if (!working) {
            return name + ": NO SIGNAL";
        }
        return String.format("%s: %.1f %s", name, value, unit);
    }
}
