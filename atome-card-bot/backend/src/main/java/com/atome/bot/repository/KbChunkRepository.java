package com.atome.bot.repository;

import com.atome.bot.entity.KbChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface KbChunkRepository extends JpaRepository<KbChunk, Long> {

    @Query("SELECT c FROM KbChunk c WHERE c.articleId IN (SELECT a.id FROM KbArticle a WHERE a.sourceId = ?1)")
    List<KbChunk> findBySourceId(Integer sourceId);

    @Modifying
    @Query("DELETE FROM KbChunk c WHERE c.articleId IN (SELECT a.id FROM KbArticle a WHERE a.sourceId = ?1)")
    void deleteBySourceId(Integer sourceId);

    List<KbChunk> findByArticleId(Long articleId);
}