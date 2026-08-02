package io.github.the13thclown.pdftables;

import io.github.the13thclown.pdftables.render.RenderContext;
import io.github.the13thclown.pdftables.style.BorderStyle;
import io.github.the13thclown.pdftables.style.Padding;
import io.github.the13thclown.pdftables.style.Style;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers {@link Cell.Builder#onEachPageSlice}: content repeated at the bottom
 * of every page slice a cell is cut into — "continued on the next page", or a
 * pointer to something that landed on another page.
 */
class PageSliceContentTest {

    /** Records the page and box it was drawn at, once per slice. */
    static final class SliceProbe implements CellContent {
        private final List<Integer> pages = new ArrayList<>();
        private final List<Float> bottoms = new ArrayList<>();

        @Override
        public List<Element> layout(float availableWidth, Style style) {
            return List.of(new Element() {
                @Override
                public float getHeight() {
                    return 8;
                }

                @Override
                public void draw(RenderContext ctx) {
                    pages.add(ctx.pageIndex());
                    bottoms.add(ctx.y());
                }
            });
        }
    }

    private static Table.Builder tableWith(Cell cell) {
        return Table.builder()
                .addColumnsOfWidth(200)
                .defaultStyle(Style.builder()
                        .borderAll(BorderStyle.of(0.5f)).padding(Padding.of(4)).fontSize(9).build())
                .add(cell);
    }

    @Test
    void aCellFittingOnePageGetsTheContentOnce() throws IOException {
        SliceProbe probe = new SliceProbe();
        Table table = tableWith(Cell.builder()
                .add(TextContent.of("short"))
                .onEachPageSlice(probe)
                .build()).build();
        try (PDDocument doc = new PDDocument()) {
            TableDrawer.builder().document(doc).table(table).build().draw();
            assertThat(doc.getNumberOfPages()).isEqualTo(1);
            assertThat(probe.pages).containsExactly(0);
        }
    }

    @Test
    void aCellCutAcrossPagesGetsTheContentOnEverySlice() throws IOException {
        SliceProbe probe = new SliceProbe();
        StringBuilder tall = new StringBuilder();
        for (int i = 0; i < 400; i++) {
            tall.append("line ").append(i).append('\n');
        }
        Table table = tableWith(Cell.builder()
                .add(TextContent.of(tall.toString()))
                .onEachPageSlice(probe)
                .build()).build();
        try (PDDocument doc = new PDDocument()) {
            TableDrawer.builder().document(doc).table(table).build().draw();
            int pages = doc.getNumberOfPages();
            assertThat(pages).isGreaterThan(3);
            // exactly one marker per page the cell touches, in page order
            assertThat(probe.pages).hasSize(pages).isSorted();
            for (int i = 0; i < pages; i++) {
                assertThat(probe.pages.get(i)).isEqualTo(i);
            }
            Files.createDirectories(Path.of("target", "test-output"));
            doc.save(Path.of("target", "test-output", "page-slice-content.pdf").toFile());
        }
    }

    @Test
    void theContentSitsInsideTheBottomOfItsSlice() throws IOException {
        SliceProbe probe = new SliceProbe();
        Table table = tableWith(Cell.builder()
                .add(TextContent.of("short"))
                .onEachPageSlice(probe)
                .build()).build();
        try (PDDocument doc = new PDDocument()) {
            float bottom = TableDrawer.builder().document(doc).table(table)
                    .startY(700).build().draw();
            // drawn above the cell's bottom edge, inset by the 4pt bottom padding
            assertThat(probe.bottoms.get(0)).isGreaterThan(bottom);
            assertThat(probe.bottoms.get(0)).isLessThan(bottom + 20);
        }
    }

    @Test
    void aCellWithoutSliceContentDrawsNothingExtra() throws IOException {
        Table table = tableWith(Cell.of(TextContent.of("plain"))).build();
        try (PDDocument doc = new PDDocument()) {
            assertThat(TableDrawer.builder().document(doc).table(table).build().draw())
                    .isGreaterThan(0);
        }
    }
}
