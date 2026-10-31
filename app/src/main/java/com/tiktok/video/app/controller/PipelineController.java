package com.tiktok.video.app.controller;

import com.tiktok.video.app.dto.PipelineRunRequest;
import com.tiktok.video.app.dto.PipelineRunResponse;
import com.tiktok.video.app.service.PipelineOrchestratorService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/pipeline")
public class PipelineController {

    private final PipelineOrchestratorService orchestratorService;

    public PipelineController(PipelineOrchestratorService orchestratorService) {
        this.orchestratorService = orchestratorService;
    }

    @PostMapping("/run")
    public ResponseEntity<PipelineRunResponse> run(@RequestBody PipelineRunRequest request) {
        if (request == null || ((request.transcript() == null || request.transcript().isBlank()) && request.script() == null)) {
            return ResponseEntity.badRequest()
                    .body(PipelineRunResponse.fail("Either transcript or script must be provided", 0L));
        }

        PipelineRunResponse response = orchestratorService.executePipeline(request);
        if (response.success()) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.internalServerError().body(response);
        }
    }
}
