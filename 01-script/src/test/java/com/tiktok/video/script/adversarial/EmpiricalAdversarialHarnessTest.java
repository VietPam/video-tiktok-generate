package com.tiktok.video.script.adversarial;

import com.tiktok.video.core.model.Scene;
import com.tiktok.video.core.model.TikTokScript;
import com.tiktok.video.script.regex.RegexTranscriptExtractor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Empirical Adversarial Challenger Test Harness for RegexTranscriptExtractor.
 * Adversarially probes:
 * 1. Novel prefix-free vocabulary words with 'Meaning:' heading (Biodiversity, Resilience)
 * 2. Words ending in 's' (Hypothesis, Analysis, Process) with copula definitions
 * 3. Novel vocabulary words with standard 'Definition:' header (Ubiquitous)
 * 4. Multi-word novel compound candidate without prefix
 * 5. Introductory preview mentions suppression
 * 6. Immunity to Stop Words and Structural Keywords as standalone lines
 * 7. Corrupted, emoji, numeric, and malformed inputs
 * 8. ReDoS stress testing (worst-case repetitive inputs)
 * 9. Regression parity against production script.txt
 */
public class EmpiricalAdversarialHarnessTest {

    private RegexTranscriptExtractor extractor;

    @BeforeEach
    void setUp() {
        extractor = new RegexTranscriptExtractor();
    }

    @Test
    @DisplayName("ADV-1 [BUG]: Novel vocabulary words with 'Meaning:' heading instead of 'Definition:' (Resilience, Biodiversity)")
    void testNovelWordsWithMeaningHeading() {
        String transcript = """
                Introduction
                0:00
                Welcome to this IELTS Master Band 7 plus vocabulary lesson on Ecology.
                Biodiversity
                0:10
                Meaning: the variety of plant and animal life in a particular habitat or in the world.
                0:18
                For example, tropical rainforests exhibit extraordinary biodiversity.
                Collocations
                0:25
                Rich biodiversity is a common collocation meaning diverse biological species.
                Resilience
                0:35
                Meaning: the capacity of an ecosystem to recover quickly from difficulties.
                0:42
                For example, ecological resilience prevents total collapse during drought.
                Collocations
                0:50
                Ecological resilience is a common collocation meaning natural recovery capacity.
                Summary
                1:00
                Action step: write two sentences using each word in the comments!
                """;

        TikTokScript script = extractor.extract(transcript);
        List<Scene> vocabScenes = script.vocabularyScenes();

        assertThat(vocabScenes)
                .as("Should extract 2 vocabulary items for Biodiversity and Resilience")
                .hasSize(2);

        // Verify that definition is actually extracted, NOT the fallback 'definition for <Word>'
        Scene s0 = vocabScenes.get(0);
        assertThat(s0.vocabularyItem().word()).isEqualTo("Biodiversity");
        assertThat(s0.vocabularyItem().definition())
                .as("Meaning: header must be extracted as definition, not fallback 'definition for Biodiversity'")
                .contains("variety of plant and animal life");

        Scene s1 = vocabScenes.get(1);
        assertThat(s1.vocabularyItem().word()).isEqualTo("Resilience");
        assertThat(s1.vocabularyItem().definition())
                .as("Meaning: header must be extracted as definition, not fallback 'definition for Resilience'")
                .contains("capacity of an ecosystem to recover");
    }

    @Test
    @DisplayName("ADV-2 [BUG]: Singular words ending in 's' (Hypothesis, Analysis, Process) with copula definitions")
    void testSingularWordsEndingInS() {
        String transcript = """
                Welcome to IELTS lesson on Academic Thinking.
                Hypothesis
                A hypothesis is defined as a proposed explanation made on the basis of limited evidence.
                Example: scientists formulate a hypothesis before testing.
                Collocation: working hypothesis is a common collocation.
                Analysis
                An analysis is defined as a detailed examination of elements or structure.
                Example: qualitative analysis reveals key behavioral trends.
                Collocation: thorough analysis is a common collocation.
                Process
                A process is defined as a series of actions or steps taken in order to achieve a particular end.
                Example: writing an essay is a structured process.
                Collocation: continuous process is a common collocation.
                Summary
                Action step: share your sentences!
                """;

        TikTokScript script = extractor.extract(transcript);
        List<Scene> scenes = script.vocabularyScenes();

        assertThat(scenes)
                .as("Must extract singular words ending in 's' (Hypothesis, Analysis, Process)")
                .hasSize(3);

        List<String> words = scenes.stream().map(s -> s.vocabularyItem().word()).toList();
        assertThat(words).containsExactly("Hypothesis", "Analysis", "Process");
    }

