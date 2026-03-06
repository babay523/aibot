package com.atome.bot.repository;

import com.atome.bot.entity.OverrideMeta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OverrideMetaRepository extends JpaRepository<OverrideMeta, Long> {

    List<OverrideMeta> findByActiveTrue();

    @Modifying
    @Query("UPDATE OverrideMeta o SET o.active = ?2 WHERE o.id = ?1")
    void updateActive(Long id, Boolean active);
}