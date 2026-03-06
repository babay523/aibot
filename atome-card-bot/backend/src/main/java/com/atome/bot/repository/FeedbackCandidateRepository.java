package com.atome.bot.repository;

import com.atome.bot.entity.FeedbackCandidate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FeedbackCandidateRepository extends JpaRepository<FeedbackCandidate, Long> {

    List<FeedbackCandidate> findByFeedbackIdOrderByRankAsc(Long feedbackId);

    FeedbackCandidate findByFeedbackIdAndRank(Long feedbackId, Integer rank);
}