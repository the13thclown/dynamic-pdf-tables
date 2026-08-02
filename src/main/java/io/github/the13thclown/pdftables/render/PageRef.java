package io.github.the13thclown.pdftables.render;

import org.apache.pdfbox.pdmodel.PDPage;

/**
 * The page an element is currently being drawn on, together with the sink that
 * collects {@link DeferredDraw}s for the running draw. Elements receive one via
 * {@link RenderContext} and never create it — {@link Deferrals#pageRef} does.
 */
public final class PageRef {

    private final PDPage page;
    private final int pageIndex;
    private final Deferrals deferrals;

    PageRef(PDPage page, int pageIndex, Deferrals deferrals) {
        this.page = page;
        this.pageIndex = pageIndex;
        this.deferrals = deferrals;
    }

    public PDPage page() {
        return page;
    }

    /** Zero-based position of this page in the document at the time it was drawn. */
    public int pageIndex() {
        return pageIndex;
    }

    Deferrals deferrals() {
        return deferrals;
    }
}
