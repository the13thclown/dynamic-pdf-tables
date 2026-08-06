# dynamic-pdf-tables

A dynamic table building library on top of [Apache PDFBox](https://pdfbox.apache.org/) 3.x.

## Installation

Releases are published to Maven Central:

```xml
<dependency>
    <groupId>io.github.the13thclown</groupId>
    <artifactId>dynamic-pdf-tables</artifactId>
    <version>0.3.0</version>
</dependency>
```

Requires Java 17+. PDFBox 3.x is the only runtime dependency.

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

Content types: `TextContent` (wrapped text — one element per line, so text paginates line by line,
with optional `strikethrough`/`underline`), `RichTextContent` (mixed-style fragments),
`VerticalTextContent` (90° rotated), `ImageContent` (from a file, bytes or `BufferedImage`; scales to
fit or to explicit dimensions), `FormContent` (a `PDFormXObject` — stamps, icons, imported pages),
`TableContent` (nested tables), and `PlaceholderContent` (a rectangle showing occupied space, useful
for prototyping layouts).

**Extending:** implement `CellContent.layout(availableWidth, style) → List<Element>` — measurement
and drawing only; pagination, spanning, nesting and the style cascade come free. The full contract
is three types (`CellContent`, `Element`, `RenderContext`); the `layout` package is internal. See
`CustomContentTest` for a complete custom bar-chart content in ~50 lines.

**Forward references.** An element that has to name a page it cannot see yet ("continued on page 5")
calls `RenderContext.defer(...)`. The callback runs after the last page is drawn and gets a context
for the same page, box and style, so the value can be resolved once pagination is settled. See
`DeferredDrawTest`.

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

### Spans must tile

Cells auto-flow forward: each cell takes the next free slot, and the cursor never goes back to
fill earlier gaps (same semantics as HTML table auto-layout). For a clean, gap-free grid, treat
`colSpan`/`rowSpan` cells like Tetris pieces — they must tile:

1. **Counts**: the slots your cells cover between two row boundaries should sum to a multiple of
   the column count. If a group of cells covers 17 slots in a 4-column table, every repetition
   ends mid-row and shifts the next group sideways.
2. **Geometry**: a `colSpan(n)` needs *n consecutive* free columns in some row. If the free slots
   in the current rows are split around an occupied column, the spanning cell wraps forward to a
   fresh row and the skipped slots stay empty forever (rendered as nothing — no border, no
   background).

A definition that violates these rules still renders — pagination, borders and continuations all
behave — but the output shows the mismatch: uncovered gaps and staggered groups. That is the
definition's shape, faithfully drawn (see `spans-stress.pdf` in the test output for a deliberately
untiled example, and `spans.pdf` for a tiled one).

## Features (v1)

- Fixed and relative column widths
- Padding, per-side borders (thicker border wins at shared edges), background colors, horizontal/vertical alignment incl. `JUSTIFY`
- Style cascade: cell → row styler → column styler → table default → built-in defaults (field-level, non-null wins)
- Inheritable text defaults on `Style` (font, size, color, line spacing): declare "this table is 9pt" once — flows into nested tables too
- Per-row and per-column styling without row objects: `rowStyler(rowIndex -> Style)`, `columnStyler(colIndex -> Style)`
- `colSpan` / `rowSpan` with automatic grid flow; configurable rowspan height distribution (`EQUAL` / `LAST_ROW`)
- Arbitrary content placement inside a cell: `Cell.Builder.addAt(x, y, content)` — offsets from the content box, overlaps allowed, arrangement preserved across page breaks
- Bottom-anchored content: `Cell.Builder.addBottom(content)` stacks against the bottom of the cell while `add(...)` still flows from the top — a label above and a sign-off below in one box, which vertical alignment cannot express since it moves the whole stack. In a cell cut across pages, bottom-anchored content lands on its **last** page
- Text: `TextContent.builder("...").font(F).fontSize(10).color(c).lineSpacing(1.3f)` — wraps at the content width (spaces, `\n`, mid-word for overlong words), aligns per cell style, splits across pages line by line; unencodable characters become `?`
- Per-line text decorations: `.strikethrough(true)`, `.underline(true)`, `.highlight(color, radius)` (filled box behind the glyphs, alpha honoured), `.frame(color, width, radius)` (outline around the glyphs) — sized to the text, not the cell
- Rich text: `RichTextContent.builder().add("plain ").add(RichTextContent.fragment("bold red").font(bold).color(red))` — mixed font/size/color fragments wrap together as one paragraph (words never break at fragment boundaries) and share a common baseline per line
- Inline images: `RichTextContent.Fragment.image(icon, 10)` places a picture in the text flow, sitting on the baseline and wrapping as one unbreakable word — an icon before a sentence stays attached to it while the text carries on beside it and wraps back to the full width underneath, which a picture in its own cell cannot do
- Images: `ImageContent.builder(pathOrBytesOrBufferedImage).width(70)` — natural size fits down to the content width, explicit width/height scale proportionally (both = stretch); the document-bound XObject is created lazily and cached per document, so a repeated header logo embeds once
- Form XObjects: `FormContent.builder(form).width(20)` — vector snippets (stamps, icons, pages imported via `LayerUtility`) placed at the cell position and scaled from their bounding box; never shrunk to fit, since a silently scaled stamp is a falsified one
- Nested tables: `TableContent.of(innerTable)` — inner rows become atomic elements, so nested tables split across pages at inner row boundaries (any depth)
- Automatic page breaks at element granularity — rows and cells split mid-cell; cut cells draw open by default, or closed on both sides of the break via `TableDrawer.Builder.closeBordersAtPageBreak(true)`
- Elements can split themselves at the exact cut position via the overridable `Element.splitAt(availableHeight)`: the top piece fills the current page, the bottom continues (recursively) — placeholders and images do this out of the box (images split seamlessly via clip windows), and nested-table row blocks split whenever the inner elements at the cut can; only unsplittable elements that exceed a page raise `TableLayoutException`
- Repeating header rows on every page (never split)
- Page identity at draw time: `RenderContext.page()` / `pageIndex()` — an element knows where it landed
- Forward page references: `RenderContext.defer(ctx -> ...)` re-invokes a draw on the same page and box once
  the whole table is paginated, so content can print what it could not know while drawing — "page 3 of 7",
  "continued on page 5", or a cross-reference to a row that ended up two pages later
- Per-page-slice content: `Cell.Builder.onEachPageSlice(content)` repeats content at the bottom of every
  page slice a cell is cut into — the "continued on the next page" marker. Drawn as an overlay, since how
  many slices a cell has is a pagination outcome and cannot feed back into heights fixed before the cut
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