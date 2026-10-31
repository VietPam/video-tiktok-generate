package com.tiktok.video.core.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

/**
 * Blueprint descriptor consolidating all assets and parameters required by FFmpeg for final video composition.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record RenderBlueprint(
    @JsonProperty("audioTimeline") AudioTimelineManifest audioTimeline,
    @JsonProperty("subtitlePath") @JsonSerialize(using = ToStringSerializer.class) Path subtitlePath,
    @JsonProperty("visualCardPaths") @JsonSerialize(contentUsing = ToStringSerializer.class) Map<String, Path> visualCardPaths,
    @JsonProperty("backgroundVideoPath") @JsonSerialize(using = ToStringSerializer.class) Path backgroundVideoPath,
    @JsonProperty("bgmAudioPath") @JsonSerialize(using = ToStringSerializer.class) Path bgmAudioPath,
    @JsonProperty("outputVideoPath") @JsonSerialize(using = ToStringSerializer.class) Path outputVideoPath,
    @JsonProperty("width") int width,
    @JsonProperty("height") int height,
    @JsonProperty("fps") double fps
) {
    public static final int DEFAULT_WIDTH = 1080;
    public static final int DEFAULT_HEIGHT = 1920;
    public static final double DEFAULT_FPS = 30.0;

    public RenderBlueprint {
        Objects.requireNonNull(audioTimeline, "audioTimeline cannot be null");
        Objects.requireNonNull(outputVideoPath, "outputVideoPath cannot be null");
        visualCardPaths = (visualCardPaths == null) ? Map.of() : Map.copyOf(visualCardPaths);
        if (width <= 0) width = DEFAULT_WIDTH;
        if (height <= 0) height = DEFAULT_HEIGHT;
        if (fps <= 0.0) fps = DEFAULT_FPS;
    }

    @JsonIgnore
    public double totalDurationSeconds() {
        return audioTimeline.totalDurationSeconds();
    }

    @JsonIgnore
    public boolean hasBgm() {
        return bgmAudioPath != null;
    }

    @JsonIgnore
    public boolean hasSubtitles() {
        return subtitlePath != null;
    }

    @JsonIgnore
    public Path getCardForScene(String sceneId) {
        if (sceneId == null) return null;
        return visualCardPaths.get(sceneId);
    }

    @JsonIgnore
    public String resolution() {
        return width + "x" + height;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private AudioTimelineManifest audioTimeline;
        private Path subtitlePath;
        private Map<String, Path> visualCardPaths = Map.of();
        private Path backgroundVideoPath;
        private Path bgmAudioPath;
        private Path outputVideoPath;
        private int width = DEFAULT_WIDTH;
        private int height = DEFAULT_HEIGHT;
        private double fps = DEFAULT_FPS;

        public Builder audioTimeline(AudioTimelineManifest audioTimeline) {
            this.audioTimeline = audioTimeline;
            return this;
        }

        public Builder subtitlePath(Path subtitlePath) {
            this.subtitlePath = subtitlePath;
            return this;
        }

        public Builder visualCardPaths(Map<String, Path> visualCardPaths) {
            this.visualCardPaths = visualCardPaths;
            return this;
        }

        public Builder backgroundVideoPath(Path backgroundVideoPath) {
            this.backgroundVideoPath = backgroundVideoPath;
            return this;
        }

        public Builder bgmAudioPath(Path bgmAudioPath) {
            this.bgmAudioPath = bgmAudioPath;
            return this;
        }

        public Builder outputVideoPath(Path outputVideoPath) {
            this.outputVideoPath = outputVideoPath;
            return this;
        }

        public Builder width(int width) {
            this.width = width;
            return this;
        }

        public Builder height(int height) {
            this.height = height;
            return this;
        }

        public Builder fps(double fps) {
            this.fps = fps;
            return this;
        }

        public RenderBlueprint build() {
            return new RenderBlueprint(
                audioTimeline,
                subtitlePath,
                visualCardPaths,
                backgroundVideoPath,
                bgmAudioPath,
                outputVideoPath,
                width,
                height,
                fps
            );
        }
    }
}
