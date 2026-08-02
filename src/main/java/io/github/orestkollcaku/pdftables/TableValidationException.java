package io.github.orestkollcaku.pdftables;

/** Thrown at render start when the table definition is structurally invalid. */
public class TableValidationException extends RuntimeException {
    public TableValidationException(String message) {
        super(message);
    }
}
