package com.atome.bot.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Component
public class OllamaClient {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Value("${ollama.embed-model}")
    private String embedModel;

    @Value("${ollama.probe-string}")
    private String probeString;

    public OllamaClient(@Value("${ollama.base-url}") String baseUrl, ObjectMapper objectMapper) {
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .build();
        this.objectMapper = objectMapper;
    }

    /**
     * 单次 embedding
     */
    public List<Float> embed(String text) {
        return embed(List.of(text)).get(0);
    }

    /**
     * 批量 embedding
     */
    public List<List<Float>> embed(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }

        // Remove nulls
        List<String> cleanTexts = texts.stream()
                .filter(t -> t != null && !t.isEmpty())
                .collect(Collectors.toList());

        if (cleanTexts.isEmpty()) {
            return List.of();
        }

        String requestBody = """
                {
                    "model": "%s",
                    "input": %s,
                    "keep_alive": "10m"
                }
                """.formatted(embedModel, toJsonArray(cleanTexts));

        String response = webClient.post()
                .uri("/api/embed")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .block();

        return parseEmbeddings(response, cleanTexts.size());
    }

    /**
     * 探测向量维度
     */
    public int probeDim() {
        List<Float> vec = embed(probeString);
        return vec.size();
    }

    private String toJsonArray(List<String> list) {
        return list.stream()
                .map(s -> "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"")
                .collect(Collectors.joining(",", "[", "]"));
    }

    private List<List<Float>> parseEmbeddings(String json, int expectedCount) {
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode embeddingsNode = root.get("embeddings");

            if (embeddingsNode == null || !embeddingsNode.isArray()) {
                throw new RuntimeException("Invalid Ollama embed response: embeddings field missing");
            }

            return StreamSupport.stream(embeddingsNode.spliterator(), false)
                    .map(this::toFloatList)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse Ollama embedding response", e);
        }
    }

    private List<Float> toFloatList(JsonNode node) {
        return StreamSupport.stream(node.spliterator(), false)
                .map(JsonNode::floatValue)
                .collect(Collectors.toList());
    }
}