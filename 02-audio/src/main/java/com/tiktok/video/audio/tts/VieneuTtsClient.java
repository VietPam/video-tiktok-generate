package com.tiktok.video.audio.tts;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tiktok.video.audio.util.WavAudioUtil;
import com.tiktok.video.core.contract.TtsClient;
import com.tiktok.video.core.contract.TtsRequest;
import com.tiktok.video.core.exception.TtsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * OpenAI-compatible HTTP client for the local VieNeu-TTS engine container.
 * Synthesizes Vietnamese speech to 48kHz mono 16-bit WAV audio and automatically
 * patches the streaming 0xFFFFFFFF header before returning.
 */
public class VieneuTtsClient implements TtsClient {

    private static final Logger log = LoggerFactory.getLogger(VieneuTtsClient.class);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String speechEndpoint;
    private final String healthEndpoint;
    private final Duration timeout;

    public VieneuTtsClient() {
        this("http://localhost:8000");
    }

    public VieneuTtsClient(String baseUrl) {
        this(baseUrl, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build(),
             new ObjectMapper(),
             Duration.ofSeconds(60));
    }

    public VieneuTtsClient(String baseUrl, HttpClient httpClient, ObjectMapper objectMapper, Duration timeout) {
        Objects.requireNonNull(baseUrl, "baseUrl must not be null");
        Objects.requireNonNull(httpClient, "httpClient must not be null");
        Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        Objects.requireNonNull(timeout, "timeout must not be null");

        String normalized = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.speechEndpoint = normalized + "/v1/audio/speech";
        this.healthEndpoint = normalized + "/health";
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.timeout = timeout;
    }

    @Override
    public byte[] synthesize(TtsRequest request) throws TtsException {
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }
        if (request.text() == null || request.text().isBlank()) {
            throw new IllegalArgumentException("text must not be blank");
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", request.model() != null ? request.model() : TtsRequest.DEFAULT_MODEL);
        payload.put("input", request.text());
        payload.put("voice", request.voice() != null ? request.voice() : TtsRequest.DEFAULT_VOICE);
        payload.put("response_format", request.responseFormat() != null ? request.responseFormat() : TtsRequest.DEFAULT_FORMAT);
        payload.put("sample_rate", 48000);
        if (request.speed() != null) {
            payload.put("speed", request.speed());
        }

        String jsonBody;
        try {
            jsonBody = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new TtsException("Failed to serialize TTS request payload to JSON", e);
        }

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(speechEndpoint))
                .header("Content-Type", "application/json")
                .timeout(timeout)
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                .build();

        HttpResponse<byte[]> response;
        try {
            response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofByteArray());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TtsException("TTS synthesis request was interrupted", e);
        } catch (IOException e) {
            throw new TtsException("Network communication failure with TTS endpoint: " + speechEndpoint, e);
        }

        int statusCode = response.statusCode();
        byte[] body = response.body();

        if (statusCode != 200) {
            String responseBodyStr = body != null ? new String(body, StandardCharsets.UTF_8) : "";
            String parsedMessage = extractErrorMessage(responseBodyStr);
            String message = String.format("TTS request failed with HTTP %d: %s", statusCode, parsedMessage);
            log.error("TTS synthesis error [HTTP {}]: {}", statusCode, responseBodyStr);
            throw new TtsException(message, statusCode, responseBodyStr);
        }

        if (body == null || body.length < WavAudioUtil.HEADER_SIZE) {
            throw new TtsException("TTS server returned invalid audio payload: payload length is "
                    + (body == null ? 0 : body.length) + " bytes (expected >= 44 bytes)", statusCode, "");
        }

        try {
            return WavAudioUtil.patchWavHeaderInPlace(body);
        } catch (Exception e) {
            throw new TtsException("Failed to patch WAV header for synthesized audio: " + e.getMessage(), e);
        }
    }

    private String extractErrorMessage(String responseBodyStr) {
        if (responseBodyStr == null || responseBodyStr.isBlank()) {
            return "Empty response body";
        }
        try {
            JsonNode root = objectMapper.readTree(responseBodyStr);
            if (root.has("error") && root.get("error").has("message")) {
                return root.get("error").get("message").asText();
            }
            if (root.has("detail")) {
                return root.get("detail").asText();
            }
        } catch (Exception ignored) {
            // Fallback to raw string snippet
        }
        return responseBodyStr.length() > 200 ? responseBodyStr.substring(0, 200) + "..." : responseBodyStr;
    }

    /**
     * Checks if the TTS service is reachable and healthy.
     */
    public boolean isAvailable() {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(healthEndpoint))
                    .timeout(Duration.ofSeconds(3))
                    .GET()
                    .build();
            HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return res.statusCode() == 200 && res.body().contains("\"status\":\"ok\"");
        } catch (Exception e) {
            log.warn("TTS engine is not available at {}: {}", healthEndpoint, e.getMessage());
            return false;
        }
    }
}
