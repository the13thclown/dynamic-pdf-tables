package io.github.the13thclown.pdftables.render;

import io.github.the13thclown.pdftables.style.Style;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Collects the {@link DeferredDraw}s registered during a draw and replays them
 * once pagination is complete. Owned by the drawer, handed to elements only
 * indirectly through {@link RenderContext}.
 */
public final class Deferrals {

    /**
     * A deferred draw may register further deferred draws; each such round is
     * replayed in turn. The bound only exists to turn a callback that defers
     * itself forever into an error instead of a hang.
     */
    private static final int MAX_ROUNDS = 8;

    private final List<Entry> entries = new ArrayList<>();

    public PageRef pageRef(PDPage page, int pageIndex) {
        return new PageRef(page, pageIndex, this);
    }

    void add(PageRef ref, float x, float y, float width, float height, Style style, DeferredDraw draw) {
        entries.add(new Entry(ref, x, y, width, height, style, draw));
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    /**
     * Runs every registered draw, one appended content stream per page, in the
     * order the pages were first deferred to.
     */
    public void runAll(PDDocument document) throws IOException {
        for (int round = 0; !entries.isEmpty(); round++) {
            if (round == MAX_ROUNDS) {
                throw new IllegalStateException("Deferred draws still registering new deferred draws after "
                        + MAX_ROUNDS + " rounds");
            }
            List<Entry> pending = List.copyOf(entries);
            entries.clear();
            runRound(document, pending);
        }
    }

    private static void runRound(PDDocument document, List<Entry> pending) throws IOException {
        Map<COSDictionary, List<Entry>> byPage = new LinkedHashMap<>();
        for (Entry e : pending) {
            byPage.computeIfAbsent(e.ref().page().getCOSObject(), k -> new ArrayList<>()).add(e);
        }
        for (List<Entry> onePage : byPage.values()) {
            PDPage page = onePage.get(0).ref().page();
            try (PDPageContentStream cs = new PDPageContentStream(
                    document, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
                for (Entry e : onePage) {
                    e.draw().draw(new RenderContext(
                            document, cs, e.x(), e.y(), e.width(), e.height(), e.style(), e.ref()));
                }
            }
        }
    }

    private record Entry(PageRef ref, float x, float y, float width, float height,
                         Style style, DeferredDraw draw) {
    }
}
