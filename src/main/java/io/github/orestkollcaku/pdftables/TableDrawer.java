package io.github.orestkollcaku.pdftables;

import io.github.orestkollcaku.pdftables.layout.GridFlow;
import io.github.orestkollcaku.pdftables.layout.LayoutCell;
import io.github.orestkollcaku.pdftables.layout.LayoutEngine;
import io.github.orestkollcaku.pdftables.layout.PageCutter;
import io.github.orestkollcaku.pdftables.layout.VirtualLayout;
import io.github.orestkollcaku.pdftables.render.LayoutRenderer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Draws a {@link Table} into a {@link PDDocument}, creating new pages as
 * needed. This is where all rendering happens: the table definition is flowed
 * into a grid, laid out on the endless virtual y axis, and then consumed page
 * by page — each page draws everything above its cut line, forgets it, and the
 * remainder is re-laid from virtual 0 until the virtual height is used up.
 */
public final class TableDrawer {

    private static final float EPS = 0.01f;
    private static final float DEFAULT_MARGIN = 50;
    private static final int MAX_PAGES = 10_000;

    private final PDDocument document;
    private final Table table;
    private final PDPage startPage;
    private final float startX;
    private final Float startY;
    private final float endY;
    private final Float startYOnNewPages;
    private final Supplier<PDPage> pageSupplier;
    private final boolean closeBordersAtPageBreak;

