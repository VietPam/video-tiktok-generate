package com.tiktok.video.core.contract;

import com.tiktok.video.core.exception.AudioProcessingException;
import com.tiktok.video.core.model.AudioTimelineManifest;
import com.tiktok.video.core.model.TikTokScript;
import java.nio.file.Path;

/**
 * Service contract for synthesizing scene audio, measuring durations,
 * inserting inter-scene pause buffers, concatenating master audio, and generating timeline manifest.
 */
public interface AudioTimelineService {

    /**
     * Synthesizes audio for all scenes in the script and outputs individual scene WAVs
     * alongside a concatenated master audio file, returning the timeline manifest.
     *
     * @param script structured TikTok script containing scenes to synthesize
     * @param outputDir directory to save individual audio clips and master audio
     * @return manifest containing start/end timestamps for each scene and master audio path
     * @throws AudioProcessingException if synthesis, duration calculation, WAV header patching, or concatenation fails
     * @throws IllegalArgumentException if script or outputDir is null
     */
    AudioTimelineManifest generateTimeline(TikTokScript script, Path outputDir) throws AudioProcessingException;
}
