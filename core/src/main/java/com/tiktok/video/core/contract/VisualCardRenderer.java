package com.tiktok.video.core.contract;

import com.tiktok.video.core.exception.VisualRenderException;
import com.tiktok.video.core.model.TikTokScript;
import java.nio.file.Path;
import java.util.Map;

/**
 * Service contract for rendering high-resolution transparent PNG visual cards (1080x1920)
 * displaying vocabulary words, IPA phonetic notation, definitions, and collocations.
 */
public interface VisualCardRenderer {

    /**
     * Renders visual cards for scenes in the script and writes PNG files to disk.
     *
     * @param script structured script containing vocabulary scenes
     * @param outputDir directory where PNG files will be written
     * @return map of sceneId to the generated PNG card file Path
     * @throws VisualRenderException if graphics rendering, font loading, or file writing fails
     * @throws IllegalArgumentException if script or outputDir is null
     */
    Map<String, Path> renderCards(TikTokScript script, Path outputDir) throws VisualRenderException;
}
