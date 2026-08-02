package io.github.the13thclown.pdftables;

import io.github.the13thclown.pdftables.render.RenderContext;
import io.github.the13thclown.pdftables.style.HorizontalAlignment;
import io.github.the13thclown.pdftables.style.Style;
import org.apache.pdfbox.pdmodel.font.PDFont;

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
 * Font, size, color and line spacing left unset here fall back to the cell's
 * resolved style (cell → row → column → table default) — so a table-wide
 * "9pt" is declared once on the table's default style.
 * <p>
 * Wrapping: lines break at spaces; explicit {@code \n} forces a break; a word
 * wider than the available width splits mid-word. Characters the font cannot
 * encode are replaced with {@code ?} instead of failing. Horizontal alignment
 * comes from the resolved cell style; {@link HorizontalAlignment#JUSTIFY}
 * stretches every wrapped line except a paragraph's last.
 */
public final class TextContent implements CellContent {

    private static final float EPS = 0.01f;

    private final String text;
    private final PDFont font;
    private final Float fontSize;
    private final Color color;
    private final Float lineSpacing;

    private TextContent(Builder b) {
        this.text = b.text;
        this.font = b.font;
        this.fontSize = b.fontSize;
        this.color = b.color;
        this.lineSpacing = b.lineSpacing;
    }

    /** Text styled entirely by the cell's resolved style defaults. */
    public static TextContent of(String text) {
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
        float lineHeight = size * spacing;

        List<Element> elements = new ArrayList<>();
        for (String paragraph : text.split("\n", -1)) {
            List<String> lines = wrap(paragraph, availableWidth, font, size);
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                boolean justifiable = i < lines.size() - 1;   // never a paragraph's last line
                elements.add(new TextLineElement(line, widthOf(line, font, size),
                        font, size, color, lineHeight, spaceCount(line), justifiable));
            }
        }
        return elements;
    }

    private List<String> wrap(String paragraph, float availableWidth, PDFont font, float size) {
        List<String> lines = new ArrayList<>();
        String[] words = paragraph.trim().split(" +");
        StringBuilder current = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            word = safe(word, font);
            String candidate = current.isEmpty() ? word : current + " " + word;
            if (widthOf(candidate, font, size) <= availableWidth + EPS) {
                current = new StringBuilder(candidate);
                continue;
            }
            if (!current.isEmpty()) {
                lines.add(current.toString());
                current = new StringBuilder();
            }
            // a word wider than the whole line splits mid-word
            while (widthOf(word, font, size) > availableWidth + EPS && word.length() > 1) {
                int fit = 1;
                while (fit < word.length()
                        && widthOf(word.substring(0, fit + 1), font, size) <= availableWidth + EPS) {
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

    private static int spaceCount(String line) {
        int n = 0;
        for (int i = 0; i < line.length(); i++) {
            if (line.charAt(i) == ' ') {
                n++;
            }
        }
        return n;
    }

    /** Replaces characters the font cannot encode with '?'. */
    static String safe(String word, PDFont font) {
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

    static float widthOf(String s, PDFont font, float size) {
        try {
            return font.getStringWidth(s) / 1000f * size;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to measure text with font " + font.getName(), e);
        }
    }

    private record TextLineElement(String line, float lineWidth, PDFont font, float size,
                                   Color color, float lineHeight, int spaces, boolean justifiable)
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
            HorizontalAlignment alignment = ctx.style().horizontalAlignment();
            float x = switch (alignment) {
                case LEFT, JUSTIFY -> ctx.x();
                case CENTER -> ctx.x() + (ctx.width() - lineWidth) / 2;
                case RIGHT -> ctx.x() + ctx.width() - lineWidth;
            };
            // center the font's ascent-to-descent band within the line box
            float ascent = font.getFontDescriptor().getAscent() / 1000f * size;
            float descent = font.getFontDescriptor().getDescent() / 1000f * size;
            float baseline = ctx.y() + (lineHeight - (ascent - descent)) / 2 - descent;

            boolean justify = alignment == HorizontalAlignment.JUSTIFY && justifiable && spaces > 0;
            ctx.stream().setNonStrokingColor(color);
            ctx.stream().beginText();
            ctx.stream().setFont(font, size);
            if (justify) {
                ctx.stream().setWordSpacing((ctx.width() - lineWidth) / spaces);
            }
            ctx.stream().newLineAtOffset(x, baseline);
            ctx.stream().showText(line);
            if (justify) {
                ctx.stream().setWordSpacing(0);
            }
            ctx.stream().endText();
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

        /** Line height as a multiple of the font size. */
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
