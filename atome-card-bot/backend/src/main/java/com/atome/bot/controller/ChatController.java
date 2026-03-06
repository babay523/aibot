package com.atome.bot.controller;

import com.atome.bot.service.RouterService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping(value = "/api", produces = "application/json;charset=UTF-8", consumes = "application/json;charset=UTF-8")
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class ChatController {

    private final RouterService routerService;

    public ChatController(RouterService routerService) {
        this.routerService = routerService;
    }

    @PostMapping("/chat")
    public ResponseEntity<RouterService.ChatResponse> chat(@RequestBody ChatRequest request) {
        RouterService.ChatResponse response = routerService.handleChat(
                request.sessionId(),
                request.message()
        );
        return ResponseEntity.ok(response);
    }

    public record ChatRequest(String sessionId, String message) {}
}