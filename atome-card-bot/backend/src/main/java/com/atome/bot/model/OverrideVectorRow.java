package com.atome.bot.model;

import java.util.List;

public class OverrideVectorRow {

    private Long overrideId;
    private List<Float> embedding;
    private Boolean active;
    private String chosenUrl;

    public OverrideVectorRow(Long overrideId, List<Float> embedding, Boolean active, String chosenUrl) {
        this.overrideId = overrideId;
        this.embedding = embedding;
        this.active = active;
        this.chosenUrl = chosenUrl;
    }

    public Long getOverrideId() { return overrideId; }
    public List<Float> getEmbedding() { return embedding; }
    public Boolean getActive() { return active; }
    public String getChosenUrl() { return chosenUrl; }
}