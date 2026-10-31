package com.tiktok.video.core.contract;

import com.tiktok.video.core.exception.ScriptExtractionException;
import com.tiktok.video.core.model.TikTokScript;

/**
 * Service contract for transforming an IELTS lesson transcript text
 * into a structured {@link TikTokScript} domain model.
 */
@FunctionalInterface
public interface ScriptExtractor {

    /**
     * Extracts structured scenes (Hook, Vocabulary items with IPA/collocations, CTA)
     * from raw lesson transcript text.
     *
     * @param transcriptText raw input transcript text; must not be null or blank
     * @return structured {@link TikTokScript} domain object
     * @throws ScriptExtractionException if transcript parsing, validation, or LLM inference fails
     * @throws IllegalArgumentException if transcriptText is null or blank
     */
    TikTokScript extract(String transcriptText) throws ScriptExtractionException;
}
