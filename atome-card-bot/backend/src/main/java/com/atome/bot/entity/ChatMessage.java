package com.atome.bot.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "chat_message")
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false, length = 64)
    private String sessionId;

    @Column(nullable = false, length = 20)
    private String role;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String text;

    @Column(length = 20)
    private String route;

    @Column(name = "matched_url", columnDefinition = "TEXT")
    private String matchedUrl;

    @Column(name = "matched_id")
    private Long matchedId;

    @Column
    private Double score;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public String getRoute() { return route; }
    public void setRoute(String route) { this.route = route; }
    public String getMatchedUrl() { return matchedUrl; }
    public void setMatchedUrl(String matchedUrl) { this.matchedUrl = matchedUrl; }
    public Long getMatchedId() { return matchedId; }
    public void setMatchedId(Long matchedId) { this.matchedId = matchedId; }
    public Double getScore() { return score; }
    public void setScore(Double score) { this.score = score; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}