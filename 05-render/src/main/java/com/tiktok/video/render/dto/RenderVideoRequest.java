package com.tiktok.video.render.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.tiktok.video.core.model.RenderBlueprint;

public record RenderVideoRequest(
    @JsonProperty("blueprint") RenderBlueprint blueprint
) {}
