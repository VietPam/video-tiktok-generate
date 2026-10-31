package com.tiktok.video.script.adversarial;

import com.tiktok.video.core.model.Scene;
import com.tiktok.video.core.model.TikTokScript;
import com.tiktok.video.script.regex.RegexTranscriptExtractor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class AdversarialRegexStressTest {

    private RegexTranscriptExtractor extractor;

    @BeforeEach
    void setUp() {
        extractor = new RegexTranscriptExtractor();
    }

    @Test
    @DisplayName("STRESS-1.2: Standalone novel words on their own line without 'Word:' prefix")
    void testStandaloneNovelWordWithoutPrefix() {
        String novelTranscript = """
                Introduction
                0:00
                Welcome to this IELTS Master Band 7 plus vocabulary lesson on Environment.
                Ubiquitous
                0:12
                The definition of ubiquitous is present, appearing, or found everywhere.
                0:20
                For example, smartphones have become ubiquitous in modern society.
                0:25
                Collocations
                0:30
                Ubiquitous presence is a common collocation meaning found all over the place.
                Summary
                0:40
                Your action step for today is to write two sentences with ubiquitous in the comments below.
                """;

        TikTokScript script = extractor.extract(novelTranscript);

        assertThat(script).isNotNull();
        assertThat(script.topic()).isEqualTo("Environment");
        assertThat(script.vocabularyScenes())
                .as("Standalone novel word without 'Word:' prefix must be extracted dynamically")
                .hasSize(1);

        Scene scene = script.vocabularyScenes().get(0);
        assertThat(scene.vocabularyItem()).isNotNull();
        assertThat(scene.vocabularyItem().word()).isEqualTo("Ubiquitous");
        assertThat(scene.vocabularyItem().definition()).contains("present, appearing, or found everywhere");
        assertThat(scene.vocabularyItem().collocation()).isEqualTo("ubiquitous presence");
        assertThat(scene.vocabularyItem().partOfSpeech()).isEqualTo("adjective");
    }

    @Test
    @DisplayName("STRESS-1.3: Multiple novel words on standalone lines with dynamic POS")
    void testMultipleNovelWordsDynamicPos() {
        String transcript = """
                Welcome to IELTS Master Band 7 plus lesson on Technology.
                Flourish
                Definition: to grow or develop in a healthy or vigorous way.
                Example: tech startups flourish in favorable economic conditions.
                Collocation: flourish rapidly is a common collocation.
                Resilience
                Definition: the capacity to recover quickly from difficulties.
                Example: digital resilience helps organizations survive cyberattacks.
                Collocation: emotional resilience is a common collocation.
                Action step: share your thoughts!
                """;

        TikTokScript script = extractor.extract(transcript);

        assertThat(script.vocabularyScenes()).hasSize(2);
        List<String> words = script.vocabularyScenes().stream().map(s -> s.vocabularyItem().word()).toList();
        assertThat(words).containsExactly("Flourish", "Resilience");

        Scene s1 = script.vocabularyScenes().get(0);
        assertThat(s1.vocabularyItem().partOfSpeech()).isEqualTo("verb"); // "to grow..."

        Scene s2 = script.vocabularyScenes().get(1);
        assertThat(s2.vocabularyItem().partOfSpeech()).isEqualTo("noun"); // ends in -ence
    }

    @Test
    @DisplayName("STRESS-1.4: Hyphenated compound novel words on standalone lines")
    void testHyphenatedCompoundNovelWords() {
        String transcript = """
                Welcome to IELTS lesson on Education.
                Well-being
                Definition: the state of being comfortable, healthy, or happy.
                Example: mental well-being is vital for academic success.
                Collocation: overall well-being is a common collocation.
                Summary
                Action step: write comments below.
                """;

        TikTokScript script = extractor.extract(transcript);
        assertThat(script.vocabularyScenes()).hasSize(1);
        assertThat(script.vocabularyScenes().get(0).vocabularyItem().word()).isEqualTo("Well-being");
        assertThat(script.vocabularyScenes().get(0).vocabularyItem().definition()).contains("the state of being comfortable");
    }

    @Test
    @DisplayName("STRESS-1.5: Casual mentions in introduction list do not produce false positive scenes")
    void testIntroCasualMentionsIgnored() {
        String transcript = """
                Introduction
                Welcome to IELTS lesson on Psychology.
                In this lesson our words are:
                resilience
                perseverance
                Now let's examine word one.
                Resilience
                The definition of resilience is the capacity to recover quickly from difficulties.
                Example: psychological resilience protects against stress.
                Collocation: emotional resilience is a common collocation.
                Perseverance
                The definition of perseverance is persistence in doing something despite difficulty.
                Example: perseverance is essential for achieving long-term goals.
                Collocation: persistent perseverance is a common collocation.
                Summary
                Action step: post your sentences.
                """;

        TikTokScript script = extractor.extract(transcript);
        assertThat(script.vocabularyScenes()).hasSize(2);
        List<String> words = script.vocabularyScenes().stream().map(s -> s.vocabularyItem().word()).toList();
        assertThat(words).containsExactly("Resilience", "Perseverance");
    }
}
