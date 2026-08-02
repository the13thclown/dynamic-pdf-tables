package io.github.the13thclown.pdftables;

import io.github.the13thclown.pdftables.render.RenderContext;
import io.github.the13thclown.pdftables.style.BorderStyle;
import io.github.the13thclown.pdftables.style.Padding;
import io.github.the13thclown.pdftables.style.Style;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves (and demonstrates) the extension contract: a third-party content
 * type implemented purely against the public API — {@link CellContent},
 * {@link Element}, {@link RenderContext}, {@link Style} — with no imports
 * from the internal {@code layout} package. If this test ever needs an
 * internal import to compile, the extension contract has regressed.
 */
class CustomContentTest {

    /**
     * Sample custom content: a mini horizontal bar chart. One atomic element;
     * bars scale to the widest value and fill the content width. Inherits the
     * cell's resolved text color for the bars — showing that custom contents
     * take part in the style cascade like built-in ones.
     */
    static final class BarChartContent implements CellContent {
        private final float[] values;
        private final float barHeight;
        private final float gap;

        BarChartContent(float barHeight, float gap, float... values) {
            this.values = values.clone();
            this.barHeight = barHeight;
            this.gap = gap;
        }

        @Override
        public List<Element> layout(float availableWidth, Style style) {
            float height = values.length * barHeight + Math.max(0, values.length - 1) * gap;
            return List.of(new BarsElement(height, availableWidth));
        }

        private final class BarsElement implements Element {
            private final float height;
            private final float width;

            private BarsElement(float height, float width) {
                this.height = height;
                this.width = width;
            }

            @Override
            public float getHeight() {
                return height;
            }

            @Override
            public void draw(RenderContext ctx) throws IOException {
                float max = 0;
                for (float v : values) {
                    max = Math.max(max, v);
                }
                Color color = ctx.style().textColor();
                for (int i = 0; i < values.length; i++) {
                    float barWidth = max == 0 ? 0 : values[i] / max * width;
                    float top = ctx.y() + height - i * (barHeight + gap);
                    ctx.stream().setNonStrokingColor(color);
                    ctx.stream().addRect(ctx.x(), top - barHeight, barWidth, barHeight);
                    ctx.stream().fill();
                }
            }
        }
    }

    @Test
    void customContentMeasuresThroughThePublicContract() {
        BarChartContent chart = new BarChartContent(8, 3, 10, 40, 25);
        List<Element> elements = chart.layout(200, Style.defaults());
        assertThat(elements).hasSize(1);
        assertThat(elements.get(0).getHeight()).isEqualTo(3 * 8 + 2 * 3);
    }

    @Test
    void customContentRendersAndPaginatesLikeBuiltInContent() throws IOException {
        Table.Builder b = Table.builder()
                .addColumnsOfWidth(140, 200)
                .defaultStyle(Style.builder().borderAll(BorderStyle.of(0.7f))
                        .padding(Padding.of(6)).fontSize(9)
                        .textColor(new Color(60, 90, 150)).build());
        for (int i = 1; i <= 40; i++) {
            b.add(Cell.of(TextContent.of("Series " + i)));
            b.add(Cell.of(new BarChartContent(8, 3, 10 + i, 55 - i % 20, 30 + (i * 7) % 25)));
        }
        // 40 rows of ~45pt: the custom content must flow across pages through
        // the standard pagination machinery, untouched
        try (PDDocument doc = new PDDocument()) {
            TableDrawer.builder().document(doc).table(b.build()).build().draw();
            assertThat(doc.getNumberOfPages()).isEqualTo(3);
            Files.createDirectories(Path.of("target", "test-output"));
            doc.save(Path.of("target", "test-output", "custom-content.pdf").toFile());
        }
    }
}
