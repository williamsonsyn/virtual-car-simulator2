package hyperdrive.systems;

import hyperdrive.enums.FaultType;

/** Owns the four tyres (an ARRAY OF OBJECTS) and warms them up with speed. */
public class TyreSystem extends VehicleSystem {
    private static final String[] POSITIONS = {"FL", "FR", "RL", "RR"};

    private final Tyre[] tyres = new Tyre[4];
    private double speedKmh = 0;

    public TyreSystem() {
        super("Tyre System");
        for (int i = 0; i < tyres.length; i++) {
            tyres[i] = new Tyre(POSITIONS[i]);
        }
    }

    public void setSpeed(double speedKmh) { this.speedKmh = speedKmh; }

    @Override
    public boolean handles(FaultType type) { return type == FaultType.LOW_TYRE_PRESSURE; }

    @Override
    protected void onFaultChanged() {
        // The fault hits the front-left tyre.
        tyres[0].setPressure(hasFault(FaultType.LOW_TYRE_PRESSURE) ? 1.2 : Tyre.NOMINAL_PRESSURE);
    }

    @Override
    public void update(double dt) {
        double target = 30.0 + speedKmh * 0.25;   // faster = hotter tyres
        for (Tyre t : tyres) {
            t.setTemperature(t.getTemperature() + (target - t.getTemperature()) * Math.min(1.0, dt * 0.05));
        }
    }

    @Override
    public boolean selfTest() {
        for (Tyre t : tyres) {
            if (t.isPressureLow() || t.isTemperatureHigh() || !t.isSensorWorking()) {
                return false;
            }
        }
        return true;
    }

    public boolean hasLowPressure() {
        for (Tyre t : tyres) {
            if (t.isPressureLow()) {
                return true;
            }
        }
        return false;
    }

    /** Returns COPIES of the tyres (uses the copy constructor) so callers cannot change the real ones. */
    public Tyre[] getSnapshot() {
        Tyre[] copy = new Tyre[tyres.length];
        for (int i = 0; i < tyres.length; i++) {
            copy[i] = new Tyre(tyres[i]);
        }
        return copy;
    }
}
