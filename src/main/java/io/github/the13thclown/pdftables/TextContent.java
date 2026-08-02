package io.github.the13thclown.pdftables;

import io.github.the13thclown.pdftables.layout.Element;
import io.github.the13thclown.pdftables.render.RenderContext;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import java.awt.Color;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Text as cell content. At render time the text wraps to the cell's content
 * width and decomposes into one {@link Element} per line — so text splits
 * across pages line by line, exactly like every other content type, with no
 * special handling in the engine.
 * <p>
 * Wrapping: lines break at spaces; explicit {@code \n} forces a break; a word
 * wider than the available width splits mid-word. Characters the font cannot
 * encode are replaced with {@code ?} instead of failing. Horizontal alignment
 * comes from the resolved cell style at draw time.
 */
public final class TextContent implements CellContent {

    private static final float EPS = 0.01f;

    private final String text;
    private final PDFont font;
    private final float fontSize;
    private final Color color;
    private final float lineSpacing;

    private TextContent(Builder b) {
        this.text = b.text;
        this.font = b.font;
        this.fontSize = b.fontSize;
        this.color = b.color;
        this.lineSpacing = b.lineSpacing;
    }

    /** Text with defaults: Helvetica 11pt, black, 1.2 line spacing. */
    public static TextContent of(String text) {
        return builder(text).build();
    }

    public static Builder builder(String text) {
        return new Builder(text);
    }

    @Override
    public List<Element> layout(float availableWidth) {
        float lineHeight = fontSize * lineSpacing;
        List<Element> lines = new ArrayList<>();
        for (String paragraph : text.split("\n", -1)) {
            for (String line : wrap(paragraph, availableWidth)) {
                lines.add(new TextLineElement(line, widthOf(line), this, lineHeight));
            }
        }
        return lines;
    }

    private List<String> wrap(String paragraph, float availableWidth) {
        List<String> lines = new ArrayList<>();
        String[] words = paragraph.trim().split(" +");
        StringBuilder current = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            word = safe(word);
            String candidate = current.isEmpty() ? word : current + " " + word;
            if (widthOf(candidate) <= availableWidth + EPS) {
                current = new StringBuilder(candidate);
                continue;
            }
            if (!current.isEmpty()) {
                lines.add(current.toString());
                current = new StringBuilder();
            }
            // a word wider than the whole line splits mid-word
            while (widthOf(word) > availableWidth + EPS && word.length() > 1) {
                int fit = 1;
                while (fit < word.length() && widthOf(word.substring(0, fit + 1)) <= availableWidth + EPS) {
                    fit++;
                }
                lines.add(word.substring(0, fit));
                word = word.substring(fit);
            }
            current = new StringBuilder(word);
        }
        if (!current.isEmpty() || lines.isEmpty()) {
            lines.add(current.toString());
        }
        return lines;
    }

    /** Replaces characters the font cannot encode with '?'. */
    private String safe(String word) {
        try {
            font.encode(word);
            return word;
        } catch (IOException | IllegalArgumentException e) {
            StringBuilder sb = new StringBuilder(word.length());
            for (int i = 0; i < word.length(); ) {
                int cp = word.codePointAt(i);
                String ch = new String(Character.toChars(cp));
                try {
                    font.encode(ch);
                    sb.append(ch);
                } catch (IOException | IllegalArgumentException notEncodable) {
                    sb.append('?');
                }
                i += Character.charCount(cp);
            }
            return sb.toString();
        }
    }

    private float widthOf(String s) {
        try {
            return font.getStringWidth(s) / 1000f * fontSize;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to measure text with font " + font.getName(), e);
        }
    }

    private record TextLineElement(String line, float lineWidth, TextContent style, float lineHeight)
            implements Element {

        @Override
        public float getHeight() {
            return lineHeight;
        }

        @Override
        public void draw(RenderContext ctx) throws IOException {
            if (line.isEmpty()) {
                return;
            }
            float x = switch (ctx.style().horizontalAlignment()) {
                case LEFT -> ctx.x();
                case CENTER -> ctx.x() + (ctx.width() - lineWidth) / 2;
                case RIGHT -> ctx.x() + ctx.width() - lineWidth;
            };
            // center the font's ascent-to-descent band within the line box
            float ascent = style.font.getFontDescriptor().getAscent() / 1000f * style.fontSize;
            float descent = style.font.getFontDescriptor().getDescent() / 1000f * style.fontSize;
            float baseline = ctx.y() + (lineHeight - (ascent - descent)) / 2 - descent;

            ctx.stream().setNonStrokingColor(style.color);
            ctx.stream().beginText();
            ctx.stream().setFont(style.font, style.fontSize);
            ctx.stream().newLineAtOffset(x, baseline);
            ctx.stream().showText(line);
            ctx.stream().endText();
        }
    }

    public static final class Builder {
        private final String text;
        private PDFont font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        private float fontSize = 11;
        private Color color = Color.BLACK;
        private float lineSpacing = 1.2f;

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

        /** Line height as a multiple of the font size; default 1.2. */
        public Builder lineSpacing(float lineSpacing) {
            if (lineSpacing <= 0) {
                throw new IllegalArgumentException("lineSpacing must be > 0");
            }
            this.lineSpacing = lineSpacing;
            return this;
        }

        public TextContent build() {
            return new TextContent(this);
        }
    }
}
