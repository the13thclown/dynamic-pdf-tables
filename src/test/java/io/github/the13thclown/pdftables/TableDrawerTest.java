package io.github.the13thclown.pdftables;

import io.github.the13thclown.pdftables.style.BorderStyle;
import io.github.the13thclown.pdftables.style.HorizontalAlignment;
import io.github.the13thclown.pdftables.style.Style;
import io.github.the13thclown.pdftables.style.VerticalAlignment;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Render smoke tests: draw representative tables, assert structural facts
 * (page counts, clean saves) and write the PDFs to target/test-output for
 * human inspection.
 */
class TableDrawerTest {

    private static final Path OUT = Path.of("target", "test-output");

    @BeforeAll
    static void createOutputDir() throws IOException {
        Files.createDirectories(OUT);
    }

    private static Style bordered() {
        return Style.builder().borderAll(BorderStyle.of(1)).build();
    }

    private static void save(PDDocument doc, String name) throws IOException {
        doc.save(OUT.resolve(name).toFile());
        doc.close();
    }

    @Test
    void simpleGridDrawsOnOnePage() throws IOException {
        Table table = Table.builder()
                .addColumnsOfWidth(150, 150, 150)
                .defaultStyle(bordered())
                .add(Cell.builder().add(PlaceholderContent.ofHeight(20)).paddingAll(5)
                        .backgroundColor(new Color(230, 230, 230)).build())
                .add(Cell.builder().add(PlaceholderContent.ofSize(80, 20)).paddingAll(5)
                        .horizontalAlignment(HorizontalAlignment.CENTER).build())
                .add(Cell.builder().add(PlaceholderContent.ofSize(80, 20)).paddingAll(5)
                        .horizontalAlignment(HorizontalAlignment.RIGHT).build())
                .add(Cell.builder().add(PlaceholderContent.ofHeight(60)).paddingAll(5).build())
                .add(Cell.builder().add(PlaceholderContent.ofSize(60, 25)).paddingAll(5)
                        .verticalAlignment(VerticalAlignment.MIDDLE).build())
                .add(Cell.builder().add(PlaceholderContent.ofSize(60, 25)).paddingAll(5)
                        .verticalAlignment(VerticalAlignment.BOTTOM).build())
                .build();

        try (PDDocument doc = new PDDocument()) {
            float bottom = TableDrawer.builder().document(doc).table(table).build().draw();
            assertThat(doc.getNumberOfPages()).isEqualTo(1);
            assertThat(bottom).isLessThan(792);
            save(doc, "simple-grid.pdf");
        }
    }

    @Test
    void spansDrawOnOnePage() throws IOException {
        Table table = Table.builder()
                .addColumnsOfWidth(120, 120, 120)
                .defaultStyle(bordered())
                .add(Cell.builder().add(PlaceholderContent.ofHeight(25)).colSpan(3).paddingAll(4)
                        .backgroundColor(new Color(200, 220, 200)).build())
                .add(Cell.builder().add(PlaceholderContent.ofHeight(30)).rowSpan(2).paddingAll(4)
                        .backgroundColor(new Color(220, 200, 200)).build())
                .add(Cell.builder().add(PlaceholderContent.ofHeight(30)).paddingAll(4).build())
                .add(Cell.builder().add(PlaceholderContent.ofHeight(30)).paddingAll(4).build())
                .add(Cell.builder().add(PlaceholderContent.ofHeight(30)).colSpan(2).paddingAll(4).build())
                .build();

        try (PDDocument doc = new PDDocument()) {
            TableDrawer.builder().document(doc).table(table).build().draw();
            assertThat(doc.getNumberOfPages()).isEqualTo(1);
            save(doc, "spans.pdf");
        }
    }

    @Test
    void longTableFlowsAcrossPages() throws IOException {
        Table.Builder b = Table.builder()
                .addColumnsOfWidth(160, 160, 160)
                .defaultStyle(bordered());
        for (int i = 0; i < 90; i++) {
            b.add(Cell.builder().add(PlaceholderContent.ofHeight(24)).paddingAll(3).build());
        }
        // 30 derived rows * 30pt = 900pt > one A4 page capacity (~742pt)
        try (PDDocument doc = new PDDocument()) {
            TableDrawer.builder().document(doc).table(b.build()).build().draw();
            assertThat(doc.getNumberOfPages()).isEqualTo(2);
            save(doc, "multi-page.pdf");
        }
    }

