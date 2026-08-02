package io.github.orestkollcaku.pdftables;

import io.github.orestkollcaku.pdftables.style.BorderStyle;
import io.github.orestkollcaku.pdftables.style.Style;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pixel-sampling checks: render pages to images and probe single pixels to
 * guard coordinates, fills, and the open-border-at-cut behavior against gross
 * regressions. Deliberately samples pixel centers far from anti-aliased edges.
 */
class RenderPixelTest {

    private static final Color BEIGE = new Color(200, 200, 100);

    private static Color pixel(BufferedImage img, float pdfX, float pdfY) {
        int px = (int) Math.floor(pdfX);
        int py = img.getHeight() - 1 - (int) Math.floor(pdfY);
        return new Color(img.getRGB(px, py));
    }

    private static void assertNear(Color actual, Color expected) {
        int d = Math.abs(actual.getRed() - expected.getRed())
                + Math.abs(actual.getGreen() - expected.getGreen())
                + Math.abs(actual.getBlue() - expected.getBlue());
        assertThat(d)
                .as("color %s should be near %s", actual, expected)
                .isLessThan(90);
    }

    private static void assertNoStroke(Color actual) {
        assertThat(actual.getRed() + actual.getGreen() + actual.getBlue())
                .as("no border stroke expected, but found dark color %s", actual)
                .isGreaterThan(400);
    }

    @Test
    void backgroundFillAndBordersLandWhereExpected() throws IOException {
        Table table = Table.builder()
                .addColumnOfWidth(100)
                .minRowHeight(50)
                .add(Cell.builder()
                        .backgroundColor(Color.RED)
                        .borderAll(BorderStyle.of(2))
                        .build())
                .build();
        try (PDDocument doc = new PDDocument()) {
            TableDrawer.builder().document(doc).table(table).startX(50).startY(800).build().draw();
            BufferedImage img = new PDFRenderer(doc).renderImage(0);
            // cell box: x 50..150, y 750..800; horizontal borders sampled at Y-1,
            // the row fully inside the 2pt stroke after the renderer's fractional
            // page-height offset (A4 is 841.89pt tall, the image 841px)
            assertNear(pixel(img, 100, 775), Color.RED);
            assertNear(pixel(img, 100, 799), Color.BLACK);   // top border
            assertNear(pixel(img, 100, 749), Color.BLACK);   // bottom border
            assertNear(pixel(img, 50, 775), Color.BLACK);    // left border
            assertNear(pixel(img, 100, 820), Color.WHITE);   // outside the table
        }
    }

    @Test
    void thickerBorderWinsAtSharedEdgesRegardlessOfCellOrder() throws IOException {
        // easytable #42: a later-drawn thin border must not cover an earlier
        // thick one. Left cell has a 4pt black border, the right cell (drawn
        // later) a 1pt red one; their shared edge must stay black.
        Table table = Table.builder()
                .addColumnsOfWidth(100, 100)
                .minRowHeight(60)
                .add(Cell.builder().borderAll(BorderStyle.of(4, Color.BLACK)).build())
                .add(Cell.builder().borderAll(BorderStyle.of(1, Color.RED)).build())
                .build();
        try (PDDocument doc = new PDDocument()) {
            TableDrawer.builder().document(doc).table(table).startX(50).startY(800).build().draw();
            BufferedImage img = new PDFRenderer(doc).renderImage(0);
            assertNear(pixel(img, 150, 770), Color.BLACK);   // shared edge: 4pt black on top
            assertNear(pixel(img, 250, 770), Color.RED);     // right cell's own right edge stays red
        }
    }

    @Test
    void closeBordersAtPageBreakDrawsClosedBoxesOnBothSidesOfTheCut() throws IOException {
        // same setup as the open-border test below, but with the option on:
        // the cut line gets a bottom border on page 1 and the continuation a
        // top border on page 2
        Cell.Builder cell = Cell.builder()
                .backgroundColor(BEIGE)
                .borderAll(BorderStyle.of(2));
        for (int i = 0; i < 3; i++) {
            cell.add(PlaceholderContent.ofSize(20, 300));
        }
        Table table = Table.builder().addColumnOfWidth(100).add(cell.build()).build();

        try (PDDocument doc = new PDDocument()) {
            TableDrawer.builder().document(doc).table(table)
                    .startX(50).startY(700).endY(100).startYOnNewPages(700)
                    .closeBordersAtPageBreak(true)
                    .build().draw();
            PDFRenderer renderer = new PDFRenderer(doc);
            BufferedImage p1 = renderer.renderImage(0);
            assertNear(pixel(p1, 120, 99), Color.BLACK);     // bottom border ON the cut line
            BufferedImage p2 = renderer.renderImage(1);
            assertNear(pixel(p2, 120, 699), Color.BLACK);    // top border on the continuation
            assertNear(pixel(p2, 120, 399), Color.BLACK);    // real bottom border still closes the cell
        }
    }

    @Test
    void cutCellHasOpenBottomOnFirstPageAndOpenTopOnContinuation() throws IOException {
        // one cell, three 300pt elements (900pt) on pages with 600pt capacity:
        // page 1 draws two elements and cuts the cell open; page 2 finishes it.
        Cell.Builder cell = Cell.builder()
                .backgroundColor(BEIGE)
                .borderAll(BorderStyle.of(2));
        for (int i = 0; i < 3; i++) {
            cell.add(PlaceholderContent.ofSize(20, 300));   // narrow: keeps sample column x=120 clear
        }
        Table table = Table.builder().addColumnOfWidth(100).add(cell.build()).build();

        try (PDDocument doc = new PDDocument()) {
            TableDrawer.builder().document(doc).table(table)
                    .startX(50).startY(700).endY(100).startYOnNewPages(700)
                    .build().draw();
            assertThat(doc.getNumberOfPages()).isEqualTo(2);
            PDFRenderer renderer = new PDFRenderer(doc);

            BufferedImage p1 = renderer.renderImage(0);
            assertNear(pixel(p1, 120, 699), Color.BLACK);    // top border present on page 1
            assertNear(pixel(p1, 120, 300), BEIGE);          // background filled down the page
            assertNear(pixel(p1, 150, 300), Color.BLACK);    // right border runs to the cut
            assertNear(pixel(p1, 120, 101), BEIGE);          // NO bottom border at the cut — box is open

            BufferedImage p2 = renderer.renderImage(1);
            assertNoStroke(pixel(p2, 120, 699));             // NO top border — continuation is open
            assertNear(pixel(p2, 120, 550), BEIGE);
            assertNear(pixel(p2, 120, 399), Color.BLACK);    // real bottom border closes the cell
        }
    }
}
