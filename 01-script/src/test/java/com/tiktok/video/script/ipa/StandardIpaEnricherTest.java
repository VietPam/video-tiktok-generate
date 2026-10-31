package com.tiktok.video.script.ipa;

import com.tiktok.video.core.model.VocabularyItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StandardIpaEnricherTest {

    private StandardIpaEnricher enricher;

    @BeforeEach
    void setUp() {
        enricher = new StandardIpaEnricher();
    }

    @Test
    @DisplayName("F6.1 - F6.5: Verify standard Band 7+ words receive accurate IPA phonetics")
    void testStandardBand7Words() {
        assertThat(enricher.lookup("sibling")).isEqualTo("/ˈsɪb.lɪŋ/");
        assertThat(enricher.lookup("relative")).isEqualTo("/ˈrel.ə.tɪv/");
        assertThat(enricher.lookup("adolescence")).isEqualTo("/ˌæd.əˈles.ns/");
        assertThat(enricher.lookup("nurture")).isEqualTo("/ˈnɜː.tʃər/");
        assertThat(enricher.lookup("relationship")).isEqualTo("/rɪˈleɪ.ʃn.ʃɪp/");
    }

    @Test
    @DisplayName("B2.2: Case variations resolve to identical IPA notation")
    void testCaseInsensitivity() {
        assertThat(enricher.lookup("SIBLING")).isEqualTo("/ˈsɪb.lɪŋ/");
        assertThat(enricher.lookup("Sibling")).isEqualTo("/ˈsɪb.lɪŋ/");
        assertThat(enricher.lookup("sibling")).isEqualTo("/ˈsɪb.lɪŋ/");
    }

    @Test
    @DisplayName("B2.3: Trailing punctuation is stripped before dictionary lookup")
    void testTrailingPunctuationStripping() {
        assertThat(enricher.lookup("sibling,")).isEqualTo("/ˈsɪb.lɪŋ/");
        assertThat(enricher.lookup("nurture.")).isEqualTo("/ˈnɜː.tʃər/");
        assertThat(enricher.lookup("\"relative\"")).isEqualTo("/ˈrel.ə.tɪv/");
    }

    @Test
    @DisplayName("B2.1: Unknown words return empty string fallback")
    void testUnknownWordFallback() {
        assertThat(enricher.lookup("supercalifragilistic")).isEmpty();
        assertThat(enricher.lookup("")).isEmpty();
        assertThat(enricher.lookup(null)).isEmpty();
    }

    @Test
    @DisplayName("B2.4: IPA stress markers (ˈ and ˌ) are preserved in UTF-8")
    void testStressMarkersPreserved() {
        String ipa = enricher.lookup("adolescence");
        assertThat(ipa).contains("ˌ").contains("ˈ");
    }

    @Test
    @DisplayName("Enrich VocabularyItem: existing IPA is preserved, missing IPA is enriched")
    void testEnrichVocabularyItem() {
        VocabularyItem withoutIpa = VocabularyItem.builder()
            .word("Sibling")
            .definition("a brother or sister")
            .build();
        VocabularyItem enriched = enricher.enrich(withoutIpa);
        assertThat(enriched.ipa()).isEqualTo("/ˈsɪb.lɪŋ/");

        VocabularyItem withIpa = VocabularyItem.builder()
            .word("Sibling")
            .ipa("/custom/")
            .definition("a brother or sister")
            .build();
        assertThat(enricher.enrich(withIpa).ipa()).isEqualTo("/custom/");
    }
}
