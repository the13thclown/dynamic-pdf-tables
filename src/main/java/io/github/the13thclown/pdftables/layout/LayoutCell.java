package io.github.the13thclown.pdftables.layout;

import io.github.the13thclown.pdftables.Element;
import io.github.the13thclown.pdftables.style.Style;
import io.github.the13thclown.pdftables.style.VerticalAlignment;

import java.util.ArrayList;
import java.util.List;

/**
 * A cell as the layout engine sees it: grid position, resolved style, laid-out
 * elements (heights fixed), and — after a layout pass — virtual-y positions.
 * Internal.
 * <p>
 * Elements are held as {@link Item}s: flowing items stack vertically; positioned
 * items sit at a fixed offset from the content box's top-left corner and stretch
 * the cell as far down as they reach.
 * <p>
 * Definition fields are immutable; positional fields are recomputed by every
 * {@link LayoutEngine#compute} pass. The continue-and-forget page loop creates
 * fresh copies for the remainder via {@link #remainderCopy}, so a drawn page's
 * positions are never disturbed.
 */
public final class LayoutCell {

    static final float EPS = 0.01f;

    /** An element plus its placement: {@code x}/{@code y} offsets, or both null for flow. */
    public record Item(Element element, Float x, Float y) {

        public boolean positioned() {
            return x != null;
        }
    }

    private final int row;
    private final int col;
    private final int rowSpan;
    private final int colSpan;
    private final Style style;
    private final List<Item> items;
    private final boolean continuedTop;
    private final float x;
    private final float width;

    private float virtualTop;
    private float height;
    private float[] elementTops;

    public LayoutCell(int row, int col, int rowSpan, int colSpan, Style style,
                      List<Item> items, boolean continuedTop, float x, float width) {
        this.row = row;
        this.col = col;
        this.rowSpan = rowSpan;
        this.colSpan = colSpan;
        this.style = style;
        this.items = List.copyOf(items);
        this.continuedTop = continuedTop;
        this.x = x;
        this.width = width;
    }

    /** Fresh copy with untouched positional state (same grid position and items). */
    public LayoutCell copy() {
        return new LayoutCell(row, col, rowSpan, colSpan, style, items, continuedTop, x, width);
    }

    /**
     * A copy for the current page's rendering with item {@code index} replaced
     * (by the top piece of a split element). Positional state is preserved:
     * the replacement keeps the original item's top position, and since it is
     * shorter, its bottom simply moves up to the cut line.
     */
    public LayoutCell withItemReplaced(int index, Element replacement) {
        return withItemReplaced(index, replacement, elementTops[index]);
    }

    /**
     * As {@link #withItemReplaced(int, Element)}, additionally moving the
     * replaced item's top to {@code newTop} — used for the bottom piece of a
     * split element, which starts at the cut instead of the original top.
     */
    public LayoutCell withItemReplaced(int index, Element replacement, float newTop) {
        List<Item> newItems = new ArrayList<>(items);
        Item original = newItems.get(index);
        newItems.set(index, new Item(replacement, original.x(), original.y()));
        LayoutCell copy = new LayoutCell(row, col, rowSpan, colSpan, style, newItems, continuedTop, x, width);
        copy.virtualTop = virtualTop;
        copy.height = height;
        copy.elementTops = elementTops.clone();
        copy.elementTops[index] = newTop;
        return copy;
    }

    /**
     * The continuation of this cell after a page cut at {@code cutY}: keeps only
     * the elements not yet drawn, re-anchors rows relative to {@code firstRemRow},
     * clips the rowspan to the remaining rows, and flags the cell as continued
     * (no top border, no vertical-alignment offset, no minRowHeight floor).
     * Positioned items move as one rigid piece: all shift up by the same amount —
     * the consumed height above the cut, capped so the topmost remaining item
     * lands at 0 — which preserves the arrangement exactly and never creates
     * overlaps that weren't in the definition.
     */
    public LayoutCell remainderCopy(int firstRemRow, float cutY) {
        return remainderCopy(firstRemRow, cutY, null);
    }

