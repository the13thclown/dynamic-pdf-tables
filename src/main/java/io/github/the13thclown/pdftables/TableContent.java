package io.github.the13thclown.pdftables;

import io.github.the13thclown.pdftables.layout.GridFlow;
import io.github.the13thclown.pdftables.layout.LayoutCell;
import io.github.the13thclown.pdftables.layout.LayoutEngine;
import io.github.the13thclown.pdftables.layout.VirtualLayout;
import io.github.the13thclown.pdftables.render.LayoutRenderer;
import io.github.the13thclown.pdftables.render.RenderContext;

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
    public List<Element> layout(float availableWidth, io.github.the13thclown.pdftables.style.Style style) {
        GridFlow.Result grid = GridFlow.flow(table);
        if (grid.placements().isEmpty()) {
            return List.of();
        }
        float[] colWidths = LayoutEngine.resolveColumns(table, availableWidth);
        // only the outer cell's TEXT defaults flow into the inner table — box
        // styling (borders, background, alignment) must not leak inward
        io.github.the13thclown.pdftables.style.Style textBase =
                io.github.the13thclown.pdftables.style.Style.builder()
                        .font(style.font())
                        .fontSize(style.fontSize())
                        .textColor(style.textColor())
                        .lineSpacing(style.lineSpacing())
                        .build()
                        .mergedOnto(io.github.the13thclown.pdftables.style.Style.defaults());
        List<LayoutCell> cells = LayoutEngine.buildCells(table, grid.placements(), colWidths, textBase);
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
     * One slice of the inner layout: the window
     * {@code [blockTop, blockTop + blockHeight)}. Initially blocks close all
     * rowspans, so cells never cross block boundaries and every block draws
     * with complete borders. A page cut landing inside a block splits it iff
     * every inner element the sub-cut crosses agrees to split — then the
     * pieces are narrower windows with the crossing elements replaced by
     * their split halves, and inner cell boxes draw cut open at the window
     * edge exactly like outer cells do.
     */
    private record RowBlockElement(VirtualLayout inner, float blockTop, float blockHeight)
            implements Element {

        private static final float EPS = 0.01f;

        @Override
        public float getHeight() {
            return blockHeight;
        }

        @Override
        public Split splitAt(float availableHeight) {
            if (availableHeight <= 1 || blockHeight - availableHeight <= 1) {
                return null;
            }
            float cut = blockTop + availableHeight;
            List<LayoutCell> topCells = new ArrayList<>(inner.cells().size());
            List<LayoutCell> bottomCells = new ArrayList<>(inner.cells().size());
            for (LayoutCell c : inner.cells()) {
                LayoutCell topCell = c;
                LayoutCell bottomCell = c;
                for (int i = 0; i < c.items().size(); i++) {
                    Element e = c.items().get(i).element();
                    float top = c.elementTop(i);
                    float bottom = top + e.getHeight();
                    if (top < cut - EPS && bottom > cut + EPS) {
                        // the sub-cut lands inside this inner element: the block
                        // can only split if the element itself can
                        Split s = e.splitAt(cut - top);
                        if (s == null || s.top() == null || s.bottom() == null
                                || s.top().getHeight() > cut - top + EPS
                                || s.top().getHeight() <= EPS
                                || s.bottom().getHeight() <= EPS) {
                            return null;
                        }
                        topCell = topCell.withItemReplaced(i, s.top());
                        bottomCell = bottomCell.withItemReplaced(i, s.bottom(), cut);
                    }
                }
                topCells.add(topCell);
                bottomCells.add(bottomCell);
            }
            VirtualLayout topLayout = new VirtualLayout(topCells,
                    inner.rowTops(), inner.rowHeights(), inner.totalHeight());
            VirtualLayout bottomLayout = new VirtualLayout(bottomCells,
                    inner.rowTops(), inner.rowHeights(), inner.totalHeight());
            return new Split(
                    new RowBlockElement(topLayout, blockTop, availableHeight),
                    new RowBlockElement(bottomLayout, cut, blockHeight - availableHeight));
        }

        @Override
        public void draw(RenderContext ctx) throws IOException {
            // map inner virtual 0 to the page y it would have if the whole
            // inner table were drawn contiguously from this block's box top
            float innerTopPageY = ctx.y() + ctx.height() + blockTop;
            LayoutRenderer.render(ctx.document(), ctx.stream(), inner, blockTop, blockTop + blockHeight,
                    ctx.x(), innerTopPageY);
        }
    }
}
