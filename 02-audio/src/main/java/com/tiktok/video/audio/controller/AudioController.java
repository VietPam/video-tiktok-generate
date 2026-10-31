package com.tiktok.video.audio.controller;

import com.tiktok.video.core.contract.AudioTimelineService;
import com.tiktok.video.core.contract.TtsClient;
import com.tiktok.video.core.contract.TtsRequest;
import com.tiktok.video.core.exception.AudioProcessingException;
import com.tiktok.video.core.exception.TtsException;
import com.tiktok.video.core.model.AudioTimelineManifest;
import com.tiktok.video.core.model.TikTokScript;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Objects;

@RestController
public class AudioController {

    private static final Logger log = LoggerFactory.getLogger(AudioController.class);

    private final TtsClient ttsClient;
    private final AudioTimelineService audioTimelineService;
    private final Path defaultOutputDir;

    public AudioController(
            TtsClient ttsClient,
            AudioTimelineService audioTimelineService,
            @Value("${tiktok.audio.output-dir:/tmp/audio}") String defaultOutputDir) {
        this.ttsClient = Objects.requireNonNull(ttsClient, "ttsClient must not be null");
        this.audioTimelineService = Objects.requireNonNull(audioTimelineService, "audioTimelineService must not be null");
        this.defaultOutputDir = Paths.get(defaultOutputDir);
    }

    @GetMapping({"/health", "/api/v1/audio/health"})
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "audio-service"
        ));
    }

    @PostMapping(
        value = "/api/v1/audio/synthesize",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = "audio/wav"
    )
    public ResponseEntity<byte[]> synthesize(@RequestBody TtsRequest request) throws TtsException {
        if (request == null) {
            throw new IllegalArgumentException("TtsRequest cannot be null");
        }
        log.info("Received synthesize request for text length: {}", request.text().length());
        byte[] wavBytes = ttsClient.synthesize(request);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "audio/wav")
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"synthesized.wav\"")
                .body(wavBytes);
    }

    @PostMapping(
        value = "/api/v1/audio/timeline",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<AudioTimelineManifest> generateTimeline(
            @RequestBody TikTokScript script,
            @RequestParam(value = "outputDir", required = false) String outputDirParam) throws AudioProcessingException {
        if (script == null) {
            throw new IllegalArgumentException("TikTokScript cannot be null");
        }

        Path targetDir;
        if (outputDirParam != null && !outputDirParam.isBlank()) {
            targetDir = Paths.get(outputDirParam);
        } else {
            targetDir = defaultOutputDir;
        }

        try {
            Files.createDirectories(targetDir);
        } catch (IOException e) {
            throw new AudioProcessingException("Failed to create audio output directory: " + targetDir, e);
        }

        log.info("Generating audio timeline for script with {} scenes into {}", script.totalScenes(), targetDir);
        AudioTimelineManifest manifest = audioTimelineService.generateTimeline(script, targetDir);
        return ResponseEntity.ok(manifest);
    }
}
