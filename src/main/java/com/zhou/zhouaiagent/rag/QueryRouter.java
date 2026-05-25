package com.zhou.zhouaiagent.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

/**
 * 查询路由器
 * 根据问题复杂度决定是否走 RAG 检索
 *
 * 简单问题（如问候、闲聊）直接让 LLM 回答，不走检索
 * 复杂问题（如专业知识、需要上下文）走 RAG 检索
 *
 * 设计原因：
 * - 减少不必要的检索开销
 * - 避免简单问题被无关文档干扰
 * - 提升响应速度
 */
@Slf4j
@Component
public class QueryRouter {

    private final ChatClient chatClient;

    public QueryRouter(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    /**
     * 判断查询是否需要走 RAG 检索
     *
     * @param query 用户查询
     * @return true 需要走 RAG，false 直接回答
     */
    public boolean shouldUseRag(String query) {
        if (query == null || query.trim().isEmpty()) {
            return false;
        }

        // 简单规则：查询长度小于 10 个字符且不包含问号，可能是闲聊
        String trimmed = query.trim();
        if (trimmed.length() < 10 && !trimmed.contains("?") && !trimmed.contains("？")) {
            log.debug("Query too short for RAG: {}", trimmed);
            return false;
        }

        // 关键词匹配：包含专业术语时走 RAG
        String[] ragKeywords = {"恋爱", "单身", "已婚", "婚姻", "感情", "分手", "复合",
                "追求", "约会", "沟通", "矛盾", "信任", "出轨", "家庭", "婆媳"};
        for (String keyword : ragKeywords) {
            if (trimmed.contains(keyword)) {
                log.debug("Query matches RAG keyword '{}': {}", keyword, trimmed);
                return true;
            }
        }

        // 默认走 RAG（保守策略）
        log.debug("Query defaults to RAG: {}", trimmed);
        return true;
    }

    /**
     * 获取查询类型标签（用于日志和可观测性）
     *
     * @param query 用户查询
     * @return 查询类型
     */
    public String getQueryType(String query) {
        return shouldUseRag(query) ? "RAG" : "DIRECT";
    }
}
