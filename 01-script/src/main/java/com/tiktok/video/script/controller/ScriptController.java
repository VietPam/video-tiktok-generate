package com.tiktok.video.script.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tiktok.video.core.contract.ScriptExtractor;
import com.tiktok.video.core.exception.ScriptExtractionException;
import com.tiktok.video.core.model.TikTokScript;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Objects;

@RestController
public class ScriptController {

    private static final Logger log = LoggerFactory.getLogger(ScriptController.class);

    private final ScriptExtractor scriptExtractor;
    private final ObjectMapper objectMapper;

    public ScriptController(ScriptExtractor scriptExtractor, ObjectMapper objectMapper) {
        this.scriptExtractor = Objects.requireNonNull(scriptExtractor, "scriptExtractor must not be null");
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    @GetMapping({"/health", "/api/v1/script/health"})
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP", "service", "script-service"));
    }

    @PostMapping(
        value = "/api/v1/script/extract",
        consumes = {MediaType.APPLICATION_JSON_VALUE, MediaType.TEXT_PLAIN_VALUE, MediaType.ALL_VALUE},
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<TikTokScript> extract(@RequestBody(required = false) String rawBody) throws ScriptExtractionException {
        if (rawBody == null || rawBody.isBlank()) {
            throw new IllegalArgumentException("Request body cannot be null or blank");
        }

        String transcript = rawBody.trim();
        // Support JSON wrapped payloads: {"transcript": "..."} or {"text": "..."} or {"content": "..."}
        if (transcript.startsWith("{")) {
            try {
                JsonNode root = objectMapper.readTree(transcript);
                if (root.hasNonNull("transcript")) {
                    transcript = root.get("transcript").asText();
                } else if (root.hasNonNull("text")) {
                    transcript = root.get("text").asText();
                } else if (root.hasNonNull("content")) {
                    transcript = root.get("content").asText();
                }
            } catch (Exception ignored) {
                // Not a JSON object or parsing failed; proceed with rawBody
            }
        }

        log.info("Received extract request, length: {} chars", transcript.length());
        TikTokScript result = scriptExtractor.extract(transcript);
        return ResponseEntity.ok(result);
    }
}