    @Test
    void repeatingHeaderDrawsOnEveryPage() throws IOException {
        Table.Builder b = Table.builder()
                .addColumnsOfWidth(160, 160, 160)
                .defaultStyle(bordered())
                .headerRowCount(1);
        for (int c = 0; c < 3; c++) {
            b.add(Cell.builder().add(PlaceholderContent.ofHeight(20)).paddingAll(5)
                    .backgroundColor(new Color(180, 195, 230)).build());
        }
        for (int i = 0; i < 120; i++) {
            b.add(Cell.builder().add(PlaceholderContent.ofHeight(24)).paddingAll(3).build());
        }
        try (PDDocument doc = new PDDocument()) {
            TableDrawer.builder().document(doc).table(b.build()).build().draw();
            assertThat(doc.getNumberOfPages()).isGreaterThanOrEqualTo(2);
            save(doc, "repeating-header.pdf");
        }
    }

    @Test
    void tallCellSplitsMidCellAcrossPages() throws IOException {
        Cell.Builder tall = Cell.builder().paddingAll(6)
                .backgroundColor(new Color(235, 235, 215));
        for (int i = 0; i < 30; i++) {
            tall.add(PlaceholderContent.ofHeight(40));
        }
        Table table = Table.builder()
                .addColumnsOfWidth(200, 200)
                .defaultStyle(bordered())
                .add(tall.build())
                .add(Cell.builder().add(PlaceholderContent.ofHeight(30)).paddingAll(6).build())
                .build();

        // 30 elements * 40pt = 1200pt in one cell: must split mid-cell over 2 pages
        try (PDDocument doc = new PDDocument()) {
            TableDrawer.builder().document(doc).table(table).build().draw();
            assertThat(doc.getNumberOfPages()).isEqualTo(2);
            save(doc, "mid-cell-split.pdf");
        }
    }

    private static Cell cell4() {
        Cell.Builder cell4 = Cell.builder().paddingAll(10)
                .backgroundColor(new Color(235, 245, 235));
        for (int i = 0; i < 30; i++) {
            cell4.add(i % 3 == 2
                    ? PlaceholderContent.ofHeight(120)
                    : PlaceholderContent.ofSize(120, 120));
        }
        return cell4.build();
    }

    @Test
    void sketchScenarioRowSpanCellBreaksDownToNextPage() throws IOException {
        // From the notebook sketch: cell "1" normal, cell "2" with lots of
        // elements spanning 3 rows, cells "3" and "4" stacked beside it, and a
        // long bar "5" below. The page break cuts through the rowspan cell:
        // its last element sits on the break line and passes down whole, the
        // bar's row moves to the next page entirely.
        // cell "2" gets five bands of arbitrarily scattered shapes (~2200pt),
        // placed with addAt like the sketch, so the rowspan keeps breaking
        // down page after page while the arrangement is preserved
        Cell.Builder cell2 = Cell.builder().rowSpan(3).paddingAll(10)
                .horizontalAlignment(HorizontalAlignment.CENTER);
        for (int i = 0; i < 5; i++) {
            float base = i * 450;
            cell2.addAt(10, base, PlaceholderContent.ofSize(90, 90))
                    .addAt(130, base + 30, PlaceholderContent.ofSize(140, 60))
                    .addAt(200, base + 110, PlaceholderContent.ofSize(40, 120))
                    .addAt(20, base + 120, PlaceholderContent.ofSize(120, 70))
                    .addAt(60, base + 240, PlaceholderContent.ofSize(180, 60))
                    .addAt(0, base + 330, PlaceholderContent.ofSize(80, 80));
        }
        Table table = Table.builder()
                .addColumnsOfWidth(200, 300)
                .defaultStyle(bordered())
                // row 0: "1" + "2" (rowSpan 3, many elements)
                .add(Cell.builder().paddingAll(10)
                        .add(PlaceholderContent.ofSize(60, 60)).build())
                .add(cell2.build())
                // rows 1 and 2 of the left column: "3" and "4" — cell 4 is
                // itself packed with a tall flowing stack, so page cuts slice
                // through it AND the rowspan cell at the same time
                .add(Cell.builder().paddingAll(10)
                        .add(PlaceholderContent.ofSize(160, 160)).build())
                .add(cell4())
                // row 3: "5", the long bar
                .add(Cell.builder().colSpan(2).paddingAll(10)
                        .add(PlaceholderContent.ofHeight(80)).build())
                .build();

        // cell "4"'s flow stack (30 x 120pt = 3620pt with padding) now dwarfs
        // cell "2"'s ~2230pt arrangement: no rowspan deficit at all, cell 4
        // drives the table height, and after cell 2 runs out of elements its
        // empty box continues alongside until cell 4 finishes
        try (PDDocument doc = new PDDocument()) {
            TableDrawer.builder().document(doc).table(table)
                    .startY(750).endY(50)
                    .build().draw();
            assertThat(doc.getNumberOfPages()).isEqualTo(6);
            save(doc, "sketch-scenario.pdf");
        }
    }