    @Test
    @DisplayName("ADV-3: Novel vocabulary words with standard 'Definition:' header (Ubiquitous)")
    void testNovelWordWithDefinitionHeader() {
        String transcript = """
                Welcome to IELTS lesson on Technology.
                Ubiquitous
                Definition: present, appearing, or found everywhere.
                Example: smartphones have become ubiquitous in daily life.
                Collocation: ubiquitous presence
                Summary
                Action step: share your thoughts!
                """;

        TikTokScript script = extractor.extract(transcript);
        assertThat(script.vocabularyScenes()).hasSize(1);

        Scene s = script.vocabularyScenes().get(0);
        assertThat(s.vocabularyItem().word()).isEqualTo("Ubiquitous");
        assertThat(s.vocabularyItem().definition()).contains("present, appearing, or found everywhere");
        assertThat(s.vocabularyItem().collocation()).isEqualTo("ubiquitous presence");
    }

    @Test
    @DisplayName("ADV-4: Multi-word novel compound candidate without prefix")
    void testMultiWordNovelCompound() {
        String transcript = """
                Welcome to IELTS lesson on Environment.
                Carbon footprint
                Definition: the total amount of greenhouse gases generated by our actions.
                Example: driving electric cars significantly reduces your carbon footprint.
                Collocation: reduce carbon footprint
                Summary
                Action step: comment below!
                """;

        TikTokScript script = extractor.extract(transcript);
        assertThat(script.vocabularyScenes()).hasSize(1);

        Scene scene = script.vocabularyScenes().get(0);
        assertThat(scene.vocabularyItem().word()).isEqualTo("Carbon footprint");
        assertThat(scene.vocabularyItem().definition()).contains("greenhouse gases");
    }

    @Test
    @DisplayName("ADV-5: Introductory preview mentions must NOT trigger false positive vocabulary scenes")
    void testIntroPreviewMentionsSuppressed() {
        String transcript = """
                Introduction
                0:00
                Welcome to this IELTS Master Band 7 plus vocabulary lesson on Urbanization.
                0:05
                In this lesson our key words are:
                infrastructure
                congestion
                gentrification
                0:12
                Let us begin with our first concept.
                Infrastructure
                0:15
                The definition of infrastructure is the basic physical systems of a business, region, or nation.
                0:22
                Example: transportation infrastructure is essential for economic trade.
                Collocations
                0:30
                Critical infrastructure is a common collocation meaning vital public systems.
                Congestion
                0:40
                Definition: the state of being overcrowded or obstructed.
                0:45
                Example: traffic congestion slows down commuters during rush hour.
                Collocations
                0:52
                Traffic congestion is a common collocation meaning heavy vehicle traffic.
                Summary
                1:00
                Action step: write 2 sentences in the comments below!
                """;

        TikTokScript script = extractor.extract(transcript);
        List<Scene> scenes = script.vocabularyScenes();
        assertThat(scenes).hasSize(2);

        List<String> words = scenes.stream().map(s -> s.vocabularyItem().word()).toList();
        assertThat(words).containsExactly("Infrastructure", "Congestion");
    }

    @Test
    @DisplayName("ADV-6: Immunity to Stop Words and Structural Keywords as standalone lines")
    void testStopWordsAndStructuralKeywordsImmunity() {
        String transcript = """
                Welcome to IELTS lesson on General Topics.
                Introduction
                Overview
                Summary
                Collocation
                Collocations
                Example
                Examples
                And
                The
                About
                Because
                During
                Lesson
                Band
                12345
                Summary
                Action step: no words learned today.
                """;

        TikTokScript script = extractor.extract(transcript);
        assertThat(script.vocabularyScenes())
                .as("Stop words and structural keywords must never produce vocabulary scenes")
                .isEmpty();
    }

