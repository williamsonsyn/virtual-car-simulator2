package hyperdrive.exceptions;

import java.util.ArrayList;
import java.util.List;

/**
 * Thrown when the car refuses an operation (start, shift, power off ...).
 * It is a CHECKED exception on purpose: every caller must handle a denial.
 * It carries the operation name and ALL the reasons, so the UI can show them.
 */
public class OperationDeniedException extends Exception {
    private final String operation;
    private final List<String> reasons;

    // Constructor overloading: one reason ...
    public OperationDeniedException(String operation, String reason) {
        this(operation, List.of(reason));
    }

    // ... or a list of reasons.
    public OperationDeniedException(String operation, List<String> reasons) {
        super(operation + " DENIED: " + String.join("; ", reasons));
        this.operation = operation;
        this.reasons = new ArrayList<>(reasons);   // defensive copy
    }

    public String getOperation() { return operation; }

    public List<String> getReasons() { return new ArrayList<>(reasons); }
}
