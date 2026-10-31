package com.tiktok.video.subtitle.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record GenerateSubtitleResponse(
    @JsonProperty("assFilePath") String assFilePath,
    @JsonProperty("success") boolean success,
    @JsonProperty("message") String message
) {
    public static GenerateSubtitleResponse ok(String assFilePath) {
        return new GenerateSubtitleResponse(assFilePath, true, "OK");
    }

    public static GenerateSubtitleResponse error(String message) {
        return new GenerateSubtitleResponse(null, false, message);
    }
}