    @Test
    @DisplayName("ADV-7: Malformed and corrupted inputs handling (emojis, control characters, extreme lengths)")
    void testMalformedAndCorruptedInputs() {
        // Only timestamps
        TikTokScript tsOnly = extractor.extract("0:00\n0:15\n1:20\n99999:99\n");
        assertThat(tsOnly.vocabularyScenes()).isEmpty();

        // Null or blank throws IllegalArgumentException
        assertThatThrownBy(() -> extractor.extract(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> extractor.extract("   \n\t  \n"))
                .isInstanceOf(IllegalArgumentException.class);

        // Emoji & symbol spam
        String emojiScript = """
                Welcome to IELTS lesson!
                🚀🔥✨💯
                Definition: emojis everywhere!
                Summary
                Action step: smile!
                """;
        TikTokScript emojiResult = extractor.extract(emojiScript);
        assertThat(emojiResult.vocabularyScenes()).isEmpty();
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    @DisplayName("ADV-8: ReDoS and catastrophic backtracking stress test on 50,000 characters")
    void testReDosStressTest() {
        StringBuilder sb = new StringBuilder();
        sb.append("Welcome to IELTS Master Band 7 plus vocabulary lesson on Linguistics.\n");
        for (int i = 0; i < 2000; i++) {
            sb.append("This is conversational line number ").append(i).append(" with many repeated words and quotes \"\"\n");
        }
        sb.append("Pragmatics\n");
        sb.append("The definition of pragmatics is the branch of linguistics dealing with language in use.\n");
        sb.append("Example: pragmatics explores conversational implicature.\n");
        sb.append("Collocations\n");
        sb.append("Social pragmatics is a common collocation.\n");
        sb.append("Summary\nAction step: done!\n");

        TikTokScript script = extractor.extract(sb.toString());
        assertThat(script.vocabularyScenes()).hasSize(1);
        assertThat(script.vocabularyScenes().get(0).vocabularyItem().word()).isEqualTo("Pragmatics");
    }

    @Test
    @DisplayName("ADV-9: Regression parity verification against production script.txt")
    void testProductionScriptParity() throws IOException {
        Path scriptPath = Paths.get("script.txt");
        if (!Files.exists(scriptPath)) {
            scriptPath = Paths.get("video-tiktok-generate/script.txt");
        }
        if (!Files.exists(scriptPath)) {
            scriptPath = Paths.get("../script.txt");
        }
        assertThat(Files.exists(scriptPath)).as("script.txt must exist").isTrue();

        String rawScript = Files.readString(scriptPath);
        TikTokScript script = extractor.extract(rawScript);

        assertThat(script.topic()).isEqualTo("Family");
        assertThat(script.targetBand()).isEqualTo("Band 7+");
        assertThat(script.scenes()).hasSize(17);

        List<Scene> vocabScenes = script.vocabularyScenes();
        assertThat(vocabScenes).hasSize(5);

        List<String> words = vocabScenes.stream().map(s -> s.vocabularyItem().word()).toList();
        assertThat(words).containsExactly("Sibling", "Relative", "Adolescence", "Nurture", "Relationship");

        // Verify exact collocations
        assertThat(vocabScenes.get(0).vocabularyItem().collocation()).isEqualTo("sibling rivalry");
        assertThat(vocabScenes.get(1).vocabularyItem().collocation()).isEqualTo("distant relative");
        assertThat(vocabScenes.get(2).vocabularyItem().collocation()).isEqualTo("early adolescence");
        assertThat(vocabScenes.get(3).vocabularyItem().collocation()).isEqualTo("nurture talent");
        assertThat(vocabScenes.get(4).vocabularyItem().collocation()).isEqualTo("establish a relationship");

        // Verify parts of speech
        assertThat(vocabScenes.get(0).vocabularyItem().partOfSpeech()).isEqualTo("noun");
        assertThat(vocabScenes.get(1).vocabularyItem().partOfSpeech()).isEqualTo("noun");
        assertThat(vocabScenes.get(2).vocabularyItem().partOfSpeech()).isEqualTo("noun");
        assertThat(vocabScenes.get(3).vocabularyItem().partOfSpeech()).isEqualTo("verb");
        assertThat(vocabScenes.get(4).vocabularyItem().partOfSpeech()).isEqualTo("noun");
    }
}