    @Test
    void closedBordersVariantDrawsCutBoxesClosedOnEveryPage() throws IOException {
        // same table as the mid-cell split test, but drawn with
        // closeBordersAtPageBreak: the cut gets a bottom border on page 1 and
        // the continuation a top border on page 2
        Cell.Builder tall = Cell.builder().paddingAll(6)
                .backgroundColor(new Color(235, 235, 215));
        for (int i = 0; i < 30; i++) {
            tall.add(PlaceholderContent.ofHeight(40));
        }
        Table table = Table.builder()
                .addColumnsOfWidth(200, 200)
                .defaultStyle(bordered())
                .add(tall.build())
                .add(Cell.builder().add(PlaceholderContent.ofHeight(30)).paddingAll(6).build())
                .build();

        try (PDDocument doc = new PDDocument()) {
            TableDrawer.builder().document(doc).table(table)
                    .closeBordersAtPageBreak(true)
                    .build().draw();
            assertThat(doc.getNumberOfPages()).isEqualTo(2);
            save(doc, "closed-borders.pdf");
        }
    }

    @Test
    void twoMultiPageTablesDrawSideBySideOnSharedPages() throws IOException {
        // easytable #177: independent multi-page tables sharing one page
        // sequence. The second drawer's supplier hands back the pages the
        // first drawer created; they are reused, not re-added.
        Table.Builder left = Table.builder().addColumnOfWidth(200).defaultStyle(bordered());
        Table.Builder right = Table.builder().addColumnOfWidth(180).defaultStyle(bordered());
        for (int i = 0; i < 60; i++) {
            left.add(Cell.builder().add(PlaceholderContent.ofHeight(26)).paddingAll(2).build());
        }
        for (int i = 0; i < 40; i++) {
            right.add(Cell.builder().add(PlaceholderContent.ofSize(120, 41)).paddingAll(2)
                    .backgroundColor(new Color(225, 240, 225)).build());
        }

        try (PDDocument doc = new PDDocument()) {
            TableDrawer.builder().document(doc).table(left.build())
                    .startX(50).build().draw();
            int pagesAfterLeft = doc.getNumberOfPages();
            assertThat(pagesAfterLeft).isGreaterThanOrEqualTo(2);

            int[] next = {1};
            TableDrawer.builder().document(doc).table(right.build())
                    .startX(320)
                    .page(doc.getPage(0))
                    .pageSupplier(() -> next[0] < doc.getNumberOfPages()
                            ? doc.getPage(next[0]++)
                            : new org.apache.pdfbox.pdmodel.PDPage(org.apache.pdfbox.pdmodel.common.PDRectangle.A4))
                    .build().draw();

            // the right table reused the left table's pages
            assertThat(doc.getNumberOfPages()).isEqualTo(pagesAfterLeft);
            save(doc, "side-by-side.pdf");
        }
    }

    @Test
    void spansWithLargeContentSurvivePageBreaks() throws IOException {
        // easytable #96: rowspan/colspan combined with large content breaks
        // their layout. Stress the same shape here across several pages.
        Table.Builder b = Table.builder()
                .addColumnsOfWidth(120, 120, 120, 120)
                .defaultStyle(bordered())
                .headerRowCount(1)
                .add(Cell.builder().colSpan(4).paddingAll(5)
                        .backgroundColor(new Color(190, 205, 235))
                        .add(PlaceholderContent.ofHeight(24)).build());
        for (int block = 0; block < 4; block++) {
            Cell.Builder tall = Cell.builder().rowSpan(4).paddingAll(5);
            for (int i = 0; i < 12; i++) {
                tall.add(PlaceholderContent.ofHeight(38));
            }
            b.add(tall.build());
            Cell.Builder wide = Cell.builder().rowSpan(2).colSpan(2).paddingAll(5);
            for (int i = 0; i < 5; i++) {
                wide.add(PlaceholderContent.ofHeight(30));
            }
            b.add(wide.build());
            for (int i = 0; i < 2; i++) {
                b.add(Cell.builder().add(PlaceholderContent.ofHeight(40)).paddingAll(5).build());
            }
            b.add(Cell.builder().colSpan(3).paddingAll(5)
                    .add(PlaceholderContent.ofHeight(35)).build());
            for (int i = 0; i < 4; i++) {
                b.add(Cell.builder().add(PlaceholderContent.ofHeight(28)).paddingAll(5).build());
            }
        }
        try (PDDocument doc = new PDDocument()) {
            TableDrawer.builder().document(doc).table(b.build()).build().draw();
            assertThat(doc.getNumberOfPages()).isGreaterThanOrEqualTo(3);
            save(doc, "spans-stress.pdf");
        }
    }

