package com.tiktok.video.core.exception;

/**
 * Root unchecked domain exception for all video-tiktok-generate failures.
 */
public class TikTokVideoException extends RuntimeException {

    public TikTokVideoException(String message) {
        super(message);
    }

    public TikTokVideoException(String message, Throwable cause) {
        super(message, cause);
    }

    public TikTokVideoException(Throwable cause) {
        super(cause);
    }
}
