package io.github.orestkollcaku.pdftables;

import io.github.orestkollcaku.pdftables.layout.Element;
import io.github.orestkollcaku.pdftables.layout.GridFlow;
import io.github.orestkollcaku.pdftables.layout.LayoutCell;
import io.github.orestkollcaku.pdftables.layout.LayoutEngine;
import io.github.orestkollcaku.pdftables.layout.VirtualLayout;
import io.github.orestkollcaku.pdftables.render.LayoutRenderer;
import io.github.orestkollcaku.pdftables.render.RenderContext;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A {@link Table} as cell content — nested tables. The inner table is laid out
 * at render time against the outer cell's content width (relative inner
 * columns without an explicit width fill the cell), and decomposes into one
 * atomic {@link Element} per inner <em>row block</em> — consecutive rows tied
 * together by a rowspan form one block. Page breaks therefore split nested
 * tables at inner row boundaries, never through an inner row.
 * <p>
 * Inner {@code headerRowCount} is ignored for repetition (there are no pages
 * inside a cell); header rows are simply the first rows. Nesting deeper works
 * recursively.
 */
public final class TableContent implements CellContent {

    private final Table table;

    private TableContent(Table table) {
        this.table = Objects.requireNonNull(table, "table");
    }

    public static TableContent of(Table table) {
        return new TableContent(table);
    }

    @Override
    public List<Element> layout(float availableWidth) {
        GridFlow.Result grid = GridFlow.flow(table);
        if (grid.placements().isEmpty()) {
            return List.of();
        }
        float[] colWidths = LayoutEngine.resolveColumns(table, availableWidth);
        List<LayoutCell> cells = LayoutEngine.buildCells(table, grid.placements(), colWidths);
        VirtualLayout inner = LayoutEngine.compute(cells, grid.rowCount(), table.minRowHeight(),
                false, table.rowSpanDistribution());

        // a boundary below row r is breakable unless a rowspan crosses it
        boolean[] breakableBelow = new boolean[inner.rowCount()];
        java.util.Arrays.fill(breakableBelow, true);
        for (LayoutCell c : cells) {
            for (int r = c.row(); r < c.row() + c.rowSpan() - 1; r++) {
                breakableBelow[r] = false;
            }
        }
        List<Element> blocks = new ArrayList<>();
        int start = 0;
        for (int r = 0; r < inner.rowCount(); r++) {
            if (breakableBelow[r]) {
                float top = inner.rowTops()[start];
                float height = inner.rowTops()[r] + inner.rowHeights()[r] - top;
                blocks.add(new RowBlockElement(inner, top, height));
                start = r + 1;
            }
        }
        return blocks;
    }

    /**
     * One atomic slice of the inner layout: the window
     * {@code [blockTop, blockTop + blockHeight)}. Cells never cross block
     * boundaries (blocks close all rowspans), so every block draws with
     * complete borders.
     */
    private record RowBlockElement(VirtualLayout inner, float blockTop, float blockHeight)
            implements Element {

        @Override
        public float getHeight() {
            return blockHeight;
        }

        @Override
        public void draw(RenderContext ctx) throws IOException {
            // map inner virtual 0 to the page y it would have if the whole
            // inner table were drawn contiguously from this block's box top
            float innerTopPageY = ctx.y() + ctx.height() + blockTop;
            LayoutRenderer.render(ctx.stream(), inner, blockTop, blockTop + blockHeight,
                    ctx.x(), innerTopPageY);
        }
    }
}
