package com.zhou.zhouaiagent.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Agentic RAG 服务
 * 整合查询路由、检索、兜底处理的完整 RAG Pipeline
 *
 * 设计原因：
 * - 查询路由：简单问题直接回答，复杂问题走 RAG
 * - 向量检索：基于语义相似度检索相关文档
 * - 兜底处理：检索为空时让 LLM 直接回答
 *
 * 面试价值：展示对 RAG 全链路的理解，包括检索策略、路由机制、兜底方案
 */
@Slf4j
@Service
public class AgenticRagService {

    private final ChatClient chatClient;
    private final DocumentRetriever documentRetriever;

    public AgenticRagService(ChatModel chatModel,
                             org.springframework.ai.vectorstore.VectorStore vectorStore) {
        this.chatClient = ChatClient.builder(chatModel).build();
        // 使用向量检索器（基于语义相似度）
        this.documentRetriever = VectorStoreDocumentRetriever.builder()
                .vectorStore(vectorStore)
                .similarityThreshold(0.5)
                .topK(3)
                .build();
    }

    /**
     * 执行 Agentic RAG 查询
     *
     * @param query    用户查询
     * @param chatId   会话 ID
     * @return 回答内容
     */
    public String query(String query, String chatId) {
        log.info("Agentic RAG query: {}", query);

        // 1. 查询路由：判断是否需要 RAG
        boolean needRag = shouldUseRag(query);
        log.info("Query type: {}", needRag ? "RAG" : "DIRECT");

        if (!needRag) {
            // 简单问题，直接让 LLM 回答
            return chatClient.prompt()
                    .user(query)
                    .call()
                    .content();
        }

        // 2. 混合检索
        Query ragQuery = new Query(query);
        List<Document> documents = documentRetriever.retrieve(ragQuery);
        log.info("Retrieved {} documents", documents.size());

        // 3. 兜底处理
        if (documents.isEmpty()) {
            log.info("No documents found, using fallback");
            return chatClient.prompt()
                    .user(query + "\n\n如果没有相关知识，请直接根据你的理解回答。")
                    .call()
                    .content();
        }

        // 4. 构建上下文并回答
        StringBuilder context = new StringBuilder();
        context.append("以下是相关的参考资料：\n\n");
        for (int i = 0; i < documents.size(); i++) {
            context.append(i + 1).append(". ").append(documents.get(i).getText()).append("\n\n");
        }
        context.append("请基于以上参考资料回答用户问题。如果资料不足以回答，请直接根据你的理解回答。");

        return chatClient.prompt()
                .system(context.toString())
                .user(query)
                .call()
                .content();
    }

    /**
     * 判断查询是否需要走 RAG
     */
    private boolean shouldUseRag(String query) {
        if (query == null || query.trim().isEmpty()) {
            return false;
        }

        String trimmed = query.trim();

        // 简单规则：太短且没有问号，可能是闲聊
        if (trimmed.length() < 10 && !trimmed.contains("?") && !trimmed.contains("？")) {
            return false;
        }

        // 关键词匹配：包含恋爱相关术语时走 RAG
        String[] ragKeywords = {"恋爱", "单身", "已婚", "婚姻", "感情", "分手", "复合",
                "追求", "约会", "沟通", "矛盾", "信任", "出轨", "家庭", "婆媳"};
        for (String keyword : ragKeywords) {
            if (trimmed.contains(keyword)) {
                return true;
            }
        }

        // 默认走 RAG
        return true;
    }
}
