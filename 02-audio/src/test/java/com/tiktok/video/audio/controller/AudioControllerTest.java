package com.tiktok.video.audio.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tiktok.video.core.contract.AudioTimelineService;
import com.tiktok.video.core.contract.TtsClient;
import com.tiktok.video.core.contract.TtsRequest;
import com.tiktok.video.core.model.AudioTimelineManifest;
import com.tiktok.video.core.model.Scene;
import com.tiktok.video.core.model.SceneAudioTimeline;
import com.tiktok.video.core.model.TikTokScript;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.file.Path;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class AudioControllerTest {

    private MockMvc mockMvc;

    @Mock
    private TtsClient ttsClient;

    @Mock
    private AudioTimelineService audioTimelineService;

    @TempDir
    Path tempDir;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        AudioController controller = new AudioController(ttsClient, audioTimelineService, tempDir.toString());
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    @DisplayName("GET /health returns UP status")
    void testHealthEndpoint() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.service").value("audio-service"));
    }

    @Test
    @DisplayName("GET /api/v1/audio/health returns UP status")
    void testAudioHealthEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/audio/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @DisplayName("POST /api/v1/audio/synthesize returns audio/wav bytes")
    void testSynthesizeEndpoint() throws Exception {
        byte[] mockWav = new byte[]{82, 73, 70, 70, 36, 0, 0, 0, 87, 65, 86, 69}; // RIFF...WAVE
        when(ttsClient.synthesize(any(TtsRequest.class))).thenReturn(mockWav);

        String payload = objectMapper.writeValueAsString(TtsRequest.of("Hello world"));

        mockMvc.perform(post("/api/v1/audio/synthesize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "audio/wav"))
                .andExpect(content().bytes(mockWav));
    }

    @Test
    @DisplayName("POST /api/v1/audio/timeline returns AudioTimelineManifest JSON")
    void testTimelineEndpoint() throws Exception {
        TikTokScript script = new TikTokScript("Family", "Band 7+", List.of(
                Scene.hook("scene_00", 0, "Welcome", "Welcome")
        ));

        AudioTimelineManifest manifest = new AudioTimelineManifest(
                Path.of("master_voiceover.wav"),
                3.0,
                List.of(SceneAudioTimeline.of("scene_00", Path.of("scene_00.wav"), 0.0, 3.0)),
                48000
        );
        when(audioTimelineService.generateTimeline(any(TikTokScript.class), any(Path.class))).thenReturn(manifest);

        String payload = objectMapper.writeValueAsString(script);

        mockMvc.perform(post("/api/v1/audio/timeline")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalDurationSeconds").value(3.0))
                .andExpect(jsonPath("$.masterAudioPath").value("master_voiceover.wav"))
                .andExpect(jsonPath("$.sceneTimelines[0].sceneId").value("scene_00"));
    }
}
