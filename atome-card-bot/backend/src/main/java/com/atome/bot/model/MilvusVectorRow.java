package com.atome.bot.model;

import java.util.List;

public class MilvusVectorRow {

    private Long chunkId;
    private List<Float> embedding;
    private Long articleId;
    private Integer sourceId;

    public MilvusVectorRow(Long chunkId, List<Float> embedding, Long articleId, Integer sourceId) {
        this.chunkId = chunkId;
        this.embedding = embedding;
        this.articleId = articleId;
        this.sourceId = sourceId;
    }

    public Long getChunkId() { return chunkId; }
    public List<Float> getEmbedding() { return embedding; }
    public Long getArticleId() { return articleId; }
    public Integer getSourceId() { return sourceId; }
}