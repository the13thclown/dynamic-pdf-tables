package io.github.the13thclown.pdftables;

/** Thrown at render time when a table cannot be laid out onto pages (e.g. an element taller than a page). */
public class TableLayoutException extends RuntimeException {
    public TableLayoutException(String message) {
        super(message);
    }
}
