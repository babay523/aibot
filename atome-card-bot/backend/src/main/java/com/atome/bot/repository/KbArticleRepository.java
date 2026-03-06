package com.atome.bot.repository;

import com.atome.bot.entity.KbArticle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface KbArticleRepository extends JpaRepository<KbArticle, Long> {

    List<KbArticle> findBySourceId(Integer sourceId);

    @Modifying
    @Query("DELETE FROM KbArticle a WHERE a.sourceId = ?1")
    void deleteBySourceId(Integer sourceId);
}