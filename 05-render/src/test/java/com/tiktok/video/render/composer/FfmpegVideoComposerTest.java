package com.tiktok.video.render.composer;

import com.tiktok.video.core.model.AudioTimelineManifest;
import com.tiktok.video.core.model.RenderBlueprint;
import com.tiktok.video.core.model.SceneAudioTimeline;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FfmpegVideoComposerTest {

    private FfmpegVideoComposer composer;

    @BeforeEach
    void setUp() {
        composer = new FfmpegVideoComposer("ffmpeg");
    }

    @Test
    void buildFfmpegCommand_shouldIncludeBasicComponents() {
        Path masterWav = Paths.get("/tmp/audio/master.wav");
        Path bgVideo = Paths.get("/tmp/video/bg.mp4");
        Path bgm = Paths.get("/tmp/audio/bgm.mp3");
        Path sub = Paths.get("/tmp/sub/sub.ass");
        Path card = Paths.get("/tmp/visual/card1.png");
        Path out = Paths.get("/tmp/output/final.mp4");

        SceneAudioTimeline sat = new SceneAudioTimeline("s1", Paths.get("/tmp/s1.wav"), 0.0, 5.0, 5.0);
        AudioTimelineManifest manifest = new AudioTimelineManifest(masterWav, 5.0, List.of(sat), 48000);

        RenderBlueprint blueprint = RenderBlueprint.builder()
                .audioTimeline(manifest)
                .backgroundVideoPath(bgVideo)
                .bgmAudioPath(bgm)
                .subtitlePath(sub)
                .visualCardPaths(Map.of("s1", card))
                .outputVideoPath(out)
                .width(1080)
                .height(1920)
                .fps(30.0)
                .build();

        List<String> cmd = composer.buildFfmpegCommand(blueprint);

        assertThat(cmd).contains("ffmpeg");
        assertThat(cmd).contains("-y");
        assertThat(cmd).contains(bgVideo.toAbsolutePath().toString());
        assertThat(cmd).contains(masterWav.toAbsolutePath().toString());
        assertThat(cmd).contains("-filter_complex");
        assertThat(cmd).contains("-c:v");
        assertThat(cmd).contains("libx264");
        assertThat(cmd).contains(out.toAbsolutePath().toString());
    }

    @Test
    void composeVideo_shouldThrowOnNullBlueprint() {
        assertThrows(IllegalArgumentException.class, () -> composer.composeVideo(null));
    }
}