    @Test
    void headerTallerThanPageCapacityThrows() throws IOException {
        Table table = Table.builder()
                .addColumnOfWidth(200)
                .headerRowCount(1)
                .add(Cell.of(PlaceholderContent.ofHeight(700)))
                .add(Cell.of(PlaceholderContent.ofHeight(300)))
                .build();
        try (PDDocument doc = new PDDocument()) {
            TableDrawer drawer = TableDrawer.builder().document(doc).table(table)
                    .startY(400).build();
            assertThatThrownBy(drawer::draw)
                    .isInstanceOf(TableLayoutException.class)
                    .hasMessageContaining("Header");
        }
    }

    @Test
    void nestedTableSplitsAcrossPagesAtInnerRowBoundaries() throws IOException {
        // deepest level: a small 2x2 grid used inside the inner table
        Table innermost = Table.builder()
                .addColumnOfRelativeWidth(1).addColumnOfRelativeWidth(1)
                .defaultStyle(Style.builder().borderAll(BorderStyle.of(0.5f)).build())
                .add(Cell.of(PlaceholderContent.ofHeight(14)))
                .add(Cell.of(PlaceholderContent.ofHeight(14)))
                .add(Cell.of(PlaceholderContent.ofHeight(14)))
                .add(Cell.of(PlaceholderContent.ofHeight(14)))
                .build();

        // inner table: 30 rows, relative columns fill the outer cell; one row
        // holds the innermost table (depth-2 nesting)
        Table.Builder inner = Table.builder()
                .addColumnOfRelativeWidth(1).addColumnOfRelativeWidth(2)
                .defaultStyle(bordered())
                .rowStyler(row -> row % 2 == 0
                        ? Style.builder().backgroundColor(new Color(235, 235, 245)).build()
                        : null);
        for (int i = 0; i < 30; i++) {
            inner.add(Cell.builder().add(PlaceholderContent.ofHeight(28)).paddingAll(3).build());
            if (i == 5) {
                inner.add(Cell.builder().add(TableContent.of(innermost)).paddingAll(3).build());
            } else {
                inner.add(Cell.builder().add(PlaceholderContent.ofSize(90, 28)).paddingAll(3).build());
            }
        }

        Table outer = Table.builder()
                .addColumnsOfWidth(120, 330)
                .defaultStyle(bordered())
                .add(Cell.builder().add(PlaceholderContent.ofSize(80, 60)).paddingAll(8).build())
                .add(Cell.builder().add(TableContent.of(inner.build())).paddingAll(8).build())
                .build();

        try (PDDocument doc = new PDDocument()) {
            TableDrawer.builder().document(doc).table(outer).build().draw();
            // ~30 inner rows of 34pt+ inside one outer cell: must split across pages
            assertThat(doc.getNumberOfPages()).isEqualTo(2);
            save(doc, "nested-table.pdf");
        }
    }

    @Test
    void textTableRendersAndSplitsLineByLineAcrossPages() throws IOException {
        Table.Builder b = Table.builder()
                .addColumnsOfWidth(90, 220, 190)
                .defaultStyle(Style.builder().borderAll(BorderStyle.of(0.7f))
                        .padding(io.github.the13thclown.pdftables.style.Padding.of(6)).build())
                .headerRowCount(1)
                .rowStyler(row -> row > 0 && row % 2 == 0
                        ? Style.builder().backgroundColor(new Color(242, 244, 250)).build()
                        : null)
                .add(headerCell("#"))
                .add(headerCell("Item"))
                .add(headerCell("Description"));
        String longText = "A reasonably long description that has to wrap over several lines "
                + "inside its cell, demonstrating that text flows, wraps at the content width "
                + "and participates in pagination one line at a time.";
        for (int i = 1; i <= 25; i++) {
            b.add(Cell.builder().add(TextContent.of(String.valueOf(i)))
                    .horizontalAlignment(HorizontalAlignment.RIGHT).build());
            b.add(Cell.of(TextContent.of("Item number " + i)));
            b.add(Cell.of(TextContent.builder(i % 5 == 0 ? longText : "Short note.")
                    .fontSize(9).color(new Color(60, 60, 60)).build()));
        }
        try (PDDocument doc = new PDDocument()) {
            TableDrawer.builder().document(doc).table(b.build()).build().draw();
            assertThat(doc.getNumberOfPages()).isGreaterThanOrEqualTo(2);
            save(doc, "text-table.pdf");
        }
    }

