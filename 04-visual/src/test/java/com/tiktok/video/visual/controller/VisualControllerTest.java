package com.tiktok.video.visual.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tiktok.video.core.contract.VisualCardRenderer;
import com.tiktok.video.core.model.Scene;
import com.tiktok.video.core.model.TikTokScript;
import com.tiktok.video.core.model.VocabularyItem;
import com.tiktok.video.visual.dto.RenderCardsRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {VisualController.class, HealthController.class})
class VisualControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private VisualCardRenderer visualCardRenderer;

    @Test
    void healthCheck_shouldReturnUp() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.port").value("38104"));
    }

    @Test
    void renderCards_shouldReturnOk() throws Exception {
        VocabularyItem item = new VocabularyItem("Sibling", "/ˈsɪb.lɪŋ/", "noun", "Anh em", "sibling rivalry", "ganh đua", "ex");
        Scene s1 = Scene.vocabulary("s1", 0, "Spoken", "Sub", item);
        TikTokScript script = new TikTokScript("Family", "Band 7+", List.of(s1));

        when(visualCardRenderer.renderCards(any(), any()))
                .thenReturn(Map.of("s1", Paths.get("/tmp/card_s1.png")));

        RenderCardsRequest req = new RenderCardsRequest(script, "/tmp");

        mockMvc.perform(post("/api/v1/visual/render-cards")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.cardCount").value(1))
                .andExpect(jsonPath("$.cardPaths.s1").value("/tmp/card_s1.png"));
    }
}
