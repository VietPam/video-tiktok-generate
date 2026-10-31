package com.tiktok.video.render.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tiktok.video.core.contract.FallbackAssetGenerator;
import com.tiktok.video.core.contract.VideoComposer;
import com.tiktok.video.core.model.AudioTimelineManifest;
import com.tiktok.video.core.model.RenderBlueprint;
import com.tiktok.video.core.model.RenderResult;
import com.tiktok.video.render.dto.RenderVideoRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Paths;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {RenderController.class, HealthController.class})
class RenderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private VideoComposer videoComposer;

    @MockBean
    private FallbackAssetGenerator fallbackAssetGenerator;

    @Test
    void healthCheck_shouldReturnUp() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.port").value("38105"));
    }

    @Test
    void compose_shouldReturnResult() throws Exception {
        AudioTimelineManifest manifest = new AudioTimelineManifest(Paths.get("/tmp/master.wav"), 10.0, List.of(), 48000);
        RenderBlueprint bp = RenderBlueprint.builder()
                .audioTimeline(manifest)
                .outputVideoPath(Paths.get("/tmp/out.mp4"))
                .build();

        RenderResult mockResult = RenderResult.success(Paths.get("/tmp/out.mp4"), 10.0, 1024L, 500L);
        when(videoComposer.composeVideo(any())).thenReturn(mockResult);

        RenderVideoRequest req = new RenderVideoRequest(bp);

        mockMvc.perform(post("/api/v1/render/compose")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.durationSeconds").value(10.0))
                .andExpect(jsonPath("$.fileSizeBytes").value(1024));
    }
}
