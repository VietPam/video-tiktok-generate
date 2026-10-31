package com.tiktok.video.render.composer;

import com.tiktok.video.core.contract.VideoComposer;
import com.tiktok.video.core.exception.RenderException;
import com.tiktok.video.core.model.RenderBlueprint;
import com.tiktok.video.core.model.RenderResult;
import com.tiktok.video.core.model.SceneAudioTimeline;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Orchestrates FFmpeg CLI via Java ProcessBuilder to combine background video,
 * visual card PNG overlays, mixed voiceover audio, BGM, and .ass subtitles into MP4.
 */
public class FfmpegVideoComposer implements VideoComposer {

    private static final Logger log = LoggerFactory.getLogger(FfmpegVideoComposer.class);

    private final String ffmpegExecutable;

    public FfmpegVideoComposer() {
        this("ffmpeg");
    }

    public FfmpegVideoComposer(String ffmpegExecutable) {
        this.ffmpegExecutable = (ffmpegExecutable == null || ffmpegExecutable.isBlank()) ? "ffmpeg" : ffmpegExecutable.trim();
    }

    @Override
    public RenderResult composeVideo(RenderBlueprint blueprint) throws RenderException {
        if (blueprint == null) {
            throw new IllegalArgumentException("blueprint cannot be null");
        }

        long startTime = System.currentTimeMillis();
        Path outputPath = blueprint.outputVideoPath();

        try {
            if (outputPath.getParent() != null) {
                Files.createDirectories(outputPath.getParent());
            }

            List<String> cmd = buildFfmpegCommand(blueprint);
            log.info("Executing video composition FFmpeg command: {}", String.join(" ", cmd));

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process p = pb.start();

            StringBuilder outputLog = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    outputLog.append(line).append("\n");
                }
            }

            int exitCode = p.waitFor();
            long renderDurationMs = System.currentTimeMillis() - startTime;

            if (exitCode != 0) {
                log.error("FFmpeg composition failed with code {}:\n{}", exitCode, outputLog);
                return RenderResult.failure("FFmpeg exited with code " + exitCode + ": " + outputLog, renderDurationMs);
            }

            if (!Files.exists(outputPath) || Files.size(outputPath) == 0) {
                return RenderResult.failure("FFmpeg completed but output file does not exist or is empty", renderDurationMs);
            }

            long fileSizeBytes = Files.size(outputPath);
            double durationSeconds = blueprint.totalDurationSeconds();

            log.info("Successfully rendered video to {} (size: {} bytes, took: {} ms)",
                    outputPath, fileSizeBytes, renderDurationMs);

