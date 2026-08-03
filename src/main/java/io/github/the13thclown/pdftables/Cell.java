package io.github.the13thclown.pdftables;

import io.github.the13thclown.pdftables.style.BorderStyle;
import io.github.the13thclown.pdftables.style.HorizontalAlignment;
import io.github.the13thclown.pdftables.style.Padding;
import io.github.the13thclown.pdftables.style.Style;
import io.github.the13thclown.pdftables.style.VerticalAlignment;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A cell definition: zero or more contents (stacked vertically), spans, and
 * style. Pure definition — nothing is measured or validated against the grid
 * until render time. Cells auto-flow into the table's grid in the order they
 * are added to the table; there is no row object.
 */
public final class Cell {

    /**
     * A content and its placement inside the cell's content box: {@code x}/{@code y}
     * are offsets from the box's top-left corner, or both null for normal flow
     * (contents stack vertically). Flowing contents anchor to the top of the box
     * unless {@code bottom} is set, in which case they stack against its bottom.
     *
     * @param content the content
     * @param x       horizontal offset from the content box's left edge, or null for flow
     * @param y       vertical offset from the content box's top edge, or null for flow
     * @param bottom  whether a flowing content anchors to the bottom of the box
     */
    public record ContentEntry(CellContent content, Float x, Float y, boolean bottom) {

        /** {@return whether this entry was placed with {@link Builder#addAt}} */
        public boolean positioned() {
            return x != null;
        }
    }

    private final List<ContentEntry> contents;
    private final int colSpan;
    private final int rowSpan;
    private final Style style;
    private final CellContent pageSliceContent;

    private Cell(Builder b) {
        this.contents = List.copyOf(b.contents);
        this.colSpan = b.colSpan;
        this.rowSpan = b.rowSpan;
        this.pageSliceContent = b.pageSliceContent;
        Style shortcuts = b.styleShortcuts == null ? null : b.styleShortcuts.build();
        if (shortcuts != null && b.style != null) {
            this.style = shortcuts.mergedOnto(b.style);
        } else if (shortcuts != null) {
            this.style = shortcuts;
        } else {
            this.style = b.style;
        }
    }

    /** {@return a new cell builder} */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Shorthand for a cell with a single content and no other settings.
     *
     * @param content the cell's only content
     * @return the cell
     */
    public static Cell of(CellContent content) {
        return builder().add(content).build();
    }

    /** {@return the cell's contents with their placements, in the order added} */
    public List<ContentEntry> contents() {
        return contents;
    }

    /** {@return the number of columns this cell spans, at least 1} */
    public int colSpan() {
        return colSpan;
    }

    /** {@return the number of derived rows this cell spans, at least 1} */
    public int rowSpan() {
        return rowSpan;
    }

    /** {@return the cell's own style, possibly null (inherit everything)} */
    public Style style() {
        return style;
    }

    /** {@return content repeated at the bottom of every page slice of this cell, or null} */
    public CellContent pageSliceContent() {
        return pageSliceContent;
    }

    /** Builds a {@link Cell}. Obtained from {@link Cell#builder()}. */
    public static final class Builder {
        private final List<ContentEntry> contents = new ArrayList<>();
        private int colSpan = 1;
        private int rowSpan = 1;
        private Style style;
        private Style.Builder styleShortcuts;
        private CellContent pageSliceContent;

        private Builder() {
        }

        /**
         * Adds a flowing content; flowing contents stack vertically inside the cell.
         *
         * @param content the content to add
         * @return this builder
         */
        public Builder add(CellContent content) {
            contents.add(new ContentEntry(Objects.requireNonNull(content, "content"), null, null, false));
            return this;
        }

        /**
         * Adds a content anchored to the <em>bottom</em> of the cell's content
         * box; several stack upwards in the order added. Contents added with
         * {@link #add} keep flowing from the top, so one cell can hold a label
         * at the top and a sign-off at the bottom — which vertical alignment
         * cannot express, since it moves the whole stack as one.
         * <p>
         * In a cell tall enough to be cut across pages, bottom-anchored content
         * therefore lands on the cell's <em>last</em> page.
         *
         * @param content the content to anchor to the bottom
         * @return this builder
         */
        public Builder addBottom(CellContent content) {
            contents.add(new ContentEntry(Objects.requireNonNull(content, "content"), null, null, true));
            return this;
        }

