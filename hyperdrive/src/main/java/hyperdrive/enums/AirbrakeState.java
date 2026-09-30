package hyperdrive.enums;

/** Airbrake position, with brief transition states so it doesn't snap instantly. */
public enum AirbrakeState {
    RETRACTED, DEPLOYING, DEPLOYED, RETRACTING
}
