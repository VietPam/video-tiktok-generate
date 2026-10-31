package com.tiktok.video.media.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/media")
public class MediaController {

    private static final Logger log = LoggerFactory.getLogger(MediaController.class);

    private final Path assetsDir;

    public MediaController(@Value("${tiktok.media.assets-dir:/app/assets/images}") String assetsDir) {
        this.assetsDir = Paths.get(assetsDir);
    }

    @GetMapping("/illustration/{keyword}")
    public ResponseEntity<Map<String, String>> getIllustration(@PathVariable("keyword") String keyword) {
        String cleanKw = keyword.toLowerCase().trim();
        String targetFileName = switch (cleanKw) {
            case "sibling", "siblings", "sibling_rivalry" -> "sibling.jpg";
            case "relative", "relatives", "distant_relative" -> "relative.jpg";
            case "hook", "intro", "welcome" -> "hook.jpg";
            default -> "cta.jpg";
        };

        Path candidate = assetsDir.resolve(targetFileName);
        if (!Files.exists(candidate)) {
            // Check fallback in current workspace if running outside docker
            Path localCandidate = Paths.get("assets/images").resolve(targetFileName);
            if (Files.exists(localCandidate)) {
                candidate = localCandidate;
            }
        }

        log.info("Requested illustration for keyword '{}' -> found: {}", cleanKw, candidate.toAbsolutePath());
        return ResponseEntity.ok(Map.of(
                "keyword", cleanKw,
                "filePath", candidate.toAbsolutePath().toString(),
                "exists", String.valueOf(Files.exists(candidate))
        ));
    }
}
