package io.github.the13thclown.pdftables;

import io.github.the13thclown.pdftables.layout.Element;

import java.util.List;

/**
 * A piece of content inside a cell. Contents are pure definition objects —
 * nothing is measured until render time, when the engine calls
 * {@link #layout(float)} with the cell's actual content width and the content
 * decomposes into atomic {@link Element}s (the units of pagination).
 * <p>
 * Future content types (text, images, nested tables) plug in here without any
 * change to the layout engine: text would yield one element per line, an image
 * a single element.
 */
public interface CellContent {

    /** Lays this content out for the given available width and returns its elements, top to bottom. */
    List<Element> layout(float availableWidth);
}
