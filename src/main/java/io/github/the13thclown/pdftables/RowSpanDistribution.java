package io.github.the13thclown.pdftables;

/**
 * How a rowspan cell's extra height demand is distributed when it needs more
 * than its spanned rows provide.
 */
public enum RowSpanDistribution {

    /** Spread the deficit equally across all spanned rows (default). */
    EQUAL,

    /** Pool the whole deficit into the last spanned row; earlier rows hug their own content. */
    LAST_ROW
}
