package com.tiktok.video.core.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExceptionHierarchyTest {

    @Test
    @DisplayName("All domain exceptions should inherit from TikTokVideoException and RuntimeException")
    void allExceptionsShouldInheritFromBase() {
        TikTokVideoException extraction = new ScriptExtractionException("Extraction error");
        TikTokVideoException tts = new TtsException("TTS failed");
        TikTokVideoException audio = new AudioProcessingException("Audio error");
        TikTokVideoException subtitle = new SubtitleGenerationException("Subtitle error");
        TikTokVideoException visual = new VisualRenderException("Visual error");
        TikTokVideoException render = new RenderException("Render error");

        assertThat(extraction).isInstanceOf(RuntimeException.class);
        assertThat(tts).isInstanceOf(RuntimeException.class);
        assertThat(audio).isInstanceOf(RuntimeException.class);
        assertThat(subtitle).isInstanceOf(RuntimeException.class);
        assertThat(visual).isInstanceOf(RuntimeException.class);
        assertThat(render).isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("TtsException should retain HTTP status code and response body diagnostics")
    void ttsExceptionShouldRetainDiagnostics() {
        TtsException ex = new TtsException("Gateway Timeout", 504, "{\"error\": \"timeout\"}");

        assertThat(ex.getMessage()).isEqualTo("Gateway Timeout");
        assertThat(ex.getStatusCode()).isEqualTo(504);
        assertThat(ex.getResponseBody()).isEqualTo("{\"error\": \"timeout\"}");
    }

    @Test
    @DisplayName("RenderException should retain exit code and command line arguments")
    void renderExceptionShouldRetainCommandDiagnostics() {
        List<String> cmd = List.of("ffmpeg", "-i", "input.mp4", "output.mp4");
        RenderException ex = new RenderException("FFmpeg exited with error", 1, cmd);

        assertThat(ex.getExitCode()).isEqualTo(1);
        assertThat(ex.getCommand()).containsExactlyElementsOf(cmd);
    }

    @Test
    @DisplayName("AudioProcessingException should retain failing audio path")
    void audioProcessingExceptionShouldRetainPath() {
        Path path = Path.of("/workspace/output/scene_01.wav");
        AudioProcessingException ex = new AudioProcessingException("Corrupted WAV header", path);

        assertThat(ex.getAudioFilePath()).isEqualTo(path);
    }

    @Test
    @DisplayName("Exceptions should preserve chained cause")
    void shouldPreserveChainedCause() {
        IOException cause = new IOException("Disk full");
        VisualRenderException ex = new VisualRenderException("Failed writing card PNG", cause);

        assertThat(ex.getCause()).isSameAs(cause);
    }
}
