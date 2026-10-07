package hyperdrive.systems;

import hyperdrive.enums.FaultType;
import hyperdrive.enums.GearPosition;

/**
 * Rear camera + parking sensors (simulated - no computer vision). Activates in REVERSE, tracks the distance to a
 * simulated obstacle behind the car (shrinks while reversing, grows when driving forward) and reports a proximity
 * warning level 0-3. The Car stops the car when the obstacle is reached.
 */
public class RearCameraSystem extends VehicleSystem {
    public static final double START_DISTANCE_M = 3.0;
    public static final double MAX_DISTANCE_M = 6.0;
    public static final double STOP_DISTANCE_M = 0.30;

    private double distanceM = START_DISTANCE_M;
    private boolean active = false;
    private GearPosition gear = GearPosition.P;
    private double speedKmh = 0;

    public RearCameraSystem() {
        super("Rear Camera");
    }

    public void setMotion(GearPosition gear, double speedKmh) {
        this.gear = gear;
        this.speedKmh = speedKmh;
    }

    @Override
    public void update(double dt) {
        active = (gear == GearPosition.R);
        double metresPerSecond = speedKmh / 3.6;
        if (gear == GearPosition.R) {
            distanceM = Math.max(STOP_DISTANCE_M, distanceM - metresPerSecond * dt);
        } else if (gear.isForward()) {
            distanceM = Math.min(MAX_DISTANCE_M, distanceM + metresPerSecond * dt);
        }
    }

    /** 0 = clear, 1 = obstacle detected, 2 = close, 3 = very close (continuous tone). */
    public int getWarningLevel() {
        if (!active) {
            return 0;
        }
        if (distanceM < 0.6) {
            return 3;
        }
        if (distanceM < 1.2) {
            return 2;
        }
        return distanceM < 2.0 ? 1 : 0;
    }

    /** True when the car cannot reverse any further. */
    public boolean isObstacleReached() { return active && distanceM <= STOP_DISTANCE_M + 0.01; }

    /** Simulator utility: put the obstacle back at its starting distance. */
    public void resetObstacle() { distanceM = START_DISTANCE_M; }

    public boolean isActive() { return active; }
    public double getDistanceM() { return distanceM; }

    @Override
    public boolean handles(FaultType type) { return false; }

    @Override
    public boolean selfTest() { return true; }
}
