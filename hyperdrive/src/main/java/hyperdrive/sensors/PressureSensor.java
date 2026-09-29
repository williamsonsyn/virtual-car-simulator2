package hyperdrive.sensors;

/** A sensor measuring pressure in bar. Safe when the reading is at or above a minimum. */
public class PressureSensor extends Sensor {
    private final double minSafe;

    public PressureSensor(String name, double initialBar, double minSafeBar) {
        super(name, "bar", initialBar);
        this.minSafe = minSafeBar;
    }

    // Constructor overloading: no minimum given -> anything >= 0 is fine.
    public PressureSensor(String name, double initialBar) {
        this(name, initialBar, 0.0);
    }

    @Override
    public boolean isReadingSafe() { return getValue() >= minSafe; }

    public double getMinSafe() { return minSafe; }
}
