package com.tiktok.video.core.exception;

import java.nio.file.Path;

/**
 * Thrown when Graphics2D card rendering, font layout, or PNG writing fails.
 */
public class VisualRenderException extends TikTokVideoException {

    private final Path cardOutputPath;

    public VisualRenderException(String message) {
        this(message, null, null);
    }

    public VisualRenderException(String message, Throwable cause) {
        this(message, cause, null);
    }

    public VisualRenderException(String message, Path cardOutputPath) {
        this(message, null, cardOutputPath);
    }

    public VisualRenderException(String message, Throwable cause, Path cardOutputPath) {
        super(message, cause);
        this.cardOutputPath = cardOutputPath;
    }

    public Path getCardOutputPath() {
        return cardOutputPath;
    }
}
