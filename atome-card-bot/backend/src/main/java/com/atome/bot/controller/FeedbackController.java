package com.atome.bot.controller;

import com.atome.bot.service.FeedbackService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class FeedbackController {

    private final FeedbackService feedbackService;

    public FeedbackController(FeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    @PostMapping("/feedback/start")
    public ResponseEntity<FeedbackService.FeedbackResult> startFeedback(
            @RequestBody StartFeedbackRequest request) {
        FeedbackService.FeedbackResult result = feedbackService.startFeedback(request.messageId());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/feedback/choose")
    public ResponseEntity<FeedbackService.ResolveResult> chooseCandidate(
            @RequestBody ChooseCandidateRequest request) {
        FeedbackService.ResolveResult result = feedbackService.chooseCandidate(
                request.feedbackId(),
                request.rank()
        );
        return ResponseEntity.ok(result);
    }

    public record StartFeedbackRequest(Long messageId) {}
    public record ChooseCandidateRequest(Long feedbackId, Integer rank) {}
}