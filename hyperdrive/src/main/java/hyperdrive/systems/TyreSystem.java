package hyperdrive.systems;

import hyperdrive.enums.FaultType;

/**
 * Owns the four tyres (an ARRAY OF OBJECTS) and simulates each one independently: temperature (from speed, braking,
 * acceleration and cornering load), pressure (rises with heat), grip (depends on temperature, pressure, downforce)
 * and slip. Index order is always FL, FR, RL, RR.
 */
public class TyreSystem extends VehicleSystem {
    private static final String[] POSITIONS = {"FL", "FR", "RL", "RR"};
    private static final double AMBIENT = 30.0;
    private static final double BASE_GRIP_G = 1.20;
    private static final double OPTIMUM_TEMP = 85.0;

    private final Tyre[] tyres = new Tyre[4];

    // Inputs
    private double speedKmh = 0;
    private double longG = 0;          // + accelerating, - braking
    private double latG = 0;           // + right turn
    private double frontSlip = 0;
    private double rearSlip = 0;
    private final double[] wheelBrake = new double[4];
    private double downforce = 1.0;

    public TyreSystem() {
        super("Tyre System");
        for (int i = 0; i < tyres.length; i++) {
            tyres[i] = new Tyre(POSITIONS[i]);
        }
    }

    public void setSpeed(double speedKmh) { this.speedKmh = speedKmh; }

    /** Everything the tyres need from the rest of the car, once per tick. */
    public void setDynamics(double speedKmh, double longG, double latG, double frontSlip, double rearSlip,
            double[] wheelBrakePressure, double downforceFactor) {
        this.speedKmh = speedKmh;
        this.longG = longG;
        this.latG = latG;
        this.frontSlip = frontSlip;
        this.rearSlip = rearSlip;
        for (int i = 0; i < 4; i++) {
            this.wheelBrake[i] = wheelBrakePressure[i];
        }
        this.downforce = downforceFactor;
    }

    @Override
    public boolean handles(FaultType type) {
        return type == FaultType.LOW_TYRE_PRESSURE || type == FaultType.TPMS_FAULT || type == FaultType.TYRE_OVERHEAT;
    }

    @Override
    protected void onFaultChanged() {
        // LOW TYRE PRESSURE hits the front-left tyre; TPMS FAULT silences every sensor.
        tyres[0].setColdPressure(hasFault(FaultType.LOW_TYRE_PRESSURE) ? 1.2 : Tyre.NOMINAL_PRESSURE);
        for (Tyre t : tyres) {
            t.setSensorWorking(!hasFault(FaultType.TPMS_FAULT));
        }
    }

    @Override
    public void update(double dt) {
        double frontLoadShift = -longG * 0.10;       // braking (negative longG) loads the front axle
        double latShift = latG * 0.12;               // turning right loads the LEFT tyres
        double base = AMBIENT + speedKmh * 0.14;

        for (int i = 0; i < 4; i++) {
            Tyre t = tyres[i];
            boolean front = i < 2;
            boolean left = (i % 2 == 0);
            double load = 0.25 + (front ? frontLoadShift : -frontLoadShift) + (left ? latShift : -latShift);
            load = Math.max(0.08, load);

            double slip = front ? frontSlip : rearSlip;
            slip = Math.max(slip, wheelBrake[i] * 0.15);   // a braked wheel also works its tyre
            double work = load * (0.9 * Math.abs(latG) + 0.9 * Math.abs(longG) + 2.0 * slip);
            double target = base + work * 130.0;

            double temp = t.getTemperature();
            double k = (target > temp) ? 0.10 : 0.04;
            temp += (target - temp) * Math.min(1.0, dt * k);
            if (i == 3 && hasFault(FaultType.TYRE_OVERHEAT)) {
                temp += (125.0 - temp) * Math.min(1.0, dt * 0.3);   // the fault drives the rear-right tyre hot
            }
            t.setTemperature(temp);

            // Grip peaks at the optimum temperature and falls off when cold, hot or under-inflated.
            double tempOff = (temp - OPTIMUM_TEMP) / 90.0;
            double tempFactor = Math.max(0.6, 1.0 - tempOff * tempOff * 0.5);
            double pressureFactor = Math.max(0.6, 1.0 - Math.abs(t.getPressure() - Tyre.NOMINAL_PRESSURE) * 0.15);
            t.setGrip(BASE_GRIP_G * tempFactor * pressureFactor * downforce);
            t.setSlip(slip);
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

    public boolean hasHotTyre() {
        for (Tyre t : tyres) {
            if (t.isTemperatureHigh()) {
                return true;
            }
        }
        return false;
    }

    public boolean hasSensorFault() { return hasFault(FaultType.TPMS_FAULT); }

    /** Name of the first tyre matching the problem, e.g. "FL" - used in warning messages. */
    public String firstLowPressurePosition() {
        for (Tyre t : tyres) {
            if (t.isPressureLow()) {
                return t.getPosition();
            }
        }
        return "";
    }

    public String firstHotPosition() {
        for (Tyre t : tyres) {
            if (t.isTemperatureHigh()) {
                return t.getPosition();
            }
        }
        return "";
    }

    public double getFrontGrip() { return (tyres[0].getGrip() + tyres[1].getGrip()) / 2.0; }
    public double getRearGrip() { return (tyres[2].getGrip() + tyres[3].getGrip()) / 2.0; }

    /** Available braking deceleration in g: the front axle does most of the work. */
    public double getBrakeGrip() { return getFrontGrip() * 0.65 + getRearGrip() * 0.35; }

    /** Returns COPIES of the tyres (uses the copy constructor) so callers cannot change the real ones. */
    public Tyre[] getSnapshot() {
        Tyre[] copy = new Tyre[tyres.length];
        for (int i = 0; i < tyres.length; i++) {
            copy[i] = new Tyre(tyres[i]);
        }
        return copy;
    }

    /** Simulator utility: bring all tyres to a working temperature. */
    public void preWarm() {
        for (Tyre t : tyres) {
            t.setTemperature(80.0);
        }
    }
}
