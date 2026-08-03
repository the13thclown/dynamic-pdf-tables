package io.github.the13thclown.pdftables.render;

import io.github.the13thclown.pdftables.style.Style;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;

/**
 * Everything an element needs to draw itself: the document (for creating
 * document-bound resources like image XObjects), the content stream, its box
 * in final PDF coordinates ({@code x},{@code y} = lower-left corner), the
 * cell's fully resolved style (no null fields except backgroundColor), and the
 * page the box landed on.
 *
 * @param document the document being drawn into
 * @param stream   the open content stream to draw with
 * @param x        the box's left edge in PDF coordinates
 * @param y        the box's bottom edge in PDF coordinates
 * @param width    the box width in points
 * @param height   the box height in points
 * @param style    the cell's fully resolved style
 * @param pageRef  the page the box landed on
 */
public record RenderContext(
        PDDocument document,
        PDPageContentStream stream,
        float x,
        float y,
        float width,
        float height,
        Style style,
        PageRef pageRef) {

    /** {@return the page this box is being drawn on} */
    public PDPage page() {
        return pageRef.page();
    }

    /** {@return the zero-based position of {@link #page()} in the document} */
    public int pageIndex() {
        return pageRef.pageIndex();
    }

    /**
     * Postpones a drawing operation until the whole table has been drawn, for
     * content that depends on pagination it cannot see yet ("continued on page
     * 5"). The callback gets a context with this same page, box and style, and
     * a fresh content stream appended to the page.
     *
     * @param draw the drawing operation to run once pagination is settled
     */
    public void defer(DeferredDraw draw) {
        pageRef.deferrals().add(pageRef, x, y, width, height, style, draw);
    }
}
