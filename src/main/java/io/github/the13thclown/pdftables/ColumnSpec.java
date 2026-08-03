package io.github.the13thclown.pdftables;

/**
 * A column definition: either a fixed width in points, or a relative weight
 * sharing the width left over after fixed columns (requires an explicit table
 * width).
 *
 * @param value    the width in points, or the relative weight
 * @param relative whether {@code value} is a weight rather than a width
 */
public record ColumnSpec(float value, boolean relative) {

    /** Validates that the width or weight is positive. */
    public ColumnSpec {
        if (value <= 0) {
            throw new IllegalArgumentException("Column width/weight must be > 0");
        }
    }

    /**
     * {@return a fixed-width column}
     *
     * @param width the column width in points
     */
    public static ColumnSpec fixed(float width) {
        return new ColumnSpec(width, false);
    }

    /**
     * {@return a relative-width column}
     *
     * @param weight the column's share of the leftover width, relative to the
     *               other relative columns' weights
     */
    public static ColumnSpec relativeTo(float weight) {
        return new ColumnSpec(weight, true);
    }
}
