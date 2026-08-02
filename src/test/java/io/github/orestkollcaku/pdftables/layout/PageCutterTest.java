package io.github.orestkollcaku.pdftables.layout;

import io.github.orestkollcaku.pdftables.Cell;
import io.github.orestkollcaku.pdftables.PlaceholderContent;
import io.github.orestkollcaku.pdftables.Table;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class PageCutterTest {

    private static VirtualLayout layoutOf(Table t) {
        GridFlow.Result grid = GridFlow.flow(t);
        float[] widths = LayoutEngine.resolveColumns(t);
        List<LayoutCell> cells = LayoutEngine.buildCells(t, grid.placements(), widths);
        return LayoutEngine.compute(cells, grid.rowCount(), t.minRowHeight(), false, t.rowSpanDistribution());
    }

    private static Table singleColumnRows(float... heights) {
        Table.Builder b = Table.builder().addColumnOfWidth(200);
        for (float h : heights) {
            b.add(Cell.of(PlaceholderContent.ofHeight(h)));
        }
        return b.build();
    }

    @Test
    void layoutFittingCapacityFinishes() {
        VirtualLayout l = layoutOf(singleColumnRows(100, 100));
        PageCutter.CutResult cut = PageCutter.cut(l, 500);
        assertThat(cut.finished()).isTrue();
        assertThat(cut.cutY()).isEqualTo(200);
        assertThat(cut.drawnElementCount()).isEqualTo(2);
        assertThat(cut.remainderCells()).isEmpty();
    }

    @Test
    void cutInsideARowContinuesItAndPassesItsElementDown() {
        VirtualLayout l = layoutOf(singleColumnRows(100, 100, 100));
        PageCutter.CutResult cut = PageCutter.cut(l, 150);
        assertThat(cut.finished()).isFalse();
        assertThat(cut.cutY()).isEqualTo(150);
        // row 0 fully drawn; row 1's element crosses the cut and passes down whole
        assertThat(cut.drawnElementCount()).isEqualTo(1);
        assertThat(cut.remainderRowCount()).isEqualTo(2);
        assertThat(cut.firstRowContinued()).isTrue();
        LayoutCell continued = cut.remainderCells().get(0);
        assertThat(continued.continuedTop()).isTrue();
        assertThat(continued.elements()).hasSize(1);
        assertThat(continued.row()).isEqualTo(0);
    }

    @Test
    void cutExactlyAtRowBoundaryDoesNotContinueTheRow() {
        VirtualLayout l = layoutOf(singleColumnRows(100, 100, 100));
        PageCutter.CutResult cut = PageCutter.cut(l, 100);
        assertThat(cut.firstRowContinued()).isFalse();
        assertThat(cut.remainderRowCount()).isEqualTo(2);
        assertThat(cut.remainderCells().get(0).continuedTop()).isFalse();
    }

    @Test
    void multiElementCellSplitsAtElementGranularity() {
        Table t = Table.builder().addColumnOfWidth(200)
                .add(Cell.builder()
                        .add(PlaceholderContent.ofHeight(50))
                        .add(PlaceholderContent.ofHeight(50))
                        .add(PlaceholderContent.ofHeight(50))
                        .add(PlaceholderContent.ofHeight(50))
                        .build())
                .build();
        VirtualLayout l = layoutOf(t);
        PageCutter.CutResult cut = PageCutter.cut(l, 120);
        // elements at 0-50 and 50-100 fit; 100-150 crosses and passes down
        assertThat(cut.drawnElementCount()).isEqualTo(2);
        LayoutCell continued = cut.remainderCells().get(0);
        assertThat(continued.continuedTop()).isTrue();
        assertThat(continued.elements()).hasSize(2);

        // continue-and-forget: the remainder re-lays from virtual 0
        VirtualLayout next = LayoutEngine.compute(cut.remainderCells(), cut.remainderRowCount(), 0, cut.firstRowContinued());
        assertThat(next.totalHeight()).isCloseTo(100, within(0.01f));
        assertThat(next.cells().get(0).elementTop(0)).isCloseTo(0, within(0.01f));
    }

    @Test
    void rowSpanCrossingCutIsClippedAndContinued() {
        Table t = Table.builder().addColumnsOfWidth(100, 100)
                .add(Cell.builder().add(PlaceholderContent.ofHeight(90)).rowSpan(3).build())
                .add(Cell.of(PlaceholderContent.ofHeight(40)))
                .add(Cell.of(PlaceholderContent.ofHeight(40)))
                .add(Cell.of(PlaceholderContent.ofHeight(40)))
                .build();
        VirtualLayout l = layoutOf(t);
        // rows are 40 each (span needs 90 <= 120), total 120
        PageCutter.CutResult cut = PageCutter.cut(l, 60);
        // cut inside row 1: row 0 fully drawn, spanning cell continues
        assertThat(cut.remainderRowCount()).isEqualTo(2);
        LayoutCell spanCell = cut.remainderCells().stream()
                .filter(c -> c.col() == 0).findFirst().orElseThrow();
        assertThat(spanCell.row()).isEqualTo(0);
        assertThat(spanCell.rowSpan()).isEqualTo(2);
        assertThat(spanCell.continuedTop()).isTrue();
    }

    @Test
    void positionedItemsShiftUpByTheCutPreservingArrangement() {
        Table t = Table.builder().addColumnOfWidth(200)
                .add(Cell.builder()
                        .addAt(0, 0, PlaceholderContent.ofSize(50, 100))
                        .addAt(30, 400, PlaceholderContent.ofSize(50, 100))
                        .build())
                .build();
        VirtualLayout l = layoutOf(t);
        assertThat(l.totalHeight()).isEqualTo(500);

        PageCutter.CutResult cut = PageCutter.cut(l, 300);
        assertThat(cut.drawnElementCount()).isEqualTo(1);
        LayoutCell continued = cut.remainderCells().get(0);
        assertThat(continued.items()).hasSize(1);
        // the surviving item keeps its x and shifts up by the 300pt cut
        assertThat(continued.items().get(0).x()).isEqualTo(30);
        assertThat(continued.items().get(0).y()).isEqualTo(100);

        VirtualLayout next = LayoutEngine.compute(cut.remainderCells(), cut.remainderRowCount(), 0, cut.firstRowContinued());
        assertThat(next.totalHeight()).isCloseTo(200, within(0.01f));
        assertThat(next.cells().get(0).elementTop(0)).isCloseTo(100, within(0.01f));
    }

    @Test
    void positionedItemsCrossingTheCutMoveAsOneRigidPiece() {
        Table t = Table.builder().addColumnOfWidth(200)
                .add(Cell.builder()
                        .addAt(0, 250, PlaceholderContent.ofSize(50, 100))
                        .addAt(0, 400, PlaceholderContent.ofSize(50, 50))
                        .build())
                .build();
        VirtualLayout l = layoutOf(t);
        PageCutter.CutResult cut = PageCutter.cut(l, 300);
        // the first item crosses the cut (250..350) and passes down whole; the
        // shift is capped at its y (250), so BOTH items move up by 250 and the
        // 150pt gap between them is preserved — no overlap is ever introduced
        assertThat(cut.drawnElementCount()).isEqualTo(0);
        LayoutCell continued = cut.remainderCells().get(0);
        assertThat(continued.items().get(0).y()).isEqualTo(0);
        assertThat(continued.items().get(1).y()).isEqualTo(150);
    }

    @Test
    void continuedRowSkipsMinRowHeightFloor() {
        Table t = Table.builder().addColumnOfWidth(200)
                .minRowHeight(300)
                .add(Cell.of(PlaceholderContent.ofHeight(20)))
                .build();
        VirtualLayout l = layoutOf(t);
        assertThat(l.totalHeight()).isEqualTo(300);
        PageCutter.CutResult cut = PageCutter.cut(l, 100);
        // element (0-20) was drawn; the continued row re-stacks without the floor
        VirtualLayout next = LayoutEngine.compute(cut.remainderCells(), cut.remainderRowCount(),
                300, cut.firstRowContinued());
        assertThat(next.totalHeight()).isCloseTo(0, within(0.01f));
    }
}
