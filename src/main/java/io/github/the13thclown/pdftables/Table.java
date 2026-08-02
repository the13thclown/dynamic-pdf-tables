package io.github.the13thclown.pdftables;

import io.github.the13thclown.pdftables.style.Style;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.IntFunction;

/**
 * A table definition: columns plus a flat sequence of cells that auto-flow
 * into the grid at render time (next free slot left-to-right, wrapping to the
 * next derived row, skipping slots covered by rowspans). There are no row
 * objects — rows are derived by the layout engine.
 * <p>
 * {@code build()} only captures the definition. Nothing is measured, laid out
 * or validated against the grid until the table is drawn.
 */
public final class Table {

    private final List<ColumnSpec> columns;
    private final Float width;
    private final List<Cell> cells;
    private final int headerRowCount;
    private final float minRowHeight;
    private final Style defaultStyle;
    private final IntFunction<Style> rowStyler;
    private final IntFunction<Style> columnStyler;
    private final RowSpanDistribution rowSpanDistribution;

    private Table(Builder b) {
        this.columns = List.copyOf(b.columns);
        this.width = b.width;
        this.cells = List.copyOf(b.cells);
        this.headerRowCount = b.headerRowCount;
        this.minRowHeight = b.minRowHeight;
        this.defaultStyle = b.defaultStyle;
        this.rowStyler = b.rowStyler;
        this.columnStyler = b.columnStyler;
        this.rowSpanDistribution = b.rowSpanDistribution;
    }

    public static Builder builder() {
        return new Builder();
    }

    public List<ColumnSpec> columns() {
        return columns;
    }

    /** Explicit table width, or null (all-fixed columns define the width). */
    public Float width() {
        return width;
    }

    public List<Cell> cells() {
        return cells;
    }

    /** Number of derived grid rows repeated at the top of every page. */
    public int headerRowCount() {
        return headerRowCount;
    }

    public float minRowHeight() {
        return minRowHeight;
    }

    /** Table-level default style, possibly null. */
    public Style defaultStyle() {
        return defaultStyle;
    }

    /** Style per derived row index, possibly null; sits between cell style and column styler. */
    public IntFunction<Style> rowStyler() {
        return rowStyler;
    }

    /** Style per column index, possibly null; sits between row styler and table default. */
    public IntFunction<Style> columnStyler() {
        return columnStyler;
    }

    public RowSpanDistribution rowSpanDistribution() {
        return rowSpanDistribution;
    }

    public static final class Builder {
        private final List<ColumnSpec> columns = new ArrayList<>();
        private Float width;
        private final List<Cell> cells = new ArrayList<>();
        private int headerRowCount;
        private float minRowHeight;
        private Style defaultStyle;
        private IntFunction<Style> rowStyler;
        private IntFunction<Style> columnStyler;
        private RowSpanDistribution rowSpanDistribution = RowSpanDistribution.EQUAL;

        /** Explicit table width; required if any column has a relative width. */
        public Builder width(float width) {
            if (width <= 0) {
                throw new IllegalArgumentException("Table width must be > 0");
            }
            this.width = width;
            return this;
        }

        public Builder addColumnOfWidth(float width) {
            columns.add(ColumnSpec.fixed(width));
            return this;
        }

        public Builder addColumnsOfWidth(float... widths) {
            for (float w : widths) {
                addColumnOfWidth(w);
            }
            return this;
        }

        /** A column taking a weighted share of the width left after fixed columns. */
        public Builder addColumnOfRelativeWidth(float weight) {
            columns.add(ColumnSpec.relativeTo(weight));
            return this;
        }

        /** Adds a cell; cells auto-flow into the grid in insertion order. */
        public Builder add(Cell cell) {
            cells.add(Objects.requireNonNull(cell, "cell"));
            return this;
        }

        /** Number of derived grid rows (from the top) repeated on every page. */
        public Builder headerRowCount(int headerRowCount) {
            if (headerRowCount < 0) {
                throw new IllegalArgumentException("headerRowCount must be >= 0");
            }
            this.headerRowCount = headerRowCount;
            return this;
        }

        /** Minimum height for every derived row. */
        public Builder minRowHeight(float minRowHeight) {
            if (minRowHeight < 0) {
                throw new IllegalArgumentException("minRowHeight must be >= 0");
            }
            this.minRowHeight = minRowHeight;
            return this;
        }

        public Builder defaultStyle(Style style) {
            this.defaultStyle = style;
            return this;
        }

        /**
         * Style applied per derived row index (0-based, evaluated at render
         * time) — the row-level layer of the style cascade, since there are no
         * row objects. Returning null for a row means "nothing at this layer".
         * Cell styles win over it; it wins over the table default. Rowspan
         * cells take the style of their anchor (topmost) row.
         */
        public Builder rowStyler(IntFunction<Style> rowStyler) {
            this.rowStyler = rowStyler;
            return this;
        }

        /**
         * Style applied per column index (0-based). Cascade: cell wins over
         * row styler wins over column styler wins over table default. Spanning
         * cells take the style of their anchor (leftmost) column. Returning
         * null for a column means "nothing at this layer".
         */
        public Builder columnStyler(IntFunction<Style> columnStyler) {
            this.columnStyler = columnStyler;
            return this;
        }

        /** How rowspan cells' extra height demand spreads over their rows; default EQUAL. */
        public Builder rowSpanDistribution(RowSpanDistribution distribution) {
            this.rowSpanDistribution = Objects.requireNonNull(distribution);
            return this;
        }

        public Table build() {
            return new Table(this);
        }
    }
}
