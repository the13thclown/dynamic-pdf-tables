package io.github.the13thclown.pdftables;

import io.github.the13thclown.pdftables.layout.Element;
import io.github.the13thclown.pdftables.render.RenderContext;
import io.github.the13thclown.pdftables.style.Style;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.util.Matrix;

import java.awt.Color;
import java.io.IOException;
import java.util.List;
import java.util.Objects;

/**
 * Text rotated 90° counter-clockwise (reading bottom-to-top) — useful for
 * narrow header columns. Explicit {@code \n} produces multiple vertical lines
 * stacked left-to-right; there is no automatic wrapping. The whole block is a
 * single atomic {@link Element}: its height is the longest line's text width,
 * and it never splits across pages (it passes down whole).
 * <p>
 * Font, size, color and line spacing left unset fall back to the cell's
 * resolved style; horizontal alignment positions the line stack in the cell.
 */
public final class VerticalTextContent implements CellContent {

    private final String text;
    private final PDFont font;
    private final Float fontSize;
    private final Color color;
    private final Float lineSpacing;

    private VerticalTextContent(Builder b) {
        this.text = b.text;
        this.font = b.font;
        this.fontSize = b.fontSize;
        this.color = b.color;
        this.lineSpacing = b.lineSpacing;
    }

    public static VerticalTextContent of(String text) {
        return builder(text).build();
    }

    public static Builder builder(String text) {
        return new Builder(text);
    }

    @Override
    public List<Element> layout(float availableWidth, Style style) {
        PDFont font = this.font != null ? this.font : style.font();
        float size = this.fontSize != null ? this.fontSize : style.fontSize();
        Color color = this.color != null ? this.color : style.textColor();
        float spacing = this.lineSpacing != null ? this.lineSpacing : style.lineSpacing();

        String[] rawLines = text.split("\n", -1);
        String[] lines = new String[rawLines.length];
        float maxLength = 0;
        for (int i = 0; i < rawLines.length; i++) {
            lines[i] = TextContent.safe(rawLines[i], font);
            maxLength = Math.max(maxLength, TextContent.widthOf(lines[i], font, size));
        }
        return List.of(new VerticalTextElement(lines, maxLength, font, size, color, size * spacing));
    }

    private record VerticalTextElement(String[] lines, float length, PDFont font, float size,
                                       Color color, float lineHeight) implements Element {

        @Override
        public float getHeight() {
            return length;
        }

        @Override
        public void draw(RenderContext ctx) throws IOException {
            float stackWidth = lines.length * lineHeight;
            float left = switch (ctx.style().horizontalAlignment()) {
                case LEFT, JUSTIFY -> ctx.x();
                case CENTER -> ctx.x() + (ctx.width() - stackWidth) / 2;
                case RIGHT -> ctx.x() + ctx.width() - stackWidth;
            };
            float ascent = font.getFontDescriptor().getAscent() / 1000f * size;
            float descent = font.getFontDescriptor().getDescent() / 1000f * size;
            for (int i = 0; i < lines.length; i++) {
                if (lines[i].isEmpty()) {
                    continue;
                }
                // rotated 90° CCW the ascent extends to the left of the baseline,
                // so the baseline sits toward the right edge of its line column
                float columnLeft = left + i * lineHeight;
                float baselineX = columnLeft + (lineHeight - (ascent - descent)) / 2 + ascent;
                ctx.stream().setNonStrokingColor(color);
                ctx.stream().beginText();
                ctx.stream().setFont(font, size);
                ctx.stream().setTextMatrix(Matrix.getRotateInstance(Math.PI / 2, baselineX, ctx.y()));
                ctx.stream().showText(lines[i]);
                ctx.stream().endText();
            }
        }
    }

    public static final class Builder {
        private final String text;
        private PDFont font;
        private Float fontSize;
        private Color color;
        private Float lineSpacing;

        private Builder(String text) {
            this.text = Objects.requireNonNull(text, "text");
        }

        public Builder font(PDFont font) {
            this.font = Objects.requireNonNull(font, "font");
            return this;
        }

        public Builder fontSize(float fontSize) {
            if (fontSize <= 0) {
                throw new IllegalArgumentException("fontSize must be > 0");
            }
            this.fontSize = fontSize;
            return this;
        }

        public Builder color(Color color) {
            this.color = Objects.requireNonNull(color, "color");
            return this;
        }

        public Builder lineSpacing(float lineSpacing) {
            if (lineSpacing <= 0) {
                throw new IllegalArgumentException("lineSpacing must be > 0");
            }
            this.lineSpacing = lineSpacing;
            return this;
        }

        public VerticalTextContent build() {
            return new VerticalTextContent(this);
        }
    }
}
