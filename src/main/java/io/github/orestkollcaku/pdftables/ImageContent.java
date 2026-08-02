package io.github.orestkollcaku.pdftables;

import io.github.orestkollcaku.pdftables.layout.Element;
import io.github.orestkollcaku.pdftables.render.RenderContext;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

/**
 * An image as cell content — a single atomic {@link Element} (an image never
 * splits across pages; it passes down whole like any element).
 * <p>
 * The definition holds only the image source (file bytes or a
 * {@link BufferedImage}); the document-bound {@link PDImageXObject} is created
 * lazily at draw time and cached per document, so an image repeated on every
 * page (e.g. a logo in a header row) is embedded once.
 * <p>
 * Sizing (1 pixel = 1 point): by default the natural size, scaled down
 * proportionally if wider than the cell's content width. An explicit
 * {@code width} or {@code height} scales proportionally; both together
 * stretch. Horizontal alignment comes from the resolved cell style.
 */
public final class ImageContent implements CellContent {

    private final byte[] bytes;
    private final String name;
    private final BufferedImage image;
    private final int pixelWidth;
    private final int pixelHeight;
    private final Float width;
    private final Float height;
    private final Map<PDDocument, PDImageXObject> xObjects = new WeakHashMap<>();

    private ImageContent(Builder b) {
        this.bytes = b.bytes;
        this.name = b.name;
        this.image = b.image;
        this.pixelWidth = b.pixelWidth;
        this.pixelHeight = b.pixelHeight;
        this.width = b.width;
        this.height = b.height;
    }

    public static ImageContent of(Path path) {
        return builder(path).build();
    }

    public static ImageContent of(byte[] bytes, String name) {
        return builder(bytes, name).build();
    }

    public static ImageContent of(BufferedImage image) {
        return builder(image).build();
    }

    public static Builder builder(Path path) {
        try {
            return builder(Files.readAllBytes(path), path.getFileName().toString());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read image " + path, e);
        }
    }

    public static Builder builder(byte[] bytes, String name) {
        return new Builder(bytes.clone(), name, null);
    }

    public static Builder builder(BufferedImage image) {
        return new Builder(null, "image", image);
    }

    @Override
    public java.util.List<Element> layout(float availableWidth) {
        float aspect = (float) pixelHeight / pixelWidth;
        float w;
        float h;
        if (width != null && height != null) {
            w = width;
            h = height;
        } else if (width != null) {
            w = width;
            h = width * aspect;
        } else if (height != null) {
            h = height;
            w = height / aspect;
        } else {
            w = Math.min(pixelWidth, Math.max(1, availableWidth));
            h = w * aspect;
        }
        return java.util.List.of(new ImageElement(w, h));
    }

    private synchronized PDImageXObject xObject(PDDocument document) throws IOException {
        PDImageXObject cached = xObjects.get(document);
        if (cached == null) {
            cached = image != null
                    ? LosslessFactory.createFromImage(document, image)
                    : PDImageXObject.createFromByteArray(document, bytes, name);
            xObjects.put(document, cached);
        }
        return cached;
    }

    private final class ImageElement implements Element {
        private final float drawWidth;
        private final float drawHeight;

        private ImageElement(float drawWidth, float drawHeight) {
            this.drawWidth = drawWidth;
            this.drawHeight = drawHeight;
        }

        @Override
        public float getHeight() {
            return drawHeight;
        }

        @Override
        public void draw(RenderContext ctx) throws IOException {
            float x = switch (ctx.style().horizontalAlignment()) {
                case LEFT -> ctx.x();
                case CENTER -> ctx.x() + (ctx.width() - drawWidth) / 2;
                case RIGHT -> ctx.x() + ctx.width() - drawWidth;
            };
            ctx.stream().drawImage(xObject(ctx.document()), x, ctx.y(), drawWidth, drawHeight);
        }
    }

    public static final class Builder {
        private final byte[] bytes;
        private final String name;
        private final BufferedImage image;
        private final int pixelWidth;
        private final int pixelHeight;
        private Float width;
        private Float height;

        private Builder(byte[] bytes, String name, BufferedImage image) {
            this.bytes = bytes;
            this.name = Objects.requireNonNull(name, "name");
            this.image = image;
            if (image != null) {
                this.pixelWidth = image.getWidth();
                this.pixelHeight = image.getHeight();
            } else {
                int[] dims = readDimensions(bytes, name);
                this.pixelWidth = dims[0];
                this.pixelHeight = dims[1];
            }
            if (pixelWidth <= 0 || pixelHeight <= 0) {
                throw new IllegalArgumentException("Image '" + name + "' has no dimensions");
            }
        }

        /** Rendered width in points; height follows the aspect ratio unless also set. */
        public Builder width(float width) {
            if (width <= 0) {
                throw new IllegalArgumentException("width must be > 0");
            }
            this.width = width;
            return this;
        }

        /** Rendered height in points; width follows the aspect ratio unless also set. */
        public Builder height(float height) {
            if (height <= 0) {
                throw new IllegalArgumentException("height must be > 0");
            }
            this.height = height;
            return this;
        }

        public ImageContent build() {
            return new ImageContent(this);
        }

        private static int[] readDimensions(byte[] bytes, String name) {
            try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
                Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
                if (!readers.hasNext()) {
                    throw new IllegalArgumentException("Unsupported image format: " + name);
                }
                ImageReader reader = readers.next();
                try {
                    reader.setInput(in);
                    return new int[]{reader.getWidth(0), reader.getHeight(0)};
                } finally {
                    reader.dispose();
                }
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to read image dimensions of " + name, e);
            }
        }
    }
}
