package hyperdrive.systems;

import hyperdrive.enums.FaultType;

/** Supplementary restraint (airbag) system. Healthy unless SRS_FAULT is injected; drives the SRS warning. */
public class SRSSystem extends VehicleSystem {
    public SRSSystem() {
        super("SRS");
    }

    @Override
    public boolean handles(FaultType type) { return type == FaultType.SRS_FAULT; }

    @Override
    public void update(double dt) { }

    @Override
    public boolean selfTest() { return !hasFault(FaultType.SRS_FAULT); }
}
