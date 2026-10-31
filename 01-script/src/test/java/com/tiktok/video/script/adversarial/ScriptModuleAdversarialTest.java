package com.tiktok.video.script.adversarial;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tiktok.video.core.contract.ScriptExtractor;
import com.tiktok.video.core.exception.ScriptExtractionException;
import com.tiktok.video.core.model.Scene;
import com.tiktok.video.core.model.TikTokScript;
import com.tiktok.video.script.controller.ScriptController;
import com.tiktok.video.script.regex.RegexTranscriptExtractor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class ScriptModuleAdversarialTest {

    private MockMvc mockMvc;
    private ScriptController scriptController;
    private RegexTranscriptExtractor regexExtractor;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        regexExtractor = new RegexTranscriptExtractor();
        objectMapper = new ObjectMapper();
        scriptController = new ScriptController(regexExtractor, objectMapper);
        mockMvc = MockMvcBuilders.standaloneSetup(scriptController).build();
    }

    // =========================================================================
    // 1. ScriptController: Concurrency Stress Testing
    // =========================================================================

    @Test
    @DisplayName("ScriptController: 50 concurrent extract requests succeed with 0 race conditions")
    void testConcurrentExtractRequests() throws Exception {
        int concurrency = 50;
        ExecutorService executor = Executors.newFixedThreadPool(concurrency);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<TikTokScript>> futures = new ArrayList<>();

        for (int i = 0; i < concurrency; i++) {
            final int index = i;
            futures.add(executor.submit(() -> {
                startLatch.await();
                String body = (index % 2 == 0)
                        ? "Hello and welcome to IELTS lesson on Family. Target word: sibling. Definition: a brother or sister. Sibling rivalry is common."
                        : "{\"transcript\": \"Welcome to IELTS lesson on Family. Word relative: Definition: a person related by blood.\"}";
                var response = scriptController.extract(body);
                return response.getBody();
            }));
        }

        startLatch.countDown();
        executor.shutdown();
        assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

        for (Future<TikTokScript> f : futures) {
            TikTokScript script = f.get();
            assertThat(script).isNotNull();
            assertThat(script.scenes()).isNotEmpty();
        }
    }

    // =========================================================================
    // 2. ScriptController: Boundary Values & Malformed JSON Inputs
    // =========================================================================

    @Test
    @DisplayName("ScriptController: Null, empty, and blank bodies throw IllegalArgumentException")
    void testControllerBoundaryNullAndBlank() {
        assertThatThrownBy(() -> scriptController.extract(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be null or blank");

        assertThatThrownBy(() -> scriptController.extract(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be null or blank");

        assertThatThrownBy(() -> scriptController.extract("   \t\n   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be null or blank");
    }

    @Test
    @DisplayName("ScriptController: Malformed JSON syntax gracefully falls back to raw text extraction")
    void testControllerMalformedJsonSyntax() throws Exception {
        // Malformed JSON that begins with '{' but is invalid JSON syntax
        String brokenJson = "{\"transcript\": \"welcome to family lesson. Word sibling. Definition: brother or sister.";

        var response = scriptController.extract(brokenJson);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().topic()).isEqualTo("Family");
    }

    @Test
    @DisplayName("ScriptController: JSON wrapper with blank transcript throws IllegalArgumentException")
    void testControllerJsonWrapperWithBlankTranscript() {
        String jsonPayload = "{\"transcript\": \"   \"}";
        assertThatThrownBy(() -> scriptController.extract(jsonPayload))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private boolean hasWord(TikTokScript script, String word) {
        return script.vocabularyScenes().stream()
                .anyMatch(s -> s.vocabularyItem() != null && s.vocabularyItem().word().equalsIgnoreCase(word));
    }

    @Test
    @DisplayName("ScriptController: JSON wrapper with alternative keys (text, content) or non-string values")
    void testControllerJsonWrapperAlternativeKeys() throws Exception {
        // Key 'text'
        String jsonText = "{\"text\": \"Welcome to lesson on family.\\nTarget word: relative\\nDefinition: related person.\"}";
        var res1 = scriptController.extract(jsonText);
        assertThat(res1.getBody()).isNotNull();
        assertThat(hasWord(res1.getBody(), "relative")).isTrue();

        // Key 'content'
        String jsonContent = "{\"content\": \"Welcome to lesson on family.\\nTarget word: nurture\\nDefinition: care for.\"}";
        var res2 = scriptController.extract(jsonContent);
        assertThat(res2.getBody()).isNotNull();
        assertThat(hasWord(res2.getBody(), "nurture")).isTrue();

        // Key with numeric transcript
        String jsonNumeric = "{\"transcript\": 123456}";
        var res3 = scriptController.extract(jsonNumeric);
        assertThat(res3.getBody()).isNotNull();
    }

    @Test
    @DisplayName("ScriptController: Oversized 100,000-character transcript extracts without OOM or stack overflow")
    void testControllerOversizedTranscript() throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append("Welcome to IELTS Master Band 7+ vocabulary lesson on Family.\n");
        sb.append("Target word: Sibling\nDefinition of sibling is a brother or sister.\nSibling rivalry is common.\n");
        for (int i = 0; i < 2000; i++) {
            sb.append("This is filler line ").append(i).append(" with general discussion about family life.\n");
        }
        sb.append("Action step: Write sentences in the comments!\n");

        String massivePayload = sb.toString();
        assertThat(massivePayload.length()).isGreaterThan(100_000);

        long start = System.currentTimeMillis();
        var response = scriptController.extract(massivePayload);
        long elapsed = System.currentTimeMillis() - start;

        assertThat(elapsed).isLessThan(3000); // must process within 3s
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        TikTokScript script = response.getBody();
        assertThat(script).isNotNull();
        assertThat(hasWord(script, "sibling")).isTrue();
    }

    // =========================================================================
    // 3. RegexTranscriptExtractor: Detailed Edge Case Inspection
    // =========================================================================

    @Test
    @DisplayName("RegexTranscriptExtractor: Empty string and whitespace throw IllegalArgumentException")
    void testExtractorEmptyString() {
        assertThatThrownBy(() -> regexExtractor.extract(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> regexExtractor.extract(""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> regexExtractor.extract("   \n\n\t  "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("RegexTranscriptExtractor: Transcript with only timestamps returns script with 0 scenes")
    void testExtractorOnlyTimestamps() throws ScriptExtractionException {
        String onlyTimestamps = "00:01\n00:05\n01:30\nxx:yy:zz\n99999:99\n";
        TikTokScript script = regexExtractor.extract(onlyTimestamps);
        assertThat(script).isNotNull();
        assertThat(script.scenes()).isEmpty();
    }

    @Test
    @DisplayName("RegexTranscriptExtractor: Missing sections (Hook and CTA only, no vocabulary items)")
    void testExtractorMissingVocabSections() throws ScriptExtractionException {
        String noVocab = """
                Welcome to this IELTS Master Band 7 plus vocabulary lesson on Family.
                Summary and action step: Please write your sentences in the comments below.
                """;
        TikTokScript script = regexExtractor.extract(noVocab);
        assertThat(script).isNotNull();
        assertThat(script.totalScenes()).isEqualTo(2); // Hook + CTA
        assertThat(script.scenes().get(0).isHook()).isTrue();
        assertThat(script.scenes().get(1).isCallToAction()).isTrue();
        assertThat(script.vocabularyScenes()).isEmpty();
    }

    @Test
    @DisplayName("RegexTranscriptExtractor: Missing CTA (generates fallback action step)")
    void testExtractorMissingCta() throws ScriptExtractionException {
        String noCta = """
                Welcome to this IELTS lesson on Family.
                Target word: Sibling
                The definition of sibling is a brother or sister.
                Sibling rivalry is a common collocation.
                """;
        TikTokScript script = regexExtractor.extract(noCta);
        assertThat(script).isNotNull();
        assertThat(hasWord(script, "sibling")).isTrue();
        // Must contain CTA scene even though transcript lacked explicit CTA heading
        Scene lastScene = script.scenes().get(script.scenes().size() - 1);
        assertThat(lastScene.isCallToAction()).isTrue();
        assertThat(lastScene.spokenText()).contains("action step");
    }

    @Test
    @DisplayName("RegexTranscriptExtractor: Missing Hook (starts directly with vocabulary)")
    void testExtractorMissingHook() throws ScriptExtractionException {
        String directVocab = """
                Target word: Relative
                The definition of relative is a person connected by blood or marriage.
                Distant relative is a common collocation.
                Action step: write sentences!
                """;
        TikTokScript script = regexExtractor.extract(directVocab);
        assertThat(script).isNotNull();
        // Hook scene should be automatically generated
        assertThat(script.scenes().get(0).isHook()).isTrue();
        assertThat(hasWord(script, "relative")).isTrue();
    }

    @Test
    @DisplayName("RegexTranscriptExtractor: No collocations (falls back gracefully to common collocation)")
    void testExtractorNoCollocations() throws ScriptExtractionException {
        String noCollocationText = """
                Welcome to lesson on Family.
                Target word: Adolescence
                Definition: the period following the onset of puberty.
                For example, adolescence can be a difficult time for teenagers.
                Action step: share your thoughts!
                """;
        TikTokScript script = regexExtractor.extract(noCollocationText);
        assertThat(script).isNotNull();
        assertThat(hasWord(script, "adolescence")).isTrue();

        Scene vocabScene = script.scenes().stream().filter(Scene::isVocabulary).findFirst().orElseThrow();
        assertThat(vocabScene.vocabularyItem()).isNotNull();
        var item = vocabScene.vocabularyItem();
        assertThat(item.word()).isEqualTo("Adolescence");
        // Collocation must not be null; should have clean fallback
        assertThat(item.collocation()).isNotBlank();
        assertThat(item.collocationMeaning()).isNotNull();
    }

    @Test
    @DisplayName("RegexTranscriptExtractor: Duplicate headings for same word are deduplicated")
    void testExtractorDuplicateHeadingsDeduplicated() throws ScriptExtractionException {
        String duplicateText = """
                Welcome to IELTS lesson on Family.
                Target word: Sibling
                The definition of sibling is a brother or sister.
                Sibling rivalry is common.
                Target word: Sibling
                The definition of sibling is a brother or sister.
                Sibling rivalry is common.
                Target word: sibling
                Meaning: a brother or sister.
                Action step: write sentences!
                """;
        TikTokScript script = regexExtractor.extract(duplicateText);
        assertThat(script).isNotNull();
        // Sibling must only appear ONCE in vocabulary scenes
        long siblingCount = script.vocabularyScenes().stream()
                .filter(s -> s.vocabularyItem() != null && s.vocabularyItem().word().equalsIgnoreCase("sibling"))
                .count();
        assertThat(siblingCount).isEqualTo(1);
    }

    @Test
    @DisplayName("RegexTranscriptExtractor: Generic word headers (non-Band 7 words)")
    void testExtractorGenericWordHeaders() throws ScriptExtractionException {
        String genericWords = """
                Welcome to IELTS lesson on Environment.
                Target word: Biodiversity
                The definition of biodiversity is the variety of plant and animal life.
                First collocation: preserve biodiversity.
                Target word: Sustainability
                The definition of sustainability is the avoidance of the depletion of natural resources.
                Common collocation: environmental sustainability.
                Action step: post your comments!
                """;
        TikTokScript script = regexExtractor.extract(genericWords);
        assertThat(script).isNotNull();
        assertThat(script.vocabularyScenes().size()).isGreaterThanOrEqualTo(2);
        assertThat(hasWord(script, "biodiversity")).isTrue();
        assertThat(hasWord(script, "sustainability")).isTrue();
    }
}