        /**
         * Adds a content at an arbitrary position inside the cell's content box:
         * {@code x}/{@code y} offsets from the box's top-left corner. Positioned
         * contents don't take part in the vertical flow — they sit exactly where
         * placed (overlaps allowed) and stretch the cell as far down as they
         * reach. Elements crossing a page break pass down whole, and the whole
         * remaining arrangement continues on the next page shifted up as one
         * rigid piece — relative positions are always preserved.
         *
         * @param x       offset from the content box's left edge, in points
         * @param y       offset from the content box's top edge, in points
         * @param content the content to place
         * @return this builder
         */
        public Builder addAt(float x, float y, CellContent content) {
            if (x < 0 || y < 0) {
                throw new IllegalArgumentException("Position offsets must be >= 0");
            }
            contents.add(new ContentEntry(Objects.requireNonNull(content, "content"), x, y, false));
            return this;
        }

        /**
         * Makes this cell span several columns.
         *
         * @param colSpan the number of columns this cell spans, at least 1
         * @return this builder
         */
        public Builder colSpan(int colSpan) {
            if (colSpan < 1) {
                throw new IllegalArgumentException("colSpan must be >= 1");
            }
            this.colSpan = colSpan;
            return this;
        }

        /**
         * Makes this cell span several derived rows.
         *
         * @param rowSpan the number of derived rows this cell spans, at least 1
         * @return this builder
         */
        public Builder rowSpan(int rowSpan) {
            if (rowSpan < 1) {
                throw new IllegalArgumentException("rowSpan must be >= 1");
            }
            this.rowSpan = rowSpan;
            return this;
        }

        /**
         * Base style for this cell; individual shortcut calls below win over it.
         *
         * @param style the cell style
         * @return this builder
         */
        public Builder style(Style style) {
            this.style = style;
            return this;
        }

        /**
         * Shortcut for equal padding on all four sides.
         *
         * @param padding the padding in points
         * @return this builder
         */
        public Builder paddingAll(float padding) {
            shortcuts().padding(Padding.of(padding));
            return this;
        }

        /**
         * Shortcut for the cell's inner padding.
         *
         * @param padding the padding
         * @return this builder
         */
        public Builder padding(Padding padding) {
            shortcuts().padding(padding);
            return this;
        }

        /**
         * Shortcut for the cell's background fill.
         *
         * @param color the fill color
         * @return this builder
         */
        public Builder backgroundColor(Color color) {
            shortcuts().backgroundColor(color);
            return this;
        }

        /**
         * Shortcut for the same border on all four sides.
         *
         * @param border the border style
         * @return this builder
         */
        public Builder borderAll(BorderStyle border) {
            shortcuts().borderAll(border);
            return this;
        }

        /**
         * Shortcut for the horizontal alignment of the cell's contents.
         *
         * @param alignment the alignment
         * @return this builder
         */
        public Builder horizontalAlignment(HorizontalAlignment alignment) {
            shortcuts().horizontalAlignment(alignment);
            return this;
        }

        /**
         * Shortcut for the vertical alignment of the cell's contents.
         *
         * @param alignment the alignment
         * @return this builder
         */
        public Builder verticalAlignment(VerticalAlignment alignment) {
            shortcuts().verticalAlignment(alignment);
            return this;
        }

        private Style.Builder shortcuts() {
            if (styleShortcuts == null) {
                styleShortcuts = Style.builder();
            }
            return styleShortcuts;
        }

        /**
         * Content drawn at the bottom of <em>every</em> page slice this cell is
         * cut into — a "continued on the next page" marker, or a pointer to
         * something that ended up on another page. A cell that fits one page
         * gets it once.
         * <p>
         * How many slices a cell has is only known once pages are cut, long
         * after heights are fixed, so this content is an overlay: it is drawn
         * inside the bottom of each slice without reserving room. Leave bottom
         * padding for it, or accept that it sits over the tail of the flow.
         *
         * @param content the content to repeat on every page slice
         * @return this builder
         */
        public Builder onEachPageSlice(CellContent content) {
            this.pageSliceContent = Objects.requireNonNull(content, "content");
            return this;
        }

        /** {@return the finished cell} */
        public Cell build() {
            return new Cell(this);
        }
    }
}
