package io.github.the13thclown.pdftables.style;

/**
 * Horizontal alignment of content within its available box. {@code JUSTIFY}
 * stretches wrapped text lines to the full content width (except a
 * paragraph's last line and hard-broken lines); non-text content treats it
 * as {@code LEFT}.
 */
public enum HorizontalAlignment {
    /** Content sits against the left edge of its box. */
    LEFT,
    /** Content is centered within its box. */
    CENTER,
    /** Content sits against the right edge of its box. */
    RIGHT,
    /** Wrapped text lines stretch to the full width; non-text content aligns left. */
    JUSTIFY
}
