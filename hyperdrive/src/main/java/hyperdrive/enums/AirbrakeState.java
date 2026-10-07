package hyperdrive.enums;

/** Airbrake position, with transition states so the animation never snaps. */
public enum AirbrakeState { STOWED, DEPLOYING, DEPLOYED, RETRACTING, UNAVAILABLE, FAULT }
