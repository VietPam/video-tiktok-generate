package com.tiktok.video.core.exception;

/**
 * Thrown when extracting structured script data from transcript text fails.
 */
public class ScriptExtractionException extends TikTokVideoException {

    private final String rawTranscriptSnippet;

    public ScriptExtractionException(String message) {
        this(message, null, null);
    }

    public ScriptExtractionException(String message, Throwable cause) {
        this(message, cause, null);
    }

    public ScriptExtractionException(String message, Throwable cause, String rawTranscriptSnippet) {
        super(message, cause);
        this.rawTranscriptSnippet = rawTranscriptSnippet;
    }

    public String getRawTranscriptSnippet() {
        return rawTranscriptSnippet;
    }
}
