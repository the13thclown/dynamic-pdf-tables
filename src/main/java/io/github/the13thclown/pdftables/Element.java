package io.github.the13thclown.pdftables;

import io.github.the13thclown.pdftables.render.RenderContext;

import java.io.IOException;

/**
 * The atomic-by-default unit of pagination. A cell's content decomposes into
 * elements at render time; when a page cut lands inside an element, the engine
 * asks it to {@link #splitAt(float) split} exactly there — an element that
 * declines (the default) passes down to the next page whole. Once laid out,
 * an element's height is fixed.
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

    /**
     * Asks this element to split at a page cut landing {@code availableHeight}
     * points below its top. Returning a {@link Split} draws the top piece in
     * the remaining space of the current page and sends the bottom piece to
     * the next page, where it may be asked to split again — an element that
     * supports this can never be "too tall for a page" and no space is wasted
     * above the cut. Returning {@code null} (the default) keeps the element
     * atomic: it passes down whole.
     * <p>
     * Contract for a non-null result: {@code top.getHeight() <= availableHeight}
     * and both pieces taller than zero — otherwise the engine ignores the split
     * and treats the element as atomic for this cut.
     */
    default Split splitAt(float availableHeight) {
        return null;
    }

    /** The two pieces of a split element: {@code top} stays on the current page. */
    record Split(Element top, Element bottom) {
    }
}
