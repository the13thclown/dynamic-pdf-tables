package io.github.the13thclown.pdftables.layout;

/**
 * A column definition: either a fixed width in points, or a relative weight
 * sharing the width left over after fixed columns (requires an explicit table
 * width).
 */
public record ColumnSpec(float value, boolean relative) {

    public ColumnSpec {
        if (value <= 0) {
            throw new IllegalArgumentException("Column width/weight must be > 0");
        }
    }

    public static ColumnSpec fixed(float width) {
        return new ColumnSpec(width, false);
    }

    public static ColumnSpec relativeTo(float weight) {
        return new ColumnSpec(weight, true);
    }
}
