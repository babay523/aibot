package com.atome.bot.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.List;
import java.util.Map;

@Component
public class DeepSeekClient {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final String model;
    private final boolean isValidApiKey;

    public DeepSeekClient(
            @Value("${deepseek.api-key:}") String apiKey,
            @Value("${deepseek.base-url}") String baseUrl,
            @Value("${deepseek.model}") String model,
            ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.model = model;
        
        // 检查 API key 是否有效
        this.isValidApiKey = apiKey != null && !apiKey.isEmpty() 
                           && !apiKey.equals("sk-demo-key-for-testing")
                           && !apiKey.equals("sk-YOUR_NEW_KEY_HERE");
        
        if (isValidApiKey) {
            this.webClient = WebClient.builder()
                    .baseUrl(baseUrl)
                    .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .build();
        } else {
            System.out.println("⚠️ DeepSeek API Key 未配置或无效，将使用本地兜底回复");
            this.webClient = null;
        }
    }

    /**
     * 生成兜底回复或追问话术
     * 不用于路由决策，只用于自然语言生成
     */
    public String generateFallback(String userMessage, String context) {
        // 如果没有有效的 API key，返回本地模拟回复
        if (!isValidApiKey) {
            return getLocalFallbackResponse(userMessage);
        }

        try {
            String prompt = """
                    You are a customer service assistant for Atome Card.
                    Context: %s
                    
                    The user asked: %s
                    
                    I could not find a specific answer in our knowledge base.
                    Please provide a helpful response that:
                    1. Acknowledges the question
                    2. Suggests they might rephrase or check our help center
                    3. Offers to connect with human support if needed
                    
                    Keep the response friendly, concise, and in the same language as the user's question.
                    """.formatted(context, userMessage);

            return chat(List.of(
                    Map.of("role", "system", "content", "你是Atome Card智能客服助手。请始终使用中文回答用户问题，语气友好专业。"),
                    Map.of("role", "user", "content", prompt)
            ), 0.7);
        } catch (Exception e) {
            System.err.println("⚠️ DeepSeek API 调用失败，使用本地兜底回复: " + e.getMessage());
            return getLocalFallbackResponse(userMessage);
        }
    }

    /**
     * 润色追问话术（补充缺失 slot 时）
     */
    public String generateFollowUp(String instruction, String missingSlotName) {
        // 如果没有有效的 API key，返回本地模拟追问
        if (!isValidApiKey) {
            return getLocalFollowUpResponse(instruction, missingSlotName);
        }

        try {
            String prompt = """
                    Instruction: %s
                    We need to ask the user for: %s
                    
                    Generate a polite follow-up message asking for this information.
                    Keep it concise and natural.
                    """.formatted(instruction, missingSlotName);

            return chat(List.of(
                    Map.of("role", "system", "content", "你是Atome Card智能客服助手。请始终使用中文回答，简洁友好。"),
                    Map.of("role", "user", "content", prompt)
            ), 0.5);
        } catch (Exception e) {
            System.err.println("⚠️ DeepSeek API 调用失败，使用本地追问回复: " + e.getMessage());
            return getLocalFollowUpResponse(instruction, missingSlotName);
        }
    }

    /**
     * 本地兜底回复（当 DeepSeek API 不可用时）
     */
    private String getLocalFallbackResponse(String userMessage) {
        // 简单的关键词判断返回不同回复
        String lowerMsg = userMessage.toLowerCase();
        
        if (lowerMsg.contains("申请") || lowerMsg.contains("进度") || lowerMsg.contains("status")) {
            return "您想了解申请进度，请提供您的申请编号或注册手机号，我可以帮您查询。";
        } else if (lowerMsg.contains("交易") || lowerMsg.contains("失败") || lowerMsg.contains("transaction")) {
            return "您遇到了交易问题，请提供交易ID，我可以帮您查询失败原因。";
        } else if (lowerMsg.contains("卡") || lowerMsg.contains("card") || lowerMsg.contains("what")) {
            return "Atome Card 是一款支持3期或6期分期付款的信用卡。您可以在任何接受Visa的商户使用。需要了解更多详情吗？";
        } else {
            return "抱歉，我暂时没有找到与您问题相关的答案。建议您：\n1. 换一种方式描述您的问题\n2. 访问我们的帮助中心: https://help.atome.ph\n3. 联系人工客服获取帮助";
        }
    }

    /**
     * 本地追问回复（当 DeepSeek API 不可用时）
     */
    private String getLocalFollowUpResponse(String instruction, String missingSlotName) {
        if (missingSlotName.contains("application") || missingSlotName.contains("申请")) {
            return "为了查询您的申请进度，请提供您的申请编号或注册手机号。";
        } else if (missingSlotName.contains("transaction") || missingSlotName.contains("交易")) {
            return "请提供您的交易ID（通常可以在交易记录或短信通知中找到）。";
        } else {
            return "请提供您的 " + missingSlotName.replace("_", " ") + "，以便我为您处理。";
        }
    }

    private String chat(List<Map<String, String>> messages, double temperature) {
        String requestBody = """
                {
                    "model": "%s",
                    "messages": %s,
                    "temperature": %s,
                    "max_tokens": 500
                }
                """.formatted(model, toMessagesJson(messages), temperature);

        String response = webClient.post()
                .uri("/chat/completions")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .block();

        return parseContent(response);
    }

    private String toMessagesJson(List<Map<String, String>> messages) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < messages.size(); i++) {
            Map<String, String> m = messages.get(i);
            sb.append("{\"role\":\"").append(m.get("role")).append("\",")
              .append("\"content\":\"").append(escapeJson(m.get("content"))).append("\"}");
            if (i < messages.size() - 1) sb.append(",");
        }
        sb.append("]");
        return sb.toString();
    }

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private String parseContent(String json) {
        try {
            var root = objectMapper.readTree(json);
            var choices = root.get("choices");
            if (choices != null && choices.isArray() && choices.size() > 0) {
                var msg = choices.get(0).get("message");
                if (msg != null && msg.has("content")) {
                    return msg.get("content").asText();
                }
            }
            return getLocalFallbackResponse("");
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse DeepSeek response", e);
        }
    }
}