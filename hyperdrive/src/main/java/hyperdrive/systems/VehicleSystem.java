package hyperdrive.systems;

import hyperdrive.enums.FaultType;
import java.util.EnumSet;
import java.util.Set;

/**
 * Abstract base class for every major system in the car.
 * Shared code (name, fault bookkeeping) lives here.
 * Abstract methods force every subclass to define its own behaviour.
 */
public abstract class VehicleSystem implements Faultable {
    private final String name;
    private final Set<FaultType> activeFaults = EnumSet.noneOf(FaultType.class);

    protected VehicleSystem(String name) {
        this.name = name;
    }

    /** Does this system react to the given fault type? */
    public abstract boolean handles(FaultType type);

    /** Advance the system by dt seconds. Called on every system through the same loop (polymorphism). */
    public abstract void update(double dt);

    /** Quick self-test used by diagnostics. */
    public abstract boolean selfTest();

    @Override
    public void injectFault(FaultType type) {
        if (handles(type)) {
            activeFaults.add(type);
            onFaultChanged();
        }
    }

    @Override
    public void clearFault(FaultType type) {
        if (activeFaults.remove(type)) {
            onFaultChanged();
        }
    }

    @Override
    public boolean hasFault() { return !activeFaults.isEmpty(); }

    // Method overloading: same name, different parameters.
    protected boolean hasFault(FaultType type) { return activeFaults.contains(type); }

    /** Hook: subclasses override this to change their sensor values when faults change. */
    protected void onFaultChanged() { }

    public String getName() { return name; }

    public String getStatus() { return hasFault() ? "FAULT" : "OK"; }

    @Override
    public String toString() { return name + " [" + getStatus() + "]"; }
}
