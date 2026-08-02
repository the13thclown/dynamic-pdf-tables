package io.github.the13thclown.pdftables.layout;

import java.util.List;

/**
 * The table laid out on the unbounded virtual y axis: top = 0, growing
 * downward, no page anywhere. Page breaks are cuts into this space. Internal.
 */
public record VirtualLayout(
        List<LayoutCell> cells,
        float[] rowTops,
        float[] rowHeights,
        float totalHeight) {

    public int rowCount() {
        return rowHeights.length;
    }
}
