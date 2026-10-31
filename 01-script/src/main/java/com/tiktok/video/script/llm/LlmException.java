package com.tiktok.video.script.llm;

import com.tiktok.video.core.exception.ScriptExtractionException;

public class LlmException extends ScriptExtractionException {
    public LlmException(String message) {
        super(message);
    }

    public LlmException(String message, Throwable cause) {
        super(message, cause);
    }

    public LlmException(String message, Throwable cause, String rawTranscriptSnippet) {
        super(message, cause, rawTranscriptSnippet);
    }
}
