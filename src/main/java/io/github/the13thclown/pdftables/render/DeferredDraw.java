package io.github.the13thclown.pdftables.render;

import java.io.IOException;

/**
 * A drawing operation an element postpones until the whole table has been
 * drawn, registered via {@link RenderContext#defer(DeferredDraw)}.
 * <p>
 * It exists for content that cannot know what to paint while it is being
 * painted, because the answer depends on pagination that has not happened yet:
 * "page 3 of 7", "continued on page 5", "stamps on page 9". The callback runs
 * once the page count and every element's page are settled, and receives a
 * {@link RenderContext} for the same page, box and style as the original draw —
 * only the content stream is a fresh one, appended to that page.
 */
@FunctionalInterface
public interface DeferredDraw {

    void draw(RenderContext ctx) throws IOException;
}
