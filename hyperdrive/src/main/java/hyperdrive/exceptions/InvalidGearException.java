package hyperdrive.exceptions;

/** Thrown by the Transmission when a requested gear change is not allowed. */
public class InvalidGearException extends Exception {
    public InvalidGearException(String message) {
        super(message);
    }
}
