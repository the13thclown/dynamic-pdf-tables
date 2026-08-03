package io.github.the13thclown.pdftables;

import io.github.the13thclown.pdftables.render.RenderContext;
import io.github.the13thclown.pdftables.style.HorizontalAlignment;
import io.github.the13thclown.pdftables.style.Style;
import org.apache.pdfbox.pdmodel.font.PDFont;

import java.awt.Color;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A paragraph of mixed-style text: fragments with their own font, size or
 * color flow and wrap together as one text. Words are never broken at fragment
 * boundaries — adjacent fragments without a space between them form one word.
 * Like {@link TextContent}, each wrapped line is one atomic {@link Element},
 * so rich text paginates line by line.
 * <p>
 * Fragment styles inherit the builder's base where set, which in turn falls
 * back to the cell's resolved style (cell → row → column → table default):
 * <pre>{@code
 * RichTextContent.builder()
 *     .add("Total: ")
 *     .add(RichTextContent.fragment("123.45 EUR").font(bold).color(red))
 *     .build()
 * }</pre>
 * {@link HorizontalAlignment#JUSTIFY} stretches wrapped lines (except a
 * paragraph's last) by widening the inter-word gaps.
 */
public final class RichTextContent implements CellContent {

    private static final float EPS = 0.01f;

    /** A styled piece of a paragraph; unset properties inherit the content's base style. */
    public static final class Fragment {
        private final String text;
        private final PDFont font;
        private final Float fontSize;
        private final Color color;

        private Fragment(String text, PDFont font, Float fontSize, Color color) {
            this.text = Objects.requireNonNull(text, "text");
            this.font = font;
            this.fontSize = fontSize;
            this.color = color;
        }

        /**
         * {@return a fragment in the content's base style}
         *
         * @param text the fragment's text
         */
        public static Fragment of(String text) {
            return new Fragment(text, null, null, null);
        }

        /**
         * {@return a copy of this fragment with its own font}
         *
         * @param font the fragment's font
         */
        public Fragment font(PDFont font) {
            return new Fragment(text, font, fontSize, color);
        }

        /**
         * {@return a copy of this fragment with its own font size}
         *
         * @param size the font size in points
         */
        public Fragment fontSize(float size) {
            return new Fragment(text, font, size, color);
        }

        /**
         * {@return a copy of this fragment with its own text color}
         *
         * @param color the text color
         */
        public Fragment color(Color color) {
            return new Fragment(text, font, fontSize, color);
        }
    }

    /**
     * Shorthand for {@link Fragment#of}.
     *
     * @param text the fragment's text
     * @return a fragment in the content's base style
     */
    public static Fragment fragment(String text) {
        return Fragment.of(text);
    }

    private final List<Fragment> fragments;
    private final PDFont baseFont;
    private final Float baseFontSize;
    private final Color baseColor;
    private final Float lineSpacing;

    private RichTextContent(Builder b) {
        this.fragments = List.copyOf(b.fragments);
        this.baseFont = b.font;
        this.baseFontSize = b.fontSize;
        this.baseColor = b.color;
        this.lineSpacing = b.lineSpacing;
    }

    /** {@return a new builder} */
    public static Builder builder() {
        return new Builder();
    }

    /** A run: a maximal same-styled piece of text with a fixed width. */
    private record Run(String text, PDFont font, float size, Color color, float width) {

        boolean isSpace() {
            return " ".equals(text);
        }
    }

    @Override
    public List<Element> layout(float availableWidth, Style style) {
        PDFont base = baseFont != null ? baseFont : style.font();
        float baseSize = baseFontSize != null ? baseFontSize : style.fontSize();
        Color color = baseColor != null ? baseColor : style.textColor();
        float spacing = lineSpacing != null ? lineSpacing : style.lineSpacing();

        // 1. resolve fragments against the base style and tokenize into words
        //    (runs joined without spaces merge into one word) and hard breaks
        List<Object> tokens = new ArrayList<>();     // List<Run>-words and NEWLINE markers
        List<Run> word = new ArrayList<>();
        for (Fragment f : fragments) {
            PDFont font = f.font != null ? f.font : base;
            float size = f.fontSize != null ? f.fontSize : baseSize;
            Color fragColor = f.color != null ? f.color : color;
            StringBuilder piece = new StringBuilder();
            for (int i = 0; i < f.text.length(); i++) {
                char c = f.text.charAt(i);
                if (c == ' ' || c == '\n') {
                    if (!piece.isEmpty()) {
                        word.add(run(piece.toString(), font, size, fragColor));
                        piece.setLength(0);
                    }
                    if (!word.isEmpty()) {
                        tokens.add(word);
                        word = new ArrayList<>();
                    }
                    if (c == '\n') {
                        tokens.add(NEWLINE);
                    }
                } else {
                    piece.append(c);
                }
            }
            if (!piece.isEmpty()) {
                word.add(run(piece.toString(), font, size, fragColor));
            }
        }
        if (!word.isEmpty()) {
            tokens.add(word);
        }

        // 2. greedy line fill; lines ended by wrapping (not by \n or the end)
        //    are justifiable
        List<List<Run>> lines = new ArrayList<>();
        List<Boolean> justifiables = new ArrayList<>();
        List<Run> line = new ArrayList<>();
        float lineWidth = 0;
        boolean lineHasContent = false;
        for (Object token : tokens) {
            if (token == NEWLINE) {
                lines.add(line);
                justifiables.add(false);
                line = new ArrayList<>();
                lineWidth = 0;
                lineHasContent = false;
                continue;
            }
            @SuppressWarnings("unchecked")
            List<Run> w = (List<Run>) token;
            float wordWidth = totalWidth(w);
            Run spaceStyle = lineHasContent ? line.get(line.size() - 1) : w.get(0);
            float spaceWidth = lineHasContent ? run(" ", spaceStyle.font(), spaceStyle.size(), spaceStyle.color()).width() : 0;
            if (lineWidth + spaceWidth + wordWidth <= availableWidth + EPS) {
                if (lineHasContent) {
                    line.add(run(" ", spaceStyle.font(), spaceStyle.size(), spaceStyle.color()));
                    lineWidth += spaceWidth;
                }
                line.addAll(w);
                lineWidth += wordWidth;
                lineHasContent = true;
                continue;
            }
            if (lineHasContent) {
                lines.add(line);
                justifiables.add(true);
                line = new ArrayList<>();
                lineWidth = 0;
                lineHasContent = false;
            }
            if (wordWidth > availableWidth + EPS) {
                // overlong word: split at character level across its runs
                for (Run r : w) {
                    String rest = r.text();
                    while (!rest.isEmpty()) {
                        int fit = 1;
                        while (fit < rest.length()
                                && lineWidth + TextContent.widthOf(rest.substring(0, fit + 1), r.font(), r.size()) <= availableWidth + EPS) {
                            fit++;
                        }
                        String part = rest.substring(0, fit);
                        float partWidth = TextContent.widthOf(part, r.font(), r.size());
                        if (lineWidth + partWidth > availableWidth + EPS && lineHasContent) {
                            lines.add(line);
                            justifiables.add(true);
                            line = new ArrayList<>();
                            lineWidth = 0;
                            lineHasContent = false;
                            continue;
                        }
                        line.add(run(part, r.font(), r.size(), r.color()));
                        lineWidth += partWidth;
                        lineHasContent = true;
                        rest = rest.substring(fit);
                    }
                }
            } else {
                line.addAll(w);
                lineWidth = wordWidth;
                lineHasContent = true;
            }
        }
        if (lineHasContent || lines.isEmpty()) {
            lines.add(line);
            justifiables.add(false);
        }

        List<Element> elements = new ArrayList<>(lines.size());
        for (int i = 0; i < lines.size(); i++) {
            List<Run> l = lines.get(i);
            elements.add(new RichLineElement(List.copyOf(l), totalWidth(l),
                    lineHeightOf(l, baseSize, spacing), justifiables.get(i)));
        }
        return elements;
    }

    private static final Object NEWLINE = new Object();

    private static float lineHeightOf(List<Run> line, float baseSize, float spacing) {
        float maxSize = baseSize;
        for (Run r : line) {
            maxSize = Math.max(maxSize, r.size());
        }
        return maxSize * spacing;
    }

    private static float totalWidth(List<Run> runs) {
        float sum = 0;
        for (Run r : runs) {
            sum += r.width();
        }
        return sum;
    }

    private static Run run(String text, PDFont font, float size, Color color) {
        String safeText = TextContent.safe(text, font);
        return new Run(safeText, font, size, color, TextContent.widthOf(safeText, font, size));
    }

    private record RichLineElement(List<Run> runs, float lineWidth, float lineHeight, boolean justifiable)
            implements Element {

        @Override
        public float getHeight() {
            return lineHeight;
        }

        @Override
        public void draw(RenderContext ctx) throws IOException {
            if (runs.isEmpty()) {
                return;
            }
            HorizontalAlignment alignment = ctx.style().horizontalAlignment();
            float x = switch (alignment) {
                case LEFT, JUSTIFY -> ctx.x();
                case CENTER -> ctx.x() + (ctx.width() - lineWidth) / 2;
                case RIGHT -> ctx.x() + ctx.width() - lineWidth;
            };
            float extraPerSpace = 0;
            if (alignment == HorizontalAlignment.JUSTIFY && justifiable) {
                int spaceRuns = 0;
                for (Run r : runs) {
                    if (r.isSpace()) {
                        spaceRuns++;
                    }
                }
                if (spaceRuns > 0) {
                    extraPerSpace = (ctx.width() - lineWidth) / spaceRuns;
                }
            }
            // one shared baseline: center the tallest ascent-descent band in the box
            float maxAscent = 0;
            float minDescent = 0;
            for (Run r : runs) {
                maxAscent = Math.max(maxAscent, r.font().getFontDescriptor().getAscent() / 1000f * r.size());
                minDescent = Math.min(minDescent, r.font().getFontDescriptor().getDescent() / 1000f * r.size());
            }
            float baseline = ctx.y() + (lineHeight - (maxAscent - minDescent)) / 2 - minDescent;

            float cursor = x;
            for (Run r : runs) {
                ctx.stream().setNonStrokingColor(r.color());
                ctx.stream().beginText();
                ctx.stream().setFont(r.font(), r.size());
                ctx.stream().newLineAtOffset(cursor, baseline);
                ctx.stream().showText(r.text());
                ctx.stream().endText();
                cursor += r.width();
                if (r.isSpace()) {
                    cursor += extraPerSpace;
                }
            }
        }
    }

    /** Builds a {@link RichTextContent}. Obtained from {@link RichTextContent#builder()}. */
    public static final class Builder {
        private final List<Fragment> fragments = new ArrayList<>();
        private PDFont font;
        private Float fontSize;
        private Color color;
        private Float lineSpacing;

        private Builder() {
        }

        /**
         * Base font for fragments that don't set their own; defaults to the cell style.
         *
         * @param font the base font
         * @return this builder
         */
        public Builder font(PDFont font) {
            this.font = Objects.requireNonNull(font, "font");
            return this;
        }

        /**
         * Base font size for fragments that don't set their own; defaults to the cell style.
         *
         * @param fontSize the base font size in points
         * @return this builder
         */
        public Builder fontSize(float fontSize) {
            if (fontSize <= 0) {
                throw new IllegalArgumentException("fontSize must be > 0");
            }
            this.fontSize = fontSize;
            return this;
        }

        /**
         * Base text color for fragments that don't set their own; defaults to the cell style.
         *
         * @param color the base text color
         * @return this builder
         */
        public Builder color(Color color) {
            this.color = Objects.requireNonNull(color, "color");
            return this;
        }

        /**
         * Line height as a multiple of the tallest font size on each line.
         *
         * @param lineSpacing the line height multiple
         * @return this builder
         */
        public Builder lineSpacing(float lineSpacing) {
            if (lineSpacing <= 0) {
                throw new IllegalArgumentException("lineSpacing must be > 0");
            }
            this.lineSpacing = lineSpacing;
            return this;
        }

        /**
         * Adds a fragment in the base style.
         *
         * @param text the fragment's text
         * @return this builder
         */
        public Builder add(String text) {
            fragments.add(Fragment.of(text));
            return this;
        }

        /**
         * Adds a styled fragment.
         *
         * @param fragment the fragment
         * @return this builder
         */
        public Builder add(Fragment fragment) {
            fragments.add(Objects.requireNonNull(fragment, "fragment"));
            return this;
        }

        /** {@return the finished content} */
        public RichTextContent build() {
            return new RichTextContent(this);
        }
    }
}
