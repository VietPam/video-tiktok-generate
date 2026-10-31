package com.tiktok.video.core.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import java.nio.file.Path;

/**
 * Output descriptor representing the result of video composition and rendering.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record RenderResult(
    @JsonProperty("success") boolean success,
    @JsonProperty("outputPath") @JsonSerialize(using = ToStringSerializer.class) Path outputPath,
    @JsonProperty("durationSeconds") double durationSeconds,
    @JsonProperty("fileSizeBytes") long fileSizeBytes,
    @JsonProperty("renderDurationMs") long renderDurationMs,
    @JsonProperty("errorMessage") String errorMessage
) {
    @JsonIgnore
    public boolean isFailure() {
        return !success;
    }

    @JsonIgnore
    public Path outputVideoPath() {
        return outputPath;
    }

    public static RenderResult success(Path outputPath, double durationSeconds, long fileSizeBytes, long renderDurationMs) {
        return new RenderResult(true, outputPath, durationSeconds, fileSizeBytes, renderDurationMs, null);
    }

    public static RenderResult failure(String errorMessage) {
        return failure(errorMessage, 0L);
    }

    public static RenderResult failure(String errorMessage, long renderDurationMs) {
        return new RenderResult(false, null, 0.0, 0L, renderDurationMs, errorMessage);
    }
}
