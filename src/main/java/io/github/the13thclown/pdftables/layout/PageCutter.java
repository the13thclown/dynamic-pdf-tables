package io.github.the13thclown.pdftables.layout;

import io.github.the13thclown.pdftables.Element;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Cuts a virtual layout at a page's capacity. Internal.
 * <p>
 * Everything strictly above the cut line is drawn on the current page:
 * elements entirely above it, and the visible portion of any cell box the cut
 * crosses. An element the cut lands inside is asked to
 * {@link Element#splitAt(float) split} at the cut — if it does, its top piece
 * is drawn in the remaining space (via {@link CutResult#pageLayout()}) and its
 * bottom piece continues on the next page; if it declines, it passes down
 * whole. The remainder — surviving cells with their undrawn elements, rows
 * re-based to 0 — is handed back for a fresh layout pass
 * ("continue and forget").
 */
public final class PageCutter {

    private static final float EPS = LayoutCell.EPS;

    /**
     * @param cutY              virtual y of the cut on the current layout; also the drawn height of this page
     * @param finished          true if the whole layout fits — no remainder
     * @param pageLayout        the layout to RENDER for this page (crossing split elements replaced by their top pieces)
     * @param remainderCells    fresh cells for the next layout pass (empty if finished)
     * @param remainderRowCount derived rows remaining
     * @param firstRowContinued true if the cut landed inside remainder row 0 (it continues, partially drawn)
     * @param drawnElementCount elements drawn on this page (fully above the cut, plus applied split tops)
     */
    public record CutResult(
            float cutY,
            boolean finished,
            VirtualLayout pageLayout,
            List<LayoutCell> remainderCells,
            int remainderRowCount,
            boolean firstRowContinued,
            int drawnElementCount) {
    }

    private PageCutter() {
    }

    public static CutResult cut(VirtualLayout layout, float capacity) {
        if (layout.totalHeight() <= capacity + EPS) {
            int all = layout.cells().stream().mapToInt(c -> c.elements().size()).sum();
            return new CutResult(layout.totalHeight(), true, layout, List.of(), 0, false, all);
        }
        float cutY = Math.max(0, capacity);

        int firstRemRow = 0;
        while (firstRemRow < layout.rowCount()
                && layout.rowTops()[firstRemRow] + layout.rowHeights()[firstRemRow] <= cutY + EPS) {
            firstRemRow++;
        }
        boolean firstRowContinued = layout.rowTops()[firstRemRow] < cutY - EPS;

        List<LayoutCell> pageCells = new ArrayList<>(layout.cells().size());
        List<LayoutCell> remainder = new ArrayList<>();
        boolean anySplit = false;
        int drawn = 0;
        for (LayoutCell c : layout.cells()) {
            LayoutCell pageCell = c;
            Map<Integer, Element.Split> splits = null;
            for (int i = 0; i < c.items().size(); i++) {
                Element e = c.items().get(i).element();
                float top = c.elementTop(i);
                float bottom = top + e.getHeight();
                if (bottom <= cutY + EPS) {
                    drawn++;
                } else if (top < cutY - EPS) {
                    // the cut lands inside this element: offer it the split
                    float available = cutY - top;
                    Element.Split split = e.splitAt(available);
                    if (split != null && split.top() != null && split.bottom() != null
                            && split.top().getHeight() <= available + EPS
                            && split.top().getHeight() > EPS
                            && split.bottom().getHeight() > EPS) {
                        if (splits == null) {
                            splits = new HashMap<>();
                        }
                        splits.put(i, split);
                        pageCell = pageCell.withItemReplaced(i, split.top());
                        anySplit = true;
                        drawn++;
                    }
                }
            }
            pageCells.add(pageCell);
            if (c.row() + c.rowSpan() - 1 >= firstRemRow) {
                remainder.add(c.remainderCopy(firstRemRow, cutY, splits));
            }
        }
        VirtualLayout pageLayout = anySplit
                ? new VirtualLayout(pageCells, layout.rowTops(), layout.rowHeights(), layout.totalHeight())
                : layout;
        return new CutResult(cutY, false, pageLayout, remainder,
                layout.rowCount() - firstRemRow, firstRowContinued, drawn);
    }
}
