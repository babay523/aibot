package com.atome.bot.config;

import com.atome.bot.client.OllamaClient;
import com.atome.bot.entity.*;
import com.atome.bot.repository.*;
import com.atome.bot.service.MilvusService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Configuration
public class DataInitializerConfig {

    @Value("${milvus.collection.kb-chunks:kb_chunks}")
    private String kbCollectionName;

    @Bean
    @Order(2)  // 在 Milvus 集合初始化之后执行
    public ApplicationRunner initializeDefaultData(
            KbSourceRepository sourceRepo,
            KbArticleRepository articleRepo,
            KbChunkRepository chunkRepo,
            IntentConfigRepository intentRepo,
            OllamaClient ollamaClient,
            MilvusService milvusService) {
        return args -> {
            System.out.println("========================================");
            System.out.println("🔄 开始数据初始化...");
            System.out.println("========================================");

            // 1. 初始化知识库源（如果不存在）
            Integer sourceId = initKbSource(sourceRepo);

            // 2. 初始化知识库文章（如果不存在）
            List<Long> articleIds = initKbArticles(articleRepo, sourceId);

            // 3. 初始化知识库分块（如果不存在）
            boolean chunksCreated = initKbChunks(chunkRepo, articleIds);

            // 4. 初始化向量（如果 chunks 存在但向量缺失）
            if (chunksCreated || needVectorRegeneration(chunkRepo, ollamaClient, milvusService)) {
                generateAndInsertVectors(chunkRepo, ollamaClient, milvusService, sourceId);
            }

            // 5. 初始化意图配置（如果不存在）
            initIntentConfigs(intentRepo);

            System.out.println("========================================");
            System.out.println("✅ 数据初始化完成！");
            System.out.println("========================================");
        };
    }

    private Integer initKbSource(KbSourceRepository sourceRepo) {
        if (sourceRepo.count() == 0) {
            System.out.println("🔄 初始化默认知识库源...");
            KbSource source = new KbSource();
            source.setName("Atome Card");
            source.setUrl("https://help.atome.ph/hc/en-gb/categories/4439682039065-Atome-Card");
            source.setEnabled(true);
            source.setCreatedAt(LocalDateTime.now());
            source.setUpdatedAt(LocalDateTime.now());
            source = sourceRepo.save(source);
            System.out.println("✅ 知识库源已创建: Atome Card (ID: " + source.getId() + ")");
            return source.getId();
        } else {
            System.out.println("✅ 知识库源已存在，跳过初始化");
            return sourceRepo.findAll().get(0).getId();
        }
    }

    private List<Long> initKbArticles(KbArticleRepository articleRepo, Integer sourceId) {
        List<Long> articleIds = new ArrayList<>();
        
        if (articleRepo.count() == 0) {
            System.out.println("🔄 初始化默认知识库文章...");
            
            String[] titles = {
                "What is Atome card?",
                "How can I check the status of my application?",
                "Why did my Card application fail?",
                "Where can I use my Atome card?",
                "Why did my card transaction fail?"
            };
            
            String[] urls = {
                "https://help.atome.ph/hc/en-gb/articles/8712898781593",
                "https://help.atome.ph/hc/en-gb/articles/8712978836377",
                "https://help.atome.ph/hc/en-gb/articles/43466854486681",
                "https://help.atome.ph/hc/en-gb/articles/44697622305049",
                "https://help.atome.ph/hc/en-gb/articles/43466116960153"
            };
            
            String[] bodyTexts = {
                "Atome Card is a credit card that allows you to pay in 3 or 6 monthly installments. It offers both physical and virtual card options for your convenience.",
                "You can check your application status by logging into the Atome app and navigating to the Card section. The status will show as Pending, Approved, or Rejected.",
                "Your application may fail due to incomplete documentation, insufficient credit history, or not meeting eligibility criteria. Please ensure all required documents are submitted.",
                "You can use your Atome card at any merchant that accepts Visa. This includes online stores, physical retail locations, and ATMs worldwide.",
                "Transaction failures can occur due to insufficient balance, incorrect card details, merchant restrictions, or security blocks. Please check your available spending limit and try again."
            };
            
            for (int i = 0; i < titles.length; i++) {
                KbArticle article = new KbArticle();
                article.setSourceId(sourceId);
                article.setTitle(titles[i]);
                article.setUrl(urls[i]);
                article.setBodyText(bodyTexts[i]);
                article.setHash("hash" + (i + 1));
                article.setCreatedAt(LocalDateTime.now());
                article.setUpdatedAt(LocalDateTime.now());
                article = articleRepo.save(article);
                articleIds.add(article.getId());
            }
            
            System.out.println("✅ " + articleIds.size() + "篇默认文章已创建");
        } else {
            System.out.println("✅ 知识库文章已存在，跳过初始化");
            articleRepo.findAll().forEach(a -> articleIds.add(a.getId()));
        }
        
        return articleIds;
    }

