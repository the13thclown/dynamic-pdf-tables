# dynamic-pdf-tables

A dynamic table building library on top of [Apache PDFBox](https://pdfbox.apache.org/) 3.x.

## Core ideas

**Definition time and render time are strictly separated.** Builders capture structure only — no
measurement, no layout, no page math at `build()`. Definition is completely free.

**No rows in the API.** A table is columns plus a flat sequence of cells. Cells auto-flow into the
grid: each one drops into the next unoccupied slot left-to-right, wraps to the next derived row when
the current one is full, and skips slots covered by rowspans. Rows exist only inside the layout
engine.

**Virtual y axis.** At render time the table is first laid out as if the page were infinitely tall.
Page breaks are then *cuts* into that virtual space, applied with a *continue-and-forget* loop: draw
everything above the cut, forget it, re-lay the remainder from virtual 0, repeat until the virtual
height is consumed.

**Elements are the unit of pagination.** Cell content decomposes into atomic elements
(`CellContent.layout(width) → List<Element>`). Rows and cells split across pages naturally — a cell
cut by a page break is drawn with an open bottom edge and continues on the next page with an open
top. Only a single element taller than a page is an error.

Content types: `TextContent` (wrapped text — one element per line, so text paginates line by line),
`RichTextContent` (mixed-style fragments), `VerticalTextContent` (90° rotated), `ImageContent`
(from a file, bytes or `BufferedImage`; scales to fit or to explicit dimensions), `TableContent`
(nested tables), and `PlaceholderContent` (a rectangle showing occupied space, useful for
prototyping layouts).

**Extending:** implement `CellContent.layout(availableWidth, style) → List<Element>` — measurement
and drawing only; pagination, spanning, nesting and the style cascade come free. The full contract
is three types (`CellContent`, `Element`, `RenderContext`); the `layout` package is internal. See
`CustomContentTest` for a complete custom bar-chart content in ~50 lines.

## Usage

```java
Table table = Table.builder()
        .addColumnsOfWidth(150, 150)
        .addColumnOfRelativeWidth(1)      // needs .width(...) on the table
        .width(500)
        .headerRowCount(1)                // first derived row repeats on every page
        .defaultStyle(Style.builder().borderAll(BorderStyle.of(1)).build())
        // header row
        .add(Cell.builder().add(PlaceholderContent.ofHeight(20)).backgroundColor(Color.LIGHT_GRAY).build())
        .add(Cell.builder().add(PlaceholderContent.ofHeight(20)).backgroundColor(Color.LIGHT_GRAY).build())
        .add(Cell.builder().add(PlaceholderContent.ofHeight(20)).backgroundColor(Color.LIGHT_GRAY).build())
        // body cells simply flow — no rows
        .add(Cell.builder().add(PlaceholderContent.ofSize(80, 30)).paddingAll(5).build())
        .add(Cell.builder().add(PlaceholderContent.ofHeight(30)).colSpan(2).build())
        .add(Cell.builder().add(PlaceholderContent.ofHeight(60)).rowSpan(2).build())
        .add(Cell.of(PlaceholderContent.ofHeight(25)))
        .add(Cell.of(PlaceholderContent.ofHeight(25)))
        .add(Cell.of(PlaceholderContent.ofHeight(25)))
        .add(Cell.of(PlaceholderContent.ofHeight(25)))
        .build();

try (PDDocument doc = new PDDocument()) {
    float bottomY = TableDrawer.builder()
            .document(doc)
            .table(table)
            .startX(50).startY(780)       // table top on the first page
            .endY(50)                     // lowest allowed y on any page
            .build()
            .draw();                      // adds pages as needed
    doc.save("out.pdf");
}
```

## Features (v1)

- Fixed and relative column widths
- Padding, per-side borders (thicker border wins at shared edges), background colors, horizontal/vertical alignment incl. `JUSTIFY`
- Style cascade: cell → row styler → column styler → table default → built-in defaults (field-level, non-null wins)
- Inheritable text defaults on `Style` (font, size, color, line spacing): declare "this table is 9pt" once — flows into nested tables too
- Per-row and per-column styling without row objects: `rowStyler(rowIndex -> Style)`, `columnStyler(colIndex -> Style)`
- `colSpan` / `rowSpan` with automatic grid flow; configurable rowspan height distribution (`EQUAL` / `LAST_ROW`)
- Arbitrary content placement inside a cell: `Cell.Builder.addAt(x, y, content)` — offsets from the content box, overlaps allowed, arrangement preserved across page breaks
- Text: `TextContent.builder("...").font(F).fontSize(10).color(c).lineSpacing(1.3f)` — wraps at the content width (spaces, `\n`, mid-word for overlong words), aligns per cell style, splits across pages line by line; unencodable characters become `?`
- Rich text: `RichTextContent.builder().add("plain ").add(RichTextContent.fragment("bold red").font(bold).color(red))` — mixed font/size/color fragments wrap together as one paragraph (words never break at fragment boundaries) and share a common baseline per line
- Images: `ImageContent.builder(pathOrBytesOrBufferedImage).width(70)` — natural size fits down to the content width, explicit width/height scale proportionally (both = stretch); the document-bound XObject is created lazily and cached per document, so a repeated header logo embeds once
- Nested tables: `TableContent.of(innerTable)` — inner rows become atomic elements, so nested tables split across pages at inner row boundaries (any depth)
- Automatic page breaks at element granularity — rows and cells split mid-cell; cut cells draw open by default, or closed on both sides of the break via `TableDrawer.Builder.closeBordersAtPageBreak(true)`
- Repeating header rows on every page (never split)
- Vertical (90° rotated) text: `VerticalTextContent.of("Q1")` for narrow header columns
- Multiple multi-page tables on a shared page sequence (side by side): the `pageSupplier` may return existing pages
- Same-page overflow columns: `TableDrawer.Builder.overflowColumns(3, gap)` — a narrow table fills column regions across the page before starting a new one, headers repeating per region
- Structural validation at render start (`TableValidationException`);
  impossible pagination (element taller than a page, oversized headers) throws `TableLayoutException`

## Building

```
mvn test
```

Render smoke tests write inspectable PDFs to `target/test-output/`.
