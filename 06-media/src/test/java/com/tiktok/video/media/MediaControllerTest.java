package com.tiktok.video.media.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {MediaController.class, HealthController.class})
class MediaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void health_shouldReturnUp() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.port").value("38106"));
    }

    @Test
    void getIllustration_shouldResolveKnownKeyword() throws Exception {
        mockMvc.perform(get("/api/v1/media/illustration/sibling"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keyword").value("sibling"))
                .andExpect(jsonPath("$.filePath").isNotEmpty());
    }
}
