package com.tiktok.video.core.exception;

/**
 * Thrown when TTS speech synthesis fails due to network, server error, or invalid audio response.
 */
public class TtsException extends TikTokVideoException {

    private final Integer statusCode;
    private final String responseBody;

    public TtsException(String message) {
        this(message, null, null, null);
    }

    public TtsException(String message, Throwable cause) {
        this(message, cause, null, null);
    }

    public TtsException(String message, Integer statusCode, String responseBody) {
        this(message, null, statusCode, responseBody);
    }

    public TtsException(String message, Throwable cause, Integer statusCode, String responseBody) {
        super(message, cause);
        this.statusCode = statusCode;
        this.responseBody = responseBody;
    }

    public Integer getStatusCode() {
        return statusCode;
    }

    public String getResponseBody() {
        return responseBody;
    }
}
