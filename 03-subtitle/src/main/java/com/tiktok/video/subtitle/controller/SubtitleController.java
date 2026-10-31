package com.tiktok.video.subtitle.controller;

import com.tiktok.video.core.contract.SubtitleGenerator;
import com.tiktok.video.core.exception.SubtitleGenerationException;
import com.tiktok.video.subtitle.dto.GenerateSubtitleRequest;
import com.tiktok.video.subtitle.dto.GenerateSubtitleResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Path;
import java.nio.file.Paths;

@RestController
@RequestMapping("/api/v1/subtitle")
public class SubtitleController {

    private static final Logger log = LoggerFactory.getLogger(SubtitleController.class);

    private final SubtitleGenerator subtitleGenerator;
    private final String defaultOutputDir;

    public SubtitleController(
            SubtitleGenerator subtitleGenerator,
            @Value("${tiktok.subtitle.output-dir:/tmp/tiktok-subtitles}") String defaultOutputDir) {
        this.subtitleGenerator = subtitleGenerator;
        this.defaultOutputDir = defaultOutputDir;
    }

    @PostMapping("/generate")
    public ResponseEntity<GenerateSubtitleResponse> generate(@RequestBody GenerateSubtitleRequest request) {
        if (request == null || request.script() == null || request.timeline() == null) {
            return ResponseEntity.badRequest()
                    .body(GenerateSubtitleResponse.error("script and timeline are required"));
        }

        try {
            String outDirStr = (request.outputDir() != null && !request.outputDir().isBlank())
                    ? request.outputDir()
                    : defaultOutputDir;
            Path outDir = Paths.get(outDirStr);

            Path generatedFile = subtitleGenerator.generateAss(request.script(), request.timeline(), outDir);
            return ResponseEntity.ok(GenerateSubtitleResponse.ok(generatedFile.toAbsolutePath().toString()));
        } catch (SubtitleGenerationException e) {
            log.error("Failed to generate subtitles: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(GenerateSubtitleResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error during subtitle generation", e);
            return ResponseEntity.internalServerError()
                    .body(GenerateSubtitleResponse.error("Internal error: " + e.getMessage()));
        }
    }
}
