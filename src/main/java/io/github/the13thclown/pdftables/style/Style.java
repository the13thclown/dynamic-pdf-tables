package io.github.the13thclown.pdftables.style;

import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import java.awt.Color;

/**
 * Visual settings for a cell. Every field is nullable; {@code null} means
 * "not set here — inherit". Styles are combined at render time along the
 * cascade cell → row styler → column styler → table default → {@link #defaults()},
 * field by field, first non-null wins. {@link #defaults()} has every field non-null
 * (except backgroundColor), so a fully merged style never exposes null.
 * <p>
 * Besides box styling (padding, borders, background, alignment), a style
 * carries inheritable <em>text defaults</em> — font, font size, text color and
 * line spacing — which text contents fall back to for properties they don't
 * set themselves. Declaring "this table is 9pt" once on the table default
 * style is enough.
 */
public final class Style {

    private static final Style DEFAULTS = Style.builder()
            .padding(Padding.NONE)
            .borderTop(BorderStyle.NONE)
            .borderRight(BorderStyle.NONE)
            .borderBottom(BorderStyle.NONE)
            .borderLeft(BorderStyle.NONE)
            .horizontalAlignment(HorizontalAlignment.LEFT)
            .verticalAlignment(VerticalAlignment.TOP)
            .font(new PDType1Font(Standard14Fonts.FontName.HELVETICA))
            .fontSize(11)
            .textColor(Color.BLACK)
            .lineSpacing(1.2f)
            .build();
    // backgroundColor stays null in DEFAULTS: "no background" is modeled as null
    // after merging, so renderers skip the fill instead of painting white.

    private final Padding padding;
    private final BorderStyle borderTop;
    private final BorderStyle borderRight;
    private final BorderStyle borderBottom;
    private final BorderStyle borderLeft;
    private final Color backgroundColor;
    private final HorizontalAlignment horizontalAlignment;
    private final VerticalAlignment verticalAlignment;
    private final PDFont font;
    private final Float fontSize;
    private final Color textColor;
    private final Float lineSpacing;

    private Style(Builder b) {
        this.padding = b.padding;
        this.borderTop = b.borderTop;
        this.borderRight = b.borderRight;
        this.borderBottom = b.borderBottom;
        this.borderLeft = b.borderLeft;
        this.backgroundColor = b.backgroundColor;
        this.horizontalAlignment = b.horizontalAlignment;
        this.verticalAlignment = b.verticalAlignment;
        this.font = b.font;
        this.fontSize = b.fontSize;
        this.textColor = b.textColor;
        this.lineSpacing = b.lineSpacing;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * The base style every merge chain ends on: no padding, no borders, no
     * background, LEFT/TOP, Helvetica 11pt black text with 1.2 line spacing.
     */
    public static Style defaults() {
        return DEFAULTS;
    }

    /** Returns a style taking each field from this style if set, otherwise from {@code fallback}. */
    public Style mergedOnto(Style fallback) {
        if (fallback == null) {
            return this;
        }
        Builder b = new Builder();
        b.padding = padding != null ? padding : fallback.padding;
        b.borderTop = borderTop != null ? borderTop : fallback.borderTop;
        b.borderRight = borderRight != null ? borderRight : fallback.borderRight;
        b.borderBottom = borderBottom != null ? borderBottom : fallback.borderBottom;
        b.borderLeft = borderLeft != null ? borderLeft : fallback.borderLeft;
        b.backgroundColor = backgroundColor != null ? backgroundColor : fallback.backgroundColor;
        b.horizontalAlignment = horizontalAlignment != null ? horizontalAlignment : fallback.horizontalAlignment;
        b.verticalAlignment = verticalAlignment != null ? verticalAlignment : fallback.verticalAlignment;
        b.font = font != null ? font : fallback.font;
        b.fontSize = fontSize != null ? fontSize : fallback.fontSize;
        b.textColor = textColor != null ? textColor : fallback.textColor;
        b.lineSpacing = lineSpacing != null ? lineSpacing : fallback.lineSpacing;
        return new Style(b);
    }

    public Padding padding() {
        return padding;
    }

    public BorderStyle borderTop() {
        return borderTop;
    }

    public BorderStyle borderRight() {
        return borderRight;
    }

    public BorderStyle borderBottom() {
        return borderBottom;
    }

    public BorderStyle borderLeft() {
        return borderLeft;
    }

    public Color backgroundColor() {
        return backgroundColor;
    }

    public HorizontalAlignment horizontalAlignment() {
        return horizontalAlignment;
    }

    public VerticalAlignment verticalAlignment() {
        return verticalAlignment;
    }

    /** Default font for text contents that don't set their own. */
    public PDFont font() {
        return font;
    }

    public Float fontSize() {
        return fontSize;
    }

    public Color textColor() {
        return textColor;
    }

    /** Line height multiple for text contents that don't set their own. */
    public Float lineSpacing() {
        return lineSpacing;
    }

    public static final class Builder {
        private Padding padding;
        private BorderStyle borderTop;
        private BorderStyle borderRight;
        private BorderStyle borderBottom;
        private BorderStyle borderLeft;
        private Color backgroundColor;
        private HorizontalAlignment horizontalAlignment;
        private VerticalAlignment verticalAlignment;
        private PDFont font;
        private Float fontSize;
        private Color textColor;
        private Float lineSpacing;

        public Builder padding(Padding padding) {
            this.padding = padding;
            return this;
        }

        public Builder borderAll(BorderStyle border) {
            this.borderTop = border;
            this.borderRight = border;
            this.borderBottom = border;
            this.borderLeft = border;
            return this;
        }

        public Builder borderTop(BorderStyle border) {
            this.borderTop = border;
            return this;
        }

        public Builder borderRight(BorderStyle border) {
            this.borderRight = border;
            return this;
        }

        public Builder borderBottom(BorderStyle border) {
            this.borderBottom = border;
            return this;
        }

        public Builder borderLeft(BorderStyle border) {
            this.borderLeft = border;
            return this;
        }

        public Builder backgroundColor(Color color) {
            this.backgroundColor = color;
            return this;
        }

        public Builder horizontalAlignment(HorizontalAlignment alignment) {
            this.horizontalAlignment = alignment;
            return this;
        }

        public Builder verticalAlignment(VerticalAlignment alignment) {
            this.verticalAlignment = alignment;
            return this;
        }

        public Builder font(PDFont font) {
            this.font = font;
            return this;
        }

        public Builder fontSize(float fontSize) {
            if (fontSize <= 0) {
                throw new IllegalArgumentException("fontSize must be > 0");
            }
            this.fontSize = fontSize;
            return this;
        }

        public Builder textColor(Color textColor) {
            this.textColor = textColor;
            return this;
        }

        public Builder lineSpacing(float lineSpacing) {
            if (lineSpacing <= 0) {
                throw new IllegalArgumentException("lineSpacing must be > 0");
            }
            this.lineSpacing = lineSpacing;
            return this;
        }

        public Style build() {
            return new Style(this);
        }
    }
}
