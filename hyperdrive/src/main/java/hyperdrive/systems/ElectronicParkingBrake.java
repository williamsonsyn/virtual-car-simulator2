package hyperdrive.systems;

import hyperdrive.enums.EpbState;
import hyperdrive.enums.FaultType;

/**
 * Lightweight electronic parking brake. Starts ENGAGED, engages itself when the engine is shut down, and the Car
 * releases it automatically when the driver pulls away (or the driver toggles it with SPACE).
 * While engaged, VehicleDynamics holds the car still.
 */
public class ElectronicParkingBrake extends VehicleSystem implements Loggable {
    private EpbState state = EpbState.ENGAGED;

    public ElectronicParkingBrake() {
        super("Parking Brake");
    }

    public void engage() { state = EpbState.ENGAGED; }
    public void release() { state = EpbState.RELEASED; }
    public boolean isEngaged() { return state == EpbState.ENGAGED; }
    public EpbState getState() { return state; }

    @Override
    public boolean handles(FaultType type) { return false; }

    @Override
    public void update(double dt) { }

    @Override
    public boolean selfTest() { return true; }

    @Override
    public String getLogSummary() { return "EPB state=" + state; }
}
