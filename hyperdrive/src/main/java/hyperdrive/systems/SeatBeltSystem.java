package hyperdrive.systems;

import hyperdrive.enums.FaultType;

/** Driver seatbelt switch. Only exists to drive the SEATBELT warning and the launch/EPB preconditions. */
public class SeatBeltSystem extends VehicleSystem {
    private boolean fastened = true;

    public SeatBeltSystem() {
        super("Seat Belt");
    }

    public boolean isFastened() { return fastened; }
    public void setFastened(boolean fastened) { this.fastened = fastened; }

    @Override
    public boolean handles(FaultType type) { return false; }

    @Override
    public void update(double dt) { }

    @Override
    public boolean selfTest() { return true; }
}
