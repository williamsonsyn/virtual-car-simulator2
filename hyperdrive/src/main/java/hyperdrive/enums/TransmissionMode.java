package hyperdrive.enums;

/** AUTO shifts by itself; MANUAL waits for the Q/E paddles. */
public enum TransmissionMode {
    AUTO("A"), MANUAL("M");

    private final String letter;

    TransmissionMode(String letter) { this.letter = letter; }

    public String getLetter() { return letter; }
}
