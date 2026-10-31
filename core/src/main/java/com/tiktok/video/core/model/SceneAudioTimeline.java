package com.tiktok.video.core.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Represents the temporal positioning of audio for a specific scene segment.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record SceneAudioTimeline(
    @JsonProperty("sceneId") String sceneId,
    @JsonProperty("audioFilePath") @JsonSerialize(using = ToStringSerializer.class) Path audioFilePath,
    @JsonProperty("startSeconds") double startSeconds,
    @JsonProperty("endSeconds") double endSeconds,
    @JsonProperty("durationSeconds") double durationSeconds
) {
    public SceneAudioTimeline {
        Objects.requireNonNull(sceneId, "sceneId cannot be null");
        if (sceneId.isBlank()) {
            throw new IllegalArgumentException("sceneId cannot be blank");
        }
        sceneId = sceneId.trim();
        if (startSeconds < 0.0) {
            throw new IllegalArgumentException("startSeconds cannot be negative: " + startSeconds);
        }
        if (endSeconds < startSeconds) {
            throw new IllegalArgumentException("endSeconds (" + endSeconds + ") cannot be less than startSeconds (" + startSeconds + ")");
        }
        if (durationSeconds < 0.0) {
            throw new IllegalArgumentException("durationSeconds cannot be negative: " + durationSeconds);
        }
    }

    @JsonIgnore
    public boolean containsTime(double second) {
        return second >= startSeconds && second <= endSeconds;
    }

    public static SceneAudioTimeline of(String sceneId, Path audioFilePath, double startSeconds, double durationSeconds) {
        return new SceneAudioTimeline(sceneId, audioFilePath, startSeconds, startSeconds + durationSeconds, durationSeconds);
    }
}
