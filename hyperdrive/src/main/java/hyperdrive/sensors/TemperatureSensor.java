package hyperdrive.sensors;

/** A sensor measuring temperature in degrees C. Safe when the reading is at or below a maximum. */
public class TemperatureSensor extends Sensor {
    private final double maxSafe;

    public TemperatureSensor(String name, double initialC, double maxSafeC) {
        super(name, "C", initialC);
        this.maxSafe = maxSafeC;
    }

    @Override
    public boolean isReadingSafe() { return getValue() <= maxSafe; }

    public double getMaxSafe() { return maxSafe; }
}
