package io.github.the13thclown.pdftables.layout;

import io.github.the13thclown.pdftables.Cell;
import io.github.the13thclown.pdftables.Table;
import io.github.the13thclown.pdftables.TableValidationException;

import java.util.ArrayList;
import java.util.List;

/**
 * Flows a table's flat cell sequence into a placement grid. Internal.
 * <p>
 * Each cell drops into the next unoccupied slot left-to-right, wrapping to the
 * next derived row when the current one has no free span-wide slot, and
 * skipping slots covered by earlier rowspans. The grid grows as needed —
 * rowspans past the last flowed row create trailing rows, and an incompletely
 * filled last row simply leaves empty slots.
 */
public final class GridFlow {

    /** A cell's resolved position in the derived grid. */
    public record Placement(Cell cell, int row, int col) {
    }

    public record Result(List<Placement> placements, int rowCount) {
    }

    private GridFlow() {
    }

    public static Result flow(Table table) {
        int cols = table.columns().size();
        if (cols < 1) {
            throw new TableValidationException("Table has no columns");
        }
        List<boolean[]> occupied = new ArrayList<>();
        List<Placement> placements = new ArrayList<>();
        int curRow = 0;
        int curCol = 0;
        int rowCount = 0;

        for (int i = 0; i < table.cells().size(); i++) {
            Cell cell = table.cells().get(i);
            if (cell.colSpan() > cols) {
                throw new TableValidationException(
                        "Cell " + i + " has colSpan " + cell.colSpan() + " but the table has only " + cols + " columns");
            }
            while (true) {
                if (curCol + cell.colSpan() > cols) {
                    curRow++;
                    curCol = 0;
                    continue;
                }
                if (isFree(occupied, curRow, curCol, cell.colSpan(), cols)) {
                    break;
                }
                curCol++;
            }
            placements.add(new Placement(cell, curRow, curCol));
            markOccupied(occupied, curRow, curCol, cell.rowSpan(), cell.colSpan(), cols);
            rowCount = Math.max(rowCount, curRow + cell.rowSpan());
            curCol += cell.colSpan();
        }

        validateHeaders(table, placements, rowCount);
        return new Result(placements, rowCount);
    }

    private static void validateHeaders(Table table, List<Placement> placements, int rowCount) {
        int hc = table.headerRowCount();
        if (hc == 0) {
            return;
        }
        if (hc > rowCount) {
            throw new TableValidationException(
                    "headerRowCount is " + hc + " but the table only has " + rowCount + " derived rows");
        }
        for (Placement p : placements) {
            if (p.row() < hc && p.row() + p.cell().rowSpan() > hc) {
                throw new TableValidationException(
                        "A rowspan starting in header row " + p.row() + " extends into the table body; "
                                + "header rows must form a self-contained repeatable block");
            }
        }
    }

    private static boolean isFree(List<boolean[]> occupied, int row, int col, int colSpan, int cols) {
        if (row >= occupied.size()) {
            return true;
        }
        boolean[] slots = occupied.get(row);
        for (int c = col; c < col + colSpan; c++) {
            if (slots[c]) {
                return false;
            }
        }
        return true;
    }

    private static void markOccupied(List<boolean[]> occupied, int row, int col, int rowSpan, int colSpan, int cols) {
        for (int r = row; r < row + rowSpan; r++) {
            while (r >= occupied.size()) {
                occupied.add(new boolean[cols]);
            }
            boolean[] slots = occupied.get(r);
            for (int c = col; c < col + colSpan; c++) {
                slots[c] = true;
            }
        }
    }
}
