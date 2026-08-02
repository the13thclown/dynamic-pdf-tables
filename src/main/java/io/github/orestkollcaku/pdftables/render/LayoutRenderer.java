package io.github.orestkollcaku.pdftables.render;

import io.github.orestkollcaku.pdftables.layout.Element;
import io.github.orestkollcaku.pdftables.layout.LayoutCell;
import io.github.orestkollcaku.pdftables.layout.VirtualLayout;
import io.github.orestkollcaku.pdftables.style.BorderStyle;
import io.github.orestkollcaku.pdftables.style.HorizontalAlignment;
import io.github.orestkollcaku.pdftables.style.Style;
import org.apache.pdfbox.pdmodel.PDPageContentStream;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Draws the window {@code [fromY, toY)} of a virtual layout onto a content
 * stream. The single place where virtual y meets PDF coordinates
 * ({@code pageY = topY − virtualY}, where {@code topY} is the page y of
 * virtual 0). Used by the page loop (window {@code [0, cutY)}) and by nested
 * tables (one window per inner row block).
 * <p>
 * Three passes keep the z-order right — backgrounds, then contents, then
 * borders. Border segments stroke thinnest-first so at shared (double-drawn)
 * edges the thicker border ends up on top. Cell boxes crossing the window's
 * bottom edge draw cut open (no bottom border); {@code continuedTop} cells
 * draw without a top border.
 */
public final class LayoutRenderer {

    private static final float EPS = 0.01f;
    private static final Style LEFT_ALIGNED = Style.builder()
            .horizontalAlignment(HorizontalAlignment.LEFT)
            .build();

    private LayoutRenderer() {
    }

    public static void render(PDPageContentStream cs, VirtualLayout layout,
                              float fromY, float toY, float originX, float topY) throws IOException {
        render(cs, layout, fromY, toY, originX, topY, false);
    }

    /**
     * As {@link #render(PDPageContentStream, VirtualLayout, float, float, float, float)},
     * but with {@code closeCutBorders} a cell cut by the window's bottom edge
     * draws its bottom border along the cut line, and a continued cell draws
     * its top border — cut boxes look closed on every page instead of open.
     */
    public static void render(PDPageContentStream cs, VirtualLayout layout,
                              float fromY, float toY, float originX, float topY,
                              boolean closeCutBorders) throws IOException {
        for (LayoutCell c : layout.cells()) {
            if (!visible(c, fromY, toY)) {
                continue;
            }
            Style s = c.style();
            if (s.backgroundColor() != null) {
                float visTop = Math.max(c.virtualTop(), fromY);
                float visBottom = Math.min(c.virtualTop() + c.height(), toY);
                cs.setNonStrokingColor(s.backgroundColor());
                cs.addRect(originX + c.x(), topY - visBottom, c.width(), visBottom - visTop);
                cs.fill();
            }
        }
        for (LayoutCell c : layout.cells()) {
            if (!visible(c, fromY, toY)) {
                continue;
            }
            Style s = c.style();
            float contentX = originX + c.x() + s.padding().left();
            float contentWidth = Math.max(0, c.width() - s.padding().horizontal());
            for (int i = 0; i < c.items().size(); i++) {
                LayoutCell.Item item = c.items().get(i);
                Element e = item.element();
                float top = c.elementTop(i);
                float bottom = top + e.getHeight();
                if (top < fromY - EPS || bottom > toY + EPS) {
                    continue;
                }
                if (item.positioned()) {
                    // positioned items sit exactly at their offset; cell alignment
                    // must not re-position them within the leftover width
                    e.draw(new RenderContext(cs, contentX + item.x(), topY - bottom,
                            Math.max(0, contentWidth - item.x()), e.getHeight(), LEFT_ALIGNED.mergedOnto(s)));
                } else {
                    e.draw(new RenderContext(cs, contentX, topY - bottom, contentWidth, e.getHeight(), s));
                }
            }
        }
        // collect all border segments, then stroke thinnest-first: at shared
        // edges (adjacent cells double-draw) the thicker border ends up on top
        // instead of whichever cell happened to come later
        List<BorderSegment> borders = new ArrayList<>();
        for (LayoutCell c : layout.cells()) {
            if (!visible(c, fromY, toY)) {
                continue;
            }
            Style s = c.style();
            float left = originX + c.x();
            float right = left + c.width();
            float cellBottom = c.virtualTop() + c.height();
            float visTop = topY - Math.max(c.virtualTop(), fromY);
            float visBottom = topY - Math.min(cellBottom, toY);

            if ((!c.continuedTop() || closeCutBorders) && c.virtualTop() >= fromY - EPS) {
                borders.add(new BorderSegment(s.borderTop(), left, visTop, right, visTop));
            }
            if (cellBottom <= toY + EPS || closeCutBorders) {
                borders.add(new BorderSegment(s.borderBottom(), left, visBottom, right, visBottom));
            }
            borders.add(new BorderSegment(s.borderLeft(), left, visTop, left, visBottom));
            borders.add(new BorderSegment(s.borderRight(), right, visTop, right, visBottom));
        }
        borders.sort(Comparator.comparingDouble(seg -> seg.style().width()));
        for (BorderSegment seg : borders) {
            line(cs, seg.style(), seg.x1(), seg.y1(), seg.x2(), seg.y2());
        }
    }

    private static boolean visible(LayoutCell c, float fromY, float toY) {
        return c.virtualTop() < toY - EPS && c.virtualTop() + c.height() > fromY + EPS;
    }

    private static void line(PDPageContentStream cs, BorderStyle border,
                             float x1, float y1, float x2, float y2) throws IOException {
        if (!border.isVisible()) {
            return;
        }
        cs.setStrokingColor(border.color());
        cs.setLineWidth(border.width());
        cs.moveTo(x1, y1);
        cs.lineTo(x2, y2);
        cs.stroke();
    }

    private record BorderSegment(BorderStyle style, float x1, float y1, float x2, float y2) {
    }
}
