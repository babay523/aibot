package com.atome.bot.repository;

import com.atome.bot.entity.KbSource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface KbSourceRepository extends JpaRepository<KbSource, Integer> {
    List<KbSource> findByEnabledTrue();
}