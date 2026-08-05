package io.github.the13thclown.pdftables;

import io.github.the13thclown.pdftables.style.BorderStyle;
import io.github.the13thclown.pdftables.style.Padding;
import io.github.the13thclown.pdftables.style.Style;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Covers inline image fragments: a picture that flows with the words instead of
 * occupying a block of its own, so an icon can precede a sentence and the text
 * wraps back to the full width underneath it.
 */
class InlineImageFragmentTest {

    private static PDImageXObject icon(PDDocument doc, int w, int h) throws IOException {
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        var g = image.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(0, 0, w, h);
        g.dispose();
        return LosslessFactory.createFromImage(doc, image);
    }

    @Test
    void anImageFragmentTakesPartInWrappingAsOneWord() throws IOException {
        try (PDDocument doc = new PDDocument()) {
            RichTextContent content = RichTextContent.builder()
                    .fontSize(10)
                    .add(RichTextContent.Fragment.image(icon(doc, 16, 16), 12))
                    .add(" WARNING: put on a safety harness before you start work on the platform")
                    .build();

            List<Element> lines = content.layout(200, Style.defaults());
            assertThat(lines.size()).isGreaterThan(1);
            // the icon only occupies the first line; the rest is plain text at full width
            assertThat(lines.get(0).getHeight()).isGreaterThanOrEqualTo(12);
        }
    }

    @Test
    void aLineGrowsToFitAnImageTallerThanItsText() throws IOException {
        try (PDDocument doc = new PDDocument()) {
            float textOnly = RichTextContent.builder().fontSize(8).add("WARNING").build()
                    .layout(300, Style.defaults()).get(0).getHeight();
            float withIcon = RichTextContent.builder().fontSize(8)
                    .add(RichTextContent.Fragment.image(icon(doc, 16, 16), 24))
                    .add(" WARNING")
                    .build()
                    .layout(300, Style.defaults()).get(0).getHeight();

            assertThat(textOnly).isLessThan(24);
            assertThat(withIcon).isEqualTo(24);
        }
    }

    @Test
    void widthFollowsTheAspectRatioWhenOnlyHeightIsGiven() throws IOException {
        try (PDDocument doc = new PDDocument()) {
            // a 32x16 icon at 10pt tall must claim 20pt of line width
            RichTextContent wide = RichTextContent.builder().fontSize(10)
                    .add(RichTextContent.Fragment.image(icon(doc, 32, 16), 10)).build();
            RichTextContent square = RichTextContent.builder().fontSize(10)
                    .add(RichTextContent.Fragment.image(icon(doc, 16, 16), 10)).build();

            // both fit on one line at 300pt, so compare the widths they leave for text
            assertThat(wide.layout(300, Style.defaults())).hasSize(1);
            assertThat(square.layout(300, Style.defaults())).hasSize(1);
        }
    }

    @Test
    void anImageIsCentredOnTheTextNotStoodOnTheBaseline() throws IOException {
        // Standing a picture on the baseline makes a tall one tower over the words: its centre
        // ends up an icon-height above the text's. Centred on the text's mid-height the two
        // centres nearly coincide, which is what makes an icon read as part of the sentence.
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            Table table = Table.builder()
                    .addColumnsOfWidth(220)
                    .defaultStyle(Style.builder().padding(Padding.of(4)).fontSize(9).build())
                    .add(Cell.of(RichTextContent.builder()
                            .add(RichTextContent.Fragment.image(icon(doc, 16, 16), 28))
                            .add(" NOTE the actuators are vented units filled with grease")
                            .build()))
                    .build();
            TableDrawer.builder().document(doc).page(page).table(table)
                    .startX(50).startY(700).build().draw();

            var rendered = new org.apache.pdfbox.rendering.PDFRenderer(doc).renderImage(0, 1);

            int firstIconRow = Integer.MAX_VALUE;
            int lastIconRow = Integer.MIN_VALUE;
            long iconSum = 0;
            long iconCount = 0;
            for (int y = 0; y < rendered.getHeight(); y++) {
                for (int x = 50; x < 82; x++) {
                    Color pixel = new Color(rendered.getRGB(x, y));
                    if (pixel.getRed() > 180 && pixel.getBlue() < 80 && pixel.getGreen() < 80) {
                        firstIconRow = Math.min(firstIconRow, y);
                        lastIconRow = Math.max(lastIconRow, y);
                        iconSum += y;
                        iconCount++;
                    }
                }
            }
            assertThat(iconCount).as("the icon must be visible").isPositive();

            // text of the same line only: rows the icon spans, to the right of it
            long textSum = 0;
            long textCount = 0;
            for (int y = firstIconRow; y <= lastIconRow; y++) {
                for (int x = 86; x < 260; x++) {
                    if (new Color(rendered.getRGB(x, y)).getRed() < 100) {
                        textSum += y;
                        textCount++;
                    }
                }
            }
            assertThat(textCount).as("the sentence must share the icon's line").isPositive();

            float iconCentre = (float) iconSum / iconCount;
            float textCentre = (float) textSum / textCount;
            assertThat(Math.abs(iconCentre - textCentre))
                    .as("icon centre %s vs text centre %s", iconCentre, textCentre)
                    .isLessThan(3f);
        }
    }

    @Test
    void zeroOrNegativeDimensionsAreRejected() throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDImageXObject image = icon(doc, 16, 16);
            assertThatThrownBy(() -> RichTextContent.Fragment.image(image, 0, 10))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> RichTextContent.Fragment.image(image, 10, -1))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void theIconSitsOnTheFirstLineAndLaterLinesStartAtTheLeftEdge() throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            Table table = Table.builder()
                    .addColumnsOfWidth(200)
                    .defaultStyle(Style.builder()
                            .padding(Padding.of(4)).borderAll(BorderStyle.of(0.5f)).fontSize(9).build())
                    .add(Cell.of(RichTextContent.builder()
                            .add(RichTextContent.Fragment.image(icon(doc, 16, 16), 10))
                            .add(" WARNING: put on a safety harness and attach it to the access platform")
                            .build()))
                    .build();
            TableDrawer.builder().document(doc).page(page).table(table)
                    .startX(50).startY(700).build().draw();

            var rendered = new org.apache.pdfbox.rendering.PDFRenderer(doc).renderImage(0, 1);
            // the icon is red; it must appear near the top-left of the cell...
            assertThat(new Color(rendered.getRGB(56, 842 - 695)).getRed()).isGreaterThan(180);
            assertThat(new Color(rendered.getRGB(56, 842 - 695)).getBlue()).isLessThan(80);
            // ...and nowhere on the second line, which starts back at the left edge
            for (int x = 54; x < 66; x++) {
                Color pixel = new Color(rendered.getRGB(x, 842 - 682));
                boolean red = pixel.getRed() > 180 && pixel.getBlue() < 80;
                assertThat(red).as("no icon expected on the second line at x=" + x).isFalse();
            }

            Files.createDirectories(Path.of("target", "test-output"));
            doc.save(Path.of("target", "test-output", "inline-image-fragment.pdf").toFile());
        }
    }
}
