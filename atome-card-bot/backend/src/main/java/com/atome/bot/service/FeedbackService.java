package com.atome.bot.service;

import com.atome.bot.entity.ChatMessage;
import com.atome.bot.entity.Feedback;
import com.atome.bot.entity.FeedbackCandidate;
import com.atome.bot.entity.OverrideMeta;
import com.atome.bot.model.SearchHit;
import com.atome.bot.repository.ChatMessageRepository;
import com.atome.bot.repository.FeedbackCandidateRepository;
import com.atome.bot.repository.FeedbackRepository;
import com.atome.bot.repository.KbArticleRepository;
import com.atome.bot.repository.OverrideMetaRepository;
import com.atome.bot.client.OllamaClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class FeedbackService {

    private static final Logger log = LoggerFactory.getLogger(FeedbackService.class);

    private final FeedbackRepository feedbackRepo;
    private final FeedbackCandidateRepository candidateRepo;
    private final OverrideMetaRepository overrideRepo;
    private final ChatMessageRepository messageRepo;
    private final KbArticleRepository articleRepo;
    private final OllamaClient ollamaClient;
    private final MilvusService milvusService;
    private final ObjectMapper objectMapper;

    @Value("${bot.thresholds.kb}")
    private double kbThreshold;

    @Value("${bot.kb-search.chunk-top-k}")
    private int kbTopK;

    public FeedbackService(FeedbackRepository feedbackRepo,
                          FeedbackCandidateRepository candidateRepo,
                          OverrideMetaRepository overrideRepo,
                          ChatMessageRepository messageRepo,
                          KbArticleRepository articleRepo,
                          OllamaClient ollamaClient,
                          MilvusService milvusService,
                          ObjectMapper objectMapper) {
        this.feedbackRepo = feedbackRepo;
        this.candidateRepo = candidateRepo;
        this.overrideRepo = overrideRepo;
        this.messageRepo = messageRepo;
        this.articleRepo = articleRepo;
        this.ollamaClient = ollamaClient;
        this.milvusService = milvusService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public FeedbackResult startFeedback(Long messageId) {
        // 1. 获取消息
        ChatMessage message = messageRepo.findAssistantMessageById(messageId)
                .orElseThrow(() -> new RuntimeException("Message not found or not assistant message: " + messageId));

        if (!"kb".equals(message.getRoute())) {
            throw new RuntimeException("Only KB messages can have feedback mismatch");
        }

        // 2. 获取用户提问（上一条用户消息）
        List<ChatMessage> sessionMessages = messageRepo.findBySessionIdOrderByCreatedAtAsc(message.getSessionId());
        String userQuestion = null;
        for (int i = 0; i < sessionMessages.size(); i++) {
            if (sessionMessages.get(i).getId().equals(messageId) && i > 0) {
                ChatMessage prev = sessionMessages.get(i - 1);
                if ("user".equals(prev.getRole())) {
                    userQuestion = prev.getText();
                    break;
                }
            }
        }

        if (userQuestion == null) {
            throw new RuntimeException("Cannot find user question for message: " + messageId);
        }

        // 3. 创建 Feedback
        Feedback feedback = new Feedback();
        feedback.setSessionId(message.getSessionId());
        feedback.setQuestionText(userQuestion);
        feedback.setBadUrl(message.getMatchedUrl());
        feedback.setBadScore(message.getScore());
        feedback.setStatus("awaiting_choice");
        feedback.setCreatedAt(LocalDateTime.now());
        feedback = feedbackRepo.save(feedback);

        // 4. 重新搜索 KB，获取 Top3 候选（排除 badUrl）
        List<FeedbackCandidate> candidates = findCandidates(userQuestion, message.getMatchedUrl(), feedback.getId());
        for (FeedbackCandidate c : candidates) {
            candidateRepo.save(c);
        }

        return new FeedbackResult(
                feedback.getId(),
                userQuestion,
                message.getMatchedUrl(),
                candidates.stream()
                        .map(c -> new CandidateDTO(c.getRank(), c.getTitle(), c.getUrl(), c.getScore()))
                        .collect(Collectors.toList())
        );
    }

    @Transactional
    public ResolveResult chooseCandidate(Long feedbackId, Integer rank) {
        // 1. 获取反馈和候选
        Feedback feedback = feedbackRepo.findById(feedbackId)
                .orElseThrow(() -> new RuntimeException("Feedback not found: " + feedbackId));

        if (!"awaiting_choice".equals(feedback.getStatus())) {
            throw new RuntimeException("Feedback status is not awaiting_choice: " + feedback.getStatus());
        }

        FeedbackCandidate candidate = candidateRepo.findByFeedbackIdAndRank(feedbackId, rank);
        if (candidate == null) {
            throw new RuntimeException("Candidate not found for rank: " + rank);
        }

        // 2. 创建 Override 元数据
        OverrideMeta meta = new OverrideMeta();
        meta.setFeedbackId(feedbackId);
        meta.setQuestionText(feedback.getQuestionText());
        meta.setChosenUrl(candidate.getUrl());
        meta.setActive(true);
        meta.setCreatedAt(LocalDateTime.now());
        meta = overrideRepo.save(meta);

        // 3. 对原问题生成 embedding，写入 Milvus overrides
        List<Float> embedding = ollamaClient.embed(feedback.getQuestionText());
        milvusService.insertOverride(new com.atome.bot.model.OverrideVectorRow(
                meta.getId(),
                embedding,
                true,
                candidate.getUrl()
        ));

        // 4. 更新 Feedback 状态
        feedback.setStatus("archived");
        feedback.setResolvedAt(LocalDateTime.now());
        feedbackRepo.save(feedback);

        log.info("Override created: id={}, question='{}', url={}", meta.getId(), 
                feedback.getQuestionText().substring(0, Math.min(50, feedback.getQuestionText().length())),
                candidate.getUrl());

        return new ResolveResult(meta.getId(), candidate.getUrl(), "archived");
    }

    private List<FeedbackCandidate> findCandidates(String question, String excludeUrl, Long feedbackId) {
        // 1. Embedding 搜索
        List<Float> queryVec = ollamaClient.embed(question);
        List<SearchHit> hits = milvusService.searchKb(queryVec, kbTopK);

        // 2. 聚合到 article，排除 badUrl
        Map<Long, SearchHit> articleBestHit = new HashMap<>();
        for (SearchHit hit : hits) {
            if (!articleBestHit.containsKey(hit.getArticleId()) || hit.getScore() > articleBestHit.get(hit.getArticleId()).getScore()) {
                articleBestHit.put(hit.getArticleId(), hit);
            }
        }

        // 3. 获取文章详情，排除 badUrl
        List<FeedbackCandidate> candidates = new ArrayList<>();
        int rank = 1;
        for (SearchHit hit : articleBestHit.values().stream()
                .sorted((a, b) -> Float.compare(b.getScore(), a.getScore()))
                .limit(5) // 多取一点，排除后可能不够
                .collect(Collectors.toList())) {
            
            var article = articleRepo.findById(hit.getArticleId());
            if (article.isEmpty()) continue;

            String url = article.get().getUrl();
            if (url != null && url.equals(excludeUrl)) {
                continue; // 排除 bad answer
            }

            FeedbackCandidate c = new FeedbackCandidate();
            c.setFeedbackId(feedbackId);
            c.setRank(rank);
            c.setUrl(url);
            c.setTitle(article.get().getTitle());
            c.setScore((double) hit.getScore());
            c.setCreatedAt(LocalDateTime.now());
            candidates.add(c);

            if (candidates.size() >= 3) break;
        }

        // 如果不足 3 个，不排除 badUrl 再补充
        if (candidates.size() < 3) {
            for (SearchHit hit : articleBestHit.values().stream()
                    .sorted((a, b) -> Float.compare(b.getScore(), a.getScore()))
                    .collect(Collectors.toList())) {
                
                var article = articleRepo.findById(hit.getArticleId());
                if (article.isEmpty()) continue;

                String url = article.get().getUrl();
                // 检查是否已经在 candidates 中
                if (candidates.stream().anyMatch(c -> c.getUrl().equals(url))) {
                    continue;
                }

                FeedbackCandidate c = new FeedbackCandidate();
                c.setFeedbackId(feedbackId);
                c.setRank(candidates.size() + 1);
                c.setUrl(url);
                c.setTitle(article.get().getTitle());
                c.setScore((double) hit.getScore());
                c.setCreatedAt(LocalDateTime.now());
                candidates.add(c);

                if (candidates.size() >= 3) break;
            }
        }

        // 重新排序 rank
        for (int i = 0; i < candidates.size(); i++) {
            candidates.get(i).setRank(i + 1);
        }

        return candidates;
    }

    public record FeedbackResult(Long feedbackId, String questionText, String badUrl, List<CandidateDTO> candidates) {}
    public record CandidateDTO(int rank, String title, String url, Double score) {}
    public record ResolveResult(Long overrideId, String chosenUrl, String status) {}
}