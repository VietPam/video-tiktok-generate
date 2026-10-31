package com.tiktok.video.script.adversarial;

import com.tiktok.video.core.model.Scene;
import com.tiktok.video.core.model.TikTokScript;
import com.tiktok.video.script.regex.RegexTranscriptExtractor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Deep empirical adversarial test suite designed by Challenger m2_it4_1.
 * Tests edge cases and specific criteria from orchestrator:
 * 1. Words ending in 's': Hypothesis, Analysis, Crisis, Process, Species with copula definitions
 * 2. 'Meaning:' and 'The meaning of X is:' labels without fallback strings
 * 3. Copula variations ('refers to', 'means', 'is a')
 * 4. Punctuation variations ('Definition of X is:', 'Meaning of X:')
 */
public class DeepEmpiricalAdversarialTest {

    private RegexTranscriptExtractor extractor;

    @BeforeEach
    void setUp() {
        extractor = new RegexTranscriptExtractor();
    }

    @Test
    @DisplayName("CHALLENGE 1: All 5 words ending in 's' (Hypothesis, Analysis, Crisis, Process, Species) with copula 'is defined as'")
    void testFiveWordsEndingInSWithCopula() {
        String transcript = """
                Welcome to this IELTS Master Band 7 plus vocabulary lesson on Scientific Inquiry.
                Hypothesis
                A hypothesis is defined as a proposed explanation made on the basis of limited evidence as a starting point.
                For example, every scientific experiment begins with a testable hypothesis.
                Collocation: working hypothesis
                Analysis
                An analysis is defined as a detailed examination of the elements or structure of something.
                For example, the researchers performed a comprehensive statistical analysis.
                Collocation: thorough analysis
                Crisis
                A crisis is defined as a time of intense difficulty, trouble, or danger requiring urgent action.
                For example, the government took emergency measures to resolve the acute financial crisis.
                Collocation: acute crisis
                Process
                A process is defined as a series of actions or steps taken in order to achieve a particular end.
                For example, language acquisition is a complex cognitive process.
                Collocation: continuous process
                Species
                A species is defined as a group of living organisms consisting of similar individuals capable of exchanging genes.
                For example, climate change threatens numerous vulnerable species with extinction.
                Collocation: endangered species
                Summary
                Action step: write five sentences using each word in the comments!
                """;

        TikTokScript script = extractor.extract(transcript);
        List<Scene> scenes = script.vocabularyScenes();

        assertThat(scenes)
                .as("All 5 words ending in 's' must be extracted")
                .hasSize(5);

        List<String> extractedWords = scenes.stream().map(s -> s.vocabularyItem().word()).toList();
        assertThat(extractedWords).containsExactly("Hypothesis", "Analysis", "Crisis", "Process", "Species");

        // Verify definitions are genuinely extracted, not fallback strings
        for (Scene s : scenes) {
            String word = s.vocabularyItem().word();
            String def = s.vocabularyItem().definition();
            assertThat(def)
                    .as("Definition for %s must not be empty or fallback", word)
                    .isNotEmpty()
                    .doesNotContain("definition for " + word)
                    .doesNotContain("definition for " + word.toLowerCase());
        }

        // Verify specific definition keywords
        assertThat(scenes.get(0).vocabularyItem().definition()).contains("proposed explanation");
        assertThat(scenes.get(1).vocabularyItem().definition()).contains("detailed examination");
        assertThat(scenes.get(2).vocabularyItem().definition()).contains("time of intense difficulty");
        assertThat(scenes.get(3).vocabularyItem().definition()).contains("series of actions or steps");
        assertThat(scenes.get(4).vocabularyItem().definition()).contains("group of living organisms");

        // Verify collocations are extracted cleanly
        assertThat(scenes.get(0).vocabularyItem().collocation()).isEqualTo("working hypothesis");
        assertThat(scenes.get(1).vocabularyItem().collocation()).isEqualTo("thorough analysis");
        assertThat(scenes.get(2).vocabularyItem().collocation()).isEqualTo("acute crisis");
        assertThat(scenes.get(3).vocabularyItem().collocation()).isEqualTo("continuous process");
        assertThat(scenes.get(4).vocabularyItem().collocation()).isEqualTo("endangered species");
    }

