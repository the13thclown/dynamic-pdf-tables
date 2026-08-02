package io.github.orestkollcaku.pdftables.style;

import org.junit.jupiter.api.Test;

import java.awt.Color;

import static org.assertj.core.api.Assertions.assertThat;

class StyleTest {

    @Test
    void defaultsHaveEveryFieldSetExceptBackground() {
        Style d = Style.defaults();
        assertThat(d.padding()).isEqualTo(Padding.NONE);
        assertThat(d.borderTop()).isEqualTo(BorderStyle.NONE);
        assertThat(d.borderRight()).isEqualTo(BorderStyle.NONE);
        assertThat(d.borderBottom()).isEqualTo(BorderStyle.NONE);
        assertThat(d.borderLeft()).isEqualTo(BorderStyle.NONE);
        assertThat(d.backgroundColor()).isNull();
        assertThat(d.horizontalAlignment()).isEqualTo(HorizontalAlignment.LEFT);
        assertThat(d.verticalAlignment()).isEqualTo(VerticalAlignment.TOP);
    }

    @Test
    void mergeTakesOwnFieldWhenSet() {
        Style own = Style.builder().padding(Padding.of(5)).build();
        Style fallback = Style.builder().padding(Padding.of(9)).backgroundColor(Color.RED).build();
        Style merged = own.mergedOnto(fallback);
        assertThat(merged.padding()).isEqualTo(Padding.of(5));
        assertThat(merged.backgroundColor()).isEqualTo(Color.RED);
    }

    @Test
    void mergeChainEndsFullyResolved() {
        Style cell = Style.builder().backgroundColor(Color.BLUE).build();
        Style table = Style.builder().borderAll(BorderStyle.of(1)).build();
        Style merged = cell.mergedOnto(table).mergedOnto(Style.defaults());
        assertThat(merged.backgroundColor()).isEqualTo(Color.BLUE);
        assertThat(merged.borderTop()).isEqualTo(BorderStyle.of(1));
        assertThat(merged.padding()).isEqualTo(Padding.NONE);
        assertThat(merged.horizontalAlignment()).isNotNull();
        assertThat(merged.verticalAlignment()).isNotNull();
    }

    @Test
    void borderAllSetsAllFourSides() {
        Style s = Style.builder().borderAll(BorderStyle.of(2, Color.GRAY)).build();
        assertThat(s.borderTop()).isEqualTo(BorderStyle.of(2, Color.GRAY));
        assertThat(s.borderRight()).isEqualTo(BorderStyle.of(2, Color.GRAY));
        assertThat(s.borderBottom()).isEqualTo(BorderStyle.of(2, Color.GRAY));
        assertThat(s.borderLeft()).isEqualTo(BorderStyle.of(2, Color.GRAY));
    }
}