    private TableDrawer(Builder b) {
        this.document = b.document;
        this.table = b.table;
        this.startPage = b.startPage;
        this.startX = b.startX;
        this.startY = b.startY;
        this.endY = b.endY;
        this.startYOnNewPages = b.startYOnNewPages;
        this.pageSupplier = b.pageSupplier;
        this.closeBordersAtPageBreak = b.closeBordersAtPageBreak;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Draws the table and returns the y coordinate of its bottom edge on the
     * last page it touched.
     */
    public float draw() throws IOException {
        GridFlow.Result grid = GridFlow.flow(table);

        PDPage page = startPage != null ? startPage : pageSupplier.get();
        addPageIfAbsent(page);
        float pageTop = startY != null ? startY : defaultTop(page);
        if (grid.placements().isEmpty()) {
            return pageTop;
        }

        float[] colWidths = LayoutEngine.resolveColumns(table);
        List<LayoutCell> cells = LayoutEngine.buildCells(table, grid.placements(), colWidths);
        VirtualLayout current = LayoutEngine.compute(cells, grid.rowCount(), table.minRowHeight(), false,
                table.rowSpanDistribution());

        VirtualLayout header = null;
        if (table.headerRowCount() > 0) {
            List<LayoutCell> headerCells = new ArrayList<>();
            for (LayoutCell c : cells) {
                if (c.row() < table.headerRowCount()) {
                    headerCells.add(c.copy());
                }
            }
            header = LayoutEngine.compute(headerCells, table.headerRowCount(), table.minRowHeight(), false,
                    table.rowSpanDistribution());
        }

        boolean firstPage = true;
        int pagesDrawn = 0;
        while (true) {
            if (++pagesDrawn > MAX_PAGES) {
                throw new TableLayoutException("Table did not finish within " + MAX_PAGES + " pages");
            }
            float capacity = pageTop - endY;
            VirtualLayout headerToDraw = null;
            if (!firstPage && header != null) {
                if (header.totalHeight() >= capacity - EPS) {
                    throw new TableLayoutException(
                            "Repeated header rows (" + header.totalHeight()
                                    + "pt) don't fit the page capacity (" + capacity + "pt)");
                }
                headerToDraw = header;
                capacity -= header.totalHeight();
            }
            if (firstPage && header != null && header.totalHeight() > capacity + EPS) {
                throw new TableLayoutException(
                        "Header rows (" + header.totalHeight()
                                + "pt) are taller than the first page capacity (" + capacity + "pt); headers never split");
            }

            PageCutter.CutResult cut = PageCutter.cut(current, capacity);
            float bodyTop = pageTop - (headerToDraw != null ? headerToDraw.totalHeight() : 0);
            try (PDPageContentStream cs = new PDPageContentStream(
                    document, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
                if (headerToDraw != null) {
                    LayoutRenderer.render(document, cs, headerToDraw, 0, headerToDraw.totalHeight(), startX, pageTop);
                }
                LayoutRenderer.render(document, cs, current, 0, cut.cutY(), startX, bodyTop, closeBordersAtPageBreak);
            }
            if (cut.finished()) {
                return bodyTop - cut.cutY();
            }

            VirtualLayout next = LayoutEngine.compute(cut.remainderCells(), cut.remainderRowCount(),
                    table.minRowHeight(), cut.firstRowContinued(), table.rowSpanDistribution());
            boolean fullCapacityPage = !firstPage || startY == null;
            if (fullCapacityPage && cut.drawnElementCount() == 0
                    && next.totalHeight() >= current.totalHeight() - EPS) {
                throw new TableLayoutException(
                        "No progress on a full page: an element (or unsplittable remainder of "
                                + next.totalHeight() + "pt) is taller than the page capacity of " + capacity + "pt");
            }
            current = next;
            page = pageSupplier.get();
            addPageIfAbsent(page);
            pageTop = startYOnNewPages != null ? startYOnNewPages : defaultTop(page);
            firstPage = false;
        }
    }

    /**
     * Adds the page to the document unless it is already part of it. Suppliers
     * may hand back existing pages — that's how several multi-page tables are
     * drawn side by side on the same page sequence.
     */
    private void addPageIfAbsent(PDPage page) {
        for (PDPage existing : document.getPages()) {
            if (existing.getCOSObject() == page.getCOSObject()) {
                return;
            }
        }
        document.addPage(page);
    }

    private static float defaultTop(PDPage page) {
        return page.getMediaBox().getUpperRightY() - DEFAULT_MARGIN;
    }

    public static final class Builder {
        private PDDocument document;
        private Table table;
        private PDPage startPage;
        private float startX = DEFAULT_MARGIN;
        private Float startY;
        private float endY = DEFAULT_MARGIN;
        private Float startYOnNewPages;
        private Supplier<PDPage> pageSupplier = () -> new PDPage(PDRectangle.A4);
        private boolean closeBordersAtPageBreak;

        public Builder document(PDDocument document) {
            this.document = document;
            return this;
        }

        public Builder table(Table table) {
            this.table = table;
            return this;
        }

        /** Existing page to start drawing on; by default a new page is added. */
        public Builder page(PDPage page) {
            this.startPage = page;
            return this;
        }

        public Builder startX(float startX) {
            this.startX = startX;
            return this;
        }

        /** Top edge of the table on the first page; default: page top minus 50. */
        public Builder startY(float startY) {
            this.startY = startY;
            return this;
        }

        /** Lowest y the table may reach on any page; default 50. */
        public Builder endY(float endY) {
            this.endY = endY;
            return this;
        }

        /** Top edge of the table on pages after the first; default: page top minus 50. */
        public Builder startYOnNewPages(float startYOnNewPages) {
            this.startYOnNewPages = startYOnNewPages;
            return this;
        }

        /**
         * Supplies pages when the table overflows; default: new A4 pages. The
         * supplier may return pages already in the document (they are not added
         * twice) — that's how several multi-page tables share a page sequence,
         * e.g. side by side, and where callers can decorate new pages before
         * the table lands on them.
         */
        public Builder pageSupplier(Supplier<PDPage> pageSupplier) {
            this.pageSupplier = Objects.requireNonNull(pageSupplier);
            return this;
        }

        /**
         * When true, cells cut by a page break draw closed: the bottom border
         * runs along the cut line and the continuation draws its top border on
         * the next page (using the cell's own border styles). Default false —
         * cut cells stay visually open to signal they continue.
         */
        public Builder closeBordersAtPageBreak(boolean close) {
            this.closeBordersAtPageBreak = close;
            return this;
        }

        public TableDrawer build() {
            Objects.requireNonNull(document, "document");
            Objects.requireNonNull(table, "table");
            return new TableDrawer(this);
        }
    }
}