    private static Cell headerCell(String label) {
        return Cell.builder()
                .add(TextContent.builder(label).fontSize(11).color(Color.WHITE)
                        .font(new org.apache.pdfbox.pdmodel.font.PDType1Font(
                                org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName.HELVETICA_BOLD))
                        .build())
                .backgroundColor(new Color(52, 74, 120))
                .build();
    }

    @Test
    void imageTableRendersWithRepeatedHeaderLogoAcrossPages() throws IOException {
        ImageContent logo = ImageContent.builder(sampleImage(120, 40, new Color(52, 74, 120), new Color(120, 160, 220)))
                .height(16).build();
        Table.Builder b = Table.builder()
                .addColumnsOfWidth(140, 180, 180)
                .defaultStyle(Style.builder().borderAll(BorderStyle.of(0.7f))
                        .padding(io.github.the13thclown.pdftables.style.Padding.of(6)).build())
                .headerRowCount(1)
                .add(Cell.builder().add(logo).build())
                .add(headerCell("Picture"))
                .add(headerCell("Caption"));
        for (int i = 1; i <= 18; i++) {
            b.add(Cell.builder().add(TextContent.of("Row " + i)).build());
            b.add(Cell.builder()
                    .add(ImageContent.builder(sampleImage(90, 60,
                            new Color(40 + i * 10, 80, 200 - i * 8), new Color(230, 240, 250)))
                            .width(70).build())
                    .horizontalAlignment(HorizontalAlignment.CENTER)
                    .build());
            b.add(Cell.of(TextContent.builder("A caption describing picture number " + i
                    + " in a couple of wrapped lines of text.").fontSize(9).build()));
        }
        try (PDDocument doc = new PDDocument()) {
            TableDrawer.builder().document(doc).table(b.build()).build().draw();
            assertThat(doc.getNumberOfPages()).isGreaterThanOrEqualTo(2);
            save(doc, "image-table.pdf");
        }
    }

    private static java.awt.image.BufferedImage sampleImage(int w, int h, Color from, Color to) {
        java.awt.image.BufferedImage img =
                new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = img.createGraphics();
        g.setPaint(new java.awt.GradientPaint(0, 0, from, w, h, to));
        g.fillRect(0, 0, w, h);
        g.setColor(Color.WHITE);
        g.fillOval(w / 4, h / 4, w / 2, h / 2);
        g.dispose();
        return img;
    }

    @Test
    void richTextRendersMixedStylesInOneParagraph() throws IOException {
        org.apache.pdfbox.pdmodel.font.PDType1Font bold =
                new org.apache.pdfbox.pdmodel.font.PDType1Font(
                        org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName.HELVETICA_BOLD);
        Table table = Table.builder()
                .addColumnsOfWidth(300, 160)
                .defaultStyle(Style.builder().borderAll(BorderStyle.of(0.7f))
                        .padding(io.github.the13thclown.pdftables.style.Padding.of(8)).build())
                .add(Cell.of(RichTextContent.builder().fontSize(10)
                        .add("Invoice ")
                        .add(RichTextContent.fragment("2026-0042").font(bold))
                        .add(" is payable within ")
                        .add(RichTextContent.fragment("14 days").font(bold).color(new Color(180, 40, 40)))
                        .add(". Late payments accrue interest at the statutory rate; this sentence "
                                + "exists mostly so the paragraph wraps over several lines with the "
                                + "styled spans flowing naturally inside it.")
                        .build()))
                .add(Cell.builder().add(RichTextContent.builder()
                                .add("Total: ")
                                .add(RichTextContent.fragment("1.234,56 EUR").font(bold).fontSize(14))
                                .build())
                        .horizontalAlignment(HorizontalAlignment.RIGHT)
                        .verticalAlignment(io.github.the13thclown.pdftables.style.VerticalAlignment.MIDDLE)
                        .build())
                .build();
        try (PDDocument doc = new PDDocument()) {
            TableDrawer.builder().document(doc).table(table).build().draw();
            assertThat(doc.getNumberOfPages()).isEqualTo(1);
            save(doc, "rich-text.pdf");
        }
    }

