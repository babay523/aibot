package com.atome.bot.service;

import com.atome.bot.entity.IntentConfig;
import com.atome.bot.repository.IntentConfigRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

@Service
public class IntentService {

    private static final Logger log = LoggerFactory.getLogger(IntentService.class);

    private final IntentConfigRepository intentRepo;
    private final ObjectMapper objectMapper;

    public IntentService(IntentConfigRepository intentRepo, ObjectMapper objectMapper) {
        this.intentRepo = intentRepo;
        this.objectMapper = objectMapper;
    }

    /**
     * 匹配意图
     * 按 priority 升序扫描，keywords 支持 contains 匹配和正则匹配
     */
    public IntentMatch match(String message) {
        if (message == null || message.isEmpty()) {
            return null;
        }

        String lowerMessage = message.toLowerCase();
        List<IntentConfig> intents = intentRepo.findByEnabledTrueOrderByPriorityAsc();

        for (IntentConfig intent : intents) {
            List<String> keywords = parseKeywords(intent.getKeywordsJson());
            for (String keyword : keywords) {
                String lowerKeyword = keyword.toLowerCase();
                boolean matched = false;
                
                // 如果关键词包含特殊字符，按正则表达式匹配
                if (lowerKeyword.contains("*") || lowerKeyword.contains("?") || lowerKeyword.contains(".")) {
                    try {
                        Pattern pattern = Pattern.compile(lowerKeyword, Pattern.CASE_INSENSITIVE);
                        matched = pattern.matcher(message).find();
                    } catch (Exception e) {
                        // 正则表达式无效，回退到 contains 匹配
                        matched = lowerMessage.contains(lowerKeyword);
                    }
                } else {
                    // 普通 contains 匹配
                    matched = lowerMessage.contains(lowerKeyword);
                }
                
                if (matched) {
                    log.debug("Intent matched: {} with keyword: {}", intent.getName(), keyword);
                    return new IntentMatch(
                            intent.getId(),
                            intent.getName(),
                            intent.getHandler(),
                            intent.getInstructions(),
                            parseSlots(intent.getRequiredSlotsJson())
                    );
                }
            }
        }

        return null;
    }

    public IntentMatch getIntentByName(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }

        return intentRepo.findByName(name)
                .filter(IntentConfig::getEnabled)
                .map(intent -> new IntentMatch(
                        intent.getId(),
                        intent.getName(),
                        intent.getHandler(),
                        intent.getInstructions(),
                        parseSlots(intent.getRequiredSlotsJson())
                ))
                .orElse(null);
    }

    private List<String> parseKeywords(String json) {
        if (json == null || json.isEmpty()) return Collections.emptyList();
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.warn("Failed to parse keywords json: {}", json);
            return Collections.emptyList();
        }
    }

    private List<String> parseSlots(String json) {
        if (json == null || json.isEmpty()) return Collections.emptyList();
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    public record IntentMatch(
            Integer intentId,
            String name,
            String handler,
            String instructions,
            List<String> requiredSlots
    ) {}
}
