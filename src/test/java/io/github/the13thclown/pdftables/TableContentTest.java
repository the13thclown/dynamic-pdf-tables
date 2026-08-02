package io.github.the13thclown.pdftables;

import io.github.the13thclown.pdftables.style.Style;
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
        List<Element> elements = TableContent.of(inner).layout(200, Style.defaults());
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
        List<Element> elements = TableContent.of(inner).layout(200, Style.defaults());
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
        List<Element> elements = TableContent.of(inner).layout(280, Style.defaults());
        assertThat(elements).hasSize(1);
    }

    @Test
    void emptyInnerTableYieldsNoElements() {
        Table inner = Table.builder().addColumnOfWidth(100).build();
        assertThat(TableContent.of(inner).layout(200, Style.defaults())).isEmpty();
    }

    @Test
    void innerRowBlockSplitsWhenItsCrossingElementsAgree() {
        Table inner = Table.builder().addColumnsOfWidth(80, 80)
                .add(Cell.of(PlaceholderContent.ofHeight(500)))
                .add(Cell.of(PlaceholderContent.ofHeight(40)))
                .build();
        List<Element> blocks = TableContent.of(inner).layout(200, Style.defaults());
        assertThat(blocks).hasSize(1);
        assertThat(blocks.get(0).getHeight()).isCloseTo(500, within(0.01f));

        Element.Split split = blocks.get(0).splitAt(200);
        assertThat(split).isNotNull();
        assertThat(split.top().getHeight()).isCloseTo(200, within(0.01f));
        assertThat(split.bottom().getHeight()).isCloseTo(300, within(0.01f));

        // and recursively: the bottom block piece splits again
        Element.Split second = split.bottom().splitAt(200);
        assertThat(second).isNotNull();
        assertThat(second.top().getHeight()).isCloseTo(200, within(0.01f));
        assertThat(second.bottom().getHeight()).isCloseTo(100, within(0.01f));
    }

    @Test
    void innerRowBlockDeclinesToSplitThroughAnAtomicElement() {
        CellContent atomic = (availableWidth, style) -> List.of(new Element() {
            @Override
            public float getHeight() {
                return 500;
            }

            @Override
            public void draw(io.github.the13thclown.pdftables.render.RenderContext ctx) {
            }
        });
        Table inner = Table.builder().addColumnOfWidth(100)
                .add(Cell.of(atomic))
                .build();
        List<Element> blocks = TableContent.of(inner).layout(200, Style.defaults());
        // the cut would land inside the unsplittable element: the block stays atomic
        assertThat(blocks.get(0).splitAt(200)).isNull();
    }
}
