package com.atome.bot.controller;

import com.atome.bot.client.OllamaClient;
import com.atome.bot.entity.*;
import com.atome.bot.model.MilvusVectorRow;
import com.atome.bot.service.KbSyncService;
import com.atome.bot.service.MilvusService;
import com.atome.bot.repository.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final KbSourceRepository sourceRepo;
    private final KbArticleRepository articleRepo;
    private final IntentConfigRepository intentRepo;
    private final FeedbackRepository feedbackRepo;
    private final OverrideMetaRepository overrideRepo;
    private final KbChunkRepository chunkRepo;
    private final KbSyncService syncService;
    private final OllamaClient ollamaClient;
    private final MilvusService milvusService;

    public AdminController(KbSourceRepository sourceRepo,
                           KbArticleRepository articleRepo,
                           IntentConfigRepository intentRepo,
                           FeedbackRepository feedbackRepo,
                           OverrideMetaRepository overrideRepo,
                           KbChunkRepository chunkRepo,
                           KbSyncService syncService,
                           OllamaClient ollamaClient,
                           MilvusService milvusService) {
        this.sourceRepo = sourceRepo;
        this.articleRepo = articleRepo;
        this.intentRepo = intentRepo;
        this.feedbackRepo = feedbackRepo;
        this.overrideRepo = overrideRepo;
        this.chunkRepo = chunkRepo;
        this.syncService = syncService;
        this.ollamaClient = ollamaClient;
        this.milvusService = milvusService;
    }

    // KB Sources
    @GetMapping("/kb-sources")
    public ResponseEntity<List<KbSource>> listSources() {
        return ResponseEntity.ok(sourceRepo.findAll());
    }

    @PostMapping("/kb-sources")
    public ResponseEntity<KbSource> createSource(@RequestBody KbSource source) {
        return ResponseEntity.ok(sourceRepo.save(source));
    }

    @PatchMapping("/kb-sources/{id}")
    public ResponseEntity<KbSource> updateSource(@PathVariable Integer id, @RequestBody KbSource source) {
        return sourceRepo.findById(id)
                .map(existing -> {
                    existing.setName(source.getName());
                    existing.setUrl(source.getUrl());
                    existing.setEnabled(source.getEnabled());
                    return ResponseEntity.ok(sourceRepo.save(existing));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/kb-sources/{id}")
    public ResponseEntity<?> deleteSource(@PathVariable Integer id) {
        Optional<KbSource> sourceOpt = sourceRepo.findById(id);
        if (sourceOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        KbSource source = sourceOpt.get();
        // 1. 删除 Milvus 中的向量
        milvusService.deleteKbBySourceId(id);
        // 2. 删除数据库中的记录
        sourceRepo.delete(source);
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("sourceId", id);
        result.put("name", source.getName());
        result.put("message", "Source and related data deleted successfully");
        return ResponseEntity.ok(result);
    }

    // KB Sync
    @PostMapping("/kb-sync")
    public ResponseEntity<Map<String, Object>> syncKb(@RequestBody SyncRequest request) {
        KbSyncService.SyncResult result = syncService.syncSource(request.sourceId(), request.rebuild());
        return ResponseEntity.ok(Map.of(
                "articleCount", result.articleCount(),
                "chunkCount", result.chunkCount(),
                "durationMs", result.durationMs()
        ));
    }

    // KB Articles
    @GetMapping("/kb-articles")
    public ResponseEntity<List<KbArticle>> listArticles(@RequestParam(required = false) Integer sourceId) {
        if (sourceId != null) {
            return ResponseEntity.ok(articleRepo.findBySourceId(sourceId));
        }
        return ResponseEntity.ok(articleRepo.findAll());
    }

    @PostMapping("/kb-articles")
    public ResponseEntity<Map<String, Object>> createArticle(@RequestBody CreateArticleRequest request) {
        try {
            // 1. 验证 source 存在
            KbSource source = sourceRepo.findById(request.sourceId())
                    .orElseThrow(() -> new RuntimeException("Source not found: " + request.sourceId()));

            // 2. 创建文章
            KbArticle article = new KbArticle();
            article.setSourceId(request.sourceId());
            article.setTitle(request.title());
            article.setUrl(request.url());
            article.setBodyText(request.bodyText());
            article.setHash(String.valueOf(System.currentTimeMillis())); // 简单 hash
            article.setCreatedAt(LocalDateTime.now());
            article.setUpdatedAt(LocalDateTime.now());
            article = articleRepo.save(article);

            // 3. 创建分块（简单的单分块策略）
            KbChunk chunk = new KbChunk();
            chunk.setArticleId(article.getId());
            chunk.setChunkNo(0);
            // 组合标题和正文
            String chunkText = request.title() + " " + request.bodyText();
            chunk.setChunkText(chunkText);
            chunk.setCreatedAt(LocalDateTime.now());
            chunk = chunkRepo.save(chunk);

            // 4. 生成向量并插入 Milvus
            List<Float> embedding = ollamaClient.embed(chunkText);
            var vectorRow = new com.atome.bot.model.MilvusVectorRow(
                    chunk.getId(),
                    embedding,
                    article.getId(),
                    request.sourceId()
            );
            milvusService.insertKbChunks(Collections.singletonList(vectorRow));

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("articleId", article.getId());
            result.put("chunkId", chunk.getId());
            result.put("title", article.getTitle());
            result.put("message", "Article created and synced to vector database");

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    @DeleteMapping("/kb-articles/{id}")
    public ResponseEntity<Map<String, Object>> deleteArticle(@PathVariable Long id) {
        return articleRepo.findById(id)
                .map(article -> {
                    Integer sourceId = article.getSourceId();
                    
                    // 1. 获取该文章的所有 chunks
                    List<KbChunk> chunks = chunkRepo.findByArticleId(id);
                    
                    // 2. 从 Milvus 中删除这些 chunks 的向量
                    for (KbChunk chunk : chunks) {
                        try {
                            milvusService.deleteKbChunk(chunk.getId());
                        } catch (Exception e) {
                            System.err.println("⚠️ 删除 Milvus chunk " + chunk.getId() + " 失败: " + e.getMessage());
                        }
                    }
                    
                    // 3. 删除数据库中的 chunks
                    chunkRepo.deleteAll(chunks);
                    
                    // 4. 删除文章
                    articleRepo.delete(article);
                    
                    Map<String, Object> result = new HashMap<>();
                    result.put("success", true);
                    result.put("articleId", id);
                    result.put("chunksDeleted", chunks.size());
                    result.put("message", "Article and associated data deleted successfully");
                    return ResponseEntity.ok(result);
                })
                .orElse(ResponseEntity.notFound().build());
    }
    @GetMapping("/intents")
    public ResponseEntity<List<IntentConfig>> listIntents() {
        return ResponseEntity.ok(intentRepo.findAll());
    }

    @PostMapping("/intents")
    public ResponseEntity<IntentConfig> createIntent(@RequestBody IntentConfig intent) {
        return ResponseEntity.ok(intentRepo.save(intent));
    }

    @PatchMapping("/intents/{id}")
    public ResponseEntity<IntentConfig> updateIntent(@PathVariable Integer id, @RequestBody IntentConfig intent) {
        return intentRepo.findById(id)
                .map(existing -> {
                    existing.setKeywordsJson(intent.getKeywordsJson());
                    existing.setInstructions(intent.getInstructions());
                    existing.setPriority(intent.getPriority());
                    existing.setEnabled(intent.getEnabled());
                    existing.setRequiredSlotsJson(intent.getRequiredSlotsJson());
                    return ResponseEntity.ok(intentRepo.save(existing));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // Feedback
    @GetMapping("/feedback")
    public ResponseEntity<List<Feedback>> listFeedback(@RequestParam(required = false) String status) {
        if (status != null) {
            return ResponseEntity.ok(feedbackRepo.findByStatusOrderByCreatedAtDesc(status));
        }
        return ResponseEntity.ok(feedbackRepo.findAll());
    }

    // Overrides
    @GetMapping("/overrides")
    public ResponseEntity<List<OverrideMeta>> listOverrides() {
        return ResponseEntity.ok(overrideRepo.findAll());
    }

    @PatchMapping("/overrides/{id}")
    public ResponseEntity<OverrideMeta> updateOverride(@PathVariable Long id, @RequestBody Map<String, Boolean> body) {
        return overrideRepo.findById(id)
                .map(existing -> {
                    Boolean active = body.get("active");
                    if (active != null) {
                        existing.setActive(active);
                    }

                    OverrideMeta saved = overrideRepo.save(existing);

                    // keep Milvus in sync (delete + insert)
                    if (active != null) {
                        var embedding = ollamaClient.embed(saved.getQuestionText());
                        milvusService.updateOverrideActive(saved.getId(), active, embedding, saved.getChosenUrl());
                    }

                    return ResponseEntity.ok(saved);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/overrides/{id}")
    public ResponseEntity<Map<String, Object>> deleteOverride(@PathVariable Long id) {
        return overrideRepo.findById(id)
                .map(existing -> {
                    // 1. 从 Milvus 中删除向量
                    milvusService.deleteOverride(id);
                    
                    // 2. 从数据库中删除
                    overrideRepo.delete(existing);
                    
                    Map<String, Object> result = new HashMap<>();
                    result.put("success", true);
                    result.put("overrideId", id);
                    result.put("message", "Override deleted successfully");
                    return ResponseEntity.ok(result);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // 临时接口：为手动插入的数据生成向量（解决爬虫403问题）
    @PostMapping("/kb-reindex-manual")
    public ResponseEntity<Map<String, Object>> reindexManualData() {
        try {
            // 获取所有 chunks
            List<KbChunk> chunks = chunkRepo.findAll();
            
            if (chunks.isEmpty()) {
                return ResponseEntity.ok(Map.of("message", "No chunks found", "processed", 0));
            }

            List<MilvusVectorRow> vectorRows = new ArrayList<>();
            int batchSize = 32;
            int processed = 0;

            // 批量处理
            for (int i = 0; i < chunks.size(); i += batchSize) {
                List<KbChunk> batch = chunks.subList(i, Math.min(i + batchSize, chunks.size()));
                List<String> texts = batch.stream().map(KbChunk::getChunkText).toList();
                
                // 生成 embeddings
                List<List<Float>> embeddings = ollamaClient.embed(texts);
                
                // 构建 vector rows
                for (int j = 0; j < batch.size(); j++) {
                    KbChunk chunk = batch.get(j);
                    // 获取 source_id 通过 article
                    Long articleId = chunk.getArticleId();
                    // 这里简化处理，假设所有文章都属于 source 1
                    Integer sourceId = 1;
                    
                    vectorRows.add(new MilvusVectorRow(
                        chunk.getId(),
                        embeddings.get(j),
                        articleId,
                        sourceId
                    ));
                }
                processed += batch.size();
            }

            // 先删除旧的 Milvus 数据
            milvusService.deleteKbBySourceId(1);
            
            // 批量插入 Milvus
            if (!vectorRows.isEmpty()) {
                milvusService.insertKbChunks(vectorRows);
            }

            return ResponseEntity.ok(Map.of(
                "message", "Manual data reindexed successfully",
                "processed", processed,
                "vectorsInserted", vectorRows.size()
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                "error", e.getMessage(),
                "processed", 0
            ));
        }
    }

    public record SyncRequest(Integer sourceId, boolean rebuild) {}
    public record CreateArticleRequest(Integer sourceId, String title, String url, String bodyText) {}
}
