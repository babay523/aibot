package com.atome.bot.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class Chunker {

    @Value("${bot.sync.chunk-size}")
    private int chunkSize;

    @Value("${bot.sync.chunk-overlap}")
    private int chunkOverlap;

    /**
     * 将文本切分为重叠的块
     */
    public List<String> chunk(String text) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }

        List<String> chunks = new ArrayList<>();
        int length = text.length();
        int start = 0;

        while (start < length) {
            int end = Math.min(start + chunkSize, length);
            
            // 尝试在句子或段落边界切分
            if (end < length) {
                // 向前找最近的自然分隔
                int breakPoint = findBreakPoint(text, end);
                if (breakPoint > start) {
                    end = breakPoint;
                }
            }

            chunks.add(text.substring(start, end).trim());

            // 下一个起始位置（带重叠）
            start = end - chunkOverlap;
            if (start >= length) break;
        }

        return chunks;
    }

    /**
     * 向后查找合适的切分点（段落或句子）
     */
    private int findBreakPoint(String text, int target) {
        // 先找段落分隔
        int paragraph = text.lastIndexOf("\n\n", target);
        if (paragraph > target - 100 && paragraph > 0) {
            return paragraph;
        }

        // 再找句子分隔（.!? 后跟空格或大写）
        for (int i = target; i > target - 200 && i > 0; i--) {
            char c = text.charAt(i);
            if (c == '.' || c == '!' || c == '?') {
                if (i + 1 < text.length() && (text.charAt(i + 1) == ' ' || text.charAt(i + 1) == '\n')) {
                    return i + 1;
                }
            }
            // 中文标点
            if (c == '。' || c == '！' || c == '？') {
                return i + 1;
            }
        }

        // 最后找空格
        for (int i = target; i > target - 100 && i > 0; i--) {
            if (text.charAt(i) == ' ') {
                return i;
            }
        }

        return target;
    }
}