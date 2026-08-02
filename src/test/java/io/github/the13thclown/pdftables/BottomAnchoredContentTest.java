package io.github.the13thclown.pdftables;

import io.github.the13thclown.pdftables.render.RenderContext;
import io.github.the13thclown.pdftables.style.BorderStyle;
import io.github.the13thclown.pdftables.style.Padding;
import io.github.the13thclown.pdftables.style.Style;
import io.github.the13thclown.pdftables.style.VerticalAlignment;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers {@link Cell.Builder#addBottom}: content anchored to the bottom of a
 * cell while other content still flows from the top — a label above, a sign-off
 * below, in one box. Vertical alignment cannot express this because it moves the
 * whole stack.
 */
class BottomAnchoredContentTest {

    /** Records the page and box of every draw. */
    static final class Probe implements CellContent {
        private final float height;
        private final List<Integer> pages = new ArrayList<>();
        private final List<Float> tops = new ArrayList<>();

        Probe(float height) {
            this.height = height;
        }

        @Override
        public List<Element> layout(float availableWidth, Style style) {
            return List.of(new Element() {
                @Override
                public float getHeight() {
                    return height;
                }

                @Override
                public void draw(RenderContext ctx) {
                    pages.add(ctx.pageIndex());
                    tops.add(ctx.y() + ctx.height());
                }
            });
        }
    }

    private static Style bordered() {
        return Style.builder().borderAll(BorderStyle.of(0.5f)).padding(Padding.of(4)).fontSize(9).build();
    }

    @Test
    void bottomContentSitsAgainstTheCellBottomWhileTopContentStaysAtTheTop() throws IOException {
        Probe top = new Probe(10);
        Probe bottom = new Probe(10);
        // a tall neighbour drives the row height, so the cell is much taller than its own content
        Table table = Table.builder()
                .addColumnsOfWidth(100, 100)
                .defaultStyle(bordered())
                .add(Cell.builder().add(top).addBottom(bottom).build())
                .add(Cell.of(PlaceholderContent.ofHeight(200)))
                .build();

        try (PDDocument doc = new PDDocument()) {
            TableDrawer.builder().document(doc).table(table).startY(700).build().draw();
            float cellTop = 700 - 4;              // padding
            float cellBottom = 700 - 200 - 8 + 4; // row height 200 + padding, then bottom padding
            assertThat(top.tops.get(0)).isCloseTo(cellTop, org.assertj.core.data.Offset.offset(0.5f));
            assertThat(bottom.tops.get(0)).isCloseTo(cellBottom + 10, org.assertj.core.data.Offset.offset(0.5f));
            // and they are genuinely far apart, not stacked
            assertThat(top.tops.get(0) - bottom.tops.get(0)).isGreaterThan(150);
        }
    }

    @Test
    void severalBottomContentsStackUpwardsInTheOrderAdded() throws IOException {
        Probe first = new Probe(10);
        Probe second = new Probe(10);
        Table table = Table.builder()
                .addColumnsOfWidth(100, 100)
                .defaultStyle(bordered())
                .add(Cell.builder().addBottom(first).addBottom(second).build())
                .add(Cell.of(PlaceholderContent.ofHeight(120)))
                .build();

        try (PDDocument doc = new PDDocument()) {
            TableDrawer.builder().document(doc).table(table).startY(700).build().draw();
            // "first" is above "second", both against the bottom
            assertThat(first.tops.get(0)).isGreaterThan(second.tops.get(0));
            assertThat(first.tops.get(0) - second.tops.get(0)).isCloseTo(10, org.assertj.core.data.Offset.offset(0.5f));
        }
    }

    @Test
    void bottomContentLandsOnTheLastPageOfACellCutAcrossPages() throws IOException {
        Probe signOff = new Probe(12);
        StringBuilder tall = new StringBuilder();
        for (int i = 0; i < 300; i++) {
            tall.append("description line ").append(i).append('\n');
        }
        Table table = Table.builder()
                .addColumnsOfWidth(300, 100)
                .defaultStyle(bordered())
                .add(Cell.of(TextContent.of(tall.toString())))
                .add(Cell.builder().add(TextContent.of("WS-1")).addBottom(signOff).build())
                .build();

        try (PDDocument doc = new PDDocument()) {
            TableDrawer.builder().document(doc).table(table).build().draw();
            assertThat(doc.getNumberOfPages()).isGreaterThan(2);
            // drawn exactly once, on the cell's final page — not alongside the label on page 1
            assertThat(signOff.pages).containsExactly(doc.getNumberOfPages() - 1);
        }
    }

    @Test
    void verticalAlignmentDistributesOnlyWhatTheTwoStacksLeaveOver() throws IOException {
        Probe top = new Probe(10);
        Probe bottom = new Probe(10);
        Table table = Table.builder()
                .addColumnsOfWidth(100, 100)
                .defaultStyle(bordered())
                .add(Cell.builder().add(top).addBottom(bottom)
                        .verticalAlignment(VerticalAlignment.MIDDLE).build())
                .add(Cell.of(PlaceholderContent.ofHeight(100)))
                .build();

        try (PDDocument doc = new PDDocument()) {
            TableDrawer.builder().document(doc).table(table).startY(700).build().draw();
            // free space is 100 - 10 - 10 = 80; MIDDLE pushes the top stack down by 40,
            // while the bottom stack stays pinned
            assertThat(top.tops.get(0)).isCloseTo(700 - 4 - 40, org.assertj.core.data.Offset.offset(0.5f));
            assertThat(bottom.tops.get(0)).isCloseTo(700 - 4 - 100 + 10, org.assertj.core.data.Offset.offset(0.5f));
        }
    }
}
