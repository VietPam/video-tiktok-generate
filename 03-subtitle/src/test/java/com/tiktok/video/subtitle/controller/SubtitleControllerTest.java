package com.tiktok.video.subtitle.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tiktok.video.core.contract.SubtitleGenerator;
import com.tiktok.video.core.model.AudioTimelineManifest;
import com.tiktok.video.core.model.Scene;
import com.tiktok.video.core.model.TikTokScript;
import com.tiktok.video.subtitle.dto.GenerateSubtitleRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {SubtitleController.class, HealthController.class})
class SubtitleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private SubtitleGenerator subtitleGenerator;

    @Test
    void healthCheck_shouldReturnUp() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.port").value("38103"));
    }

    @Test
    void generate_shouldReturnOk() throws Exception {
        Scene scene = Scene.hook("s1", 0, "Test speech", "Test sub");
        TikTokScript script = new TikTokScript("Family", "Band 7+", List.of(scene));
        AudioTimelineManifest manifest = new AudioTimelineManifest(Paths.get("/tmp/master.wav"), 5.0, List.of(), 48000);

        when(subtitleGenerator.generateAss(any(), any(), any()))
                .thenReturn(Paths.get("/tmp/output.ass"));

        GenerateSubtitleRequest request = new GenerateSubtitleRequest(script, manifest, "/tmp");

        mockMvc.perform(post("/api/v1/subtitle/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.assFilePath").value("/tmp/output.ass"));
    }
}
