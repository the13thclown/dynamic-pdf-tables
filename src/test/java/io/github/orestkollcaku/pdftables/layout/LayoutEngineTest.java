package io.github.orestkollcaku.pdftables.layout;

import io.github.orestkollcaku.pdftables.Cell;
import io.github.orestkollcaku.pdftables.PlaceholderContent;
import io.github.orestkollcaku.pdftables.Table;
import io.github.orestkollcaku.pdftables.TableValidationException;
import io.github.orestkollcaku.pdftables.style.VerticalAlignment;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class LayoutEngineTest {

    private static VirtualLayout layoutOf(Table t) {
        GridFlow.Result grid = GridFlow.flow(t);
        float[] widths = LayoutEngine.resolveColumns(t);
        List<LayoutCell> cells = LayoutEngine.buildCells(t, grid.placements(), widths);
        return LayoutEngine.compute(cells, grid.rowCount(), t.minRowHeight(), false, t.rowSpanDistribution());
    }

    @Test
    void fixedColumnsResolveAsGiven() {
        Table t = Table.builder().addColumnsOfWidth(100, 200).build();
        assertThat(LayoutEngine.resolveColumns(t)).containsExactly(100, 200);
    }

    @Test
    void relativeColumnsShareRemainingWidthByWeight() {
        Table t = Table.builder()
                .width(400)
                .addColumnOfWidth(100)
                .addColumnOfRelativeWidth(1)
                .addColumnOfRelativeWidth(3)
                .build();
        float[] w = LayoutEngine.resolveColumns(t);
        assertThat(w[0]).isEqualTo(100);
        assertThat(w[1]).isCloseTo(75, within(0.01f));
        assertThat(w[2]).isCloseTo(225, within(0.01f));
    }

    @Test
    void relativeColumnsWithoutTableWidthThrow() {
        Table t = Table.builder().addColumnOfRelativeWidth(1).build();
        assertThatThrownBy(() -> LayoutEngine.resolveColumns(t))
                .isInstanceOf(TableValidationException.class)
                .hasMessageContaining("width");
    }

    @Test
    void fixedWidthsExceedingTableWidthThrow() {
        Table t = Table.builder()
                .width(100)
                .addColumnOfWidth(150)
                .addColumnOfRelativeWidth(1)
                .build();
        assertThatThrownBy(() -> LayoutEngine.resolveColumns(t))
                .isInstanceOf(TableValidationException.class);
    }

    @Test
    void rowHeightIsTallestCellStackPlusPadding() {
        Table t = Table.builder().addColumnsOfWidth(100, 100)
                .add(Cell.builder().add(PlaceholderContent.ofHeight(30)).paddingAll(5).build())
                .add(Cell.builder().add(PlaceholderContent.ofHeight(50)).paddingAll(5).build())
                .build();
        VirtualLayout l = layoutOf(t);
        assertThat(l.rowHeights()[0]).isCloseTo(60, within(0.01f));
        assertThat(l.totalHeight()).isCloseTo(60, within(0.01f));
    }

    @Test
    void multipleContentsStackVertically() {
        Table t = Table.builder().addColumnsOfWidth(100)
                .add(Cell.builder()
                        .add(PlaceholderContent.ofHeight(20))
                        .add(PlaceholderContent.ofHeight(30))
                        .paddingAll(5)
                        .build())
                .build();
        VirtualLayout l = layoutOf(t);
        LayoutCell c = l.cells().get(0);
        assertThat(l.rowHeights()[0]).isCloseTo(60, within(0.01f));
        assertThat(c.elementTop(0)).isCloseTo(5, within(0.01f));
        assertThat(c.elementTop(1)).isCloseTo(25, within(0.01f));
    }

    @Test
    void minRowHeightFloorsRows() {
        Table t = Table.builder().addColumnsOfWidth(100)
                .minRowHeight(40)
                .add(Cell.of(PlaceholderContent.ofHeight(10)))
                .build();
        VirtualLayout l = layoutOf(t);
        assertThat(l.rowHeights()[0]).isEqualTo(40);
    }

    @Test
    void middleAlignmentCentersElementStack() {
        Table t = Table.builder().addColumnsOfWidth(100, 100)
                .add(Cell.builder().add(PlaceholderContent.ofHeight(20))
                        .verticalAlignment(VerticalAlignment.MIDDLE).build())
                .add(Cell.of(PlaceholderContent.ofHeight(60)))
                .build();
        VirtualLayout l = layoutOf(t);
        assertThat(l.cells().get(0).elementTop(0)).isCloseTo(20, within(0.01f));
    }

    @Test
    void rowSpanDeficitDistributesEquallyAcrossRows() {
        Table t = Table.builder().addColumnsOfWidth(100, 100)
                .add(Cell.builder().add(PlaceholderContent.ofHeight(100)).rowSpan(2).build())
                .add(Cell.of(PlaceholderContent.ofHeight(20)))
                .add(Cell.of(PlaceholderContent.ofHeight(20)))
                .build();
        VirtualLayout l = layoutOf(t);
        assertThat(l.rowHeights()[0]).isCloseTo(50, within(0.01f));
        assertThat(l.rowHeights()[1]).isCloseTo(50, within(0.01f));
        assertThat(l.cells().get(0).height()).isCloseTo(100, within(0.01f));
    }

    @Test
    void lastRowDistributionPoolsDeficitIntoTheFinalSpannedRow() {
        Table t = Table.builder().addColumnsOfWidth(100, 100)
                .rowSpanDistribution(io.github.orestkollcaku.pdftables.RowSpanDistribution.LAST_ROW)
                .add(Cell.builder().add(PlaceholderContent.ofHeight(100)).rowSpan(2).build())
                .add(Cell.of(PlaceholderContent.ofHeight(20)))
                .add(Cell.of(PlaceholderContent.ofHeight(20)))
                .build();
        VirtualLayout l = layoutOf(t);
        assertThat(l.rowHeights()[0]).isCloseTo(20, within(0.01f));
        assertThat(l.rowHeights()[1]).isCloseTo(80, within(0.01f));
    }

    @Test
    void rowStylerSitsBetweenCellStyleAndTableDefault() {
        java.awt.Color tableColor = java.awt.Color.GRAY;
        java.awt.Color evenRow = java.awt.Color.WHITE;
        java.awt.Color cellColor = java.awt.Color.RED;
        Table t = Table.builder().addColumnsOfWidth(100)
                .defaultStyle(io.github.orestkollcaku.pdftables.style.Style.builder()
                        .backgroundColor(tableColor).build())
                .rowStyler(row -> row % 2 == 0
                        ? io.github.orestkollcaku.pdftables.style.Style.builder().backgroundColor(evenRow).build()
                        : null)
                .add(Cell.of(PlaceholderContent.ofHeight(10)))                        // row 0: rowStyler wins
                .add(Cell.of(PlaceholderContent.ofHeight(10)))                        // row 1: table default
                .add(Cell.builder().add(PlaceholderContent.ofHeight(10))
                        .backgroundColor(cellColor).build())                           // row 2: cell wins over rowStyler
                .build();
        VirtualLayout l = layoutOf(t);
        assertThat(l.cells().get(0).style().backgroundColor()).isEqualTo(evenRow);
        assertThat(l.cells().get(1).style().backgroundColor()).isEqualTo(tableColor);
        assertThat(l.cells().get(2).style().backgroundColor()).isEqualTo(cellColor);
    }

    @Test
    void positionedContentStretchesRowToItsDeepestPoint() {
        Table t = Table.builder().addColumnsOfWidth(200)
                .add(Cell.builder()
                        .addAt(20, 100, PlaceholderContent.ofSize(50, 50))
                        .paddingAll(10)
                        .build())
                .build();
        VirtualLayout l = layoutOf(t);
        assertThat(l.rowHeights()[0]).isCloseTo(170, within(0.01f));   // 100 + 50 + 2*10
        assertThat(l.cells().get(0).elementTop(0)).isCloseTo(110, within(0.01f));
    }

    @Test
    void positionedContentDoesNotDisturbTheFlowStack() {
        Table t = Table.builder().addColumnsOfWidth(200)
                .add(Cell.builder()
                        .add(PlaceholderContent.ofHeight(30))
                        .addAt(100, 5, PlaceholderContent.ofSize(40, 40))
                        .add(PlaceholderContent.ofHeight(20))
                        .build())
                .build();
        VirtualLayout l = layoutOf(t);
        LayoutCell c = l.cells().get(0);
        // flow: 30 at 0, 20 at 30; positioned: at 5; row = max(flow 50, 5+40)
        assertThat(c.elementTop(0)).isCloseTo(0, within(0.01f));
        assertThat(c.elementTop(1)).isCloseTo(5, within(0.01f));
        assertThat(c.elementTop(2)).isCloseTo(30, within(0.01f));
        assertThat(l.rowHeights()[0]).isCloseTo(50, within(0.01f));
    }

    @Test
    void spannedCellWidthSumsColumns() {
        Table t = Table.builder().addColumnsOfWidth(100, 150, 50)
                .add(Cell.builder().add(PlaceholderContent.ofHeight(10)).colSpan(2).build())
                .add(Cell.of(PlaceholderContent.ofHeight(10)))
                .build();
        VirtualLayout l = layoutOf(t);
        assertThat(l.cells().get(0).width()).isEqualTo(250);
        assertThat(l.cells().get(1).x()).isEqualTo(250);
        assertThat(l.cells().get(1).width()).isEqualTo(50);
    }
}
