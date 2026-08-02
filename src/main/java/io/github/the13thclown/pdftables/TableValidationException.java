package io.github.the13thclown.pdftables;

/** Thrown at render start when the table definition is structurally invalid. */
public class TableValidationException extends RuntimeException {
    public TableValidationException(String message) {
        super(message);
    }
}
