package com.tiktok.video.script.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tiktok.video.core.contract.ScriptExtractor;
import com.tiktok.video.core.model.Scene;
import com.tiktok.video.core.model.TikTokScript;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class ScriptControllerTest {

    private MockMvc mockMvc;

    @Mock
    private ScriptExtractor scriptExtractor;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        ScriptController controller = new ScriptController(scriptExtractor, objectMapper);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    @DisplayName("GET /health returns UP status")
    void testHealthEndpoint() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.service").value("script-service"));
    }

    @Test
    @DisplayName("GET /api/v1/script/health returns UP status")
    void testScriptHealthEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/script/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @DisplayName("POST /api/v1/script/extract with text plain body returns TikTokScript JSON")
    void testExtractWithPlainBody() throws Exception {
        TikTokScript mockScript = new TikTokScript("Family", "Band 7+", List.of(
                Scene.hook("scene_00", 0, "Welcome", "Welcome")
        ));
        when(scriptExtractor.extract(anyString())).thenReturn(mockScript);

        mockMvc.perform(post("/api/v1/script/extract")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("hello and welcome to ielts lesson on family"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.topic").value("Family"))
                .andExpect(jsonPath("$.targetBand").value("Band 7+"))
                .andExpect(jsonPath("$.scenes[0].spokenText").value("Welcome"));
    }

    @Test
    @DisplayName("POST /api/v1/script/extract with JSON wrapper returns TikTokScript")
    void testExtractWithJsonWrapper() throws Exception {
        TikTokScript mockScript = new TikTokScript("Family", "Band 7+", List.of(
                Scene.hook("scene_00", 0, "Welcome", "Welcome")
        ));
        when(scriptExtractor.extract(anyString())).thenReturn(mockScript);

        String jsonPayload = "{\"transcript\": \"hello and welcome to ielts lesson on family\"}";

        mockMvc.perform(post("/api/v1/script/extract")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.topic").value("Family"));
    }
}
