package hyperdrive.safety;

import hyperdrive.enums.DiagnosticLevel;
import hyperdrive.model.Car;
import hyperdrive.systems.OilSystem;

/** Oil pressure (critical) and oil temperature. Cold oil is NOT a failure - it only lowers the RPM limit. */
public class OilCheck extends AbstractSafetyCheck {
    public OilCheck() { super("Engine oil"); }

    @Override
    public boolean check(Car car) {
        begin();
        OilSystem oil = car.getOil();
        if (oil.isPressureLow()) {
            fail(DiagnosticLevel.CRITICAL, String.format("Oil pressure %.1f bar is below %.1f bar",
                    oil.getPressure(), OilSystem.MIN_PRESSURE_BAR));
        }
        if (!oil.isTemperatureOk()) {
            fail(DiagnosticLevel.FAULT, String.format("Oil %.0f C is above %.0f C",
                    oil.getTemperature(), oil.getTemperatureLimit()));
        }
        return finish();
    }
}
