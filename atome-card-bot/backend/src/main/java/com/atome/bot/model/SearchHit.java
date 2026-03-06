package com.atome.bot.model;

public class SearchHit {

    private Long articleId;
    private Long chunkId;
    private float score;
    private Integer sourceId;

    public SearchHit(Long chunkId, Long articleId, float score, Integer sourceId) {
        this.chunkId = chunkId;
        this.articleId = articleId;
        this.score = score;
        this.sourceId = sourceId;
    }

    public Long getArticleId() { return articleId; }
    public Long getChunkId() { return chunkId; }
    public float getScore() { return score; }
    public Integer getSourceId() { return sourceId; }
}