package io.github.the13thclown.pdftables.layout;

import io.github.the13thclown.pdftables.Cell;
import io.github.the13thclown.pdftables.RowSpanDistribution;
import io.github.the13thclown.pdftables.Table;
import io.github.the13thclown.pdftables.TableValidationException;
import io.github.the13thclown.pdftables.style.Style;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Turns a table definition into a {@link VirtualLayout} on the endless virtual
 * y axis. Internal. Called once for the full table, and again by the page loop
 * for each continue-and-forget remainder (with element heights already fixed).
 */
public final class LayoutEngine {

    private static final float EPS = LayoutCell.EPS;

    private LayoutEngine() {
    }

    /** Resolves column specs to concrete widths in points. */
    public static float[] resolveColumns(Table table) {
        return resolveColumns(table, null);
    }

    /**
     * Resolves column specs; {@code fallbackWidth} substitutes for a missing
     * explicit table width (nested tables pass the cell's content width, so an
     * inner table with relative columns naturally fills its cell).
     */
    public static float[] resolveColumns(Table table, Float fallbackWidth) {
        List<ColumnSpec> specs = table.columns();
        if (specs.isEmpty()) {
            throw new TableValidationException("Table has no columns");
        }
        float fixedSum = 0;
        float weightSum = 0;
        for (ColumnSpec spec : specs) {
            if (spec.relative()) {
                weightSum += spec.value();
            } else {
                fixedSum += spec.value();
            }
        }
        float[] widths = new float[specs.size()];
        if (weightSum > 0) {
            Float tableWidth = table.width() != null ? table.width() : fallbackWidth;
            if (tableWidth == null) {
                throw new TableValidationException(
                        "Columns with relative widths require an explicit table width");
            }
            float remaining = tableWidth - fixedSum;
            if (remaining < -EPS) {
                throw new TableValidationException(
                        "Fixed column widths (" + fixedSum + ") exceed the table width (" + tableWidth + ")");
            }
            remaining = Math.max(0, remaining);
            for (int i = 0; i < specs.size(); i++) {
                ColumnSpec spec = specs.get(i);
                widths[i] = spec.relative() ? remaining * spec.value() / weightSum : spec.value();
            }
        } else {
            for (int i = 0; i < specs.size(); i++) {
                widths[i] = specs.get(i).value();
            }
        }
        return widths;
    }

    public static List<LayoutCell> buildCells(Table table, List<GridFlow.Placement> placements,
                                              float[] colWidths) {
        return buildCells(table, placements, colWidths, Style.defaults());
    }

    /**
     * Builds layout cells from grid placements: resolves each cell's style
     * along the cascade cell → row styler → column styler → table default →
     * {@code base}, computes its x/width from the columns, and lays out its
     * contents into elements at the content width. The only place
     * {@link io.github.the13thclown.pdftables.CellContent#layout} is ever
     * called. {@code base} is normally {@link Style#defaults()}; nested tables
     * pass their outer cell's text defaults so fonts inherit inward.
     */
    public static List<LayoutCell> buildCells(Table table, List<GridFlow.Placement> placements,
                                              float[] colWidths, Style base) {
        float[] colX = new float[colWidths.length];
        for (int i = 1; i < colWidths.length; i++) {
            colX[i] = colX[i - 1] + colWidths[i - 1];
        }
        Style tableDefault = table.defaultStyle() == null
                ? base
                : table.defaultStyle().mergedOnto(base);

        List<LayoutCell> cells = new ArrayList<>(placements.size());
        for (GridFlow.Placement p : placements) {
            Cell cell = p.cell();
            Style style = tableDefault;
            if (table.columnStyler() != null) {
                Style columnStyle = table.columnStyler().apply(p.col());
                if (columnStyle != null) {
                    style = columnStyle.mergedOnto(style);
                }
            }
            if (table.rowStyler() != null) {
                Style rowStyle = table.rowStyler().apply(p.row());
                if (rowStyle != null) {
                    style = rowStyle.mergedOnto(style);
                }
            }
            if (cell.style() != null) {
                style = cell.style().mergedOnto(style);
            }
            float width = 0;
            for (int c = p.col(); c < p.col() + cell.colSpan(); c++) {
                width += colWidths[c];
            }
            float contentWidth = Math.max(0, width - style.padding().horizontal());
            List<LayoutCell.Item> items = new ArrayList<>();
            for (Cell.ContentEntry entry : cell.contents()) {
                List<Element> elements = entry.content().layout(contentWidth, style);
                if (entry.positioned()) {
                    // a positioned content's elements stack from its anchor point
                    float y = entry.y();
                    for (Element e : elements) {
                        items.add(new LayoutCell.Item(e, entry.x(), y));
                        y += e.getHeight();
                    }
                } else {
                    for (Element e : elements) {
                        items.add(new LayoutCell.Item(e, null, null));
                    }
                }
            }
            cells.add(new LayoutCell(p.row(), p.col(), cell.rowSpan(), cell.colSpan(),
                    style, items, false, colX[p.col()], width));
        }
        return cells;
    }

    /**
     * Computes row heights and virtual positions for the given cells.
     * {@code firstRowContinued} marks row 0 as the continuation of a cell row
     * cut on the previous page: it re-stacks from its remaining elements and
     * skips the minRowHeight floor so consumed height is not re-added.
     */
    public static VirtualLayout compute(List<LayoutCell> cells, int rowCount,
                                        float minRowHeight, boolean firstRowContinued) {
        return compute(cells, rowCount, minRowHeight, firstRowContinued, RowSpanDistribution.EQUAL);
    }

    public static VirtualLayout compute(List<LayoutCell> cells, int rowCount,
                                        float minRowHeight, boolean firstRowContinued,
                                        RowSpanDistribution distribution) {
        float[] rowHeights = new float[rowCount];
        for (LayoutCell c : cells) {
            if (c.rowSpan() == 1) {
                rowHeights[c.row()] = Math.max(rowHeights[c.row()], c.requiredHeight());
            }
        }
        for (int r = 0; r < rowCount; r++) {
            if (!(r == 0 && firstRowContinued)) {
                rowHeights[r] = Math.max(rowHeights[r], minRowHeight);
            }
        }
        // Spanning cells needing more than their rows provide distribute the
        // deficit equally; increasing span order keeps the result deterministic.
        List<LayoutCell> spanning = cells.stream()
                .filter(c -> c.rowSpan() > 1)
                .sorted(Comparator.comparingInt(LayoutCell::rowSpan))
                .toList();
        for (LayoutCell c : spanning) {
            float have = 0;
            for (int r = c.row(); r < c.row() + c.rowSpan(); r++) {
                have += rowHeights[r];
            }
            float deficit = c.requiredHeight() - have;
            if (deficit > EPS) {
                if (distribution == RowSpanDistribution.LAST_ROW) {
                    rowHeights[c.row() + c.rowSpan() - 1] += deficit;
                } else {
                    float add = deficit / c.rowSpan();
                    for (int r = c.row(); r < c.row() + c.rowSpan(); r++) {
                        rowHeights[r] += add;
                    }
                }
            }
        }
        float[] rowTops = new float[rowCount];
        float total = 0;
        for (int r = 0; r < rowCount; r++) {
            rowTops[r] = total;
            total += rowHeights[r];
        }
        for (LayoutCell c : cells) {
            c.position(rowTops, rowHeights);
        }
        return new VirtualLayout(cells, rowTops, rowHeights, total);
    }
}
