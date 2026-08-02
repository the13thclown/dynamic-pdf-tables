package io.github.the13thclown.pdftables;

import io.github.the13thclown.pdftables.layout.Element;
import io.github.the13thclown.pdftables.render.RenderContext;

import java.awt.Color;
import java.io.IOException;
import java.util.List;

/**
 * The v1 stand-in for real cell content: a single rectangle that visually
 * shows how much space the content occupies. Declared with an explicit size
 * or full available width; positioned within the cell's content box per the
 * resolved horizontal alignment.
 */
public final class PlaceholderContent implements CellContent {

    private static final Color FILL = new Color(215, 225, 245);
    private static final Color STROKE = new Color(90, 110, 160);

    private final Float width;
    private final float height;

    private PlaceholderContent(Float width, float height) {
        if (height < 0 || (width != null && width < 0)) {
            throw new IllegalArgumentException("Placeholder dimensions must be >= 0");
        }
        this.width = width;
        this.height = height;
    }

    /** A placeholder of a declared width and height. */
    public static PlaceholderContent ofSize(float width, float height) {
        return new PlaceholderContent(width, height);
    }

    /** A placeholder spanning the full available width of the cell. */
    public static PlaceholderContent ofHeight(float height) {
        return new PlaceholderContent(null, height);
    }

    @Override
    public List<Element> layout(float availableWidth, io.github.the13thclown.pdftables.style.Style style) {
        float w = width == null ? availableWidth : Math.min(width, availableWidth);
        return List.of(new PlaceholderElement(Math.max(0, w), height));
    }

    private record PlaceholderElement(float rectWidth, float rectHeight) implements Element {

        @Override
        public float getHeight() {
            return rectHeight;
        }

        @Override
        public void draw(RenderContext ctx) throws IOException {
            if (rectWidth <= 0 || rectHeight <= 0) {
                return;
            }
            float x = switch (ctx.style().horizontalAlignment()) {
                case LEFT, JUSTIFY -> ctx.x();
                case CENTER -> ctx.x() + (ctx.width() - rectWidth) / 2;
                case RIGHT -> ctx.x() + ctx.width() - rectWidth;
            };
            ctx.stream().setNonStrokingColor(FILL);
            ctx.stream().addRect(x, ctx.y(), rectWidth, rectHeight);
            ctx.stream().fill();
            ctx.stream().setStrokingColor(STROKE);
            ctx.stream().setLineWidth(0.5f);
            ctx.stream().addRect(x, ctx.y(), rectWidth, rectHeight);
            ctx.stream().stroke();
        }
    }
}