    @Test
    void justifiedTextStretchesWrappedLines() throws IOException {
        String para = "Justified text stretches every wrapped line to the full content width "
                + "by widening the gaps between words, while the last line of the paragraph "
                + "stays at its natural width like in any book.";
        Table table = Table.builder()
                .addColumnsOfWidth(220, 220)
                .defaultStyle(Style.builder().borderAll(BorderStyle.of(0.7f))
                        .padding(io.github.the13thclown.pdftables.style.Padding.of(8))
                        .fontSize(10).build())
                .add(Cell.builder().add(TextContent.of(para))
                        .horizontalAlignment(HorizontalAlignment.JUSTIFY).build())
                .add(Cell.of(TextContent.of(para)))     // left-aligned for comparison
                .add(Cell.builder().add(RichTextContent.builder()
                                .add("Rich text justifies too: ")
                                .add(RichTextContent.fragment("styled spans").fontSize(12))
                                .add(" flow inside the stretched lines without breaking the "
                                        + "shared baseline or the word gaps around them.")
                                .build())
                        .horizontalAlignment(HorizontalAlignment.JUSTIFY).build())
                .add(Cell.of(TextContent.of("Short.")))
                .build();
        try (PDDocument doc = new PDDocument()) {
            TableDrawer.builder().document(doc).table(table).build().draw();
            save(doc, "justified-text.pdf");
        }
    }

    @Test
    void verticalTextRendersRotatedInNarrowColumns() throws IOException {
        Table.Builder b = Table.builder()
                .addColumnsOfWidth(150, 30, 30, 30)
                .defaultStyle(Style.builder().borderAll(BorderStyle.of(0.7f))
                        .padding(io.github.the13thclown.pdftables.style.Padding.of(4))
                        .fontSize(9).build())
                .add(Cell.of(TextContent.of("Criterion")))
                .add(Cell.builder().add(VerticalTextContent.of("First quarter"))
                        .horizontalAlignment(HorizontalAlignment.CENTER).build())
                .add(Cell.builder().add(VerticalTextContent.of("Second quarter"))
                        .horizontalAlignment(HorizontalAlignment.CENTER).build())
                .add(Cell.builder().add(VerticalTextContent.of("Q3\nand Q4"))
                        .horizontalAlignment(HorizontalAlignment.CENTER).build());
        for (int i = 1; i <= 3; i++) {
            b.add(Cell.of(TextContent.of("Row " + i)));
            for (int c = 0; c < 3; c++) {
                b.add(Cell.builder().add(PlaceholderContent.ofSize(14, 14))
                        .horizontalAlignment(HorizontalAlignment.CENTER).build());
            }
        }
        try (PDDocument doc = new PDDocument()) {
            TableDrawer.builder().document(doc).table(b.build()).build().draw();
            assertThat(doc.getNumberOfPages()).isEqualTo(1);
            save(doc, "vertical-text.pdf");
        }
    }

    @Test
    void overflowColumnsFillThePageBeforeStartingANewOne() throws IOException {
        Table.Builder b = Table.builder()
                .addColumnsOfWidth(60, 90)
                .defaultStyle(Style.builder().borderAll(BorderStyle.of(0.7f))
                        .padding(io.github.the13thclown.pdftables.style.Padding.of(4))
                        .fontSize(9).build())
                .headerRowCount(1)
                .add(headerCell("#"))
                .add(headerCell("Value"));
        for (int i = 1; i <= 120; i++) {
            b.add(Cell.of(TextContent.of(String.valueOf(i))));
            b.add(Cell.of(TextContent.of("Value " + i)));
        }
        // ~121 rows of ~19pt: far too tall for one page, but three 150pt-wide
        // column regions fit side by side and absorb most of it on page 1
        try (PDDocument doc = new PDDocument()) {
            TableDrawer.builder().document(doc).table(b.build())
                    .overflowColumns(3, 25)
                    .build().draw();
            assertThat(doc.getNumberOfPages()).isEqualTo(2);
            save(doc, "overflow-columns.pdf");
        }
    }

    @Test
    void giantSplittableElementFlowsAcrossPagesInsteadOfThrowing() throws IOException {
        // a placeholder taller than two pages: splitAt cuts it at every page
        // boundary, filling each page completely — nothing thrown, no space lost
        Table table = Table.builder()
                .addColumnsOfWidth(200, 120)
                .defaultStyle(bordered())
                .add(Cell.builder().add(PlaceholderContent.ofHeight(2000)).paddingAll(5).build())
                .add(Cell.builder().add(PlaceholderContent.ofHeight(30)).paddingAll(5).build())
                .build();
        try (PDDocument doc = new PDDocument()) {
            TableDrawer.builder().document(doc).table(table).build().draw();
            assertThat(doc.getNumberOfPages()).isEqualTo(3);
            save(doc, "giant-element-split.pdf");
        }
    }

