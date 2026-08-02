package io.github.the13thclown.pdftables;

import io.github.the13thclown.pdftables.style.Style;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FormContentTest {

    private static final Path OUT = Path.of("target", "test-output");

    /** A form XObject with a coloured square, {@code size} points wide and tall. */
    private static PDFormXObject square(PDDocument doc, float size, Color color) throws IOException {
        PDFormXObject form = new PDFormXObject(doc);
        form.setResources(new org.apache.pdfbox.pdmodel.PDResources());
        form.setBBox(new PDRectangle(size, size));
        try (org.apache.pdfbox.pdmodel.PDFormContentStream cs =
                     new org.apache.pdfbox.pdmodel.PDFormContentStream(form)) {
            cs.setNonStrokingColor(color);
            cs.addRect(0, 0, size, size);
            cs.fill();
        }
        return form;
    }

    @Test
    void naturalSizeComesFromTheBoundingBox() throws IOException {
        try (PDDocument doc = new PDDocument()) {
            List<Element> elements = FormContent.of(square(doc, 24, Color.RED)).layout(200, Style.defaults());
            assertThat(elements).hasSize(1);
            assertThat(elements.get(0).getHeight()).isEqualTo(24);
        }
    }

    @Test
    void explicitWidthScalesTheHeightProportionally() throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDFormXObject form = new PDFormXObject(doc);
            form.setBBox(new PDRectangle(40, 20));
            List<Element> elements = FormContent.builder(form).width(80).build()
                    .layout(200, Style.defaults());
            assertThat(elements.get(0).getHeight()).isEqualTo(40);
        }
    }

    @Test
    void aFormWiderThanTheCellIsNotScaledDown() throws IOException {
        try (PDDocument doc = new PDDocument()) {
            // a stamp shrunk to fit would misrepresent it — overflow is the honest outcome
            List<Element> elements = FormContent.of(square(doc, 120, Color.RED)).layout(30, Style.defaults());
            assertThat(elements.get(0).getHeight()).isEqualTo(120);
        }
    }

    @Test
    void formWithoutBoundingBoxIsRejected() throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDFormXObject form = new PDFormXObject(doc);
            form.setBBox(new PDRectangle(0, 0));
            assertThatThrownBy(() -> FormContent.of(form))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("bounding box");
        }
    }

    @Test
    void formsRenderInsideCellsAndPaginate() throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDFormXObject stamp = square(doc, 20, new Color(20, 90, 160));
            Table.Builder b = Table.builder()
                    .addColumnsOfWidth(300, 60)
                    .defaultStyle(Style.builder()
                            .borderAll(io.github.the13thclown.pdftables.style.BorderStyle.of(0.5f))
                            .padding(io.github.the13thclown.pdftables.style.Padding.of(3))
                            .fontSize(9).build());
            for (int i = 0; i < 40; i++) {
                b.add(Cell.of(TextContent.of("step " + i)));
                b.add(Cell.builder().add(FormContent.of(stamp))
                        .horizontalAlignment(io.github.the13thclown.pdftables.style.HorizontalAlignment.CENTER)
                        .build());
            }
            TableDrawer.builder().document(doc).table(b.build()).build().draw();
            assertThat(doc.getNumberOfPages()).isGreaterThan(1);
            Files.createDirectories(OUT);
            doc.save(OUT.resolve("form-content.pdf").toFile());
        }
    }

    @Test
    void aFormIsDrawnAtTheCellPositionNotThePageOrigin() throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            PDFormXObject stamp = square(doc, 20, Color.BLACK);
            Table table = Table.builder()
                    .addColumnsOfWidth(100)
                    .add(Cell.of(FormContent.of(stamp)))
                    .build();
            TableDrawer.builder().document(doc).page(page).table(table)
                    .startX(100).startY(700).build().draw();

            var image = new org.apache.pdfbox.rendering.PDFRenderer(doc).renderImage(0, 1);
            // the square occupies [100,120] x [680,700] in PDF space; in image
            // space y counts from the top of the 842pt page
            int px = 110;
            int py = (int) (842 - 690);
            assertThat(new Color(image.getRGB(px, py)).getRed()).isLessThan(60);
            // and nothing was painted at the page origin
            assertThat(new Color(image.getRGB(10, 832)).getRed()).isGreaterThan(200);
        }
    }
}