            return RenderResult.success(outputPath, durationSeconds, fileSizeBytes, renderDurationMs);
        } catch (Exception e) {
            long renderDurationMs = System.currentTimeMillis() - startTime;
            log.error("Error during video composition: {}", e.getMessage(), e);
            throw new RenderException("Video composition failed: " + e.getMessage(), e);
        }
    }

    List<String> buildFfmpegCommand(RenderBlueprint blueprint) {
        List<String> cmd = new ArrayList<>();
        cmd.add(ffmpegExecutable);
        cmd.add("-y");

        // Input 0: Background video
        cmd.add("-i");
        cmd.add(blueprint.backgroundVideoPath().toAbsolutePath().toString());

        // Input 1: Master Voiceover audio
        cmd.add("-i");
        cmd.add(blueprint.audioTimeline().masterAudioPath().toAbsolutePath().toString());

        // Input 2 (Optional): BGM audio
        int bgmInputIndex = -1;
        if (blueprint.hasBgm() && Files.exists(blueprint.bgmAudioPath())) {
            bgmInputIndex = 2;
            cmd.add("-i");
            cmd.add(blueprint.bgmAudioPath().toAbsolutePath().toString());
        }

        // Overlay inputs: Card PNGs
        // Map card inputs to index
        List<OverlaySpec> overlaySpecs = new ArrayList<>();
        int nextInputIdx = (bgmInputIndex == -1) ? 2 : 3;

        for (SceneAudioTimeline sat : blueprint.audioTimeline().sceneTimelines()) {
            Path cardPath = blueprint.getCardForScene(sat.sceneId());
            if (cardPath != null && Files.exists(cardPath)) {
                cmd.add("-i");
                cmd.add(cardPath.toAbsolutePath().toString());
                overlaySpecs.add(new OverlaySpec(nextInputIdx++, sat.startSeconds(), sat.endSeconds()));
            }
        }

        // Build complex filter for video and audio
        StringBuilder filterComplex = new StringBuilder();

        // Video chain: scale background, then overlay cards sequentially, then apply ass subtitle if present
        String currentVideoLabel = "0:v";
        // Ensure background is scaled and cropped to 1080x1920
        filterComplex.append(String.format(Locale.ROOT,
                "[%s]scale=%d:%d:force_original_aspect_ratio=increase,crop=%d:%d[vbase];",
                currentVideoLabel, blueprint.width(), blueprint.height(), blueprint.width(), blueprint.height()));
        currentVideoLabel = "vbase";

        // Chain overlay cards with enable='between(t,start,end)'
        for (int i = 0; i < overlaySpecs.size(); i++) {
            OverlaySpec spec = overlaySpecs.get(i);
            String nextVideoLabel = "vcard" + i;
            filterComplex.append(String.format(Locale.ROOT,
                    "[%s][%d:v]overlay=0:0:enable='between(t,%.2f,%.2f)'[%s];",
                    currentVideoLabel, spec.inputIndex, spec.startSec, spec.endSec, nextVideoLabel));
            currentVideoLabel = nextVideoLabel;
        }

        // Subtitle filter (ass)
        if (blueprint.hasSubtitles() && Files.exists(blueprint.subtitlePath())) {
            String nextVideoLabel = "vsub";
            String escapedAssPath = blueprint.subtitlePath().toAbsolutePath().toString()
                    .replace("\\", "/")
                    .replace(":", "\\:")
                    .replace("'", "\\'");
            filterComplex.append(String.format(Locale.ROOT,
                    "[%s]ass='%s'[%s];",
                    currentVideoLabel, escapedAssPath, nextVideoLabel));
            currentVideoLabel = nextVideoLabel;
        }

        // Audio mix chain: Voiceover + BGM (volume 0.15)
        if (bgmInputIndex != -1) {
            filterComplex.append(String.format(Locale.ROOT,
                    "[1:a]volume=1.0[voice];[%d:a]volume=0.12[bgm];[voice][bgm]amix=inputs=2:duration=first:dropout_transition=2[aout]",
                    bgmInputIndex));
        } else {
            filterComplex.append("[1:a]volume=1.0[aout]");
        }

        cmd.add("-filter_complex");
        cmd.add(filterComplex.toString());

        // Map streams
        cmd.add("-map");
        cmd.add("[" + currentVideoLabel + "]");
        cmd.add("-map");
        cmd.add("[aout]");

        // Duration limit to audio length
        cmd.add("-t");
        cmd.add(String.format(Locale.ROOT, "%.2f", blueprint.totalDurationSeconds()));

        // Encoders and flags
        cmd.add("-c:v");
        cmd.add("libx264");
        cmd.add("-preset");
        cmd.add("ultrafast");
        cmd.add("-pix_fmt");
        cmd.add("yuv420p");
        cmd.add("-r");
        cmd.add(String.format(Locale.ROOT, "%.0f", blueprint.fps()));
        cmd.add("-c:a");
        cmd.add("aac");
        cmd.add("-b:a");
        cmd.add("192k");

        cmd.add(blueprint.outputVideoPath().toAbsolutePath().toString());

        return cmd;
    }

    record OverlaySpec(int inputIndex, double startSec, double endSec) {}
}
