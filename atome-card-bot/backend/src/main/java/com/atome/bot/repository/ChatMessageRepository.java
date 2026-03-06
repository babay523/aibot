package com.atome.bot.repository;

import com.atome.bot.entity.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findBySessionIdOrderByCreatedAtAsc(String sessionId);

    Optional<ChatMessage> findFirstBySessionIdOrderByCreatedAtDesc(String sessionId);

    @Query("SELECT m FROM ChatMessage m WHERE m.id = ?1 AND m.role = 'assistant'")
    Optional<ChatMessage> findAssistantMessageById(Long id);
}