package io.github.the13thclown.pdftables;

import io.github.the13thclown.pdftables.style.Style;

import java.util.List;

/**
 * A piece of content inside a cell. Contents are pure definition objects —
 * nothing is measured until render time, when the engine calls
 * {@link #layout(float, Style)} with the cell's actual content width and fully
 * resolved style, and the content decomposes into atomic {@link Element}s
 * (the units of pagination).
 * <p>
 * The style carries the inheritable text defaults (font, size, color, line
 * spacing) that text contents fall back to; contents that size themselves
 * (images, placeholders) may ignore it at layout time.
 */
public interface CellContent {

    /**
     * Lays this content out for the given available width and returns its
     * elements, top to bottom. {@code style} is the cell's resolved style —
     * every field non-null except backgroundColor.
     */
    List<Element> layout(float availableWidth, Style style);
}
