package io.github.the13thclown.pdftables.layout;

import java.util.ArrayList;
import java.util.List;

/**
 * Cuts a virtual layout at a page's capacity. Internal.
 * <p>
 * Everything strictly above the cut line is drawn on the current page:
 * elements entirely above it, and the visible portion of any cell box the cut
 * crosses. Elements crossing the cut pass down whole. The remainder — surviving
 * cells with their undrawn elements, rows re-based to 0 — is handed back for a
 * fresh layout pass ("continue and forget").
 */
public final class PageCutter {

    private static final float EPS = LayoutCell.EPS;

    /**
     * @param cutY              virtual y of the cut on the current layout; also the drawn height of this page
     * @param finished          true if the whole layout fits — no remainder
     * @param remainderCells    fresh cells for the next layout pass (empty if finished)
     * @param remainderRowCount derived rows remaining
     * @param firstRowContinued true if the cut landed inside remainder row 0 (it continues, partially drawn)
     * @param drawnElementCount elements fully above the cut (drawn on this page)
     */
    public record CutResult(
            float cutY,
            boolean finished,
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
            return new CutResult(layout.totalHeight(), true, List.of(), 0, false, all);
        }
        float cutY = Math.max(0, capacity);

        int firstRemRow = 0;
        while (firstRemRow < layout.rowCount()
                && layout.rowTops()[firstRemRow] + layout.rowHeights()[firstRemRow] <= cutY + EPS) {
            firstRemRow++;
        }
        boolean firstRowContinued = layout.rowTops()[firstRemRow] < cutY - EPS;

        List<LayoutCell> remainder = new ArrayList<>();
        int drawn = 0;
        for (LayoutCell c : layout.cells()) {
            for (int i = 0; i < c.elements().size(); i++) {
                if (c.elementTop(i) + c.elements().get(i).getHeight() <= cutY + EPS) {
                    drawn++;
                }
            }
            if (c.row() + c.rowSpan() - 1 >= firstRemRow) {
                remainder.add(c.remainderCopy(firstRemRow, cutY));
            }
        }
        return new CutResult(cutY, false, remainder, layout.rowCount() - firstRemRow, firstRowContinued, drawn);
    }
}
