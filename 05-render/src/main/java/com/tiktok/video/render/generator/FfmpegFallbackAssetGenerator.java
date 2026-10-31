package com.tiktok.video.render.generator;

import com.tiktok.video.core.contract.FallbackAssetGenerator;
import com.tiktok.video.core.exception.RenderException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Generates synthetic fallback placeholder media assets using FFmpeg commands:
 * - 9:16 background video with smooth dark aesthetic gradient
 * - Gentle ambient BGM audio
 */
public class FfmpegFallbackAssetGenerator implements FallbackAssetGenerator {

    private static final Logger log = LoggerFactory.getLogger(FfmpegFallbackAssetGenerator.class);

    private final String ffmpegExecutable;

    public FfmpegFallbackAssetGenerator() {
        this("ffmpeg");
    }

    public FfmpegFallbackAssetGenerator(String ffmpegExecutable) {
        this.ffmpegExecutable = (ffmpegExecutable == null || ffmpegExecutable.isBlank()) ? "ffmpeg" : ffmpegExecutable.trim();
    }

    @Override
    public Path generateBackgroundVideo(double durationSeconds, Path outputPath) throws RenderException {
        if (durationSeconds <= 0.0) {
            throw new IllegalArgumentException("durationSeconds must be > 0: " + durationSeconds);
        }
        if (outputPath == null) {
            throw new IllegalArgumentException("outputPath cannot be null");
        }

        try {
            if (outputPath.getParent() != null) {
                Files.createDirectories(outputPath.getParent());
            }

            // Command: Generate 1080x1920 pastel dot-pattern background video
            List<String> cmd = new ArrayList<>();
            cmd.add(ffmpegExecutable);
            cmd.add("-y");

            // Check if pastel dots image exists, otherwise fallback to color filter
            Path bgImg = Paths.get("/app/assets/images/pastel_dots_bg.png");
            if (!Files.exists(bgImg)) {
                bgImg = Paths.get("assets/images/pastel_dots_bg.png");
            }

            if (Files.exists(bgImg)) {
                cmd.add("-loop");
                cmd.add("1");
                cmd.add("-i");
                cmd.add(bgImg.toAbsolutePath().toString());
            } else {
                cmd.add("-f");
                cmd.add("lavfi");
                cmd.add("-i");
                cmd.add(String.format(Locale.ROOT, "color=c=0xFEFAF3:s=1080x1920:r=30:d=%.2f", durationSeconds));
            }

            cmd.add("-t");
            cmd.add(String.format(Locale.ROOT, "%.2f", durationSeconds));
            cmd.add("-vf");
            cmd.add("scale=1080:1920");
            cmd.add("-c:v");
            cmd.add("libx264");
            cmd.add("-pix_fmt");
            cmd.add("yuv420p");
            cmd.add(outputPath.toAbsolutePath().toString());

            runProcess(cmd, "generateBackgroundVideo");
            return outputPath.toAbsolutePath();
        } catch (Exception e) {
            throw new RenderException("Failed to generate fallback background video: " + e.getMessage(), e);
        }
    }

    @Override
    public Path generateBgmAudio(double durationSeconds, Path outputPath) throws RenderException {
        if (durationSeconds <= 0.0) {
            throw new IllegalArgumentException("durationSeconds must be > 0: " + durationSeconds);
        }
        if (outputPath == null) {
            throw new IllegalArgumentException("outputPath cannot be null");
        }

        try {
            if (outputPath.getParent() != null) {
                Files.createDirectories(outputPath.getParent());
            }

            // Generate soft ambient sine/pad tone: aevalsrc=sin(220*2*PI*t)*0.03
            List<String> cmd = new ArrayList<>();
            cmd.add(ffmpegExecutable);
            cmd.add("-y");
            cmd.add("-f");
            cmd.add("lavfi");
            cmd.add("-i");
            cmd.add(String.format(Locale.ROOT, "aevalsrc=sin(220*2*PI*t)*0.02|sin(330*2*PI*t)*0.02:s=48000:d=%.2f", durationSeconds));
            cmd.add("-t");
            cmd.add(String.format(Locale.ROOT, "%.2f", durationSeconds));
            cmd.add("-c:a");
            cmd.add("aac");
            cmd.add("-b:a");
            cmd.add("128k");
            cmd.add(outputPath.toAbsolutePath().toString());

            runProcess(cmd, "generateBgmAudio");
            return outputPath.toAbsolutePath();
        } catch (Exception e) {
            throw new RenderException("Failed to generate fallback BGM audio: " + e.getMessage(), e);
        }
    }

    private void runProcess(List<String> command, String operationName) throws Exception {
        log.debug("Executing FFmpeg command for {}: {}", operationName, String.join(" ", command));
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process p = pb.start();

        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }

        int exitCode = p.waitFor();
        if (exitCode != 0) {
            log.error("FFmpeg error output ({}):\n{}", operationName, output);
            throw new RenderException("FFmpeg command failed with exit code " + exitCode + ": " + output);
        }
    }
}
