package io.github.orestkollcaku.pdftables.layout;

import io.github.orestkollcaku.pdftables.Cell;
import io.github.orestkollcaku.pdftables.PlaceholderContent;
import io.github.orestkollcaku.pdftables.Table;
import io.github.orestkollcaku.pdftables.TableValidationException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GridFlowTest {

    private static Cell cell() {
        return Cell.of(PlaceholderContent.ofHeight(10));
    }

    private static Cell cell(int colSpan, int rowSpan) {
        return Cell.builder().add(PlaceholderContent.ofHeight(10)).colSpan(colSpan).rowSpan(rowSpan).build();
    }

    private static Table.Builder threeColumns() {
        return Table.builder().addColumnsOfWidth(100, 100, 100);
    }

    @Test
    void cellsFlowLeftToRightAndWrap() {
        Table t = threeColumns()
                .add(cell()).add(cell()).add(cell())
                .add(cell()).add(cell())
                .build();
        GridFlow.Result r = GridFlow.flow(t);
        assertThat(r.rowCount()).isEqualTo(2);
        assertThat(r.placements()).extracting(GridFlow.Placement::row)
                .containsExactly(0, 0, 0, 1, 1);
        assertThat(r.placements()).extracting(GridFlow.Placement::col)
                .containsExactly(0, 1, 2, 0, 1);
    }

    @Test
    void colSpanTooWideForRemainingRowWraps() {
        Table t = threeColumns()
                .add(cell(2, 1)).add(cell(2, 1))
                .build();
        GridFlow.Result r = GridFlow.flow(t);
        assertThat(r.placements().get(0).row()).isEqualTo(0);
        assertThat(r.placements().get(0).col()).isEqualTo(0);
        assertThat(r.placements().get(1).row()).isEqualTo(1);
        assertThat(r.placements().get(1).col()).isEqualTo(0);
    }

    @Test
    void rowSpanCoversSlotsBelowAndFlowSkipsThem() {
        Table t = Table.builder().addColumnsOfWidth(100, 100)
                .add(cell(1, 2)).add(cell()).add(cell())
                .build();
        GridFlow.Result r = GridFlow.flow(t);
        assertThat(r.rowCount()).isEqualTo(2);
        // third cell lands at (1,1) because (1,0) is covered by the rowspan
        assertThat(r.placements().get(2).row()).isEqualTo(1);
        assertThat(r.placements().get(2).col()).isEqualTo(1);
    }

    @Test
    void rowSpanGrowsGridPastLastFlowedRow() {
        Table t = Table.builder().addColumnsOfWidth(100, 100)
                .add(cell(1, 3)).add(cell())
                .build();
        GridFlow.Result r = GridFlow.flow(t);
        assertThat(r.rowCount()).isEqualTo(3);
    }

    @Test
    void incompleteLastRowIsAllowed() {
        Table t = threeColumns()
                .add(cell()).add(cell()).add(cell()).add(cell())
                .build();
        GridFlow.Result r = GridFlow.flow(t);
        assertThat(r.rowCount()).isEqualTo(2);
        assertThat(r.placements()).hasSize(4);
    }

    @Test
    void colSpanWiderThanTableThrows() {
        Table t = Table.builder().addColumnsOfWidth(100, 100)
                .add(cell(3, 1))
                .build();
        assertThatThrownBy(() -> GridFlow.flow(t))
                .isInstanceOf(TableValidationException.class)
                .hasMessageContaining("colSpan");
    }

    @Test
    void headerRowSpanLeakingIntoBodyThrows() {
        Table t = Table.builder().addColumnsOfWidth(100, 100)
                .headerRowCount(1)
                .add(cell(1, 2)).add(cell()).add(cell())
                .build();
        assertThatThrownBy(() -> GridFlow.flow(t))
                .isInstanceOf(TableValidationException.class)
                .hasMessageContaining("header");
    }

    @Test
    void headerRowCountBeyondGridThrows() {
        Table t = Table.builder().addColumnsOfWidth(100)
                .headerRowCount(3)
                .add(cell())
                .build();
        assertThatThrownBy(() -> GridFlow.flow(t))
                .isInstanceOf(TableValidationException.class)
                .hasMessageContaining("headerRowCount");
    }

    @Test
    void noColumnsThrows() {
        Table t = Table.builder().add(cell()).build();
        assertThatThrownBy(() -> GridFlow.flow(t))
                .isInstanceOf(TableValidationException.class)
                .hasMessageContaining("columns");
    }
}
