package io.github.the13thclown.pdftables;

import io.github.the13thclown.pdftables.layout.Element;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class RichTextContentTest {

    private static final PDType1Font BOLD =
            new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

    @Test
    void mixedFragmentsOnOneLine() {
        List<Element> lines = RichTextContent.builder()
                .add("Total: ")
                .add(RichTextContent.fragment("123.45").font(BOLD).color(Color.RED))
                .add(" EUR")
                .build()
                .layout(300);
        assertThat(lines).hasSize(1);
    }

    @Test
    void fragmentsWrapTogetherAcrossBoundaries() {
        List<Element> lines = RichTextContent.builder()
                .add("Some introductory words before ")
                .add(RichTextContent.fragment("an emphasised span of several words").font(BOLD))
                .add(" and a plain tail that also needs room.")
                .build()
                .layout(140);
        assertThat(lines.size()).isGreaterThan(2);
    }

    @Test
    void adjacentFragmentsWithoutSpaceFormOneWord() {
        // "num" + "ber" must never be split apart by wrapping: at a width that
        // fits "number" but not "xxxx number", it lands whole on line 2
        List<Element> lines = RichTextContent.builder()
                .add("wwwwwwww num")
                .add(RichTextContent.fragment("ber").font(BOLD))
                .build()
                .layout(60);
        assertThat(lines).hasSize(2);
    }

    @Test
    void lineHeightFollowsTheTallestFragment() {
        List<Element> lines = RichTextContent.builder()
                .fontSize(10).lineSpacing(1.2f)
                .add("small ")
                .add(RichTextContent.fragment("BIG").fontSize(20))
                .build()
                .layout(500);
        assertThat(lines.get(0).getHeight()).isCloseTo(24, within(0.01f));
    }

    @Test
    void hardBreaksWork() {
        List<Element> lines = RichTextContent.builder()
                .add("one\ntwo")
                .build()
                .layout(500);
        assertThat(lines).hasSize(2);
    }

    @Test
    void overlongStyledWordSplitsMidWord() {
        List<Element> lines = RichTextContent.builder()
                .add(RichTextContent.fragment("Honorificabilitudinitatibus").font(BOLD))
                .build()
                .layout(40);
        assertThat(lines.size()).isGreaterThan(1);
    }
}