    /**
     * As {@link #remainderCopy(int, float)}, with {@code splits} mapping item
     * indexes to the split applied at this cut: the item continues as its
     * bottom piece, positioned right below where the drawn top piece ended —
     * no space is lost above the cut.
     */
    public LayoutCell remainderCopy(int firstRemRow, float cutY, java.util.Map<Integer, Element.Split> splits) {
        float shift = Math.max(0, cutY - virtualTop);
        List<Item> keep = new ArrayList<>();
        float minPositionedY = Float.MAX_VALUE;
        for (int i = 0; i < items.size(); i++) {
            Item item = items.get(i);
            if (elementTops[i] + item.element().getHeight() > cutY + EPS) {
                Element.Split split = splits == null ? null : splits.get(i);
                Item kept;
                if (split != null) {
                    kept = item.positioned()
                            ? new Item(split.bottom(), item.x(), item.y() + split.top().getHeight())
                            : new Item(split.bottom(), null, null);
                } else {
                    kept = item;
                }
                keep.add(kept);
                if (kept.positioned()) {
                    minPositionedY = Math.min(minPositionedY, kept.y());
                }
            }
        }
        float uniformShift = Math.min(shift, minPositionedY);
        List<Item> remaining = new ArrayList<>(keep.size());
        for (Item item : keep) {
            if (item.positioned()) {
                remaining.add(new Item(item.element(), item.x(), item.y() - uniformShift));
            } else {
                remaining.add(item);
            }
        }
        int newRow = Math.max(0, row - firstRemRow);
        int newRowSpan = row + rowSpan - Math.max(row, firstRemRow);
        boolean continued = virtualTop < cutY - EPS;
        return new LayoutCell(newRow, col, newRowSpan, colSpan, style, remaining, continued, x, width);
    }

    /** Height this cell needs: flow stack or deepest positioned item, plus vertical padding. */
    float requiredHeight() {
        return Math.max(flowStackHeight(), positionedBottom()) + style.padding().vertical();
    }

    private float flowStackHeight() {
        float sum = 0;
        for (Item item : items) {
            if (!item.positioned()) {
                sum += item.element().getHeight();
            }
        }
        return sum;
    }

    private float positionedBottom() {
        float max = 0;
        for (Item item : items) {
            if (item.positioned()) {
                max = Math.max(max, item.y() + item.element().getHeight());
            }
        }
        return max;
    }

    /** Called by a layout pass once row geometry is known; computes virtual positions. */
    void position(float[] rowTops, float[] rowHeights) {
        virtualTop = rowTops[row];
        float h = 0;
        for (int r = row; r < row + rowSpan; r++) {
            h += rowHeights[r];
        }
        height = h;

        float contentTop = virtualTop + style.padding().top();
        float contentHeight = height - style.padding().vertical();
        float free = Math.max(0, contentHeight - flowStackHeight());
        float offset;
        if (continuedTop || style.verticalAlignment() == VerticalAlignment.TOP) {
            offset = 0;
        } else if (style.verticalAlignment() == VerticalAlignment.MIDDLE) {
            offset = free / 2;
        } else {
            offset = free;
        }
        elementTops = new float[items.size()];
        float flowY = contentTop + offset;
        for (int i = 0; i < items.size(); i++) {
            Item item = items.get(i);
            if (item.positioned()) {
                elementTops[i] = contentTop + item.y();
            } else {
                elementTops[i] = flowY;
                flowY += item.element().getHeight();
            }
        }
    }

    public int row() {
        return row;
    }

    public int col() {
        return col;
    }

    public int rowSpan() {
        return rowSpan;
    }

    public int colSpan() {
        return colSpan;
    }

    public Style style() {
        return style;
    }

    public List<Item> items() {
        return items;
    }

    /** The items' elements, in item order (convenience for callers indexing by element). */
    public List<Element> elements() {
        return items.stream().map(Item::element).toList();
    }

    /** True if this cell was cut on a previous page and continues here. */
    public boolean continuedTop() {
        return continuedTop;
    }

    public float x() {
        return x;
    }

    public float width() {
        return width;
    }

    public float virtualTop() {
        return virtualTop;
    }

    public float height() {
        return height;
    }

    /** Absolute virtual y of item {@code i}'s top edge. */
    public float elementTop(int i) {
        return elementTops[i];
    }
}
