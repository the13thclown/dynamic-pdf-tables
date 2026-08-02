package io.github.the13thclown.pdftables;

import io.github.the13thclown.pdftables.layout.Element;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class TextContentTest {

    @Test
    void shortTextIsASingleLine() {
        List<Element> lines = TextContent.of("hello world").layout(300);
        assertThat(lines).hasSize(1);
    }

    @Test
    void textWrapsToTheAvailableWidth() {
        List<Element> lines = TextContent.of(
                "The quick brown fox jumps over the lazy dog and keeps on running").layout(120);
        assertThat(lines.size()).isGreaterThan(2);
    }

    @Test
    void explicitNewlinesForceBreaks() {
        List<Element> lines = TextContent.of("one\ntwo\nthree").layout(500);
        assertThat(lines).hasSize(3);
    }

    @Test
    void blankLinesArePreserved() {
        List<Element> lines = TextContent.of("one\n\ntwo").layout(500);
        assertThat(lines).hasSize(3);
    }

    @Test
    void wordWiderThanTheLineSplitsMidWord() {
        List<Element> lines = TextContent.of("Honorificabilitudinitatibus").layout(40);
        assertThat(lines.size()).isGreaterThan(1);
    }

    @Test
    void lineHeightIsFontSizeTimesSpacing() {
        List<Element> lines = TextContent.builder("x").fontSize(10).lineSpacing(1.5f).build().layout(200);
        assertThat(lines.get(0).getHeight()).isCloseTo(15, within(0.01f));
    }

    @Test
    void unencodableCharactersAreReplacedInsteadOfFailing() {
        // U+2192 RIGHTWARDS ARROW is not encodable in WinAnsi/Helvetica
        List<Element> lines = TextContent.of("a → b").layout(300);
        assertThat(lines).hasSize(1);
    }

    @Test
    void emptyTextIsOneEmptyLine() {
        List<Element> lines = TextContent.of("").layout(300);
        assertThat(lines).hasSize(1);
        assertThat(lines.get(0).getHeight()).isCloseTo(11 * 1.2f, within(0.01f));
    }
}
