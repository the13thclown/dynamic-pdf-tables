package io.github.the13thclown.pdftables;

import io.github.the13thclown.pdftables.style.Style;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class TextContentTest {

    @Test
    void shortTextIsASingleLine() {
        List<Element> lines = TextContent.of("hello world").layout(300, Style.defaults());
        assertThat(lines).hasSize(1);
    }

    @Test
    void textWrapsToTheAvailableWidth() {
        List<Element> lines = TextContent.of(
                "The quick brown fox jumps over the lazy dog and keeps on running").layout(120, Style.defaults());
        assertThat(lines.size()).isGreaterThan(2);
    }

    @Test
    void explicitNewlinesForceBreaks() {
        List<Element> lines = TextContent.of("one\ntwo\nthree").layout(500, Style.defaults());
        assertThat(lines).hasSize(3);
    }

    @Test
    void blankLinesArePreserved() {
        List<Element> lines = TextContent.of("one\n\ntwo").layout(500, Style.defaults());
        assertThat(lines).hasSize(3);
    }

    @Test
    void wordWiderThanTheLineSplitsMidWord() {
        List<Element> lines = TextContent.of("Honorificabilitudinitatibus").layout(40, Style.defaults());
        assertThat(lines.size()).isGreaterThan(1);
    }

    @Test
    void lineHeightIsFontSizeTimesSpacing() {
        List<Element> lines = TextContent.builder("x").fontSize(10).lineSpacing(1.5f).build().layout(200, Style.defaults());
        assertThat(lines.get(0).getHeight()).isCloseTo(15, within(0.01f));
    }

    @Test
    void unencodableCharactersAreReplacedInsteadOfFailing() {
        // U+2192 RIGHTWARDS ARROW is not encodable in WinAnsi/Helvetica
        List<Element> lines = TextContent.of("a → b").layout(300, Style.defaults());
        assertThat(lines).hasSize(1);
    }

    @Test
    void emptyTextIsOneEmptyLine() {
        List<Element> lines = TextContent.of("").layout(300, Style.defaults());
        assertThat(lines).hasSize(1);
        assertThat(lines.get(0).getHeight()).isCloseTo(11 * 1.2f, within(0.01f));
    }

    @Test
    void decorationsDoNotChangeLineMetrics() {
        List<Element> plain = TextContent.builder("struck out text").fontSize(10).build()
                .layout(200, Style.defaults());
        List<Element> decorated = TextContent.builder("struck out text").fontSize(10)
                .strikethrough(true).underline(true).build()
                .layout(200, Style.defaults());
        assertThat(decorated).hasSameSizeAs(plain);
        assertThat(decorated.get(0).getHeight()).isEqualTo(plain.get(0).getHeight());
    }

    @Test
    void strikethroughStrokesThroughEveryWrappedLine() throws java.io.IOException {
        // a strike runs unbroken across the whole line; glyphs alone always have
        // gaps between letters, so the longest horizontal dark run separates them
        assertThat(longestDarkRun(true)).isGreaterThan(3 * longestDarkRun(false));
    }

    @Test
    void highlightPaintsBehindTheGlyphsAndHonoursAlpha() throws java.io.IOException {
        java.awt.Color washed = highlightSample(new java.awt.Color(255, 0, 0, 102));
        java.awt.Color solid = highlightSample(new java.awt.Color(255, 0, 0));
        java.awt.Color none = highlightSample(null);

        assertThat(none.getRed()).isGreaterThan(240);
        assertThat(none.getGreen()).isGreaterThan(240);
        // a red wash leaves red high and green low-ish; 40% alpha stays lighter than full red
        assertThat(solid.getGreen()).isLessThan(60);
        assertThat(washed.getGreen()).isBetween(solid.getGreen() + 40, 245);
    }

    /** Samples a pixel inside the highlight box but beside the glyphs. */
    private static java.awt.Color highlightSample(java.awt.Color highlight) throws java.io.IOException {
        try (org.apache.pdfbox.pdmodel.PDDocument doc = new org.apache.pdfbox.pdmodel.PDDocument()) {
            org.apache.pdfbox.pdmodel.PDPage page =
                    new org.apache.pdfbox.pdmodel.PDPage(org.apache.pdfbox.pdmodel.common.PDRectangle.A4);
            TextContent.Builder text = TextContent.builder("PANEL").fontSize(10);
            if (highlight != null) {
                text.highlight(highlight, 3);
            }
            Table table = Table.builder().addColumnsOfWidth(120).add(Cell.of(text.build())).build();
            TableDrawer.builder().document(doc).page(page).table(table)
                    .startX(50).startY(700).build().draw();

            var image = new org.apache.pdfbox.rendering.PDFRenderer(doc).renderImage(0, 1);
            // just above the cap height of the 10pt line, still inside the box
            return new java.awt.Color(image.getRGB(52, 842 - 697));
        }
    }

    @Test
    void perContentAlignmentOverridesTheCellAlignment() throws java.io.IOException {
        // one cell, a LEFT label above a CENTER value — impossible via cell style alone
        try (org.apache.pdfbox.pdmodel.PDDocument doc = new org.apache.pdfbox.pdmodel.PDDocument()) {
            org.apache.pdfbox.pdmodel.PDPage page =
                    new org.apache.pdfbox.pdmodel.PDPage(org.apache.pdfbox.pdmodel.common.PDRectangle.A4);
            Table table = Table.builder()
                    .addColumnsOfWidth(200)
                    .add(Cell.builder()
                            .add(TextContent.builder("L").fontSize(10)
                                    .alignment(io.github.the13thclown.pdftables.style.HorizontalAlignment.LEFT).build())
                            .add(TextContent.builder("C").fontSize(10)
                                    .alignment(io.github.the13thclown.pdftables.style.HorizontalAlignment.CENTER).build())
                            .horizontalAlignment(io.github.the13thclown.pdftables.style.HorizontalAlignment.RIGHT)
                            .build())
                    .build();
            TableDrawer.builder().document(doc).page(page).table(table)
                    .startX(50).startY(700).build().draw();

            var image = new org.apache.pdfbox.rendering.PDFRenderer(doc).renderImage(0, 1);
            assertThat(darkColumn(image, 842 - 700, 842 - 688)).isLessThan(60);   // "L" hugs the left edge
            assertThat(darkColumn(image, 842 - 688, 842 - 676)).isBetween(90, 110); // "C" sits mid-column
        }
    }

    /** Mean x of the dark pixels in a horizontal band, relative to the table's left edge at x=50. */
    private static int darkColumn(java.awt.image.BufferedImage image, int fromRow, int toRow) {
        long sum = 0;
        long count = 0;
        for (int y = fromRow; y < toRow; y++) {
            for (int x = 50; x < 250; x++) {
                if (new java.awt.Color(image.getRGB(x, y)).getRed() < 128) {
                    sum += x - 50;
                    count++;
                }
            }
        }
        return count == 0 ? -1 : (int) (sum / count);
    }

    private static int longestDarkRun(boolean strikethrough) throws java.io.IOException {
        try (org.apache.pdfbox.pdmodel.PDDocument doc = new org.apache.pdfbox.pdmodel.PDDocument()) {
            org.apache.pdfbox.pdmodel.PDPage page =
                    new org.apache.pdfbox.pdmodel.PDPage(org.apache.pdfbox.pdmodel.common.PDRectangle.A4);
            Table table = Table.builder()
                    .addColumnsOfWidth(120)
                    .add(Cell.of(TextContent.builder("aaaa aaaa aaaa aaaa aaaa aaaa")
                            .fontSize(10).strikethrough(strikethrough).build()))
                    .build();
            TableDrawer.builder().document(doc).page(page).table(table)
                    .startX(50).startY(700).build().draw();

            var image = new org.apache.pdfbox.rendering.PDFRenderer(doc).renderImage(0, 3);
            int longest = 0;
            for (int y = 0; y < image.getHeight(); y++) {
                int run = 0;
                for (int x = 0; x < image.getWidth(); x++) {
                    boolean dark = new java.awt.Color(image.getRGB(x, y)).getRed() < 200;
                    run = dark ? run + 1 : 0;
                    longest = Math.max(longest, run);
                }
            }
            return longest;
        }
    }
}
