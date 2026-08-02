package io.github.the13thclown.pdftables.style;

import java.awt.Color;
import java.util.Objects;

/** Style of a single cell border side: line width in points and color. */
public record BorderStyle(float width, Color color) {

    public static final BorderStyle NONE = new BorderStyle(0, Color.BLACK);

    public BorderStyle {
        if (width < 0) {
            throw new IllegalArgumentException("Border width must be >= 0");
        }
        Objects.requireNonNull(color, "color");
    }

    public static BorderStyle of(float width, Color color) {
        return new BorderStyle(width, color);
    }

    public static BorderStyle of(float width) {
        return new BorderStyle(width, Color.BLACK);
    }

    public boolean isVisible() {
        return width > 0;
    }
}
