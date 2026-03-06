package com.atome.bot.repository;

import com.atome.bot.entity.Feedback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface FeedbackRepository extends JpaRepository<Feedback, Long> {

    List<Feedback> findByStatusOrderByCreatedAtDesc(String status);

    List<Feedback> findBySessionId(String sessionId);

    @Modifying
    @Query("UPDATE Feedback f SET f.status = ?2, f.resolvedAt = ?3 WHERE f.id = ?1")
    void updateStatus(Long id, String status, LocalDateTime resolvedAt);
}