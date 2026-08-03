package io.github.the13thclown.pdftables.style;

/**
 * Inner padding of a cell, in PDF points.
 *
 * @param top    padding above the content box
 * @param right  padding right of the content box
 * @param bottom padding below the content box
 * @param left   padding left of the content box
 */
public record Padding(float top, float right, float bottom, float left) {

    /** No padding. */
    public static final Padding NONE = new Padding(0, 0, 0, 0);

    /** Validates that no side is negative. */
    public Padding {
        if (top < 0 || right < 0 || bottom < 0 || left < 0) {
            throw new IllegalArgumentException("Padding values must be >= 0");
        }
    }

    /**
     * {@return equal padding on all four sides}
     *
     * @param all the padding for all four sides
     */
    public static Padding of(float all) {
        return new Padding(all, all, all, all);
    }

    /**
     * {@return symmetric padding}
     *
     * @param vertical   the top and bottom padding
     * @param horizontal the left and right padding
     */
    public static Padding of(float vertical, float horizontal) {
        return new Padding(vertical, horizontal, vertical, horizontal);
    }

    /** {@return left plus right padding} */
    public float horizontal() {
        return left + right;
    }

    /** {@return top plus bottom padding} */
    public float vertical() {
        return top + bottom;
    }
}
