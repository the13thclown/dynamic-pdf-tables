package io.github.the13thclown.pdftables;

import io.github.the13thclown.pdftables.layout.Element;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class TableContentTest {

    @Test
    void innerRowsBecomeOneElementEach() {
        Table inner = Table.builder().addColumnsOfWidth(60, 60)
                .add(Cell.of(PlaceholderContent.ofHeight(30)))
                .add(Cell.of(PlaceholderContent.ofHeight(50)))
                .add(Cell.of(PlaceholderContent.ofHeight(20)))
                .add(Cell.of(PlaceholderContent.ofHeight(20)))
                .build();
        List<Element> elements = TableContent.of(inner).layout(200);
        assertThat(elements).hasSize(2);
        assertThat(elements.get(0).getHeight()).isCloseTo(50, within(0.01f));
        assertThat(elements.get(1).getHeight()).isCloseTo(20, within(0.01f));
    }

    @Test
    void rowSpanUnitesRowsIntoOneAtomicBlock() {
        Table inner = Table.builder().addColumnsOfWidth(60, 60)
                .add(Cell.builder().add(PlaceholderContent.ofHeight(80)).rowSpan(2).build())
                .add(Cell.of(PlaceholderContent.ofHeight(20)))
                .add(Cell.of(PlaceholderContent.ofHeight(20)))
                .add(Cell.of(PlaceholderContent.ofHeight(20)))
                .add(Cell.of(PlaceholderContent.ofHeight(20)))
                .build();
        List<Element> elements = TableContent.of(inner).layout(200);
        // rows 0+1 are tied by the rowspan -> one 80pt block; row 2 is its own
        assertThat(elements).hasSize(2);
        assertThat(elements.get(0).getHeight()).isCloseTo(80, within(0.01f));
        assertThat(elements.get(1).getHeight()).isCloseTo(20, within(0.01f));
    }

    @Test
    void relativeInnerColumnsFillTheAvailableWidth() {
        Table inner = Table.builder()
                .addColumnOfRelativeWidth(1)
                .addColumnOfRelativeWidth(3)
                .add(Cell.of(PlaceholderContent.ofHeight(10)))
                .add(Cell.of(PlaceholderContent.ofHeight(10)))
                .build();
        // no explicit width: the cell's content width (280) is the fallback
        List<Element> elements = TableContent.of(inner).layout(280);
        assertThat(elements).hasSize(1);
    }

    @Test
    void emptyInnerTableYieldsNoElements() {
        Table inner = Table.builder().addColumnOfWidth(100).build();
        assertThat(TableContent.of(inner).layout(200)).isEmpty();
    }
}
