package com.tiktok.video.visual.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.tiktok.video.core.model.TikTokScript;

public record RenderCardsRequest(
    @JsonProperty("script") TikTokScript script,
    @JsonProperty("outputDir") String outputDir
) {}
