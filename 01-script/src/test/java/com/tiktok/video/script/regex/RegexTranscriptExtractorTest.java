package com.tiktok.video.script.regex;

import com.tiktok.video.core.model.Scene;
import com.tiktok.video.core.model.SceneType;
import com.tiktok.video.core.model.TikTokScript;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RegexTranscriptExtractorTest {

    private RegexTranscriptExtractor extractor;
    private Path fixturesDir;
    private Path scriptTxtPath;

    @BeforeEach
    void setUp() {
        extractor = new RegexTranscriptExtractor();
        Path baseDir = Files.exists(Path.of("script.txt")) ? Path.of(".") : Path.of("..");
        fixturesDir = baseDir.resolve("tests/fixtures/transcripts");
        scriptTxtPath = baseDir.resolve("script.txt");
    }

    @Test
    @DisplayName("F5 & Scenario 1: Parse standard script.txt into 7 scenes (Hook, 5 Words, CTA)")
    void testExtractStandardScriptTxt() throws IOException {
        String content = Files.readString(scriptTxtPath);
        TikTokScript script = extractor.extract(content);

        assertThat(script.topic()).isEqualTo("Family");
        assertThat(script.targetBand()).isEqualTo("Band 7+");
        assertThat(script.totalScenes()).isEqualTo(7);

        // Hook Scene
        Scene hook = script.hookScene().orElseThrow();
        assertThat(hook.sequenceIndex()).isZero();
        assertThat(hook.sceneType()).isEqualTo(SceneType.HOOK);
        assertThat(hook.spokenText()).contains("IELTS Master Band 7 plus");

        // Vocabulary Scenes
        List<Scene> vocabScenes = script.vocabularyScenes();
        assertThat(vocabScenes).hasSize(5);

        List<String> words = vocabScenes.stream().map(s -> s.vocabularyItem().word()).toList();
        assertThat(words).containsExactly("Sibling", "Relative", "Adolescence", "Nurture", "Relationship");

        // Collocations & IPA
        assertThat(vocabScenes.get(0).vocabularyItem().collocation()).isEqualTo("sibling rivalry");
        assertThat(vocabScenes.get(0).vocabularyItem().ipa()).isEqualTo("/ˈsɪb.lɪŋ/");

        assertThat(vocabScenes.get(1).vocabularyItem().collocation()).isEqualTo("distant relative");
        assertThat(vocabScenes.get(1).vocabularyItem().ipa()).isEqualTo("/ˈrel.ə.tɪv/");

        assertThat(vocabScenes.get(2).vocabularyItem().collocation()).isEqualTo("early adolescence");
        assertThat(vocabScenes.get(2).vocabularyItem().ipa()).isEqualTo("/ˌæd.əˈles.ns/");

        assertThat(vocabScenes.get(3).vocabularyItem().collocation()).isEqualTo("nurture talent");
        assertThat(vocabScenes.get(3).vocabularyItem().ipa()).isEqualTo("/ˈnɜː.tʃər/");

        assertThat(vocabScenes.get(4).vocabularyItem().collocation()).isEqualTo("establish a relationship");
        assertThat(vocabScenes.get(4).vocabularyItem().ipa()).isEqualTo("/rɪˈleɪ.ʃn.ʃɪp/");

        // CTA Scene
        Scene cta = script.ctaScene().orElseThrow();
        assertThat(cta.sequenceIndex()).isEqualTo(6);
        assertThat(cta.sceneType()).isEqualTo(SceneType.CALL_TO_ACTION);
        assertThat(cta.spokenText()).contains("action step");
    }

    @Test
    @DisplayName("B1.3: Single word transcript parses exactly 1 vocabulary item")
    void testSingleWordScript() throws IOException {
        String content = Files.readString(fixturesDir.resolve("single_word_script.txt"));
        TikTokScript script = extractor.extract(content);

        assertThat(script.vocabularyScenes()).hasSize(1);
        assertThat(script.vocabularyScenes().get(0).vocabularyItem().word()).isEqualTo("Sibling");
        assertThat(script.vocabularyScenes().get(0).vocabularyItem().collocation()).isEqualTo("sibling rivalry");
    }

    @Test
    @DisplayName("B1.4: Multi-collocation transcript captures relationship collocations")
    void testMultiCollocationScript() throws IOException {
        String content = Files.readString(fixturesDir.resolve("multi_collocation_script.txt"));
        TikTokScript script = extractor.extract(content);

        assertThat(script.vocabularyScenes()).hasSize(1);
        String collocations = script.vocabularyScenes().get(0).vocabularyItem().collocation();
        assertThat(collocations).contains("establish a relationship");
    }

    @Test
    @DisplayName("B1.5: Malformed timestamps do not crash parser")
    void testMalformedTimestampsScript() throws IOException {
        String content = Files.readString(fixturesDir.resolve("malformed_timestamps_script.txt"));
        TikTokScript script = extractor.extract(content);

        assertThat(script.vocabularyScenes()).hasSize(1);
        assertThat(script.vocabularyScenes().get(0).vocabularyItem().word()).isEqualTo("Sibling");
    }

    @Test
    @DisplayName("B1.6: Extreme length transcript is parsed without overflow")
    void testExtremeLengthScript() throws IOException {
        String content = Files.readString(fixturesDir.resolve("extreme_length_script.txt"));
        TikTokScript script = extractor.extract(content);

        assertThat(script.vocabularyScenes()).hasSize(1);
        assertThat(script.vocabularyScenes().get(0).vocabularyItem().word()).isEqualTo("Adolescence");
    }

    @Test
    @DisplayName("B1.7: Special characters, em-dashes, and existing IPA are preserved")
    void testSpecialCharactersScript() throws IOException {
        String content = Files.readString(fixturesDir.resolve("special_characters_script.txt"));
        TikTokScript script = extractor.extract(content);

        assertThat(script.vocabularyScenes()).hasSize(1);
        assertThat(script.vocabularyScenes().get(0).vocabularyItem().word()).isEqualTo("Nurture");
        assertThat(script.vocabularyScenes().get(0).vocabularyItem().ipa()).isEqualTo("/ˈnɜː.tʃər/");
    }

    @Test
    @DisplayName("B1.1 & B1.2: Empty or blank transcript throws IllegalArgumentException")
    void testBlankTranscriptThrows() {
        assertThatThrownBy(() -> extractor.extract(null))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> extractor.extract("   \n\t  "))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
