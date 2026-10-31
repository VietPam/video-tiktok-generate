package com.tiktok.video.app.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tiktok.video.app.dto.PipelineRunRequest;
import com.tiktok.video.app.dto.PipelineRunResponse;
import com.tiktok.video.core.model.AudioTimelineManifest;
import com.tiktok.video.core.model.RenderBlueprint;
import com.tiktok.video.core.model.RenderResult;
import com.tiktok.video.core.model.TikTokScript;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Service
public class PipelineOrchestratorService {

    private static final Logger log = LoggerFactory.getLogger(PipelineOrchestratorService.class);

    private final String scriptUrl;
    private final String audioUrl;
    private final String subtitleUrl;
    private final String visualUrl;
    private final String renderUrl;
    private final String outputDir;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public PipelineOrchestratorService(
            @Value("${tiktok.pipeline.script-url:http://localhost:38101}") String scriptUrl,
            @Value("${tiktok.pipeline.audio-url:http://localhost:38102}") String audioUrl,
            @Value("${tiktok.pipeline.subtitle-url:http://localhost:38103}") String subtitleUrl,
            @Value("${tiktok.pipeline.visual-url:http://localhost:38104}") String visualUrl,
            @Value("${tiktok.pipeline.render-url:http://localhost:38105}") String renderUrl,
            @Value("${tiktok.pipeline.output-dir:/tmp/tiktok-pipeline}") String outputDir,
            ObjectMapper objectMapper) {
        this.scriptUrl = scriptUrl;
        this.audioUrl = audioUrl;
        this.subtitleUrl = subtitleUrl;
        this.visualUrl = visualUrl;
        this.renderUrl = renderUrl;
        this.outputDir = outputDir;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public PipelineRunResponse executePipeline(PipelineRunRequest request) {
        long startTime = System.currentTimeMillis();
        log.info("Starting end-to-end TikTok video generation pipeline...");

        try {
            Path workDir = Paths.get(outputDir);
            Files.createDirectories(workDir);

            // Step 1: Call 01-script service to extract script (or use pre-structured script)
            TikTokScript script;
            if (request.script() != null) {
                log.info("Using provided pre-structured TikTokScript with {} scenes", request.script().totalScenes());
                script = request.script();
            } else {
                log.info("Step 1: Calling Script service at {}", scriptUrl);
                Map<String, String> scriptReq = Map.of(
                        "transcript", request.transcript(),
                        "topic", request.topic() != null ? request.topic() : "Family",
                        "targetBand", request.targetBand() != null ? request.targetBand() : "Band 7+"
                );
                String scriptJson = sendPost(scriptUrl + "/api/v1/script/extract", scriptReq);
                script = objectMapper.readValue(scriptJson, TikTokScript.class);
                log.info("Extracted script with {} scenes", script.totalScenes());
            }

            // Step 2: Call 02-audio service to generate audio timeline
            log.info("Step 2: Calling Audio service at {}", audioUrl);
            String audioOutDir = workDir.resolve("audio").toAbsolutePath().toString();
            String audioJson = sendPost(audioUrl + "/api/v1/audio/timeline?outputDir=" + audioOutDir, script);
            AudioTimelineManifest timeline = objectMapper.readValue(audioJson, AudioTimelineManifest.class);
            log.info("Generated audio timeline: total duration = {}s", timeline.totalDurationSeconds());

            // Step 3: Call 03-subtitle service to generate .ass subtitle
            log.info("Step 3: Calling Subtitle service at {}", subtitleUrl);
            Map<String, Object> subReq = Map.of(
                    "script", script,
                    "timeline", timeline,
                    "outputDir", workDir.resolve("subtitles").toString()
            );
            String subJson = sendPost(subtitleUrl + "/api/v1/subtitle/generate", subReq);
            Map<String, Object> subResp = objectMapper.readValue(subJson, new TypeReference<>() {});
            String assFilePath = (String) subResp.get("assFilePath");
            log.info("Generated subtitles at: {}", assFilePath);

            // Step 4: Call 04-visual service to render cards
            log.info("Step 4: Calling Visual service at {}", visualUrl);
            Map<String, Object> visualReq = Map.of(
                    "script", script,
                    "outputDir", workDir.resolve("cards").toString()
            );
            String visualJson = sendPost(visualUrl + "/api/v1/visual/render-cards", visualReq);
            Map<String, Object> visualResp = objectMapper.readValue(visualJson, new TypeReference<>() {});
            @SuppressWarnings("unchecked")
            Map<String, String> cardPathsStr = (Map<String, String>) visualResp.get("cardPaths");
            Map<String, Path> cardPaths = new HashMap<>();
            if (cardPathsStr != null) {
                cardPathsStr.forEach((k, v) -> cardPaths.put(k, Paths.get(v)));
            }
            log.info("Rendered {} visual cards", cardPaths.size());

            // Step 5: Fallback background video & BGM from 05-render service if needed
            log.info("Step 5: Preparing background video and BGM assets...");
            Path bgVideo = workDir.resolve("background_" + System.currentTimeMillis() + ".mp4");
            sendPost(renderUrl + "/api/v1/render/fallback/background", Map.of(
                    "durationSeconds", timeline.totalDurationSeconds() + 1.0,
                    "outputPath", bgVideo.toAbsolutePath().toString()
            ));

            Path bgmAudio = workDir.resolve("bgm.aac");
            if (!Files.exists(bgmAudio)) {
                sendPost(renderUrl + "/api/v1/render/fallback/bgm", Map.of(
                        "durationSeconds", timeline.totalDurationSeconds() + 1.0,
                        "outputPath", bgmAudio.toAbsolutePath().toString()
                ));
            }

            // Step 6: Call 05-render service to compose final MP4 video
            log.info("Step 6: Calling Render service at {}", renderUrl);
            String outName = request.outputFileName() != null ? request.outputFileName() : "tiktok_ielts_lesson.mp4";
            Path finalOutput = workDir.resolve(outName);

            RenderBlueprint blueprint = RenderBlueprint.builder()
                    .audioTimeline(timeline)
                    .backgroundVideoPath(bgVideo)
                    .bgmAudioPath(bgmAudio)
                    .subtitlePath(assFilePath != null ? Paths.get(assFilePath) : null)
                    .visualCardPaths(cardPaths)
                    .outputVideoPath(finalOutput)
                    .width(1080)
                    .height(1920)
                    .fps(30.0)
                    .build();

            Map<String, Object> renderReq = Map.of("blueprint", blueprint);
            String renderJson = sendPost(renderUrl + "/api/v1/render/compose", renderReq);
            RenderResult renderResult = objectMapper.readValue(renderJson, RenderResult.class);

            long totalDurationMs = System.currentTimeMillis() - startTime;
            if (renderResult.success()) {
                log.info("Pipeline succeeded! Video: {} (duration: {}s, size: {} bytes, total pipeline: {} ms)",
                        renderResult.outputPath(), renderResult.durationSeconds(), renderResult.fileSizeBytes(), totalDurationMs);
                return PipelineRunResponse.ok(
                        renderResult.outputPath().toString(),
                        renderResult.durationSeconds(),
                        renderResult.fileSizeBytes(),
                        totalDurationMs
                );
            } else {
                log.error("Render service failed: {}", renderResult.errorMessage());
                return PipelineRunResponse.fail("Render failed: " + renderResult.errorMessage(), totalDurationMs);
            }

        } catch (Exception e) {
            long totalDurationMs = System.currentTimeMillis() - startTime;
            log.error("Pipeline execution failed: {}", e.getMessage(), e);
            return PipelineRunResponse.fail("Pipeline execution failed: " + e.getMessage(), totalDurationMs);
        }
    }

    private String sendPost(String url, Object body) throws Exception {
        String bodyJson = objectMapper.writeValueAsString(body);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(bodyJson))
                .timeout(Duration.ofMinutes(3))
                .build();

        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            throw new RuntimeException("HTTP " + resp.statusCode() + " from " + url + ": " + resp.body());
        }
        return resp.body();
    }
}
