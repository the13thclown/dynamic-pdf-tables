package io.github.the13thclown.pdftables;

import io.github.the13thclown.pdftables.style.Style;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class ImageContentTest {

    private static BufferedImage image(int w, int h) {
        return new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
    }

    @Test
    void naturalSizeWhenItFits() {
        List<Element> e = ImageContent.of(image(100, 50)).layout(200, Style.defaults());
        assertThat(e).hasSize(1);
        assertThat(e.get(0).getHeight()).isCloseTo(50, within(0.01f));
    }

    @Test
    void scalesDownProportionallyToFitTheAvailableWidth() {
        List<Element> e = ImageContent.of(image(100, 50)).layout(50, Style.defaults());
        assertThat(e.get(0).getHeight()).isCloseTo(25, within(0.01f));
    }

    @Test
    void explicitWidthScalesHeightByAspectRatio() {
        List<Element> e = ImageContent.builder(image(100, 50)).width(80).build().layout(500, Style.defaults());
        assertThat(e.get(0).getHeight()).isCloseTo(40, within(0.01f));
    }

    @Test
    void explicitHeightWins() {
        List<Element> e = ImageContent.builder(image(100, 50)).height(30).build().layout(500, Style.defaults());
        assertThat(e.get(0).getHeight()).isCloseTo(30, within(0.01f));
    }

    @Test
    void widthAndHeightTogetherStretch() {
        List<Element> e = ImageContent.builder(image(100, 50)).width(60).height(60).build().layout(500, Style.defaults());
        assertThat(e.get(0).getHeight()).isCloseTo(60, within(0.01f));
    }

    @Test
    void pngBytesAreMeasuredWithoutADocument() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image(40, 20), "png", out);
        List<Element> e = ImageContent.of(out.toByteArray(), "sample.png").layout(200, Style.defaults());
        assertThat(e.get(0).getHeight()).isCloseTo(20, within(0.01f));
    }

    @Test
    void embeddedXObjectContentRefusesASecondDocument() throws IOException {
        try (org.apache.pdfbox.pdmodel.PDDocument first = new org.apache.pdfbox.pdmodel.PDDocument();
             org.apache.pdfbox.pdmodel.PDDocument second = new org.apache.pdfbox.pdmodel.PDDocument()) {
            org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject embedded =
                    org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory
                            .createFromImage(first, image(20, 20));
            ImageContent content = ImageContent.of(embedded);
            Table table = Table.builder().addColumnOfWidth(100).add(Cell.of(content)).build();

            TableDrawer.builder().document(first).table(table).build().draw();
            TableDrawer secondDrawer = TableDrawer.builder().document(second).table(table).build();
            assertThatThrownBy(secondDrawer::draw)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("bound to the document");
        }
    }

    @Test
    void unreadableBytesFailAtDefinitionTime() {
        assertThatThrownBy(() -> ImageContent.of(new byte[]{1, 2, 3}, "junk.bin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("junk.bin");
    }
}
