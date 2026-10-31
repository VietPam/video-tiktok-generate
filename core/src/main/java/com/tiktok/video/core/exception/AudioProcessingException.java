package com.tiktok.video.core.exception;

import java.nio.file.Path;

/**
 * Thrown when audio duration calculation, WAV header patching, or concatenation fails.
 */
public class AudioProcessingException extends TikTokVideoException {

    private final Path audioFilePath;

    public AudioProcessingException(String message) {
        this(message, null, null);
    }

    public AudioProcessingException(String message, Throwable cause) {
        this(message, cause, null);
    }

    public AudioProcessingException(String message, Path audioFilePath) {
        this(message, null, audioFilePath);
    }

    public AudioProcessingException(String message, Throwable cause, Path audioFilePath) {
        super(message, cause);
        this.audioFilePath = audioFilePath;
    }

    public Path getAudioFilePath() {
        return audioFilePath;
    }
}
