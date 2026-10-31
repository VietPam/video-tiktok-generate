package com.tiktok.video.core.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Manifest containing the stitched master audio file and the individual scene audio timelines.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record AudioTimelineManifest(
    @JsonProperty("masterAudioPath") @JsonSerialize(using = ToStringSerializer.class) Path masterAudioPath,
    @JsonProperty("totalDurationSeconds") double totalDurationSeconds,
    @JsonProperty("sceneTimelines") List<SceneAudioTimeline> sceneTimelines,
    @JsonProperty("sampleRate") int sampleRate
) {
    public AudioTimelineManifest {
        sceneTimelines = (sceneTimelines == null) ? List.of() : List.copyOf(sceneTimelines);
        if (totalDurationSeconds < 0.0) {
            throw new IllegalArgumentException("totalDurationSeconds cannot be negative: " + totalDurationSeconds);
        }
        if (sampleRate <= 0) {
            sampleRate = 48000;
        }
    }

    @JsonIgnore
    public Optional<SceneAudioTimeline> getTimeline(String sceneId) {
        if (sceneId == null) {
            return Optional.empty();
        }
        return sceneTimelines.stream()
            .filter(t -> sceneId.equals(t.sceneId()))
            .findFirst();
    }

    @JsonIgnore
    public int sceneCount() {
        return sceneTimelines.size();
    }

    @JsonIgnore
    public boolean isEmpty() {
        return sceneTimelines.isEmpty();
    }
}
