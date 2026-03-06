package com.atome.bot.controller;

import com.atome.bot.client.OllamaClient;
import com.atome.bot.entity.*;
import com.atome.bot.model.MilvusVectorRow;
import com.atome.bot.service.KbSyncService;
import com.atome.bot.service.MilvusService;
import com.atome.bot.repository.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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

    public record SyncRequest(Integer sourceId, boolean rebuild) {}

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
}
