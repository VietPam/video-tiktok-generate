package com.tiktok.video.core.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RenderBlueprintAndResultTest {

    @Test
    @DisplayName("RenderResult factory methods should construct success and failure records correctly")
    void shouldConstructRenderResultCorrectly() {
        RenderResult success = RenderResult.success(
                Path.of("/workspace/output/final.mp4"),
                65.4,
                14_500_000L,
                8200L
        );

        assertThat(success.success()).isTrue();
        assertThat(success.outputPath()).isEqualTo(Path.of("/workspace/output/final.mp4"));
        assertThat(success.outputVideoPath()).isEqualTo(Path.of("/workspace/output/final.mp4"));
        assertThat(success.durationSeconds()).isEqualTo(65.4);
        assertThat(success.errorMessage()).isNull();

        RenderResult failure = RenderResult.failure("FFmpeg segmentation fault");
        assertThat(failure.success()).isFalse();
        assertThat(failure.outputVideoPath()).isNull();
        assertThat(failure.errorMessage()).isEqualTo("FFmpeg segmentation fault");
    }

    @Test
    @DisplayName("RenderBlueprint should default to 1080x1920 at 30 fps")
    void shouldDefaultDimensionsAndFps() {
        AudioTimelineManifest timeline = new AudioTimelineManifest(
                Path.of("/audio/master.wav"),
                60.0,
                List.of(),
                48000
        );

        RenderBlueprint blueprint = new RenderBlueprint(
                timeline,
                Path.of("/subs/subtitles.ass"),
                Map.of("scene-1", Path.of("/cards/card_1.png")),
                Path.of("/assets/bg.mp4"),
                Path.of("/assets/bgm.mp3"),
                Path.of("/output/video.mp4"),
                1080,
                1920,
                30
        );

        assertThat(blueprint.width()).isEqualTo(1080);
        assertThat(blueprint.height()).isEqualTo(1920);
        assertThat(blueprint.fps()).isEqualTo(30.0);
    }
}
