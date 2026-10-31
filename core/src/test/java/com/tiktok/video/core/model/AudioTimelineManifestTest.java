package com.tiktok.video.core.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AudioTimelineManifestTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("Should validate timeline sequence and compute segment intervals")
    void shouldValidateTimelineSequence() {
        SceneAudioTimeline hook = new SceneAudioTimeline("scene-0", Path.of("/audio/scene_0.wav"), 0.0, 3.5, 3.5);
        SceneAudioTimeline vocab = new SceneAudioTimeline("scene-1", Path.of("/audio/scene_1.wav"), 3.8, 12.0, 8.2);

        AudioTimelineManifest manifest = new AudioTimelineManifest(
                Path.of("/audio/master.wav"),
                12.5,
                List.of(hook, vocab),
                48000
        );

        assertThat(manifest.totalDurationSeconds()).isEqualTo(12.5);
        assertThat(manifest.sampleRate()).isEqualTo(48000);
        assertThat(manifest.getTimeline("scene-1")).contains(vocab);
        assertThat(manifest.getTimeline("non-existent")).isEmpty();
    }

    @Test
    @DisplayName("Should enforce duration invariants on SceneAudioTimeline")
    void shouldEnforceTimelineInvariants() {
        // startSeconds cannot be negative
        assertThatThrownBy(() -> new SceneAudioTimeline("s0", Path.of("a.wav"), -1.0, 5.0, 6.0))
                .isInstanceOf(IllegalArgumentException.class);

        // endSeconds cannot be less than startSeconds
        assertThatThrownBy(() -> new SceneAudioTimeline("s0", Path.of("a.wav"), 5.0, 3.0, -2.0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
