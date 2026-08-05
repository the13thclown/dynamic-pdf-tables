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
 * <p>
 * {@code strikethrough} and {@code underline} decorate each wrapped line
 * across its own width (the full column width on justified lines);
 * {@code highlight} and {@code frame} put a filled or stroked box behind each
 * line, hugging the glyphs rather than the cell. All of them are per line, so
 * they survive page breaks with the text.
 */
public final class TextContent implements CellContent {

    private static final float EPS = 0.01f;

    private final String text;
    private final PDFont font;
    private final Float fontSize;
    private final Color color;
    private final Float lineSpacing;
    private final boolean strikethrough;
    private final boolean underline;
    private final Color highlight;
    private final float highlightRadius;
    private final Color frameColor;
    private final float frameWidth;
    private final float frameRadius;
    private final HorizontalAlignment alignment;

    private TextContent(Builder b) {
        this.text = b.text;
        this.font = b.font;
        this.fontSize = b.fontSize;
        this.color = b.color;
        this.lineSpacing = b.lineSpacing;
        this.strikethrough = b.strikethrough;
        this.underline = b.underline;
        this.highlight = b.highlight;
        this.highlightRadius = b.highlightRadius;
        this.frameColor = b.frameColor;
        this.frameWidth = b.frameWidth;
        this.frameRadius = b.frameRadius;
        this.alignment = b.alignment;
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
                        font, size, color, lineHeight, spaceCount(line), justifiable,
                        new Decorations(strikethrough, underline, highlight, highlightRadius,
                                frameColor, frameWidth, frameRadius),
                        alignment));
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

    /** Per-line ornaments drawn around or through the glyphs. */
    private record Decorations(boolean strikethrough, boolean underline,
                               Color highlight, float highlightRadius,
                               Color frameColor, float frameWidth, float frameRadius) {

        boolean anyBox() {
            return highlight != null || frameColor != null;
        }

        boolean anyLine() {
            return strikethrough || underline;
        }
    }

    private record TextLineElement(String line, float lineWidth, PDFont font, float size,
                                   Color color, float lineHeight, int spaces, boolean justifiable,
                                   Decorations decorations, HorizontalAlignment alignmentOverride)
            implements Element {

        /** Breathing room between the glyph bounds and a highlight or frame edge. */
        private static final float BOX_PADDING = 1.5f;

        @Override
        public float getHeight() {
            return lineHeight;
        }

        @Override
        public void draw(RenderContext ctx) throws IOException {
            if (line.isEmpty()) {
                return;
            }
            HorizontalAlignment alignment =
                    alignmentOverride != null ? alignmentOverride : ctx.style().horizontalAlignment();
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
            float drawnWidth = justify ? ctx.width() : lineWidth;

            drawBoxes(ctx, x, drawnWidth, ascent, descent);

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
            drawLines(ctx, x, baseline, drawnWidth, ascent);
        }

        /** Highlight fill and frame stroke, hugging the glyph band of this line. */
        private void drawBoxes(RenderContext ctx, float x, float width,
                               float ascent, float descent) throws IOException {
            if (!decorations.anyBox()) {
                return;
            }
            float boxX = x - BOX_PADDING;
            float boxWidth = width + 2 * BOX_PADDING;
            // A font's ascent-to-descent band is commonly taller than the line box, so the
            // padded box must be clamped to the line: unclamped, the highlights of consecutive
            // lines grow into one another. Concentric with the glyph band, which the baseline
            // is centred on too.
            float boxHeight = Math.min((ascent - descent) + 2 * BOX_PADDING, ctx.height());
            float boxY = ctx.y() + (ctx.height() - boxHeight) / 2f;

            if (decorations.highlight() != null) {
                ctx.stream().saveGraphicsState();
                applyAlpha(ctx, decorations.highlight(), false);
                ctx.stream().setNonStrokingColor(opaque(decorations.highlight()));
                roundedRect(ctx, boxX, boxY, boxWidth, boxHeight, decorations.highlightRadius());
                ctx.stream().fill();
                ctx.stream().restoreGraphicsState();
            }
            if (decorations.frameColor() != null) {
                // a stroke straddles its path, so inset by half the pen width to keep the
                // whole frame inside the line box
                float inset = decorations.frameWidth() / 2f;
                float frameWidth = Math.max(boxWidth - 2 * inset, 0.1f);
                float frameHeight = Math.max(boxHeight - 2 * inset, 0.1f);
                ctx.stream().saveGraphicsState();
                applyAlpha(ctx, decorations.frameColor(), true);
                ctx.stream().setStrokingColor(opaque(decorations.frameColor()));
                ctx.stream().setLineWidth(decorations.frameWidth());
                roundedRect(ctx, boxX + inset, boxY + inset, frameWidth, frameHeight,
                        decorations.frameRadius());
                ctx.stream().stroke();
                ctx.stream().restoreGraphicsState();
            }
        }

        private void drawLines(RenderContext ctx, float x, float baseline, float width, float ascent)
                throws IOException {
            if (!decorations.anyLine()) {
                return;
            }
            ctx.stream().saveGraphicsState();
            ctx.stream().setStrokingColor(color);
            ctx.stream().setLineWidth(Math.max(size * 0.06f, 0.5f));
            if (decorations.strikethrough()) {
                // through the middle of the x-height band, where the eye expects it
                float xHeight = font.getFontDescriptor().getXHeight() / 1000f * size;
                float mid = xHeight > 0 ? xHeight / 2 : ascent * 0.3f;
                ctx.stream().moveTo(x, baseline + mid);
                ctx.stream().lineTo(x + width, baseline + mid);
            }
            if (decorations.underline()) {
                float below = size * 0.12f;
                ctx.stream().moveTo(x, baseline - below);
                ctx.stream().lineTo(x + width, baseline - below);
            }
            ctx.stream().stroke();
            ctx.stream().restoreGraphicsState();
        }

        private static Color opaque(Color c) {
            return new Color(c.getRed(), c.getGreen(), c.getBlue());
        }

        /** Honours the alpha channel of the decoration color via a graphics state. */
        private static void applyAlpha(RenderContext ctx, Color color, boolean stroking)
                throws IOException {
            if (color.getAlpha() == 255) {
                return;
            }
            var state = new org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState();
            if (stroking) {
                state.setStrokingAlphaConstant(color.getAlpha() / 255f);
            } else {
                state.setNonStrokingAlphaConstant(color.getAlpha() / 255f);
            }
            ctx.stream().setGraphicsStateParameters(state);
        }

        private static void roundedRect(RenderContext ctx, float x, float y,
                                        float width, float height, float radius) throws IOException {
            float r = Math.min(radius, Math.min(width, height) / 2);
            if (r <= 0) {
                ctx.stream().addRect(x, y, width, height);
                return;
            }
            // circular arcs approximated with cubic Béziers
            float k = 0.5522848f * r;
            ctx.stream().moveTo(x + r, y);
            ctx.stream().lineTo(x + width - r, y);
            ctx.stream().curveTo(x + width - r + k, y, x + width, y + r - k, x + width, y + r);
            ctx.stream().lineTo(x + width, y + height - r);
            ctx.stream().curveTo(x + width, y + height - r + k, x + width - r + k, y + height,
                    x + width - r, y + height);
            ctx.stream().lineTo(x + r, y + height);
            ctx.stream().curveTo(x + r - k, y + height, x, y + height - r + k, x, y + height - r);
            ctx.stream().lineTo(x, y + r);
            ctx.stream().curveTo(x, y + r - k, x + r - k, y, x + r, y);
            ctx.stream().closePath();
        }
    }

    public static final class Builder {
        private final String text;
        private PDFont font;
        private Float fontSize;
        private Color color;
        private Float lineSpacing;
        private boolean strikethrough;
        private boolean underline;
        private Color highlight;
        private float highlightRadius;
        private Color frameColor;
        private float frameWidth;
        private float frameRadius;
        private HorizontalAlignment alignment;

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

        /** Strikes a line through the middle of the x-height of every wrapped line. */
        public Builder strikethrough(boolean strikethrough) {
            this.strikethrough = strikethrough;
            return this;
        }

        /** Draws a line just below the baseline of every wrapped line. */
        public Builder underline(boolean underline) {
            this.underline = underline;
            return this;
        }

        /**
         * Fills a rounded rectangle behind each wrapped line, sized to the
         * glyphs rather than the cell — the "highlighted term" look. The
         * color's alpha channel is honoured, so {@code new Color(r, g, b, 102)}
         * gives a 40% wash that leaves the text readable.
         */
        public Builder highlight(Color fill, float cornerRadius) {
            if (cornerRadius < 0) {
                throw new IllegalArgumentException("cornerRadius must be >= 0");
            }
            this.highlight = Objects.requireNonNull(fill, "fill");
            this.highlightRadius = cornerRadius;
            return this;
        }

        /** Strokes a rounded rectangle around each wrapped line, sized to the glyphs. */
        public Builder frame(Color stroke, float lineWidth, float cornerRadius) {
            if (lineWidth <= 0) {
                throw new IllegalArgumentException("lineWidth must be > 0");
            }
            if (cornerRadius < 0) {
                throw new IllegalArgumentException("cornerRadius must be >= 0");
            }
            this.frameColor = Objects.requireNonNull(stroke, "stroke");
            this.frameWidth = lineWidth;
            this.frameRadius = cornerRadius;
            return this;
        }

        /**
         * Overrides the cell's horizontal alignment for this text only. Needed
         * when one cell stacks contents that align differently — a left-aligned
         * label above a centered value — which is otherwise impossible, since
         * alignment is a property of the cell box.
         */
        public Builder alignment(HorizontalAlignment alignment) {
            this.alignment = Objects.requireNonNull(alignment, "alignment");
            return this;
        }

        public TextContent build() {
            return new TextContent(this);
        }
    }
}
