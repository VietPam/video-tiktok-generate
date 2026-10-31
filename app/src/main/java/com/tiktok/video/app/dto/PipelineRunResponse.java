package com.tiktok.video.app.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PipelineRunResponse(
    @JsonProperty("success") boolean success,
    @JsonProperty("finalVideoPath") String finalVideoPath,
    @JsonProperty("durationSeconds") double durationSeconds,
    @JsonProperty("fileSizeBytes") long fileSizeBytes,
    @JsonProperty("totalPipelineDurationMs") long totalPipelineDurationMs,
    @JsonProperty("message") String message
) {
    public static PipelineRunResponse ok(String videoPath, double duration, long sizeBytes, long durationMs) {
        return new PipelineRunResponse(true, videoPath, duration, sizeBytes, durationMs, "Video generated successfully");
    }

    public static PipelineRunResponse fail(String message, long durationMs) {
        return new PipelineRunResponse(false, null, 0.0, 0L, durationMs, message);
    }
}
