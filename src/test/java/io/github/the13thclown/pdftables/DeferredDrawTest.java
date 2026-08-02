package io.github.the13thclown.pdftables;

import io.github.the13thclown.pdftables.render.RenderContext;
import io.github.the13thclown.pdftables.style.BorderStyle;
import io.github.the13thclown.pdftables.style.Padding;
import io.github.the13thclown.pdftables.style.Style;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers page identity on {@link RenderContext} and {@link RenderContext#defer}
 * — the mechanism for content whose text only becomes knowable after the table
 * has paginated ("page 3 of 7", "continued on page 5"). Written against the
 * public contract only, like {@link CustomContentTest}.
 */
class DeferredDrawTest {

    private static final Path OUT = Path.of("target", "test-output");

    /** Records the page each of its elements was drawn on. */
    static final class PageProbe implements CellContent {
        private final List<Integer> pageIndexes = new ArrayList<>();
        private final float height;

        PageProbe(float height) {
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
                    pageIndexes.add(ctx.pageIndex());
                }
            });
        }
    }

    /**
     * Draws {@code prefix} immediately and appends a number that is only known
     * once the table is fully paginated — the "continued on page N" pattern.
     * The number is supplied lazily, so the callback reads state written by
     * later pages.
     */
    static final class ForwardReferenceContent implements CellContent {
        private final String prefix;
        private final java.util.function.IntSupplier number;

        ForwardReferenceContent(String prefix, java.util.function.IntSupplier number) {
            this.prefix = prefix;
            this.number = number;
        }

        @Override
        public List<Element> layout(float availableWidth, Style style) {
            float height = style.fontSize() * style.lineSpacing();
            return List.of(new Element() {
                @Override
                public float getHeight() {
                    return height;
                }

                @Override
                public void draw(RenderContext ctx) {
                    ctx.defer(deferred -> text(deferred, prefix + number.getAsInt()));
                }
            });
        }

        private static void text(RenderContext ctx, String value) throws IOException {
            ctx.stream().beginText();
            ctx.stream().setFont(ctx.style().font(), ctx.style().fontSize());
            ctx.stream().setNonStrokingColor(ctx.style().textColor());
            ctx.stream().newLineAtOffset(ctx.x(), ctx.y() + ctx.style().fontSize() * 0.2f);
            ctx.stream().showText(value);
            ctx.stream().endText();
        }
    }

    private static Table.Builder tallTable(Cell first) {
        Table.Builder b = Table.builder()
                .addColumnsOfWidth(200, 200)
                .defaultStyle(Style.builder()
                        .borderAll(BorderStyle.of(0.5f)).padding(Padding.of(4)).fontSize(9).build());
        b.add(first);
        b.add(Cell.of(TextContent.of("row 0")));
        return b;
    }

    @Test
    void pageIndexFollowsTheElementAcrossPageBreaks() throws IOException {
        PageProbe probe = new PageProbe(40);
        Table.Builder b = Table.builder().addColumnsOfWidth(200);
        for (int i = 0; i < 60; i++) {
            b.add(Cell.of(probe));
        }
        try (PDDocument doc = new PDDocument()) {
            TableDrawer.builder().document(doc).table(b.build()).build().draw();
            assertThat(doc.getNumberOfPages()).isGreaterThan(2);
            assertThat(probe.pageIndexes).hasSize(60);
            assertThat(probe.pageIndexes.get(0)).isZero();
            assertThat(probe.pageIndexes.get(probe.pageIndexes.size() - 1)).isEqualTo(doc.getNumberOfPages() - 1);
            // monotonically non-decreasing: elements are drawn page by page
            assertThat(probe.pageIndexes).isSorted();
        }
    }

    @Test
    void pageIndexCountsPagesAlreadyInTheDocument() throws IOException {
        PageProbe probe = new PageProbe(40);
        Table.Builder b = Table.builder().addColumnsOfWidth(200);
        for (int i = 0; i < 5; i++) {
            b.add(Cell.of(probe));
        }
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new org.apache.pdfbox.pdmodel.PDPage());
            doc.addPage(new org.apache.pdfbox.pdmodel.PDPage());
            TableDrawer.builder().document(doc).table(b.build()).build().draw();
            assertThat(probe.pageIndexes).containsOnly(2);
        }
    }

    @Test
    void deferredDrawResolvesAForwardReferenceToALaterPage() throws IOException {
        PageProbe target = new PageProbe(40);
        // the reference sits in the first row; its target only lands on a later page
        Cell reference = Cell.of(new ForwardReferenceContent(
                "See page ", () -> target.pageIndexes.get(0) + 1));

        Table.Builder b = tallTable(reference);
        for (int i = 0; i < 40; i++) {
            b.add(Cell.of(TextContent.of("filler " + i)));
            b.add(Cell.of(TextContent.of("filler " + i)));
        }
        b.add(Cell.of(target));
        b.add(Cell.of(TextContent.of("target row")));

        try (PDDocument doc = new PDDocument()) {
            TableDrawer.builder().document(doc).table(b.build()).build().draw();
            assertThat(doc.getNumberOfPages()).isGreaterThan(1);

            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setStartPage(1);
            stripper.setEndPage(1);
            String firstPage = stripper.getText(doc);
            assertThat(firstPage).contains("See page " + (target.pageIndexes.get(0) + 1));
            assertThat(target.pageIndexes.get(0)).isGreaterThan(0);

            Files.createDirectories(OUT);
            doc.save(OUT.resolve("deferred-forward-reference.pdf").toFile());
        }
    }

    @Test
    void deferredDrawSeesTheFinalPageCount() throws IOException {
        // "page N of TOTAL" is only expressible after the last page exists
        List<String> stamped = new ArrayList<>();
        Table.Builder b = Table.builder().addColumnsOfWidth(200);
        b.add(Cell.of((availableWidth, style) -> List.of(new Element() {
            @Override
            public float getHeight() {
                return 12;
            }

            @Override
            public void draw(RenderContext ctx) {
                ctx.defer(deferred -> stamped.add(
                        (deferred.pageIndex() + 1) + " of " + deferred.document().getNumberOfPages()));
            }
        })));
        for (int i = 0; i < 60; i++) {
            b.add(Cell.of(TextContent.of("row " + i)));
        }
        try (PDDocument doc = new PDDocument()) {
            TableDrawer.builder().document(doc).table(b.build()).build().draw();
            assertThat(doc.getNumberOfPages()).isGreaterThan(1);
            assertThat(stamped).containsExactly("1 of " + doc.getNumberOfPages());
        }
    }

    @Test
    void deferredDrawFromANestedTableTargetsTheOuterPage() throws IOException {
        List<Integer> deferredPages = new ArrayList<>();
        CellContent probe = (availableWidth, style) -> List.of(new Element() {
            @Override
            public float getHeight() {
                return 30;
            }

            @Override
            public void draw(RenderContext ctx) {
                ctx.defer(deferred -> deferredPages.add(deferred.pageIndex()));
            }
        });

        Table.Builder inner = Table.builder().addColumnsOfWidth(150);
        for (int i = 0; i < 30; i++) {
            inner.add(Cell.of(probe));
        }
        Table outer = Table.builder()
                .addColumnsOfWidth(200)
                .add(Cell.of(TableContent.of(inner.build())))
                .build();

        try (PDDocument doc = new PDDocument()) {
            TableDrawer.builder().document(doc).table(outer).build().draw();
            assertThat(doc.getNumberOfPages()).isGreaterThan(1);
            assertThat(deferredPages).hasSize(30).isSorted();
            assertThat(deferredPages.get(deferredPages.size() - 1)).isEqualTo(doc.getNumberOfPages() - 1);
        }
    }
}
