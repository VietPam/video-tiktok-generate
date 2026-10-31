package com.tiktok.video.app;

import com.tiktok.video.app.dto.PipelineRunRequest;
import com.tiktok.video.app.dto.PipelineRunResponse;
import com.tiktok.video.app.service.PipelineOrchestratorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@SpringBootApplication
public class AppServiceApplication {

    private static final Logger log = LoggerFactory.getLogger(AppServiceApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(AppServiceApplication.class, args);
    }

    @Bean
    public CommandLineRunner commandLineRunner(
            PipelineOrchestratorService orchestratorService,
            @Value("${tiktok.pipeline.run-on-startup:false}") boolean runOnStartup,
            @Value("${tiktok.pipeline.transcript-path:}") String transcriptPath) {
        return args -> {
            if (runOnStartup && transcriptPath != null && !transcriptPath.isBlank()) {
                Path path = Paths.get(transcriptPath);
                if (Files.exists(path)) {
                    log.info("Startup run triggered for transcript: {}", path);
                    String transcript = Files.readString(path);
                    PipelineRunRequest req = new PipelineRunRequest(transcript, "Family", "Band 7+", "startup_tiktok_lesson.mp4");
                    PipelineRunResponse resp = orchestratorService.executePipeline(req);
                    log.info("Startup pipeline response: success={}, video={}", resp.success(), resp.finalVideoPath());
                } else {
                    log.warn("Transcript file not found at: {}", path);
                }
            }
        };
    }
}
