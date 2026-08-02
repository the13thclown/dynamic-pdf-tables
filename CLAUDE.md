# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

`dynamic-pdf-tables` — a Java 17 / Maven library on top of Apache PDFBox 3.x for dynamic PDF table building. Content types: `TextContent` (one element per wrapped line — pagination splits text line by line for free), `ImageContent` (single atomic element; source held document-free, the `PDImageXObject` is created lazily at draw time and cached per document via `RenderContext.document()` — required because XObjects are document-bound but definitions must stay document-free), `TableContent` (nested tables), `PlaceholderContent` (layout prototyping). New types plug in via `CellContent` without engine changes; content styling (font/size/color, image dimensions) lives on the content type, not `Style` — `Style` is cell-box styling only.

## Commands

- `mvn test` — build + full test suite
- `mvn test -Dtest=PageCutterTest` — single test class; `-Dtest=PageCutterTest#methodName` for one method
- Render smoke tests write PDFs to `target/test-output/` for human inspection. To eyeball them, render to PNG with PDFBox's `PDFRenderer` (classpath via `mvn dependency:build-classpath -Dmdep.outputFile=target/cp.txt`).

## Architecture — the rules that must not be broken

The design was specified explicitly by the project owner; keep these invariants:

1. **Definition time vs render time are strictly separated.** `Table.build()` / `Cell.build()` only capture structure. No measurement, no layout, no grid validation at build time — everything resolves when `TableDrawer.draw()` runs. Never move validation or layout into the builders.
2. **No `Row` in the public API.** A table is columns + a flat cell sequence. Cells auto-flow into the grid (`GridFlow`): next free slot left-to-right, wrap to the next derived row, skip rowspan-covered slots, grid grows as needed, incomplete last row is valid. Rows exist only as a derived concept inside `layout/`.
3. **Virtual y axis.** `LayoutEngine.compute()` lays the table on an unbounded y axis (top 0, growing down) — no page anywhere in layout code. Page breaks are cuts into that space.
4. **Continue-and-forget pagination.** Per page: `PageCutter.cut()` at capacity → draw everything above the cut → drop it → re-run `LayoutEngine.compute()` on the remainder from virtual 0 → repeat. `TableDrawer` is a dumb walker; all cut math is pure and PDF-free (that's why `PageCutter`/`LayoutEngine` are unit-testable without documents).
5. **The public extension contract is exactly three types**: `CellContent`, `Element` (both root package) and `render.RenderContext`. The `layout` package is INTERNAL (see its package-info) — never let public API reference it and never tell users to import from it. `CustomContentTest` implements a sample content against the public contract only; if it ever needs a `layout` import to compile, the contract has regressed.
6. **Elements are the atomic unit of pagination**, not rows or cells. `CellContent.layout(width)` → `List<Element>` (height fixed once laid). Rows/cells split across pages naturally; a cut cell draws with an open bottom border and continues open-topped (`LayoutCell.continuedTop`) — unless the render-time option `TableDrawer.Builder.closeBordersAtPageBreak(true)` is set, which closes cut boxes on both sides of the break (a drawer option, not a table one: how cuts look is render config, keeping the definition pure). Elements themselves may opt into splitting: `Element.splitAt(availableHeight)` (default null = atomic) lets the cut land INSIDE an element — the top piece fills the current page (drawn via `CutResult.pageLayout()`, where the crossing item is swapped for the top piece with positions preserved), the bottom piece joins the remainder (positioned items re-base right below the drawn part) and may split again recursively. Placeholders and images implement it (images via a clip-window so pieces align seamlessly); text is line-granular already; nested-table row blocks stay atomic. Only an unsplittable element taller than a page still throws `TableLayoutException`. Only a single element taller than a page throws (`TableLayoutException`). New content types (text = one element per line) must plug in via this interface without touching the engine.

## Key mechanics to know before editing

- `LayoutCell` items are either **flowing** (stack vertically, vertical alignment applies to the stack) or **positioned** (`Cell.Builder.addAt(x, y, content)` — offsets from the cell *content box* top-left, after padding; overlaps and right-edge overflow allowed by design). On a page cut, remaining positioned items move as ONE rigid piece: all shift up by the same amount (the consumed height, capped so the topmost remaining item lands at 0) — never per-item clamping, which would compress crossing items into their neighbors and invent overlaps. Flowing items re-stack compactly.
- Continued rows (row 0 of a remainder with `firstRowContinued`) skip the `minRowHeight` floor and vertical-alignment offset — re-adding them would recreate already-consumed height and stall pagination. `TableDrawer` has a no-progress guard that throws instead of looping.
- Rowspan cells needing more height than their spanned rows distribute the deficit **equally** across those rows (`LayoutEngine.compute`, increasing-span order). This is a deliberate v1 policy choice.
- Style resolution happens once, in `LayoutEngine.buildCells`: cell style → `rowStyler(rowIndex)` → `columnStyler(colIndex)` → table default → `Style.defaults()`, field-level null-wins. Downstream code assumes non-null fields (except `backgroundColor`, where null = no fill). Row/column styles are resolved against *original* derived indexes and travel with the cell through page cuts.
- `Style` also carries inheritable TEXT defaults (font, fontSize, textColor, lineSpacing) that text contents fall back to; `CellContent.layout(availableWidth, Style)` receives the resolved style for exactly this. Nested tables inherit only the outer cell's text defaults (never box styling — see `TableContent`).
- `HorizontalAlignment.JUSTIFY`: text contents stretch wrapped lines (never a paragraph's last, tracked via a `justifiable` flag per line element); non-text contents treat it as LEFT.
- `TableDrawer.Builder.overflowColumns(n, gap)` re-flows the remainder into further x-regions on the same page before a new page; the header repeats per region ("continuation" is per slice, not per page).
- Rowspan deficit distribution is a table-level policy (`RowSpanDistribution.EQUAL` default, `LAST_ROW` pools into the final spanned row).
- PDF coordinates (origin bottom-left) appear **only** in `render/LayoutRenderer` (`pageY = topY − virtualY`) and in `Element.draw` boxes. Everything else thinks in virtual y. `LayoutRenderer.render` draws an arbitrary window `[fromY, toY)` of a layout — the page loop passes `[0, cutY)`; nested tables (`TableContent`) pass one window per inner row block.
- Nested tables: `TableContent` lays the inner table out at the cell's content width (relative inner columns fall back to it) and emits one atomic `Element` per inner *row block* (rows united by rowspans), so page breaks never land inside an inner row. Border segments across the whole window stroke thinnest-first so thicker borders win at shared edges.
- `TableDrawer.addPageIfAbsent`: the `pageSupplier` may return pages already in the document (side-by-side multi-page tables); they are not re-added.
- Float comparisons use `EPS = 0.01f` (`LayoutCell.EPS`); an element whose bottom is within EPS of the cut counts as fitting.
- Headers (`headerRowCount` derived rows) are laid out once as a separate block and redrawn at full height on pages 2+; they never split, and a header rowspan leaking into the body is a validation error.

## Testing conventions

- Layout/pagination logic gets pure number-in/number-out tests (`GridFlowTest`, `LayoutEngineTest`, `PageCutterTest`) — build a `Table`, run the real pipeline helpers, assert geometry. No `PDDocument`.
- `TableDrawerTest` smoke tests assert page counts and save inspectable PDFs; `RenderPixelTest` samples single pixels via `PDFRenderer` to guard coordinates and the open-border-at-cut behavior. Pixel sampling caveat: the A4 render is 841px for an 841.89pt page, so the solid row for a horizontal line at y is the pixel row for y−1; use the existing `pixel()`/`assertNear`/`assertNoStroke` helpers.
