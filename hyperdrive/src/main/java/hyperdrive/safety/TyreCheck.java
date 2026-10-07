package hyperdrive.safety;

import hyperdrive.enums.DiagnosticLevel;
import hyperdrive.model.Car;
import hyperdrive.systems.TyreSystem;

/** TPMS: low pressure, hot tyres or silent sensors. Warnings only - they never stop the engine starting. */
public class TyreCheck extends AbstractSafetyCheck {
    public TyreCheck() { super("Tyres / TPMS"); }

    @Override
    public boolean check(Car car) {
        begin();
        TyreSystem tyres = car.getTyres();
        if (tyres.hasSensorFault()) {
            fail(DiagnosticLevel.WARNING, "TPMS not responding");
        }
        if (tyres.hasLowPressure()) {
            fail(DiagnosticLevel.WARNING, "Low pressure " + tyres.firstLowPressurePosition());
        }
        if (tyres.hasHotTyre()) {
            fail(DiagnosticLevel.WARNING, "Tyre temperature high " + tyres.firstHotPosition());
        }
        return finish();
    }
}
