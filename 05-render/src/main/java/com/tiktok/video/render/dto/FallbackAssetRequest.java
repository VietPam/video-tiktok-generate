package com.tiktok.video.render.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record FallbackAssetRequest(
    @JsonProperty("durationSeconds") double durationSeconds,
    @JsonProperty("outputPath") String outputPath
) {}
