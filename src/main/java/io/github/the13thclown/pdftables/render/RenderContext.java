package io.github.the13thclown.pdftables.render;

import io.github.the13thclown.pdftables.style.Style;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPageContentStream;

/**
 * Everything an element needs to draw itself: the document (for creating
 * document-bound resources like image XObjects), the content stream, its box
 * in final PDF coordinates ({@code x},{@code y} = lower-left corner), and the
 * cell's fully resolved style (no null fields except backgroundColor).
 */
public record RenderContext(
        PDDocument document,
        PDPageContentStream stream,
        float x,
        float y,
        float width,
        float height,
        Style style) {
}
