package com.tiktok.video.script.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class OpenAiLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiLlmClient.class);
    private static final Pattern MARKDOWN_CODE_BLOCK = Pattern.compile("^```(?:json)?\\s*([\\s\\S]*?)\\s*```$", Pattern.MULTILINE);

    private final URI endpointUri;
    private final String apiKey;
    private final String model;
    private final Duration timeout;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public OpenAiLlmClient(URI endpointUri, String apiKey, String model, Duration timeout, HttpClient httpClient, ObjectMapper objectMapper) {
        this.endpointUri = Objects.requireNonNull(endpointUri, "endpointUri cannot be null");
        this.apiKey = apiKey;
        this.model = (model == null || model.isBlank()) ? "gpt-4o-mini" : model.trim();
        this.timeout = timeout == null ? Duration.ofSeconds(30) : timeout;
        this.httpClient = httpClient == null ? HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build() : httpClient;
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
    }

    public OpenAiLlmClient(URI endpointUri, String apiKey, String model) {
        this(endpointUri, apiKey, model, Duration.ofSeconds(30), null, null);
    }

    @Override
    public String generate(String systemPrompt, String userPrompt) throws LlmException {
        if (userPrompt == null || userPrompt.isBlank()) {
            throw new IllegalArgumentException("userPrompt cannot be null or blank");
        }

        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("model", model);
            payload.put("temperature", 0.2);
            payload.put("response_format", Map.of("type", "json_object"));
            payload.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt == null ? "" : systemPrompt),
                Map.of("role", "user", "content", userPrompt)
            ));

            String requestBodyJson = objectMapper.writeValueAsString(payload);

            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                .uri(endpointUri)
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBodyJson, StandardCharsets.UTF_8));

            if (apiKey != null && !apiKey.isBlank()) {
                reqBuilder.header("Authorization", "Bearer " + apiKey.trim());
            }

            HttpResponse<String> response = httpClient.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (response.statusCode() >= 400) {
                throw new LlmException("OpenAI API error status " + response.statusCode() + ": " + response.body());
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode messageContent = root.path("choices").path(0).path("message").path("content");
            if (messageContent.isMissingNode() || messageContent.asText().isBlank()) {
                throw new LlmException("LLM returned response without choices[0].message.content: " + response.body());
            }

            return stripMarkdownCodeBlock(messageContent.asText());
        } catch (HttpTimeoutException e) {
            throw new LlmException("LLM request timed out after " + timeout.toSeconds() + "s", e);
        } catch (IOException e) {
            throw new LlmException("I/O error communicating with LLM endpoint: " + endpointUri, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmException("LLM request was interrupted", e);
        }
    }

    public static String stripMarkdownCodeBlock(String raw) {
        if (raw == null) return null;
        String trimmed = raw.trim();
        Matcher matcher = MARKDOWN_CODE_BLOCK.matcher(trimmed);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            if (firstNewline != -1) {
                trimmed = trimmed.substring(firstNewline + 1);
            }
            if (trimmed.endsWith("```")) {
                trimmed = trimmed.substring(0, trimmed.length() - 3);
            }
        }
        return trimmed.trim();
    }
}
