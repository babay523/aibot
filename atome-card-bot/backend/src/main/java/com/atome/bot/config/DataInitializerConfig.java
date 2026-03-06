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
            initKbSource(sourceRepo);

            // 2. 初始化知识库文章（如果不存在）
            boolean articlesCreated = initKbArticles(articleRepo);

            // 3. 初始化知识库分块（如果不存在）
            boolean chunksCreated = initKbChunks(chunkRepo, articlesCreated);

            // 4. 初始化向量（如果 chunks 存在但向量缺失）
            if (chunksCreated || needVectorRegeneration(chunkRepo, ollamaClient, milvusService)) {
                generateAndInsertVectors(chunkRepo, ollamaClient, milvusService);
            }

            // 5. 初始化意图配置（如果不存在）
            initIntentConfigs(intentRepo);

            System.out.println("========================================");
            System.out.println("✅ 数据初始化完成！");
            System.out.println("========================================");
        };
    }

    private void initKbSource(KbSourceRepository sourceRepo) {
        if (sourceRepo.count() == 0) {
            System.out.println("🔄 初始化默认知识库源...");
            KbSource source = new KbSource();
            source.setName("Atome Card");
            source.setUrl("https://help.atome.ph/hc/en-gb/categories/4439682039065-Atome-Card");
            source.setEnabled(true);
            source.setCreatedAt(LocalDateTime.now());
            source.setUpdatedAt(LocalDateTime.now());
            sourceRepo.save(source);
            System.out.println("✅ 知识库源已创建: Atome Card");
        } else {
            System.out.println("✅ 知识库源已存在，跳过初始化");
        }
    }

    private boolean initKbArticles(KbArticleRepository articleRepo) {
        if (articleRepo.count() == 0) {
            System.out.println("🔄 初始化默认知识库文章...");
            
            KbArticle article1 = new KbArticle();
            article1.setSourceId(1);
            article1.setTitle("What is Atome card?");
            article1.setUrl("https://help.atome.ph/hc/en-gb/articles/8712898781593");
            article1.setBodyText("Atome Card is a credit card that allows you to pay in 3 or 6 monthly installments. It offers both physical and virtual card options for your convenience.");
            article1.setHash("hash1");
            article1.setCreatedAt(LocalDateTime.now());
            article1.setUpdatedAt(LocalDateTime.now());
            articleRepo.save(article1);

            KbArticle article2 = new KbArticle();
            article2.setSourceId(1);
            article2.setTitle("How can I check the status of my application?");
            article2.setUrl("https://help.atome.ph/hc/en-gb/articles/8712978836377");
            article2.setBodyText("You can check your application status by logging into the Atome app and navigating to the Card section. The status will show as Pending, Approved, or Rejected.");
            article2.setHash("hash2");
            article2.setCreatedAt(LocalDateTime.now());
            article2.setUpdatedAt(LocalDateTime.now());
            articleRepo.save(article2);

            KbArticle article3 = new KbArticle();
            article3.setSourceId(1);
            article3.setTitle("Why did my Card application fail?");
            article3.setUrl("https://help.atome.ph/hc/en-gb/articles/43466854486681");
            article3.setBodyText("Your application may fail due to incomplete documentation, insufficient credit history, or not meeting eligibility criteria. Please ensure all required documents are submitted.");
            article3.setHash("hash3");
            article3.setCreatedAt(LocalDateTime.now());
            article3.setUpdatedAt(LocalDateTime.now());
            articleRepo.save(article3);

            KbArticle article4 = new KbArticle();
            article4.setSourceId(1);
            article4.setTitle("Where can I use my Atome card?");
            article4.setUrl("https://help.atome.ph/hc/en-gb/articles/44697622305049");
            article4.setBodyText("You can use your Atome card at any merchant that accepts Visa. This includes online stores, physical retail locations, and ATMs worldwide.");
            article4.setHash("hash4");
            article4.setCreatedAt(LocalDateTime.now());
            article4.setUpdatedAt(LocalDateTime.now());
            articleRepo.save(article4);

            KbArticle article5 = new KbArticle();
            article5.setSourceId(1);
            article5.setTitle("Why did my card transaction fail?");
            article5.setUrl("https://help.atome.ph/hc/en-gb/articles/43466116960153");
            article5.setBodyText("Transaction failures can occur due to insufficient balance, incorrect card details, merchant restrictions, or security blocks. Please check your available spending limit and try again.");
            article5.setHash("hash5");
            article5.setCreatedAt(LocalDateTime.now());
            article5.setUpdatedAt(LocalDateTime.now());
            articleRepo.save(article5);
            
            System.out.println("✅ 5篇默认文章已创建");
            return true;
        } else {
            System.out.println("✅ 知识库文章已存在，跳过初始化");
            return false;
        }
    }

    private boolean initKbChunks(KbChunkRepository chunkRepo, boolean articlesCreated) {
        if (articlesCreated || chunkRepo.count() == 0) {
            System.out.println("🔄 初始化知识库分块...");
            
            KbChunk chunk1 = new KbChunk();
            chunk1.setArticleId(1L);
            chunk1.setChunkNo(0);
            chunk1.setChunkText("Atome Card is a credit card that allows you to pay in 3 or 6 monthly installments. It offers both physical and virtual card options for your convenience.");
            chunk1.setCreatedAt(LocalDateTime.now());
            chunkRepo.save(chunk1);

            KbChunk chunk2 = new KbChunk();
            chunk2.setArticleId(2L);
            chunk2.setChunkNo(0);
            chunk2.setChunkText("You can check your application status by logging into the Atome app and navigating to the Card section. The status will show as Pending, Approved, or Rejected.");
            chunk2.setCreatedAt(LocalDateTime.now());
            chunkRepo.save(chunk2);

            KbChunk chunk3 = new KbChunk();
            chunk3.setArticleId(3L);
            chunk3.setChunkNo(0);
            chunk3.setChunkText("Your application may fail due to incomplete documentation, insufficient credit history, or not meeting eligibility criteria. Please ensure all required documents are submitted.");
            chunk3.setCreatedAt(LocalDateTime.now());
            chunkRepo.save(chunk3);

            KbChunk chunk4 = new KbChunk();
            chunk4.setArticleId(4L);
            chunk4.setChunkNo(0);
            chunk4.setChunkText("You can use your Atome card at any merchant that accepts Visa. This includes online stores, physical retail locations, and ATMs worldwide.");
            chunk4.setCreatedAt(LocalDateTime.now());
            chunkRepo.save(chunk4);

            KbChunk chunk5 = new KbChunk();
            chunk5.setArticleId(5L);
            chunk5.setChunkNo(0);
            chunk5.setChunkText("Transaction failures can occur due to insufficient balance, incorrect card details, merchant restrictions, or security blocks. Please check your available spending limit and try again.");
            chunk5.setCreatedAt(LocalDateTime.now());
            chunkRepo.save(chunk5);
            
            System.out.println("✅ 5个分块已创建");
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
                                         MilvusService milvusService) {
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
                    1  // source_id
                ));
            }

            // 清除旧向量并插入新向量
            milvusService.deleteKbBySourceId(1);
            milvusService.insertKbChunks(vectorRows);
            System.out.println("✅ " + vectorRows.size() + " 个向量已成功插入 Milvus");
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
            intent1.setKeywordsJson("[\"申请进度\", \"申请状态\", \"办卡进度\", \"application status\"]");
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
