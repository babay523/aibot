package com.atome.bot.service;

import com.atome.bot.entity.KbArticle;
import com.atome.bot.entity.KbChunk;
import com.atome.bot.entity.KbSource;
import com.atome.bot.model.MilvusVectorRow;
import com.atome.bot.repository.KbArticleRepository;
import com.atome.bot.repository.KbChunkRepository;
import com.atome.bot.repository.KbSourceRepository;
import com.atome.bot.client.OllamaClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class KbSyncService {

    private static final Logger log = LoggerFactory.getLogger(KbSyncService.class);

    private final ZendeskCrawlerService crawler;
    private final KbSourceRepository sourceRepo;
    private final KbArticleRepository articleRepo;
    private final KbChunkRepository chunkRepo;
    private final Chunker chunker;
    private final OllamaClient ollamaClient;
    private final MilvusService milvusService;

    @Value("${bot.sync.batch-size}")
    private int batchSize;

    public KbSyncService(ZendeskCrawlerService crawler,
                        KbSourceRepository sourceRepo,
                        KbArticleRepository articleRepo,
                        KbChunkRepository chunkRepo,
                        Chunker chunker,
                        OllamaClient ollamaClient,
                        MilvusService milvusService) {
        this.crawler = crawler;
        this.sourceRepo = sourceRepo;
        this.articleRepo = articleRepo;
        this.chunkRepo = chunkRepo;
        this.chunker = chunker;
        this.ollamaClient = ollamaClient;
        this.milvusService = milvusService;
    }

    @Transactional
    public SyncResult syncSource(Integer sourceId, boolean rebuild) {
        KbSource source = sourceRepo.findById(sourceId)
                .orElseThrow(() -> new RuntimeException("Source not found: " + sourceId));

        log.info("Starting KB sync for source {}: {}", sourceId, source.getUrl());

        long startTime = System.currentTimeMillis();

        // 1. 删除旧数据（如果 rebuild）
        if (rebuild) {
            log.info("Rebuilding: deleting old data for source {}", sourceId);
            milvusService.deleteKbBySourceId(sourceId);
            chunkRepo.deleteBySourceId(sourceId);
            articleRepo.deleteBySourceId(sourceId);
        }

        // 2. 抓取文章
        Set<String> articleUrls;
        try {
            articleUrls = crawler.extractArticleUrls(source.getUrl());
        } catch (IOException e) {
            throw new RuntimeException("Failed to crawl category page: " + source.getUrl(), e);
        }

        log.info("Found {} article URLs", articleUrls.size());

        // 3. 抓取每篇文章并处理
        int articleCount = 0;
        int chunkCount = 0;
        List<KbChunk> allChunks = new ArrayList<>();

        for (String url : articleUrls) {
            try {
                var content = crawler.fetchArticle(url);
                
                // 计算 hash
                String hash = computeHash(content.bodyText());
                
                // 保存文章
                KbArticle article = new KbArticle();
                article.setSourceId(sourceId);
                article.setTitle(content.title());
                article.setUrl(content.url());
                article.setBodyText(content.bodyText());
                article.setHash(hash);
                article.setCreatedAt(LocalDateTime.now());
                article.setUpdatedAt(LocalDateTime.now());
                article = articleRepo.save(article);
                articleCount++;

                // 切分 chunk
                List<String> chunks = chunker.chunk(content.bodyText());
                for (int i = 0; i < chunks.size(); i++) {
                    KbChunk chunk = new KbChunk();
                    chunk.setArticleId(article.getId());
                    chunk.setChunkNo(i);
                    chunk.setChunkText(chunks.get(i));
                    chunk.setCreatedAt(LocalDateTime.now());
                    chunk = chunkRepo.save(chunk);
                    allChunks.add(chunk);
                    chunkCount++;
                }

                // 每批处理一次 embedding
                if (allChunks.size() >= batchSize) {
                    processBatch(allChunks, sourceId);
                    allChunks.clear();
                }

                log.debug("Processed article: {}", content.title());

            } catch (IOException e) {
                log.warn("Failed to fetch article: {}", url, e);
            }
        }

        // 处理剩余 chunks
        if (!allChunks.isEmpty()) {
            processBatch(allChunks, sourceId);
        }

        long duration = System.currentTimeMillis() - startTime;
        log.info("KB sync completed: {} articles, {} chunks in {}ms", articleCount, chunkCount, duration);

        return new SyncResult(articleCount, chunkCount, duration);
    }

    private void processBatch(List<KbChunk> chunks, Integer sourceId) {
        // 批量获取 embedding
        List<String> texts = chunks.stream()
                .map(KbChunk::getChunkText)
                .toList();

        List<List<Float>> embeddings = ollamaClient.embed(texts);

        // 构建 Milvus 向量行
        List<MilvusVectorRow> rows = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            KbChunk chunk = chunks.get(i);
            List<Float> embedding = embeddings.get(i);
            rows.add(new MilvusVectorRow(
                    chunk.getId(),
                    embedding,
                    chunk.getArticleId(),
                    sourceId
            ));
        }

        // 批量写入 Milvus
        milvusService.insertKbChunks(rows);
        log.debug("Inserted {} chunks to Milvus", rows.size());
    }

    private String computeHash(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(text.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return String.valueOf(text.hashCode());
        }
    }

    public record SyncResult(int articleCount, int chunkCount, long durationMs) {}
}