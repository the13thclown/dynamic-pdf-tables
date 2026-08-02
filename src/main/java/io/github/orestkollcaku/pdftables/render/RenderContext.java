package io.github.orestkollcaku.pdftables.render;

import io.github.orestkollcaku.pdftables.style.Style;
import org.apache.pdfbox.pdmodel.PDPageContentStream;

/**
 * Everything an element needs to draw itself: the content stream, its box in
 * final PDF coordinates ({@code x},{@code y} = lower-left corner), and the
 * cell's fully resolved style (no null fields except backgroundColor).
 */
public record RenderContext(
        PDPageContentStream stream,
        float x,
        float y,
        float width,
        float height,
        Style style) {
}
