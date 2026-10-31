package com.tiktok.video.core.exception;

import java.util.List;

/**
 * Thrown when FFmpeg CLI video composition or fallback asset generation fails.
 */
public class RenderException extends TikTokVideoException {

    private final Integer exitCode;
    private final List<String> command;

    public RenderException(String message) {
        this(message, null, null, null);
    }

    public RenderException(String message, Throwable cause) {
        this(message, cause, null, null);
    }

    public RenderException(String message, Integer exitCode, List<String> command) {
        this(message, null, exitCode, command);
    }

    public RenderException(String message, Throwable cause, Integer exitCode, List<String> command) {
        super(message, cause);
        this.exitCode = exitCode;
        this.command = command != null ? List.copyOf(command) : null;
    }

    public Integer getExitCode() {
        return exitCode;
    }

    public List<String> getCommand() {
        return command;
    }
}
