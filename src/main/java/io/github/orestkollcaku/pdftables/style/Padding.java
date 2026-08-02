package io.github.orestkollcaku.pdftables.style;

/** Inner padding of a cell, in PDF points. */
public record Padding(float top, float right, float bottom, float left) {

    public static final Padding NONE = new Padding(0, 0, 0, 0);

    public Padding {
        if (top < 0 || right < 0 || bottom < 0 || left < 0) {
            throw new IllegalArgumentException("Padding values must be >= 0");
        }
    }

    public static Padding of(float all) {
        return new Padding(all, all, all, all);
    }

    public static Padding of(float vertical, float horizontal) {
        return new Padding(vertical, horizontal, vertical, horizontal);
    }

    public float horizontal() {
        return left + right;
    }

    public float vertical() {
        return top + bottom;
    }
}