    @Test
    @DisplayName("CHALLENGE 2: Definition labels 'Meaning:' and 'The meaning of X is:' without fallback strings")
    void testMeaningAndTheMeaningOfXIsLabels() {
        String transcript = """
                Welcome to IELTS Master Band 7 plus vocabulary lesson on Advanced Lexis.
                Resilience
                Meaning: the capacity to recover quickly from difficulties or toughness.
                For example, emotional resilience helps individuals overcome adversity.
                Collocation: psychological resilience is a common collocation.
                Hypothesis
                The meaning of hypothesis is: an unproven proposition tentatively accepted to explain certain facts.
                For example, we formulated a hypothesis to test the reaction rate.
                Collocation: testable hypothesis
                Crisis
                The meaning of crisis is a critical turning point in progress or an unstable situation.
                For example, leaders held an emergency summit during the energy crisis.
                Collocation: severe crisis
                Species
                The meaning of species is: a fundamental category of biological classification.
                For example, the Amazon basin contains thousands of undiscovered species.
                Collocation: native species
                Summary
                Action step: write sentences in the comments!
                """;

        TikTokScript script = extractor.extract(transcript);
        List<Scene> scenes = script.vocabularyScenes();

        assertThat(scenes).hasSize(4);

        List<String> extractedWords = scenes.stream().map(s -> s.vocabularyItem().word()).toList();
        assertThat(extractedWords).containsExactly("Resilience", "Hypothesis", "Crisis", "Species");

        // Check Resilience ("Meaning:")
        Scene sResilience = scenes.get(0);
        assertThat(sResilience.vocabularyItem().definition())
                .as("Meaning: label should extract authentic definition")
                .contains("capacity to recover quickly from difficulties")
                .doesNotContain("definition for Resilience");

        // Check Hypothesis ("The meaning of hypothesis is:")
        Scene sHypothesis = scenes.get(1);
        assertThat(sHypothesis.vocabularyItem().definition())
                .as("The meaning of hypothesis is: label should extract authentic definition")
                .contains("unproven proposition tentatively accepted")
                .doesNotContain("definition for Hypothesis");

        // Check Crisis ("The meaning of crisis is")
        Scene sCrisis = scenes.get(2);
        assertThat(sCrisis.vocabularyItem().definition())
                .as("The meaning of crisis is label should extract authentic definition")
                .contains("critical turning point in progress")
                .doesNotContain("definition for Crisis");

        // Check Species ("The meaning of species is:")
        Scene sSpecies = scenes.get(3);
        assertThat(sSpecies.vocabularyItem().definition())
                .as("The meaning of species is: label should extract authentic definition")
                .contains("fundamental category of biological classification")
                .doesNotContain("definition for Species");
    }

    @Test
    @DisplayName("CHALLENGE 3: Words ending in 's' with copula variations ('refers to', 'means', 'is a')")
    void testWordsEndingInSWithCopulaVariations() {
        String transcript = """
                Welcome to IELTS Master Band 7 plus vocabulary lesson on Biology.
                Species
                Species refers to a distinct group of organisms sharing common characteristics.
                For example, this island is home to an endemic bird species.
                Collocation: invasive species
                Hypothesis
                Hypothesis means an educated guess based on initial observations.
                For example, our preliminary data supported the null hypothesis.
                Collocation: null hypothesis
                Crisis
                Crisis is an urgent situation of severe danger or instability.
                For example, climate change represents a global ecological crisis.
                Collocation: ecological crisis
                Summary
                Action step: share your thoughts!
                """;

        TikTokScript script = extractor.extract(transcript);
        List<Scene> scenes = script.vocabularyScenes();

        assertThat(scenes).hasSize(3);

        List<String> words = scenes.stream().map(s -> s.vocabularyItem().word()).toList();
        assertThat(words).containsExactly("Species", "Hypothesis", "Crisis");

        assertThat(scenes.get(0).vocabularyItem().definition())
                .contains("distinct group of organisms");
        assertThat(scenes.get(1).vocabularyItem().definition())
                .contains("educated guess based on initial observations");
        assertThat(scenes.get(2).vocabularyItem().definition())
                .contains("urgent situation of severe danger");
    }

    @Test
    @DisplayName("CHALLENGE 4: Standard 'Definition of X is:' vs 'Meaning of X:' punctuation")
    void testStandardDefinitionAndMeaningPunctuation() {
        String transcript = """
                Welcome to IELTS Master Band 7 plus vocabulary lesson on Advanced Linguistics.
                Syntax
                The definition of syntax is: the arrangement of words and phrases to create well-formed sentences.
                For example, English syntax requires subject-verb agreement.
                Collocation: complex syntax
                Semantics
                Meaning of semantics: the branch of linguistics concerned with meaning.
                For example, computational semantics powers natural language search engines.
                Collocation: formal semantics
                Summary
                Action step: submit your sentences!
                """;

        TikTokScript script = extractor.extract(transcript);
        List<Scene> scenes = script.vocabularyScenes();

        assertThat(scenes).hasSize(2);
        assertThat(scenes.get(0).vocabularyItem().word()).isEqualTo("Syntax");
        assertThat(scenes.get(0).vocabularyItem().definition()).contains("arrangement of words and phrases");

        assertThat(scenes.get(1).vocabularyItem().word()).isEqualTo("Semantics");
        assertThat(scenes.get(1).vocabularyItem().definition()).contains("branch of linguistics concerned with meaning");
    }
}
