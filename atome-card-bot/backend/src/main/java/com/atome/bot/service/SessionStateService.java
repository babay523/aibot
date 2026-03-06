package com.atome.bot.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
public class SessionStateService {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public SessionStateService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 获取会话状态
     */
    public SessionState getState(String sessionId) {
        String sql = "SELECT pending_intent, slots_json FROM session_state WHERE session_id = ?";
        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            String pendingIntent = rs.getString("pending_intent");
            String slotsJson = rs.getString("slots_json");
            Map<String, String> slots = parseSlots(slotsJson);
            return new SessionState(sessionId, pendingIntent, slots);
        }, sessionId).stream().findFirst().orElse(new SessionState(sessionId, null, new HashMap<>()));
    }

    /**
     * 保存会话状态
     */
    public void saveState(SessionState state) {
        String sql = """
                INSERT INTO session_state (session_id, pending_intent, slots_json, updated_at)
                VALUES (?, ?, ?::jsonb, ?)
                ON CONFLICT (session_id) DO UPDATE SET
                    pending_intent = EXCLUDED.pending_intent,
                    slots_json = EXCLUDED.slots_json,
                    updated_at = EXCLUDED.updated_at
                """;
        jdbcTemplate.update(sql,
                state.sessionId(),
                state.pendingIntent(),
                toJson(state.slots()),
                LocalDateTime.now()
        );
    }

    /**
     * 清除会话状态
     */
    public void clearState(String sessionId) {
        jdbcTemplate.update("DELETE FROM session_state WHERE session_id = ?", sessionId);
    }

    private Map<String, String> parseSlots(String json) {
        if (json == null || json.isEmpty()) return new HashMap<>();
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, String>>() {});
        } catch (Exception e) {
            return new HashMap<>();
        }
    }

    private String toJson(Map<String, String> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            return "{}";
        }
    }

    public record SessionState(String sessionId, String pendingIntent, Map<String, String> slots) {
        public boolean hasPendingIntent() {
            return pendingIntent != null && !pendingIntent.isEmpty();
        }

        public boolean isSlotComplete(String slotName) {
            return slots.containsKey(slotName) && slots.get(slotName) != null && !slots.get(slotName).isEmpty();
        }

        public void collectSlot(String slotName, String value) {
            slots.put(slotName, value);
        }

        public String getSlotValue(String slotName) {
            return slots.getOrDefault(slotName, "");
        }
    }
}