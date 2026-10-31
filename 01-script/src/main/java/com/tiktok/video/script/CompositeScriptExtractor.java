package com.tiktok.video.script;

import com.tiktok.video.core.contract.ScriptExtractor;
import com.tiktok.video.core.exception.ScriptExtractionException;
import com.tiktok.video.core.model.TikTokScript;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

public class CompositeScriptExtractor implements ScriptExtractor {

    private static final Logger log = LoggerFactory.getLogger(CompositeScriptExtractor.class);

    private final ScriptExtractor primaryExtractor;
    private final ScriptExtractor fallbackExtractor;
    private final boolean llmEnabled;

    public CompositeScriptExtractor(ScriptExtractor primaryExtractor, ScriptExtractor fallbackExtractor, boolean llmEnabled) {
        this.primaryExtractor = primaryExtractor;
        this.fallbackExtractor = Objects.requireNonNull(fallbackExtractor, "fallbackExtractor cannot be null");
        this.llmEnabled = llmEnabled;
    }

    public CompositeScriptExtractor(ScriptExtractor primaryExtractor, ScriptExtractor fallbackExtractor) {
        this(primaryExtractor, fallbackExtractor, true);
    }

    public CompositeScriptExtractor(ScriptExtractor fallbackExtractor) {
        this(null, fallbackExtractor, false);
    }

    @Override
    public TikTokScript extract(String transcriptText) throws ScriptExtractionException {
        if (transcriptText == null || transcriptText.isBlank()) {
            throw new IllegalArgumentException("transcriptText cannot be null or blank");
        }

        if (llmEnabled && primaryExtractor != null) {
            try {
                log.info("Attempting LLM-based transcript extraction...");
                TikTokScript script = primaryExtractor.extract(transcriptText);
                if (script != null && !script.scenes().isEmpty()) {
                    log.info("LLM transcript extraction succeeded with {} scenes", script.totalScenes());
                    return script;
                }
                log.warn("LLM extractor returned empty script, falling back to deterministic regex");
            } catch (Exception e) {
                log.warn("LLM script extraction failed ({}), falling back to regex: {}", e.getClass().getSimpleName(), e.getMessage());
            }
        }

        log.info("Executing deterministic RegexTranscriptExtractor fallback...");
        return fallbackExtractor.extract(transcriptText);
    }
}
