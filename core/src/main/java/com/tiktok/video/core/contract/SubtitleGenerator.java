package com.tiktok.video.core.contract;

import com.tiktok.video.core.exception.SubtitleGenerationException;
import com.tiktok.video.core.model.AudioTimelineManifest;
import com.tiktok.video.core.model.TikTokScript;
import java.nio.file.Path;

/**
 * Service contract for generating TikTok-styled Advanced SubStation Alpha (.ass) subtitles
 * synchronized with audio timeline milestones.
 */
public interface SubtitleGenerator {

    /**
     * Generates a TikTok-styled .ass subtitle file with high-visibility styling
     * (large font, heavy outline, active word/phrase highlight) synchronized with audio timeline.
     *
     * @param script structured script containing scene dialogue and vocabulary details
     * @param timeline audio timeline manifest providing exact scene start and end times
     * @param outputDir directory where the .ass subtitle file will be saved
     * @return absolute path to the generated .ass file
     * @throws SubtitleGenerationException if timestamps are inconsistent or file generation fails
     * @throws IllegalArgumentException if script, timeline, or outputDir is null
     */
    Path generateAss(TikTokScript script, AudioTimelineManifest timeline, Path outputDir) throws SubtitleGenerationException;
}
