package com.atome.bot.repository;

import com.atome.bot.entity.IntentConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface IntentConfigRepository extends JpaRepository<IntentConfig, Integer> {
    List<IntentConfig> findByEnabledTrueOrderByPriorityAsc();

    Optional<IntentConfig> findByName(String name);
}
