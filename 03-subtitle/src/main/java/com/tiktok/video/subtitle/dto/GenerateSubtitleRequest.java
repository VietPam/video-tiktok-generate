package com.tiktok.video.subtitle.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.tiktok.video.core.model.AudioTimelineManifest;
import com.tiktok.video.core.model.TikTokScript;

public record GenerateSubtitleRequest(
    @JsonProperty("script") TikTokScript script,
    @JsonProperty("timeline") AudioTimelineManifest timeline,
    @JsonProperty("outputDir") String outputDir
) {}
