package com.tiktok.video.app.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.tiktok.video.core.model.TikTokScript;

public record PipelineRunRequest(
    @JsonProperty("transcript") String transcript,
    @JsonProperty("topic") String topic,
    @JsonProperty("targetBand") String targetBand,
    @JsonProperty("outputFileName") String outputFileName,
    @JsonProperty("script") TikTokScript script
) {
    public PipelineRunRequest(String transcript, String topic, String targetBand, String outputFileName) {
        this(transcript, topic, targetBand, outputFileName, null);
    }
}