    @Test
    void giantImageSplitsSeamlesslyAcrossPages() throws IOException {
        // a 1600pt-tall gradient image: the clip-window split draws each page's
        // slice so the pieces line up seamlessly across the break
        Table table = Table.builder()
                .addColumnsOfWidth(260, 140)
                .defaultStyle(bordered())
                .add(Cell.builder().paddingAll(6)
                        .add(ImageContent.builder(sampleImage(240, 1500, new Color(40, 70, 160), new Color(240, 150, 60)))
                                .width(240).build())
                        .build())
                .add(Cell.of(TextContent.of("A caption beside a picture far taller than a page.")))
                .build();
        try (PDDocument doc = new PDDocument()) {
            TableDrawer.builder().document(doc).table(table).build().draw();
            assertThat(doc.getNumberOfPages()).isGreaterThanOrEqualTo(3);
            save(doc, "giant-image-split.pdf");
        }
    }

    @Test
    void nestedTableWithGiantInnerElementSplitsThroughBothLevels() throws IOException {
        // the inner table's single row block is ~1560pt tall: the outer cut
        // splits the block, the block asks its crossing inner elements to
        // split, and both the tall image and the tall placeholder flow across
        // three pages inside the nested cell
        Table inner = Table.builder()
                .addColumnOfRelativeWidth(1).addColumnOfRelativeWidth(1)
                .defaultStyle(Style.builder().borderAll(BorderStyle.of(0.5f))
                        .padding(io.github.the13thclown.pdftables.style.Padding.of(4)).build())
                .add(Cell.builder()
                        .add(ImageContent.builder(sampleImage(150, 1500,
                                new Color(30, 90, 60), new Color(220, 240, 200))).width(150).build())
                        .build())
                .add(Cell.of(PlaceholderContent.ofHeight(1550)))
                .build();
        Table outer = Table.builder()
                .addColumnsOfWidth(360, 120)
                .defaultStyle(bordered())
                .add(Cell.builder().add(TableContent.of(inner)).paddingAll(8).build())
                .add(Cell.of(TextContent.of("Beside a nested table far taller than a page.")))
                .build();
        try (PDDocument doc = new PDDocument()) {
            TableDrawer.builder().document(doc).table(outer).build().draw();
            assertThat(doc.getNumberOfPages()).isEqualTo(3);
            save(doc, "nested-giant-split.pdf");
        }
    }

