package com.atome.bot.service;

import com.atome.bot.entity.ChatMessage;
import com.atome.bot.entity.KbArticle;
import com.atome.bot.model.SearchHit;
import com.atome.bot.repository.ChatMessageRepository;
import com.atome.bot.repository.KbArticleRepository;
import com.atome.bot.repository.OverrideMetaRepository;
import com.atome.bot.client.DeepSeekClient;
import com.atome.bot.client.OllamaClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class RouterService {

    private static final Logger log = LoggerFactory.getLogger(RouterService.class);

    private final IntentService intentService;
    private final SessionStateService sessionService;
    private final OllamaClient ollamaClient;
    private final MilvusService milvusService;
    private final KbArticleRepository articleRepo;
    private final OverrideMetaRepository overrideRepo;
    private final ChatMessageRepository messageRepo;
    private final DeepSeekClient deepSeekClient;

    @Value("${bot.thresholds.override}")
    private double overrideThreshold;

    @Value("${bot.thresholds.kb}")
    private double kbThreshold;

    @Value("${bot.kb-search.chunk-top-k}")
    private int kbTopK;

    @Value("${bot.kb-search.aggregate-limit}")
    private int aggregateLimit;

    public RouterService(IntentService intentService,
                        SessionStateService sessionService,
                        OllamaClient ollamaClient,
                        MilvusService milvusService,
                        KbArticleRepository articleRepo,
                        OverrideMetaRepository overrideRepo,
                        ChatMessageRepository messageRepo,
                        DeepSeekClient deepSeekClient) {
        this.intentService = intentService;
        this.sessionService = sessionService;
        this.ollamaClient = ollamaClient;
        this.milvusService = milvusService;
        this.articleRepo = articleRepo;
        this.overrideRepo = overrideRepo;
        this.messageRepo = messageRepo;
        this.deepSeekClient = deepSeekClient;
    }

    @Transactional
    public ChatResponse handleChat(String sessionId, String message) {
        // 保存用户消息
        ChatMessage userMsg = new ChatMessage();
        userMsg.setSessionId(sessionId);
        userMsg.setRole("user");
        userMsg.setText(message);
        userMsg.setCreatedAt(LocalDateTime.now());
        messageRepo.save(userMsg);

        // 获取会话状态
        SessionStateService.SessionState state = sessionService.getState(sessionId);

        // Step 1: 检查是否有待处理的 intent（slot 收集）
        if (state.hasPendingIntent()) {
            return handleSlotCollection(sessionId, message, state);
        }

        // Step 2: 意图匹配
        IntentService.IntentMatch intent = intentService.match(message);
        if (intent != null) {
            return handleIntent(sessionId, message, intent);
        }

        // Step 3: Overrides（纠错优先）
        OverrideResult override = searchOverride(message);
        if (override != null && override.score >= overrideThreshold) {
            return buildOverrideResponse(sessionId, override);
        }

        // Step 4: KB 向量检索
        KbResult kb = searchKb(message);
        log.debug("KB result: kb={}, score={}, threshold={}", kb, kb != null ? kb.score : null, kbThreshold);
        if (kb != null && kb.score >= kbThreshold) {
            return buildKbResponse(sessionId, kb);
        }

        // Step 5: Fallback（DeepSeek）
        return buildFallbackResponse(sessionId, message);
    }

    private ChatResponse handleSlotCollection(String sessionId, String message, SessionStateService.SessionState state) {
        IntentService.IntentMatch intent = intentService.getIntentByName(state.pendingIntent());
        if (intent == null) {
            sessionService.clearState(sessionId);
            return buildFallbackResponse(sessionId, message);
        }

        // 简化：直接把用户消息作为当前缺失 slot 的值
        String missingSlot = findMissingSlot(state, intent.requiredSlots());
        if (missingSlot != null) {
            state.collectSlot(missingSlot, message);
            sessionService.saveState(state);
        }

        // 检查是否所有 slots 都已收集
        if (intent.requiredSlots().stream().allMatch(state::isSlotComplete)) {
            // 执行 handler
            sessionService.clearState(sessionId);
            return executeHandler(sessionId, intent, state.slots());
        } else {
            // 继续追问
            String nextMissing = findMissingSlot(state, intent.requiredSlots());
            String followUp = deepSeekClient.generateFollowUp(intent.instructions(), nextMissing);
            return saveAndReturn(sessionId, followUp, "intent", null, null, null, false);
        }
    }

    private ChatResponse handleIntent(String sessionId, String message, IntentService.IntentMatch intent) {
        if (intent.requiredSlots().isEmpty()) {
            // 无需收集 slots，直接执行
            return executeHandler(sessionId, intent, Map.of());
        } else {
            // 需要收集 slots，创建会话状态
            SessionStateService.SessionState state = new SessionStateService.SessionState(
                    sessionId, intent.name(), new HashMap<>()
            );
            sessionService.saveState(state);

            // 追问第一个缺失的 slot
            String missing = intent.requiredSlots().get(0);
            String followUp = deepSeekClient.generateFollowUp(intent.instructions(), missing);
            return saveAndReturn(sessionId, followUp, "intent", null, null, null, false);
        }
    }

    private String findMissingSlot(SessionStateService.SessionState state, List<String> requiredSlots) {
        for (String slot : requiredSlots) {
            if (!state.isSlotComplete(slot)) {
                return slot;
            }
        }
        return null;
    }

    private ChatResponse executeHandler(String sessionId, IntentService.IntentMatch intent, Map<String, String> slots) {
        String answer;
        switch (intent.handler()) {
            case "cardApplicationHandler" -> {
                String appId = slots.getOrDefault("application_id", "未知");
                answer = "您的申请进度：申请编号 " + appId + " 当前状态为 **审核中**，预计 3-5 个工作日完成。";
            }
            case "failedTransactionHandler" -> {
                String txId = slots.getOrDefault("transaction_id", "未知");
                answer = "交易编号 " + txId + " 的状态为 **失败**，失败原因：卡片余额不足或商户拒绝。建议检查余额后重试。";
            }
            default -> {
                answer = "我已收到您的请求，正在处理中...";
            }
        }
        return saveAndReturn(sessionId, answer, "intent", null, null, null, false);
    }

    private OverrideResult searchOverride(String message) {
        List<Float> queryVec = ollamaClient.embed(message);
        var hit = milvusService.searchOverrideHit(queryVec);
        if (hit == null) {
            log.info("Override search: no hit found for message '{}'", message);
            return null;
        }
        log.info("Override search: found hit id={}, url={}, score={}, threshold={}", 
                 hit.overrideId(), hit.chosenUrl(), hit.score(), overrideThreshold);
        return new OverrideResult(hit.overrideId(), hit.chosenUrl(), hit.score());
    }

    private KbResult searchKb(String message) {
        log.debug("Searching KB for message: {}", message);
        List<Float> queryVec = ollamaClient.embed(message);
        List<SearchHit> hits = milvusService.searchKb(queryVec, kbTopK);
        
        log.debug("KB search returned {} hits", hits.size());
        for (SearchHit hit : hits) {
            log.debug("  Hit: articleId={}, score={}", hit.getArticleId(), hit.getScore());
        }

        if (hits.isEmpty()) return null;

        // 聚合到 article，取最高 score
        Map<Long, SearchHit> articleBest = new HashMap<>();
        for (SearchHit hit : hits) {
            if (!articleBest.containsKey(hit.getArticleId()) || hit.getScore() > articleBest.get(hit.getArticleId()).getScore()) {
                articleBest.put(hit.getArticleId(), hit);
            }
        }

        // 取 top article
        Optional<Map.Entry<Long, SearchHit>> best = articleBest.entrySet().stream()
                .max(Map.Entry.comparingByValue(Comparator.comparingDouble(SearchHit::getScore)));

        if (best.isEmpty()) return null;

        Long articleId = best.get().getKey();
        float score = best.get().getValue().getScore();
        
        log.debug("Best article: id={}, score={}, threshold={}", articleId, score, kbThreshold);

        var articleOpt = articleRepo.findById(articleId);
        log.debug("Article lookup: id={}, found={}", articleId, articleOpt.isPresent());
        if (articleOpt.isEmpty()) return null;

        KbArticle article = articleOpt.get();
        return new KbResult(articleId, article.getTitle(), article.getUrl(), score);
    }

    private ChatResponse buildOverrideResponse(String sessionId, OverrideResult override) {
        String answer = "根据您的历史反馈，推荐查看：" + override.url;
        return saveAndReturn(sessionId, answer, "override", override.url, override.id, override.score, false);
    }

    private ChatResponse buildKbResponse(String sessionId, KbResult kb) {
        String answer = kb.title + "\n" + kb.url;
        return saveAndReturn(sessionId, answer, "kb", kb.url, kb.articleId, kb.score, true);
    }

    private ChatResponse buildFallbackResponse(String sessionId, String message) {
        String context = "KB not found for query";
        String answer = deepSeekClient.generateFallback(message, context);
        return saveAndReturn(sessionId, answer, "fallback", null, null, null, false);
    }

    private ChatResponse saveAndReturn(String sessionId, String answer, String route, String matchedUrl, Long matchedId, Double score, boolean canFeedback) {
        ChatMessage msg = new ChatMessage();
        msg.setSessionId(sessionId);
        msg.setRole("assistant");
        msg.setText(answer);
        msg.setRoute(route);
        msg.setMatchedUrl(matchedUrl);
        msg.setMatchedId(matchedId);
        msg.setScore(score);
        msg.setCreatedAt(LocalDateTime.now());
        msg = messageRepo.save(msg);

        return new ChatResponse(
                msg.getId(),
                answer,
                route,
                new MatchedInfo(matchedUrl, matchedId, score, route),
                new UIInfo(canFeedback)
        );
    }

    public record ChatResponse(Long messageId, String answerText, String route,
                               MatchedInfo matched, UIInfo ui) {}
    public record MatchedInfo(String url, Long id, Double score, String route) {}
    public record UIInfo(boolean canFeedbackMismatch) {}
    private record OverrideResult(Long id, String url, double score) {}
    private record KbResult(Long articleId, String title, String url, double score) {}
}
