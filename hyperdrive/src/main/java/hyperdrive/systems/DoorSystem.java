package hyperdrive.systems;

import hyperdrive.enums.FaultType;

/** Door switch. Only exists to drive the DOOR warning and the launch/EPB preconditions. */
public class DoorSystem extends VehicleSystem {
    private boolean open = false;

    public DoorSystem() {
        super("Doors");
    }

    public boolean isOpen() { return open; }
    public void setOpen(boolean open) { this.open = open; }

    @Override
    public boolean handles(FaultType type) { return false; }

    @Override
    public void update(double dt) { }

    @Override
    public boolean selfTest() { return true; }
}
