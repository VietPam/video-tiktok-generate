package com.tiktok.video.visual.controller;

import com.tiktok.video.core.contract.VisualCardRenderer;
import com.tiktok.video.core.exception.VisualRenderException;
import com.tiktok.video.visual.dto.RenderCardsRequest;
import com.tiktok.video.visual.dto.RenderCardsResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/visual")
public class VisualController {

    private static final Logger log = LoggerFactory.getLogger(VisualController.class);

    private final VisualCardRenderer visualCardRenderer;
    private final String defaultOutputDir;

    public VisualController(
            VisualCardRenderer visualCardRenderer,
            @Value("${tiktok.visual.output-dir:/tmp/tiktok-cards}") String defaultOutputDir) {
        this.visualCardRenderer = visualCardRenderer;
        this.defaultOutputDir = defaultOutputDir;
    }

    @PostMapping("/render-cards")
    public ResponseEntity<RenderCardsResponse> renderCards(@RequestBody RenderCardsRequest request) {
        if (request == null || request.script() == null) {
            return ResponseEntity.badRequest()
                    .body(RenderCardsResponse.error("script is required"));
        }

        try {
            String outDirStr = (request.outputDir() != null && !request.outputDir().isBlank())
                    ? request.outputDir()
                    : defaultOutputDir;
            Path outDir = Paths.get(outDirStr);

            Map<String, Path> pathMap = visualCardRenderer.renderCards(request.script(), outDir);
            Map<String, String> stringMap = new HashMap<>();
            pathMap.forEach((sceneId, path) -> stringMap.put(sceneId, path.toAbsolutePath().toString()));

            return ResponseEntity.ok(RenderCardsResponse.ok(stringMap));
        } catch (VisualRenderException e) {
            log.error("Failed to render visual cards: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(RenderCardsResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error during visual rendering", e);
            return ResponseEntity.internalServerError()
                    .body(RenderCardsResponse.error("Internal error: " + e.getMessage()));
        }
    }
}
