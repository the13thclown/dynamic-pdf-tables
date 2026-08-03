package io.github.the13thclown.pdftables.style;

import java.awt.Color;
import java.util.Objects;

/**
 * Style of a single cell border side: line width in points and color.
 *
 * @param width the line width in points; 0 means no border
 * @param color the line color
 */
public record BorderStyle(float width, Color color) {

    /** No border. */
    public static final BorderStyle NONE = new BorderStyle(0, Color.BLACK);

    /** Validates that the width is not negative and the color is present. */
    public BorderStyle {
        if (width < 0) {
            throw new IllegalArgumentException("Border width must be >= 0");
        }
        Objects.requireNonNull(color, "color");
    }

    /**
     * {@return a border of the given width and color}
     *
     * @param width the line width in points
     * @param color the line color
     */
    public static BorderStyle of(float width, Color color) {
        return new BorderStyle(width, color);
    }

    /**
     * {@return a black border of the given width}
     *
     * @param width the line width in points
     */
    public static BorderStyle of(float width) {
        return new BorderStyle(width, Color.BLACK);
    }

    /** {@return whether this border draws anything (width above zero)} */
    public boolean isVisible() {
        return width > 0;
    }
}
