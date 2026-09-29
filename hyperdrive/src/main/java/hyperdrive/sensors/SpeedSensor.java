package hyperdrive.sensors;

/** Vehicle speed sensor in km/h. */
public class SpeedSensor extends Sensor {
    public static final double MAX_SPEED_KMH = 340.0;

    public SpeedSensor() {
        super("Speed", "km/h", 0.0);
    }

    @Override
    public boolean isReadingSafe() { return getValue() <= MAX_SPEED_KMH; }
}
