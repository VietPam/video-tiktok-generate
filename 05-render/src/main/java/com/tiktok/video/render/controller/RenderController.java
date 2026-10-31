package com.tiktok.video.render.controller;

import com.tiktok.video.core.contract.FallbackAssetGenerator;
import com.tiktok.video.core.contract.VideoComposer;
import com.tiktok.video.core.model.RenderResult;
import com.tiktok.video.render.dto.FallbackAssetRequest;
import com.tiktok.video.render.dto.RenderVideoRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/render")
public class RenderController {

    private static final Logger log = LoggerFactory.getLogger(RenderController.class);

    private final VideoComposer videoComposer;
    private final FallbackAssetGenerator fallbackAssetGenerator;

    public RenderController(VideoComposer videoComposer, FallbackAssetGenerator fallbackAssetGenerator) {
        this.videoComposer = videoComposer;
        this.fallbackAssetGenerator = fallbackAssetGenerator;
    }

    @PostMapping("/compose")
    public ResponseEntity<RenderResult> compose(@RequestBody RenderVideoRequest request) {
        if (request == null || request.blueprint() == null) {
            return ResponseEntity.badRequest()
                    .body(RenderResult.failure("blueprint is required"));
        }

        try {
            RenderResult result = videoComposer.composeVideo(request.blueprint());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Video composition error: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(RenderResult.failure("Composition failed: " + e.getMessage()));
        }
    }

    @PostMapping("/fallback/background")
    public ResponseEntity<Map<String, String>> generateBackground(@RequestBody FallbackAssetRequest req) {
        try {
            Path out = fallbackAssetGenerator.generateBackgroundVideo(req.durationSeconds(), Paths.get(req.outputPath()));
            return ResponseEntity.ok(Map.of("path", out.toAbsolutePath().toString(), "status", "OK"));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/fallback/bgm")
    public ResponseEntity<Map<String, String>> generateBgm(@RequestBody FallbackAssetRequest req) {
        try {
            Path out = fallbackAssetGenerator.generateBgmAudio(req.durationSeconds(), Paths.get(req.outputPath()));
            return ResponseEntity.ok(Map.of("path", out.toAbsolutePath().toString(), "status", "OK"));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }
}