    private boolean initKbChunks(KbChunkRepository chunkRepo, List<Long> articleIds) {
        if (articleIds.isEmpty()) {
            return false;
        }
        
        if (chunkRepo.count() == 0) {
            System.out.println("🔄 初始化知识库分块...");
            
            String[] chunkTexts = {
                "Atome Card is a credit card that allows you to pay in 3 or 6 monthly installments. It offers both physical and virtual card options for your convenience.",
                "How can I check the status of my application? You can check your application status by logging into the Atome app and navigating to the Card section. The status will show as Pending, Approved, or Rejected.",
                "Your application may fail due to incomplete documentation, insufficient credit history, or not meeting eligibility criteria. Please ensure all required documents are submitted.",
                "You can use your Atome card at any merchant that accepts Visa. This includes online stores, physical retail locations, and ATMs worldwide.",
                "Transaction failures can occur due to insufficient balance, incorrect card details, merchant restrictions, or security blocks. Please check your available spending limit and try again."
            };
            
            for (int i = 0; i < articleIds.size() && i < chunkTexts.length; i++) {
                KbChunk chunk = new KbChunk();
                chunk.setArticleId(articleIds.get(i));
                chunk.setChunkNo(0);
                chunk.setChunkText(chunkTexts[i]);
                chunk.setCreatedAt(LocalDateTime.now());
                chunkRepo.save(chunk);
            }
            
            System.out.println("✅ " + Math.min(articleIds.size(), chunkTexts.length) + "个分块已创建");
            return true;
        } else {
            System.out.println("✅ 知识库分块已存在，跳过初始化");
            return false;
        }
    }

    private boolean needVectorRegeneration(KbChunkRepository chunkRepo, 
                                          OllamaClient ollamaClient,
                                          MilvusService milvusService) {
        long chunkCount = chunkRepo.count();
        if (chunkCount == 0) {
            return false; // 没有 chunks，不需要生成向量
        }

        // 尝试查询 Milvus 查看是否有向量
        try {
            // 使用第一个 chunk 的文本作为探测查询
            var firstChunk = chunkRepo.findAll().get(0);
            var queryText = firstChunk.getChunkText();
            var queryVec = ollamaClient.embed(queryText);
            var hits = milvusService.searchKb(queryVec, 1);
            
            if (hits.isEmpty()) {
                System.out.println("⚠️ Milvus 中未找到向量，需要重新生成");
                return true;
            } else {
                System.out.println("✅ Milvus 向量已存在，跳过生成");
                return false;
            }
        } catch (Exception e) {
            System.out.println("⚠️ 检查 Milvus 向量时出错: " + e.getMessage());
            return true; // 出错时默认需要重新生成
        }
    }

    private void generateAndInsertVectors(KbChunkRepository chunkRepo, 
                                         OllamaClient ollamaClient, 
                                         MilvusService milvusService,
                                         Integer sourceId) {
        System.out.println("🔄 生成向量嵌入...");
        try {
            var chunks = chunkRepo.findAll();
            if (chunks.isEmpty()) {
                System.out.println("⚠️ 没有分块数据，跳过向量生成");
                return;
            }

            var vectorRows = new java.util.ArrayList<com.atome.bot.model.MilvusVectorRow>();
            
            // 批量生成 embeddings
            var texts = chunks.stream().map(KbChunk::getChunkText).toList();
            System.out.println("  正在生成 " + texts.size() + " 个向量...");
            var embeddings = ollamaClient.embed(texts);
            
            for (int j = 0; j < chunks.size(); j++) {
                var chunk = chunks.get(j);
                vectorRows.add(new com.atome.bot.model.MilvusVectorRow(
                    chunk.getId(),
                    embeddings.get(j),
                    chunk.getArticleId(),
                    sourceId
                ));
            }

            // 插入新向量
            milvusService.insertKbChunks(vectorRows);
            System.out.println("✅ " + vectorRows.size() + " 个向量已成功插入 Milvus");
            
            // 创建索引并加载集合
            milvusService.createIndexAndLoadCollection();
            
        } catch (Exception e) {
            System.err.println("⚠️ 向量生成失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void initIntentConfigs(IntentConfigRepository intentRepo) {
        if (intentRepo.count() == 0) {
            System.out.println("🔄 初始化默认意图配置...");
            
            IntentConfig intent1 = new IntentConfig();
            intent1.setName("CARD_APPLICATION_STATUS");
            intent1.setPriority(10);
            intent1.setKeywordsJson("[\"申请进度\", \"申请状态\", \"办卡进度\", \"status\", \"check.*status\", \"application.*status\", \"check.*application\"]");
            intent1.setInstructions("询问卡片申请进度时，需要用户提供申请标识（手机号或申请编号）");
            intent1.setHandler("cardApplicationHandler");
            intent1.setRequiredSlotsJson("[\"application_id\"]");
            intent1.setEnabled(true);
            intent1.setCreatedAt(LocalDateTime.now());
            intent1.setUpdatedAt(LocalDateTime.now());
            intentRepo.save(intent1);

            IntentConfig intent2 = new IntentConfig();
            intent2.setName("FAILED_TRANSACTION");
            intent2.setPriority(20);
            intent2.setKeywordsJson("[\"交易失败\", \"失败交易\", \"transaction failed\", \"declined\"]");
            intent2.setInstructions("询问失败交易时，要求用户提供交易ID");
            intent2.setHandler("failedTransactionHandler");
            intent2.setRequiredSlotsJson("[\"transaction_id\"]");
            intent2.setEnabled(true);
            intent2.setCreatedAt(LocalDateTime.now());
            intent2.setUpdatedAt(LocalDateTime.now());
            intentRepo.save(intent2);
            
            System.out.println("✅ 2个默认意图已创建: CARD_APPLICATION_STATUS, FAILED_TRANSACTION");
        } else {
            System.out.println("✅ 意图配置已存在，跳过初始化");
        }
    }
}
