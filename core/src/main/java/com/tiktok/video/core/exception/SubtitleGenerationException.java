package com.tiktok.video.core.exception;

/**
 * Thrown when generating Advanced SubStation Alpha (.ass) subtitles fails.
 */
public class SubtitleGenerationException extends TikTokVideoException {

    public SubtitleGenerationException(String message) {
        super(message);
    }

    public SubtitleGenerationException(String message, Throwable cause) {
        super(message, cause);
    }

    public SubtitleGenerationException(Throwable cause) {
        super(cause);
    }
}
