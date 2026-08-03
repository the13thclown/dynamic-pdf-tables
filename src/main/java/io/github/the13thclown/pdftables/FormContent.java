package io.github.the13thclown.pdftables;

import io.github.the13thclown.pdftables.render.RenderContext;
import io.github.the13thclown.pdftables.style.Style;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.util.Matrix;

import java.awt.geom.AffineTransform;
import java.util.List;
import java.util.Objects;

/**
 * A {@link PDFormXObject} as cell content — the vector counterpart of
 * {@link ImageContent}, for reusable drawing snippets such as stamps, icons or
 * signatures that are already embedded in the document.
 * <p>
 * Unlike an image, a form is document-bound by nature: it must belong to the
 * document it is drawn into. Callers therefore build it against the target
 * document before defining the table.
 * <p>
 * Sizing comes from the form's bounding box; an explicit {@code width} or
 * {@code height} scales it proportionally, both together stretch it. The form
 * never scales itself down to the cell — a form wider than the content width
 * overflows, because clipping a stamp would silently falsify it.
 */
public final class FormContent implements CellContent {

    private final PDFormXObject form;
    private final Float width;
    private final Float height;

    private FormContent(Builder b) {
        this.form = b.form;
        this.width = b.width;
        this.height = b.height;
    }

    /**
     * Shorthand for a form drawn at its natural bounding box size.
     *
     * @param form the form XObject, already embedded in the target document
     * @return the content
     */
    public static FormContent of(PDFormXObject form) {
        return builder(form).build();
    }

    /**
     * {@return a new builder}
     *
     * @param form the form XObject, already embedded in the target document
     */
    public static Builder builder(PDFormXObject form) {
        return new Builder(form);
    }

    @Override
    public List<Element> layout(float availableWidth, Style style) {
        float naturalWidth = form.getBBox().getWidth();
        float naturalHeight = form.getBBox().getHeight();
        float w;
        float h;
        if (width != null && height != null) {
            w = width;
            h = height;
        } else if (width != null) {
            w = width;
            h = naturalHeight * (width / naturalWidth);
        } else if (height != null) {
            h = height;
            w = naturalWidth * (height / naturalHeight);
        } else {
            w = naturalWidth;
            h = naturalHeight;
        }
        return List.of(new FormElement(w, h));
    }

    private final class FormElement implements Element {
        private final float drawWidth;
        private final float drawHeight;

        private FormElement(float drawWidth, float drawHeight) {
            this.drawWidth = drawWidth;
            this.drawHeight = drawHeight;
        }

        @Override
        public float getHeight() {
            return drawHeight;
        }

        @Override
        public void draw(RenderContext ctx) throws java.io.IOException {
            float x = switch (ctx.style().horizontalAlignment()) {
                case LEFT, JUSTIFY -> ctx.x();
                case CENTER -> ctx.x() + (ctx.width() - drawWidth) / 2;
                case RIGHT -> ctx.x() + ctx.width() - drawWidth;
            };
            // the form draws in its own coordinate space with its BBox origin at
            // (0,0), so place it by transforming the space rather than the form
            float bboxWidth = form.getBBox().getWidth();
            float bboxHeight = form.getBBox().getHeight();
            AffineTransform placement = AffineTransform.getTranslateInstance(x, ctx.y());
            placement.scale(drawWidth / bboxWidth, drawHeight / bboxHeight);
            placement.translate(-form.getBBox().getLowerLeftX(), -form.getBBox().getLowerLeftY());

            ctx.stream().saveGraphicsState();
            ctx.stream().transform(new Matrix(placement));
            ctx.stream().drawForm(form);
            ctx.stream().restoreGraphicsState();
        }
    }

    /** Builds a {@link FormContent}. Obtained from {@link FormContent#builder}. */
    public static final class Builder {
        private final PDFormXObject form;
        private Float width;
        private Float height;

        private Builder(PDFormXObject form) {
            this.form = Objects.requireNonNull(form, "form");
            if (form.getBBox() == null
                    || form.getBBox().getWidth() <= 0 || form.getBBox().getHeight() <= 0) {
                throw new IllegalArgumentException("Form XObject has no usable bounding box");
            }
        }

        /**
         * Rendered width in points; height follows the bounding box ratio unless also set.
         *
         * @param width the width in points
         * @return this builder
         */
        public Builder width(float width) {
            if (width <= 0) {
                throw new IllegalArgumentException("width must be > 0");
            }
            this.width = width;
            return this;
        }

        /**
         * Rendered height in points; width follows the bounding box ratio unless also set.
         *
         * @param height the height in points
         * @return this builder
         */
        public Builder height(float height) {
            if (height <= 0) {
                throw new IllegalArgumentException("height must be > 0");
            }
            this.height = height;
            return this;
        }

        /** {@return the finished content} */
        public FormContent build() {
            return new FormContent(this);
        }
    }
}