    @Test
    void showcaseDocumentExercisesTheWholeFeatureSet() throws IOException {
        org.apache.pdfbox.pdmodel.font.PDType1Font bold =
                new org.apache.pdfbox.pdmodel.font.PDType1Font(
                        org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName.HELVETICA_BOLD);
        Color night = new Color(38, 54, 89);
        Color accent = new Color(180, 60, 45);

        Table.Builder b = Table.builder()
                .addColumnsOfWidth(40, 190, 135, 150)
                .defaultStyle(Style.builder()
                        .borderAll(BorderStyle.of(0.6f, new Color(120, 128, 145)))
                        .padding(io.github.the13thclown.pdftables.style.Padding.of(7))
                        .fontSize(10)                             // table-wide text default
                        .build())
                .headerRowCount(1)
                .rowStyler(row -> row >= 2 && row % 2 == 0
                        ? Style.builder().backgroundColor(new Color(242, 244, 249)).build()
                        : null)
                .columnStyler(col -> col == 3
                        ? Style.builder().backgroundColor(new Color(248, 246, 238)).build()
                        : null);

        // repeating header: logo + bold white title on a dark band
        b.add(Cell.builder().colSpan(4)
                .backgroundColor(night)
                .add(ImageContent.builder(sampleImage(120, 30, accent, night)).height(16).build())
                .add(TextContent.builder("QUARTERLY PRODUCT REPORT").font(bold).fontSize(16)
                        .color(Color.WHITE).build())
                .horizontalAlignment(HorizontalAlignment.CENTER)
                .build());

        // intro: justified rich text across the full width
        b.add(Cell.builder().colSpan(4)
                .horizontalAlignment(HorizontalAlignment.JUSTIFY)
                .add(RichTextContent.builder()
                        .add("This document is produced by a single test of ")
                        .add(RichTextContent.fragment("dynamic-pdf-tables").font(bold))
                        .add(" and exercises the whole feature set at once: a repeating image header, "
                                + "justified rich text, vertical category labels spanning rows, nested "
                                + "stat tables, images, zebra striping via the row styler, a tinted "
                                + "column via the column styler, and page breaks cutting through "
                                + "whatever happens to be at the page boundary — cells continue "
                                + "with open borders and finish on the next page.")
                        .build())
                .build());

        String[] categories = {"ALPHA", "BETA", "GAMMA"};
        int product = 0;
        for (String category : categories) {
            for (int i = 0; i < 4; i++) {
                product++;
                if (i == 0) {
                    b.add(Cell.builder().rowSpan(4)
                            .add(VerticalTextContent.builder(category).font(bold).build())
                            .horizontalAlignment(HorizontalAlignment.CENTER)
                            .verticalAlignment(io.github.the13thclown.pdftables.style.VerticalAlignment.MIDDLE)
                            .backgroundColor(new Color(230, 233, 240))
                            .build());
                }
                b.add(Cell.of(RichTextContent.builder()
                        .add(RichTextContent.fragment("Product " + product + "\n").font(bold).fontSize(12))
                        .add(RichTextContent.fragment("A short description that wraps over a couple "
                                + "of lines and inherits the table-wide 10pt default.")
                                .color(new Color(90, 90, 90)))
                        .build()));
                b.add(Cell.builder()
                        .add(ImageContent.builder(sampleImage(90, 54,
                                new Color(40 + product * 12, 90, 190 - product * 9),
                                new Color(235, 240, 248))).width(112).build())
                        .horizontalAlignment(HorizontalAlignment.CENTER)
                        .build());
                Table stats = Table.builder()
                        .addColumnOfRelativeWidth(2).addColumnOfRelativeWidth(1)
                        .defaultStyle(Style.builder()
                                .borderAll(BorderStyle.of(0.4f, new Color(170, 175, 190)))
                                .padding(io.github.the13thclown.pdftables.style.Padding.of(4))
                                .fontSize(9).build())
                        .add(Cell.of(TextContent.of("Units")))
                        .add(Cell.builder().add(TextContent.of(String.valueOf(120 + product * 37)))
                                .horizontalAlignment(HorizontalAlignment.RIGHT).build())
                        .add(Cell.of(TextContent.of("Revenue")))
                        .add(Cell.builder().add(TextContent.of((2 + product) + "." + (product * 7 % 100) + "k"))
                                .horizontalAlignment(HorizontalAlignment.RIGHT).build())
                        .add(Cell.of(TextContent.of("Trend")))
                        .add(Cell.builder().add(TextContent.builder(product % 3 == 0 ? "-" : "+").font(bold)
                                        .color(product % 3 == 0 ? accent : new Color(40, 120, 60)).build())
                                .horizontalAlignment(HorizontalAlignment.RIGHT).build())
                        .build();
                b.add(Cell.of(TableContent.of(stats)));
            }
        }

        // totals row: right-aligned mixed-size rich text
        b.add(Cell.builder().colSpan(4)
                .horizontalAlignment(HorizontalAlignment.RIGHT)
                .backgroundColor(new Color(230, 233, 240))
                .add(RichTextContent.builder()
                        .add("Grand total: ")
                        .add(RichTextContent.fragment("187.4k EUR").font(bold).fontSize(14))
                        .build())
                .build());

        try (PDDocument doc = new PDDocument()) {
            TableDrawer.builder().document(doc).table(b.build())
                    .startX(40).build().draw();
            assertThat(doc.getNumberOfPages()).isGreaterThanOrEqualTo(2);
            save(doc, "showcase.pdf");
        }
    }

    @Test
    void unsplittableElementTallerThanAPageStillThrows() throws IOException {
        CellContent atomic = (availableWidth, style) -> java.util.List.of(
                new io.github.the13thclown.pdftables.Element() {
                    @Override
                    public float getHeight() {
                        return 2000;
                    }

                    @Override
                    public void draw(io.github.the13thclown.pdftables.render.RenderContext ctx) {
                    }
                });
        Table table = Table.builder()
                .addColumnOfWidth(200)
                .add(Cell.of(atomic))
                .build();
        try (PDDocument doc = new PDDocument()) {
            TableDrawer drawer = TableDrawer.builder().document(doc).table(table).build();
            assertThatThrownBy(drawer::draw).isInstanceOf(TableLayoutException.class);
        }
    }

    @Test
    void zeroCellsDrawsNothing() throws IOException {
        Table table = Table.builder().addColumnsOfWidth(100, 100).build();
        try (PDDocument doc = new PDDocument()) {
            float bottom = TableDrawer.builder().document(doc).table(table).startY(700).build().draw();
            assertThat(bottom).isEqualTo(700);
            assertThat(doc.getNumberOfPages()).isEqualTo(1);
        }
    }
}
