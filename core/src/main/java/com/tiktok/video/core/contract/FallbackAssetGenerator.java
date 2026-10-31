package com.tiktok.video.core.contract;

import com.tiktok.video.core.exception.RenderException;
import java.nio.file.Path;

/**
 * Service contract for generating synthetic fallback placeholder media assets
 * when external background video or BGM audio files are not provided.
 */
public interface FallbackAssetGenerator {

    /**
     * Generates a synthetic vertical 9:16 background video (1080x1920, 30fps)
     * of specified duration.
     *
     * @param durationSeconds duration of placeholder video in seconds; must be > 0
     * @param outputPath destination path for the synthetic video file (e.g. .mp4)
     * @return absolute path to the generated background video file
     * @throws RenderException if video synthesis fails
     * @throws IllegalArgumentException if durationSeconds <= 0 or outputPath is null
     */
    Path generateBackgroundVideo(double durationSeconds, Path outputPath) throws RenderException;

    /**
     * Generates a synthetic background music (BGM) audio track of specified duration.
     *
     * @param durationSeconds duration of placeholder audio in seconds; must be > 0
     * @param outputPath destination path for the synthetic audio file (e.g. .wav or .mp3)
     * @return absolute path to the generated BGM audio file
     * @throws RenderException if audio synthesis fails
     * @throws IllegalArgumentException if durationSeconds <= 0 or outputPath is null
     */
    Path generateBgmAudio(double durationSeconds, Path outputPath) throws RenderException;
}
