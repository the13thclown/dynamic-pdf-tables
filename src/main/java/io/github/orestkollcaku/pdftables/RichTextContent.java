package io.github.orestkollcaku.pdftables;

import io.github.orestkollcaku.pdftables.layout.Element;
import io.github.orestkollcaku.pdftables.render.RenderContext;
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
 * A paragraph of mixed-style text: fragments with their own font, size or
 * color flow and wrap together as one text. Words are never broken at fragment
 * boundaries — adjacent fragments without a space between them form one word.
 * Like {@link TextContent}, each wrapped line is one atomic {@link Element},
 * so rich text paginates line by line.
 * <p>
 * Fragment styles inherit the builder's base font/size/color where unset:
 * <pre>{@code
 * RichTextContent.builder().fontSize(10)
 *     .add("Total: ")
 *     .add(RichTextContent.fragment("123.45 EUR").font(bold).color(red))
 *     .build()
 * }</pre>
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

        public static Fragment of(String text) {
            return new Fragment(text, null, null, null);
        }

        public Fragment font(PDFont font) {
            return new Fragment(text, font, fontSize, color);
        }

        public Fragment fontSize(float size) {
            return new Fragment(text, font, size, color);
        }

        public Fragment color(Color color) {
            return new Fragment(text, font, fontSize, color);
        }
    }

    /** Shorthand for {@link Fragment#of}. */
    public static Fragment fragment(String text) {
        return Fragment.of(text);
    }

    private final List<Fragment> fragments;
    private final PDFont baseFont;
    private final float baseFontSize;
    private final Color baseColor;
    private final float lineSpacing;

    private RichTextContent(Builder b) {
        this.fragments = List.copyOf(b.fragments);
        this.baseFont = b.font;
        this.baseFontSize = b.fontSize;
        this.baseColor = b.color;
        this.lineSpacing = b.lineSpacing;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** A run: a maximal same-styled piece of text with a fixed width. */
    private record Run(String text, PDFont font, float size, Color color, float width) {
    }

    @Override
    public List<Element> layout(float availableWidth) {
        // 1. resolve fragments against the base style and tokenize into words
        //    (runs joined without spaces merge into one word) and hard breaks
        List<Object> tokens = new ArrayList<>();     // List<Run>-words and NEWLINE markers
        List<Run> word = new ArrayList<>();
        for (Fragment f : fragments) {
            PDFont font = f.font != null ? f.font : baseFont;
            float size = f.fontSize != null ? f.fontSize : baseFontSize;
            Color color = f.color != null ? f.color : baseColor;
            StringBuilder piece = new StringBuilder();
            for (int i = 0; i < f.text.length(); i++) {
                char c = f.text.charAt(i);
                if (c == ' ' || c == '\n') {
                    if (!piece.isEmpty()) {
                        word.add(run(piece.toString(), font, size, color));
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
                word.add(run(piece.toString(), font, size, color));
            }
        }
        if (!word.isEmpty()) {
            tokens.add(word);
        }

        // 2. greedy line fill
        List<List<Run>> lines = new ArrayList<>();
        List<Run> line = new ArrayList<>();
        float lineWidth = 0;
        boolean lineHasContent = false;
        for (Object token : tokens) {
            if (token == NEWLINE) {
                lines.add(line);
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
                                && lineWidth + widthOf(rest.substring(0, fit + 1), r.font(), r.size()) <= availableWidth + EPS) {
                            fit++;
                        }
                        String part = rest.substring(0, fit);
                        float partWidth = widthOf(part, r.font(), r.size());
                        if (lineWidth + partWidth > availableWidth + EPS && lineHasContent) {
                            lines.add(line);
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
        }

        List<Element> elements = new ArrayList<>(lines.size());
        for (List<Run> l : lines) {
            elements.add(new RichLineElement(List.copyOf(l), totalWidth(l), lineHeightOf(l), lineSpacing));
        }
        return elements;
    }

    private static final Object NEWLINE = new Object();

    private float lineHeightOf(List<Run> line) {
        float maxSize = baseFontSize;
        for (Run r : line) {
            maxSize = Math.max(maxSize, r.size());
        }
        return maxSize * lineSpacing;
    }

    private static float totalWidth(List<Run> runs) {
        float sum = 0;
        for (Run r : runs) {
            sum += r.width();
        }
        return sum;
    }

    private static Run run(String text, PDFont font, float size, Color color) {
        return new Run(safe(text, font), font, size, color, widthOf(safe(text, font), font, size));
    }

    private static String safe(String text, PDFont font) {
        try {
            font.encode(text);
            return text;
        } catch (IOException | IllegalArgumentException e) {
            StringBuilder sb = new StringBuilder(text.length());
            for (int i = 0; i < text.length(); ) {
                int cp = text.codePointAt(i);
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

    private static float widthOf(String s, PDFont font, float size) {
        try {
            return font.getStringWidth(s) / 1000f * size;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to measure text with font " + font.getName(), e);
        }
    }

    private record RichLineElement(List<Run> runs, float lineWidth, float lineHeight, float lineSpacing)
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
            float x = switch (ctx.style().horizontalAlignment()) {
                case LEFT -> ctx.x();
                case CENTER -> ctx.x() + (ctx.width() - lineWidth) / 2;
                case RIGHT -> ctx.x() + ctx.width() - lineWidth;
            };
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
            }
        }
    }

    public static final class Builder {
        private final List<Fragment> fragments = new ArrayList<>();
        private PDFont font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        private float fontSize = 11;
        private Color color = Color.BLACK;
        private float lineSpacing = 1.2f;

        /** Base font for fragments that don't set their own. */
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

        /** Line height as a multiple of the tallest font size on each line; default 1.2. */
        public Builder lineSpacing(float lineSpacing) {
            if (lineSpacing <= 0) {
                throw new IllegalArgumentException("lineSpacing must be > 0");
            }
            this.lineSpacing = lineSpacing;
            return this;
        }

        /** Adds a fragment in the base style. */
        public Builder add(String text) {
            fragments.add(Fragment.of(text));
            return this;
        }

        public Builder add(Fragment fragment) {
            fragments.add(Objects.requireNonNull(fragment, "fragment"));
            return this;
        }

        public RichTextContent build() {
            return new RichTextContent(this);
        }
    }
}
