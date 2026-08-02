package io.github.orestkollcaku.pdftables.layout;

import io.github.orestkollcaku.pdftables.render.RenderContext;

import java.io.IOException;

/**
 * The atomic unit of pagination. A cell's content decomposes into elements at
 * render time; a page cut never splits an element — an element either fits
 * entirely on the current page or passes down whole to the next one.
 * Once laid out, an element's height is fixed.
 */
public interface Element {

    /** Height of this element in points, fixed once the content has been laid out. */
    float getHeight();

    /**
     * Draws this element into its box. The box in {@code ctx} is in final PDF
     * coordinates (origin bottom-left), spans the full content width of the cell,
     * and is exactly {@link #getHeight()} tall — elements know nothing about
     * cells or pages. Horizontal alignment within the box is the element's job,
     * using the resolved style in {@code ctx}.
     */
    void draw(RenderContext ctx) throws IOException;
}
