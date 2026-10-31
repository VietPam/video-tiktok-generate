package com.tiktok.video.app.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tiktok.video.app.dto.PipelineRunRequest;
import com.tiktok.video.app.dto.PipelineRunResponse;
import com.tiktok.video.app.service.PipelineOrchestratorService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {PipelineController.class, HealthController.class})
class PipelineControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PipelineOrchestratorService orchestratorService;

    @Test
    void healthCheck_shouldReturnUp() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.port").value("38100"));
    }

    @Test
    void run_shouldReturnSuccess() throws Exception {
        PipelineRunResponse mockResp = PipelineRunResponse.ok("/tmp/video.mp4", 15.0, 10240L, 2000L);
        when(orchestratorService.executePipeline(any())).thenReturn(mockResp);

        PipelineRunRequest req = new PipelineRunRequest("Sample transcript", "Family", "Band 7+", "out.mp4");

        mockMvc.perform(post("/api/v1/pipeline/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.finalVideoPath").value("/tmp/video.mp4"));
    }
}
