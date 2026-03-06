package com.atome.bot.config;

import com.atome.bot.client.OllamaClient;
import com.atome.bot.service.MilvusService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

@Configuration
public class InitializationConfig {

    private static final Logger log = LoggerFactory.getLogger(InitializationConfig.class);

    @Bean
    @Order(1)  // 高优先级，先执行Milvus初始化
    public ApplicationRunner initEmbeddingDimension(OllamaClient ollamaClient, MilvusService milvusService) {
        return args -> {
            log.info("Probing embedding dimension from Ollama...");
            
            try {
                int dim = ollamaClient.probeDim();
                log.info("Detected embedding dimension: {}", dim);
                
                // 设置维度并创建/校验 Milvus collections
                milvusService.setDimension(dim);
                milvusService.ensureCollections(dim);
                
                log.info("Milvus collections initialized successfully");
            } catch (Exception e) {
                log.error("Failed to initialize embedding dimension: {}", e.getMessage(), e);
                throw new RuntimeException("Application startup failed - cannot connect to Ollama or Milvus", e);
            }
        };
    }
}