package com.tiktok.video.subtitle.generator;

import com.tiktok.video.core.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AssSubtitleGeneratorTest {

    private AssSubtitleGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new AssSubtitleGenerator("Arial", 64);
    }

    @Test
    void formatAssTime_shouldFormatCorrectly() {
        assertThat(AssSubtitleGenerator.formatAssTime(0.0)).isEqualTo("0:00:00.00");
        assertThat(AssSubtitleGenerator.formatAssTime(5.25)).isEqualTo("0:00:05.25");
        assertThat(AssSubtitleGenerator.formatAssTime(65.5)).isEqualTo("0:01:05.50");
        assertThat(AssSubtitleGenerator.formatAssTime(3661.12)).isEqualTo("1:01:01.12");
    }

    @Test
    void generateAss_shouldProduceValidAssFile(@TempDir Path tempDir) throws IOException {
        VocabularyItem item = new VocabularyItem("Sibling", "/ˈsɪb.lɪŋ/", "noun", "Anh chị em ruột", "sibling rivalry", "sự ganh đua giữa anh chị em", "I have two siblings.");
        Scene scene1 = Scene.hook("scene_1", 0, "Welcome to IELTS Vocabulary Lesson", "Welcome to IELTS Lesson");
        Scene scene2 = Scene.vocabulary("scene_2", 1, "Sibling means brother or sister", "Sibling: Brother or sister", item);
        Scene scene3 = Scene.callToAction("scene_3", 2, "Comment below your sentence!", "Comment below!");

        TikTokScript script = new TikTokScript("Family", "Band 7+", List.of(scene1, scene2, scene3));

        SceneAudioTimeline sat1 = new SceneAudioTimeline("scene_1", tempDir.resolve("s1.wav"), 0.0, 3.5, 3.5);
        SceneAudioTimeline sat2 = new SceneAudioTimeline("scene_2", tempDir.resolve("s2.wav"), 3.5, 9.0, 5.5);
        SceneAudioTimeline sat3 = new SceneAudioTimeline("scene_3", tempDir.resolve("s3.wav"), 9.0, 12.0, 3.0);

        AudioTimelineManifest manifest = new AudioTimelineManifest(tempDir.resolve("master.wav"), 12.0, List.of(sat1, sat2, sat3), 48000);

        Path generatedFile = generator.generateAss(script, manifest, tempDir);

        assertThat(generatedFile).isNotNull();
        assertThat(Files.exists(generatedFile)).isTrue();
        assertThat(generatedFile.toString()).endsWith(".ass");

        String content = Files.readString(generatedFile);
        assertThat(content).contains("[Script Info]");
        assertThat(content).contains("PlayResX: 1080");
        assertThat(content).contains("PlayResY: 1920");
        assertThat(content).contains("[V4+ Styles]");
        assertThat(content).contains("CardBg");
        assertThat(content).contains("SubEng");
        assertThat(content).contains("SubVie");
        assertThat(content).contains("[Events]");
        assertThat(content).contains("CardBg");
        assertThat(content).contains("SubEng");
        assertThat(content).contains("SubVie");
    }

    @Test
    void generateAss_shouldThrowOnNullArguments(@TempDir Path tempDir) {
        assertThrows(IllegalArgumentException.class, () -> generator.generateAss(null, null, tempDir));
    }
}
